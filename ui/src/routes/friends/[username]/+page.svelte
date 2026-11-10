<script lang="ts">
	// Someone's LiMusic profile: photo, name and bio for anyone signed in; for their friends, or for
	// anyone when they made it public, also their shared playlists and the stats page their phone
	// published, plus how compatible their listening is with yours. What they play right now stays
	// for friends only. The data and its privacy rules: lib/friends.svelte.ts, supabase/amigos*.sql.
	import { page } from '$app/state';
	import { goto } from '$app/navigation';
	import { HugeiconsIcon } from '@hugeicons/svelte';
	import {
		LockIcon,
		MusicNote01Icon,
		PlayIcon,
		UserAdd01Icon,
		UserGroup02Icon
	} from '@hugeicons/core-free-icons';
	import * as Dialog from '$lib/components/ui/dialog';
	import { Button } from '$lib/components/ui/button';
	import { Switch } from '$lib/components/ui/switch';
	import StatsView from '$lib/components/StatsView.svelte';
	import TrackRow from '$lib/components/TrackRow.svelte';
	import * as api from '$lib/api';
	import { openAddToPlaylist, openPlayer, playback, playSong, toast } from '$lib/player.svelte';
	import { thumb } from '$lib/thumb';
	import { currentLocale, t } from '$lib/i18n.svelte';
	import { tagOf, type Range } from '$lib/stats';
	import {
		acceptFriend,
		addFriend,
		compatibility,
		friendPlaylists,
		friends,
		inviteFriend,
		profileByUsername,
		profileStats,
		removeFriend,
		setPublicProfile,
		sharedPlaylistTracks,
		type PublishedStats,
		type Profile,
		type SharedPlaylist,
		type SharedTrack
	} from '$lib/friends.svelte';

	const username = $derived(page.params.username ?? '');

	let profile = $state<Profile | null>(null);
	let missing = $state(false);
	let published = $state.raw<{ data: PublishedStats; updated_at: string } | null>(null);
	let mine = $state.raw<PublishedStats | null>(null);
	let lists = $state<SharedPlaylist[]>([]);
	let range = $state<Range>('month');
	let busy = $state(false);
	let openList = $state<SharedPlaylist | null>(null);
	let openTracks = $state<SharedTrack[] | null>(null);

	const isMe = $derived(!!profile && profile.id === friends.me?.id);
	const link = $derived(profile ? friends.list.find((f) => f.profile.id === profile!.id) : undefined);
	const isFriend = $derived(link?.status === 'accepted');
	const canSee = $derived(!!profile && (isMe || isFriend || profile.public_profile));
	const match = $derived(!isMe && published && mine ? compatibility(mine, published.data) : null);
	const stats = $derived(published?.data.ranges[range] ?? null);
	const tag = $derived(tagOf(currentLocale.id));

	// Loads on arrival and again on whatever can change what this profile shows you: another
	// @username, becoming friends, or them opening their profile.
	$effect(() => {
		const name = username;
		const me = friends.me;
		isFriend;
		if (!me) return;
		let stale = false;
		(async () => {
			try {
				const p = name === me.username ? me : await profileByUsername(name);
				if (stale) return;
				profile = p;
				missing = !p;
				if (!p) return;
				const [theirs, ours, shared] = await Promise.all([
					profileStats(p.id),
					p.id === me.id ? Promise.resolve(null) : profileStats(me.id),
					friendPlaylists(p.id)
				]);
				if (stale) return;
				published = theirs;
				mine = ours?.data ?? null;
				lists = shared;
			} catch (e) {
				if (!stale) toast.error(t('friends.errors.generic', { error: String(e) }));
			}
		})();
		return () => (stale = true);
	});

	/** "3 min ago", "2 h ago": how old the published stats are. */
	function ago(iso: string): string {
		const mins = Math.round((Date.now() - Date.parse(iso)) / 60_000);
		const rtf = new Intl.RelativeTimeFormat(tag, { numeric: 'auto' });
		if (mins < 60) return rtf.format(-mins, 'minute');
		if (mins < 48 * 60) return rtf.format(-Math.round(mins / 60), 'hour');
		return rtf.format(-Math.round(mins / 1440), 'day');
	}

	async function run(action: () => Promise<unknown>, done?: string) {
		busy = true;
		try {
			await action();
			if (done) toast.success(done);
		} catch (e) {
			toast.error(t('friends.errors.generic', { error: e instanceof Error ? e.message : String(e) }));
		} finally {
			busy = false;
		}
	}

	async function showList(list: SharedPlaylist) {
		openList = list;
		openTracks = null;
		try {
			openTracks = await sharedPlaylistTracks(list.owner, list.source_id);
		} catch (e) {
			toast.error(t('friends.errors.generic', { error: String(e) }));
			openList = null;
		}
	}

	function playList(list: SharedPlaylist, tracks: SharedTrack[], start: number | null) {
		if (!tracks.length) return;
		openPlayer();
		api.playPlaylist(tracks, start, undefined, list.title).catch((e) => toast.error(String(e)));
	}
