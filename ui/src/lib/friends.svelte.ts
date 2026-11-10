// Friends: a LiMusic @username and profile, friend requests, what accepted friends are playing, and
// Listen Together invites between them. Backed by the Supabase project that supabase/amigos.sql
// sets up; every privacy rule (who sees what someone plays, who can invite whom) is enforced there,
// in row level security, not here.
//
// Each install signs in anonymously (no email, nothing from Google), so the account is this phone's
// session: reinstalling the app starts a new one.
import { createClient, type RealtimeChannel, type SupabaseClient } from '@supabase/supabase-js';
import * as api from '$lib/api';
import { lt, makeInvite, parseInvite, inRoom } from '$lib/lt.svelte';
import { auth, ui } from '$lib/player.svelte';

/** The project (Project Settings ▸ API). The anon key is meant to ship in the app: what it can do is
 *  bounded by the row level security in supabase/amigos.sql. Empty: friends are switched off. */
const SUPABASE_URL = 'https://qnjupsustazvjfmqgfid.supabase.co';
const SUPABASE_ANON_KEY = 'sb_publishable_bvU1BlEGM8ES3K0sXGTrPA_wKKI1Fq6';
export const friendsConfigured = Boolean(SUPABASE_URL && SUPABASE_ANON_KEY);

export type Profile = {
	id: string;
	username: string;
	display_name: string;
	bio: string;
	color: string;
	/** Their photo (an upload, or their Google picture); null shows the colored initial. */
	avatar_url: string | null;
};
export type NowPlaying = {
	user_id: string;
	video_id: string | null;
	title: string | null;
	artist: string | null;
	thumbnail: string | null;
	is_playing: boolean;
	updated_at: string;
};
export type Friend = {
	profile: Profile;
	/** `incoming`: they asked you; `outgoing`: you asked them and they haven't answered. */
	status: 'accepted' | 'incoming' | 'outgoing';
	nowPlaying: NowPlaying | null;
};
export type Invite = { id: string; from_user: string; invite: string; created_at: string };
/** A playlist someone shares with their friends, without its tracks (`sharedPlaylistTracks`). */
export type SharedPlaylist = {
	owner: string;
	source_id: string;
	title: string;
	thumbnail: string | null;
	track_count: number;
	updated_at: string;
};
/** A shared track: the fields a friend needs to see and play it. */
export type SharedTrack = Pick<api.SongItem, 'video_id' | 'title' | 'artists' | 'thumbnail' | 'duration'>;

/** An invite older than this is a room that has most likely ended. */
const INVITE_TTL_MS = 60 * 60 * 1000;
const SHARING_KEY = 'friends_share_playing';
/** Set once the person removes their photo, so their Google picture isn't put back on the next start. */
const NO_GOOGLE_AVATAR_KEY = 'friends_no_google_avatar';
/** A shared playlist is copied again on start when its copy is older than this. */
const RESHARE_AFTER_MS = 12 * 60 * 60 * 1000;
const MAX_SHARED_TRACKS = 1000;
const SHARED_LIST_COLUMNS = 'owner, source_id, title, thumbnail, track_count, updated_at';

class Friends {
	/** Signed in and the profile (or its absence) known. */
	ready = $state(false);
	me = $state<Profile | null>(null);
	list = $state<Friend[]>([]);
	invites = $state<Invite[]>([]);
	/** Publish what this phone plays to accepted friends. */
	sharing = $state(readSharing());
	/** The playlists you share (`sharePlaylist`). */
	shared = $state<SharedPlaylist[]>([]);
	error = $state<string | null>(null);

	profileOf(id: string): Profile | undefined {
		return this.list.find((f) => f.profile.id === id)?.profile;
	}
}
export const friends = new Friends();

let client: SupabaseClient | null = null;
let channel: RealtimeChannel | null = null;

function readSharing(): boolean {
	try {
		return localStorage.getItem(SHARING_KEY) !== 'off';
	} catch {
		return true;
	}
}

