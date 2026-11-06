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
import { ui } from '$lib/player.svelte';

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

/** An invite older than this is a room that has most likely ended. */
const INVITE_TTL_MS = 60 * 60 * 1000;
const SHARING_KEY = 'friends_share_playing';

class Friends {
	/** Signed in and the profile (or its absence) known. */
	ready = $state(false);
	me = $state<Profile | null>(null);
	list = $state<Friend[]>([]);
	invites = $state<Invite[]>([]);
	/** Publish what this phone plays to accepted friends. */
	sharing = $state(readSharing());
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
		await Promise.all([loadFriends(), loadInvites()]);
		subscribe();
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

export async function updateProfile(patch: Partial<Pick<Profile, 'display_name' | 'bio' | 'color'>>) {
	const me = friends.me;
	if (!me) return;
	const { error } = await db().from('profiles').update(patch).eq('id', me.id);
	if (error) throw error;
	friends.me = { ...me, ...patch };
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