</script>

{#snippet avatar(p: Profile, size: string)}
	{#if p.avatar_url}
		<img
			src={p.avatar_url}
			alt=""
			class="shrink-0 rounded-full object-cover {size}"
			style="background:{p.color}"
			referrerpolicy="no-referrer"
		/>
	{:else}
		<span
			class="flex shrink-0 items-center justify-center rounded-full font-heading font-bold text-white {size}"
			style="background:{p.color}"
			aria-hidden="true"
		>
			{(p.display_name || p.username).trim()[0]?.toUpperCase() ?? '?'}
		</span>
	{/if}
{/snippet}

{#snippet cover(src: string | null | undefined, size: string)}
	{#if src}
		<img src={thumb(src, 240)} alt="" class="shrink-0 rounded-lg object-cover {size}" />
	{:else}
		<span class="flex shrink-0 items-center justify-center rounded-lg bg-muted {size}">
			<HugeiconsIcon icon={MusicNote01Icon} class="h-6 w-6 text-muted-foreground" />
		</span>
	{/if}
{/snippet}

{#if missing}
	<p class="p-6 text-sm text-muted-foreground">{t('friends.errors.not_found')}</p>
{:else if !profile}
	<p class="p-6 text-sm text-muted-foreground">{t('common.loading')}</p>
{:else}
	{@const p = profile}
	<div class="relative isolate">
		<!-- Their color as the page's wash, the way the stats page washes in the top cover. -->
		<div
			class="pointer-events-none absolute inset-x-0 top-0 -z-10 h-72 opacity-40"
			style="background:radial-gradient(100% 80% at 20% 0%, {p.color} 0%, transparent 70%)"
			aria-hidden="true"
		></div>
		<header class="flex flex-col items-center gap-3 px-4 pt-8 text-center sm:flex-row sm:items-end sm:gap-6 sm:px-6 sm:text-left">
			{@render avatar(p, 'size-28 text-5xl shadow-xl')}
			<div class="min-w-0 flex-1">
				<h1 class="truncate font-heading text-3xl font-bold tracking-tight">{p.display_name || p.username}</h1>
				<p class="text-sm text-muted-foreground">@{p.username}</p>
				{#if p.bio}<p class="mt-2 text-sm">{p.bio}</p>{/if}
			</div>
		</header>

		<div class="flex flex-wrap justify-center gap-2 px-4 pt-5 sm:justify-start sm:px-6">
			{#if isMe}
				<Button variant="outline" class="min-h-11" onclick={() => goto('/friends')}>{t('friends.edit')}</Button>
			{:else if isFriend}
				<Button class="min-h-11" disabled={busy} onclick={() => run(() => inviteFriend(p.id), t('friends.invite_sent'))}>
					<HugeiconsIcon icon={UserGroup02Icon} class="h-4 w-4" />
					{t('friends.invite')}
				</Button>
				<Button variant="ghost" class="min-h-11 text-destructive" disabled={busy} onclick={() => run(() => removeFriend(p.id))}>
					{t('friends.remove')}
				</Button>
			{:else if link?.status === 'incoming'}
				<Button class="min-h-11" disabled={busy} onclick={() => run(() => acceptFriend(p.id))}>{t('friends.accept')}</Button>
				<Button variant="ghost" class="min-h-11" disabled={busy} onclick={() => run(() => removeFriend(p.id))}>
					{t('friends.decline')}
				</Button>
			{:else if link?.status === 'outgoing'}
				<Button variant="outline" class="min-h-11" disabled={busy} onclick={() => run(() => removeFriend(p.id))}>
					{t('friends.cancel_request')}
				</Button>
			{:else}
				<Button class="min-h-11" disabled={busy} onclick={() => run(() => addFriend(p.username), t('friends.request_sent'))}>
					<HugeiconsIcon icon={UserAdd01Icon} class="h-4 w-4" />
					{t('friends.add')}
				</Button>
			{/if}
		</div>

		<div class="flex flex-col gap-10 px-4 pb-6 pt-8 sm:px-6">
			{#if isMe}
				<div class="flex items-center justify-between gap-4 rounded-2xl border bg-card p-4">
					<span class="text-sm">
						<span class="block font-medium">{t('friends.public_profile')}</span>
						<span class="block text-xs text-muted-foreground">{t('friends.public_profile_hint')}</span>
					</span>
					<Switch checked={p.public_profile} onCheckedChange={(on) => run(() => setPublicProfile(on))} />
				</div>
			{/if}

			{#if isFriend && link?.nowPlaying?.title}
				{@const n = link.nowPlaying}
				<button
					type="button"
					class="flex items-center gap-3 rounded-2xl border bg-card p-3 text-left hover:bg-muted"
					disabled={!n.video_id}
					onclick={() =>
						n.video_id &&
						playSong({ video_id: n.video_id, title: n.title ?? '', artists: n.artist ?? '', thumbnail: n.thumbnail ?? undefined })}
				>
					{@render cover(n.thumbnail, 'size-14')}
					<span class="min-w-0 flex-1">
						<span class="block text-xs text-muted-foreground">
							{n.is_playing ? t('friends.now_playing') : t('friends.last_played')}
						</span>
						<span class="block truncate font-medium">{n.title}</span>
						<span class="block truncate text-sm text-muted-foreground">{n.artist}</span>
					</span>
					<HugeiconsIcon icon={PlayIcon} class="h-5 w-5 shrink-0 text-muted-foreground" />
				</button>
			{/if}

			{#if !canSee}
				<div class="flex flex-col items-center gap-2 py-10 text-center">
					<HugeiconsIcon icon={LockIcon} class="h-8 w-8 text-muted-foreground" />
					<p class="max-w-sm text-sm text-muted-foreground">{t('friends.private_profile')}</p>
				</div>
			{:else}
				{#if match}
					<section class="flex flex-col gap-3">
						<h2 class="font-heading text-xl font-bold tracking-tight">{t('friends.compatibility')}</h2>
						<div class="flex items-center gap-4 rounded-2xl bg-foreground/[0.04] p-4">
							<span class="font-heading text-5xl font-bold tabular-nums text-primary">{match.score}%</span>
							<span class="text-sm text-muted-foreground">
								{match.artists.length
									? t('friends.compatibility_artists', { artists: match.artists.slice(0, 5).join(', ') })
									: t('friends.compatibility_none')}
							</span>
						</div>
						{#if match.songs.length}
							<div>
								<h3 class="mb-1 text-sm font-semibold text-muted-foreground">{t('friends.songs_in_common')}</h3>
								{#each match.songs as song, i (song.video_id)}
									<TrackRow
										{song}
										index={i}
										active={song.video_id === playback.now?.videoId}
										onplay={() => {
											openPlayer();
											api.playPlaylist(match.songs, i, undefined, t('friends.songs_in_common'));
										}}
										onAdd={() => openAddToPlaylist(song)}
									/>
								{/each}
							</div>
						{/if}
					</section>
				{/if}

				{#if lists.length}
					<section class="flex flex-col gap-3">
						<h2 class="font-heading text-xl font-bold tracking-tight">{isMe ? t('friends.your_playlists') : t('friends.their_playlists')}</h2>
						<div class="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
							{#each lists as list (list.source_id)}
								<button type="button" class="flex min-w-0 flex-col gap-2 rounded-xl p-2 text-left hover:bg-muted" onclick={() => showList(list)}>
									{@render cover(list.thumbnail, 'aspect-square w-full')}
									<span class="min-w-0">
										<span class="block truncate text-sm font-medium">{list.title}</span>
										<span class="block truncate text-xs text-muted-foreground">
											{t('friends.playlist_tracks', { count: list.track_count })}
										</span>
									</span>
								</button>
							{/each}
						</div>
					</section>
				{/if}
			{/if}
		</div>

		{#if canSee}
			<!-- -mt: StatsView brings its own top padding and backdrop. -->
			<div class="-mt-6">
				{#if published}
					<StatsView
						stats={stats}
						bind:range
						shown={range}
						title={isMe ? t('stats.title') : t('friends.their_stats')}
						empty={{ title: t('friends.stats_empty_title'), text: t('friends.stats_empty') }}
					>
						{#snippet footer()}
							<p class="text-xs text-muted-foreground">{t('friends.stats_updated', { when: ago(published!.updated_at) })}</p>
						{/snippet}
					</StatsView>
				{:else}
					<p class="px-4 pb-10 pt-6 text-sm text-muted-foreground sm:px-6">{t('friends.stats_none')}</p>
				{/if}
			</div>
		{/if}
	</div>

	<!-- One of their playlists -->
	<Dialog.Root open={openList !== null} onOpenChange={(o) => !o && (openList = null)}>
		<Dialog.Content
			class="sm:max-w-lg max-md:fixed max-md:inset-x-0 max-md:bottom-0 max-md:top-auto max-md:mb-0 max-md:max-w-none max-md:translate-y-0 max-md:rounded-t-3xl max-md:rounded-b-none max-md:pb-[max(env(safe-area-inset-bottom),1rem)]"
		>
			{#if openList}
				{@const list = openList}
				<div class="flex items-center gap-3 pr-8">
					{@render cover(list.thumbnail, 'size-16')}
					<div class="min-w-0 flex-1">
						<Dialog.Title class="truncate font-heading text-lg">{list.title}</Dialog.Title>
						<Dialog.Description>{t('friends.playlist_tracks', { count: list.track_count })}</Dialog.Description>
					</div>
				</div>
				<Button class="min-h-11" disabled={!openTracks?.length} onclick={() => openTracks && playList(list, openTracks, null)}>
					<HugeiconsIcon icon={PlayIcon} class="h-4 w-4" />
					{t('player.play')}
				</Button>
				<div class="-mx-2 max-h-[55vh] overflow-y-auto">
					{#if !openTracks}
						<p class="p-2 text-sm text-muted-foreground">{t('common.loading')}</p>
					{:else}
						{#each openTracks as track, i (i)}
							<TrackRow
								song={track}
								index={i}
								active={track.video_id === playback.now?.videoId}
								onplay={() => playList(list, openTracks!, i)}
								onAdd={() => openAddToPlaylist(track)}
							/>
						{:else}
							<p class="p-2 text-sm text-muted-foreground">{t('friends.playlist_empty')}</p>
						{/each}
					{/if}
				</div>
			{/if}
		</Dialog.Content>
	</Dialog.Root>
{/if}