function db(): SupabaseClient {
	if (!client) throw new Error('friends are not configured');
	return client;
}

/** Sign in (anonymously, the first time), load the profile and friends, and follow live changes.
 *  Returns the teardown. A no-op when no project is configured. */
export function initFriends(): () => void {
	if (!friendsConfigured) return () => {};
	client = createClient(SUPABASE_URL, SUPABASE_ANON_KEY, {
		auth: { persistSession: true, autoRefreshToken: true }
	});
	(async () => {
		try {
			const { data } = await db().auth.getSession();
			if (!data.session) {
				const { error } = await db().auth.signInAnonymously();
				if (error) throw error;
			}
			await loadMe();
		} catch (e) {
			friends.error = String(e instanceof Error ? e.message : e);
		} finally {
			friends.ready = true;
		}
	})();
	return () => {
		channel?.unsubscribe();
		channel = null;
	};
}

async function uid(): Promise<string> {
	const { data } = await db().auth.getUser();
	if (!data.user) throw new Error('not signed in');
	return data.user.id;
}

async function loadMe() {
	const id = await uid();
	const { data, error } = await db().from('profiles').select('*').eq('id', id).maybeSingle();
	if (error) throw error;
	friends.me = data as Profile | null;
	if (friends.me) {
		await Promise.all([loadFriends(), loadInvites(), loadShared()]);
		subscribe();
		useGoogleAvatarByDefault().catch(() => {});
		refreshShared().catch(() => {});
	}
}

/** Pick the @username (and a display name) the first time. Throws `username_taken` or
 *  `username_invalid` for the form to show. */
export async function createProfile(username: string, displayName: string) {
	const name = username.trim();
	if (!/^[A-Za-z0-9_.]{3,20}$/.test(name)) throw new Error('username_invalid');
	const { error } = await db()
		.from('profiles')
		.insert({ id: await uid(), username: name, display_name: displayName.trim().slice(0, 40) });
	if (error) throw new Error(error.code === '23505' ? 'username_taken' : error.message);
	await loadMe();
}

export async function updateProfile(
	patch: Partial<Pick<Profile, 'display_name' | 'bio' | 'color' | 'avatar_url'>>
) {
	const me = friends.me;
	if (!me) return;
	const { error } = await db().from('profiles').update(patch).eq('id', me.id);
	if (error) throw error;
	friends.me = { ...me, ...patch };
}

/** The Google account's picture, when signed in to YouTube Music with one. */
function googlePicture(): string | null {
	const url = auth.account?.signedIn ? auth.account.thumbnail : null;
	return url && /^https:\/\/(lh3\.googleusercontent\.com|yt3\.ggpht\.com|yt3\.googleusercontent\.com)\//.test(url)
		? url
		: null;
}
export const hasGooglePicture = () => googlePicture() !== null;

/** No photo yet: start with the Google one, unless the person removed their photo before. Also
 *  called once the account is known, which can be after the profile loads. */
export async function useGoogleAvatarByDefault() {
	if (friends.me?.avatar_url) return;
	try {
		if (localStorage.getItem(NO_GOOGLE_AVATAR_KEY)) return;
	} catch {
		/* no storage: take the default */
	}
	const url = googlePicture();
	if (url) await updateProfile({ avatar_url: url });
}

export async function useGoogleAvatar() {
	const url = googlePicture();
	if (!url) return;
	await clearUploads();
	await updateProfile({ avatar_url: url });
	try {
		localStorage.removeItem(NO_GOOGLE_AVATAR_KEY);
	} catch {
		/* the default then comes back on its own anyway */
	}
}

/** Upload a picture from the phone as the photo: cropped to a centered square, 256 px JPEG. */
export async function uploadAvatar(file: File) {
	const me = friends.me;
	if (!me) return;
	const image = await createImageBitmap(file);
	const side = Math.min(image.width, image.height);
	const canvas = document.createElement('canvas');
	canvas.width = canvas.height = 256;
	canvas
		.getContext('2d')!
		.drawImage(image, (image.width - side) / 2, (image.height - side) / 2, side, side, 0, 0, 256, 256);
	image.close();
	const blob = await new Promise<Blob>((resolve, reject) =>
		canvas.toBlob((b) => (b ? resolve(b) : reject(new Error('image'))), 'image/jpeg', 0.85)
	);
	await clearUploads();
	// A new name per upload, so friends' phones don't keep showing the cached old one.
	const path = `${me.id}/${Date.now()}.jpg`;
	const { error } = await db().storage.from('avatars').upload(path, blob, { contentType: 'image/jpeg' });
	if (error) throw error;
	await updateProfile({ avatar_url: db().storage.from('avatars').getPublicUrl(path).data.publicUrl });
}

export async function removeAvatar() {
	await clearUploads();
	await updateProfile({ avatar_url: null });
	try {
		localStorage.setItem(NO_GOOGLE_AVATAR_KEY, '1');
	} catch {
		/* the Google picture may then come back on the next start */
	}
}

/** Delete the photos this person uploaded before. */
async function clearUploads() {
	const me = friends.me;
	if (!me) return;
	const { data } = await db().storage.from('avatars').list(me.id);
	if (data?.length) await db().storage.from('avatars').remove(data.map((f) => `${me.id}/${f.name}`));
}

async function loadShared() {
	const me = friends.me;
	if (!me) return;
	const { data, error } = await db().from('shared_playlists').select(SHARED_LIST_COLUMNS).eq('owner', me.id);
	if (error) throw error;
	friends.shared = (data ?? []) as SharedPlaylist[];
}

/** Share a playlist from the library with friends: a copy of its tracks, up to 1000. Tracks that
 *  are files on this phone are left out, since a friend couldn't play them. Sharing it again
 *  refreshes the copy. */
export async function sharePlaylist(item: Pick<api.BrowseItem, 'id' | 'title' | 'thumbnail'>) {
	const me = friends.me;
	if (!me) return;
	const page = await api.getPlaylist(item.id);
	const songs = [...page.items];
	let token = page.continuation;
	while (token && songs.length < MAX_SHARED_TRACKS) {
		const more = await api.getPlaylistMore(token);
		songs.push(...more.items);
		token = more.continuation;
	}
	const tracks: SharedTrack[] = songs
		.filter((s) => s.video_id && !api.isLocalId(s.video_id))
		.slice(0, MAX_SHARED_TRACKS)
		.map((s) => ({
			video_id: s.video_id,
			title: s.title.slice(0, 300),
			artists: s.artists.slice(0, 300),
			thumbnail: s.thumbnail?.startsWith('https://') ? s.thumbnail.slice(0, 600) : undefined,
			duration: s.duration
		}));
	const thumbnail = page.thumbnail ?? item.thumbnail;
	const listed: SharedPlaylist = {
		owner: me.id,
		source_id: item.id,
		title: (page.title ?? item.title).slice(0, 200),
		thumbnail: thumbnail?.startsWith('https://') ? thumbnail.slice(0, 600) : null,
		track_count: tracks.length,
		updated_at: new Date().toISOString()
	};
	const { error } = await db().from('shared_playlists').upsert({ ...listed, tracks });
	if (error) throw error;
	friends.shared = [...friends.shared.filter((p) => p.source_id !== item.id), listed];
}

export async function unsharePlaylist(sourceId: string) {
	const me = friends.me;
	if (!me) return;
	const { error } = await db().from('shared_playlists').delete().eq('owner', me.id).eq('source_id', sourceId);
	if (error) throw error;
	friends.shared = friends.shared.filter((p) => p.source_id !== sourceId);
}

/** Copy again, one at a time, the shared playlists whose copy has gone stale, so friends see
 *  what was added or removed since. A playlist that can't be read now keeps its last copy. */
async function refreshShared() {
	for (const p of friends.shared) {
		if (Date.now() - Date.parse(p.updated_at) < RESHARE_AFTER_MS) continue;
		await sharePlaylist({ id: p.source_id, title: p.title, thumbnail: p.thumbnail ?? undefined }).catch(() => {});
	}
}

/** The playlists a friend shares (row level security returns nothing for anyone else). */
export async function friendPlaylists(id: string): Promise<SharedPlaylist[]> {
	const { data, error } = await db()
		.from('shared_playlists')
		.select(SHARED_LIST_COLUMNS)
		.eq('owner', id)
		.order('title');
	if (error) throw error;
	return (data ?? []) as SharedPlaylist[];
}

export async function sharedPlaylistTracks(owner: string, sourceId: string): Promise<SharedTrack[]> {
	const { data, error } = await db()
		.from('shared_playlists')
		.select('tracks')
		.eq('owner', owner)
		.eq('source_id', sourceId)
		.maybeSingle();
	if (error) throw error;
	return (data?.tracks ?? []) as SharedTrack[];
}

export async function loadFriends() {
	const me = friends.me;
	if (!me) return;
	const { data: rows, error } = await db()
		.from('friendships')
		.select('requester, addressee, status');
	if (error) throw error;
	const links = (rows ?? []) as { requester: string; addressee: string; status: string }[];
	const others = links.map((l) => (l.requester === me.id ? l.addressee : l.requester));
	if (!others.length) {
		friends.list = [];
		return;
	}
	const [{ data: profiles }, { data: playing }] = await Promise.all([
		db().from('profiles').select('*').in('id', others),
		// Row level security returns accepted friends' rows only.
		db().from('now_playing').select('*').in('user_id', others)
	]);
	friends.list = links
		.map((l): Friend | null => {
			const other = l.requester === me.id ? l.addressee : l.requester;
			const profile = (profiles as Profile[] | null)?.find((p) => p.id === other);
			if (!profile) return null;
			const status =
				l.status === 'accepted' ? 'accepted' : l.requester === me.id ? 'outgoing' : 'incoming';
			const nowPlaying = (playing as NowPlaying[] | null)?.find((n) => n.user_id === other) ?? null;
			return { profile, status, nowPlaying };
		})
		.filter((f): f is Friend => f !== null)
		.sort((a, b) => a.profile.username.localeCompare(b.profile.username));
}

/** Send a friend request by @username, or accept theirs if they already sent one. Throws
 *  `not_found`, `self` or `already` for the form to show. */
export async function addFriend(username: string) {
	const me = friends.me;
	if (!me) return;
	const { data: other } = await db()
		.from('profiles')
		.select('*')
		.eq('username', username.trim().replace(/^@/, ''))
		.maybeSingle();
	if (!other) throw new Error('not_found');
	if (other.id === me.id) throw new Error('self');
	const existing = friends.list.find((f) => f.profile.id === other.id);
	if (existing?.status === 'incoming') return acceptFriend(other.id);
	if (existing) throw new Error('already');
	const { error } = await db().from('friendships').insert({ requester: me.id, addressee: other.id });
	if (error) throw error;
	await loadFriends();
}

export async function acceptFriend(id: string) {
	const me = friends.me;
	if (!me) return;
	const { error } = await db()
		.from('friendships')
		.update({ status: 'accepted' })
		.eq('requester', id)
		.eq('addressee', me.id);
	if (error) throw error;
	await loadFriends();
}

/** Decline a request, cancel yours, or unfriend: the link goes either way. */
export async function removeFriend(id: string) {
	const me = friends.me;
	if (!me) return;
	const { error } = await db()
		.from('friendships')
		.delete()
		.or(`and(requester.eq.${me.id},addressee.eq.${id}),and(requester.eq.${id},addressee.eq.${me.id})`);
	if (error) throw error;
	await loadFriends();
}

let lastPublished = '';
/** What this phone is playing, for friends. Only sends on a change of track or of play state. */
export async function publishNowPlaying(
	now: { videoId: string; title: string; artists: string; thumbnail?: string | null } | null,
	playing: boolean
) {
	const me = friends.me;
	if (!me || !friends.sharing) return;
	const key = `${now?.videoId ?? ''}:${playing}`;
	if (key === lastPublished) return;
	lastPublished = key;
	await db()
		.from('now_playing')
		.upsert({
			user_id: me.id,
			video_id: now && !api.isLocalId(now.videoId) ? now.videoId : null,
			title: now?.title ?? null,
			artist: now?.artists ?? null,
			thumbnail: now?.thumbnail ?? null,
			is_playing: playing,
			updated_at: new Date().toISOString()
		});
}

/** Stop (or resume) sharing what this phone plays. Off also clears what friends last saw. */
export async function setSharing(on: boolean) {
	friends.sharing = on;
	lastPublished = '';
	try {
		localStorage.setItem(SHARING_KEY, on ? 'on' : 'off');
	} catch {
		/* the choice then lasts until restart */
	}
	if (!on && friends.me) await db().from('now_playing').delete().eq('user_id', friends.me.id);
}

async function loadInvites() {
	const me = friends.me;
	if (!me) return;
	const since = new Date(Date.now() - INVITE_TTL_MS).toISOString();
	const { data } = await db()
		.from('invites')
		.select('*')
		.eq('to_user', me.id)
		.gte('created_at', since)
		.order('created_at', { ascending: false });
	friends.invites = (data ?? []) as Invite[];
}

/** Invite a friend to Listen Together: host a room first if not in one, then send its invite. */
export async function inviteFriend(id: string) {
	const me = friends.me;
	if (!me) return;
	if (!inRoom()) await api.ltCreateRoom(me.display_name || me.username);
	// The room code arrives with the next `lt-state` from the backend.
	for (let i = 0; i < 50 && !lt.roomCode; i++) await new Promise((r) => setTimeout(r, 200));
	if (!lt.roomCode) throw new Error('no_room');
	const { error } = await db()
		.from('invites')
		.insert({ from_user: me.id, to_user: id, invite: makeInvite(lt.serverUrl, lt.roomCode) });
	if (error) throw error;
}

/** Join the room an invite is for, as the Listen Together dialog's own Join does. */
export async function acceptInvite(invite: Invite) {
	const me = friends.me;
	const parsed = parseInvite(invite.invite);
	await dismissInvite(invite);
	if (!me || !parsed?.code) throw new Error('bad_invite');
	const server = parsed.server && parsed.server !== lt.defaultServerUrl ? parsed.server : '';
	if (server !== lt.serverUrl) await api.ltSetServerUrl(server);
	await api.ltJoinRoom(parsed.code, me.display_name || me.username);
	ui.ltOpen = true;
}

export async function dismissInvite(invite: Invite) {
	friends.invites = friends.invites.filter((i) => i.id !== invite.id);
	await db().from('invites').delete().eq('id', invite.id);
}

/** Live: friends' tracks as they change, requests and accepts, and invites as they arrive. */
function subscribe() {
	const me = friends.me;
	if (!me || channel) return;
	channel = db()
		.channel('friends')
		.on('postgres_changes', { event: '*', schema: 'public', table: 'now_playing' }, (change) => {
			const row = (change.new ?? change.old) as NowPlaying | undefined;
			if (!row?.user_id) return;
			const friend = friends.list.find((f) => f.profile.id === row.user_id);
			if (friend) friend.nowPlaying = change.eventType === 'DELETE' ? null : (change.new as NowPlaying);
		})
		.on('postgres_changes', { event: '*', schema: 'public', table: 'friendships' }, () => {
			loadFriends().catch(() => {});
		})
		.on(
			'postgres_changes',
			{ event: 'INSERT', schema: 'public', table: 'invites', filter: `to_user=eq.${me.id}` },
			(change) => {
				friends.invites = [change.new as Invite, ...friends.invites];
				// The sender may be a friend whose profile isn't loaded yet (a request just accepted).
				if (!friends.profileOf((change.new as Invite).from_user)) loadFriends().catch(() => {});
			}
		)
		.subscribe();
}
