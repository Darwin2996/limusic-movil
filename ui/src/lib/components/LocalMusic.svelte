<script lang="ts">
	// The Library page's Local tab: music that lives on this machine. Works signed out and offline,
	// because nothing here goes near YouTube (Rust `local.rs`). Albums open the normal album page
	// and songs play through the normal queue, so everything past this component is shared.
	import { onMount } from 'svelte';
	import { HugeiconsIcon } from '@hugeicons/svelte';
	import { open } from '@tauri-apps/plugin-dialog';
	import {
		Add01Icon,
		Delete02Icon,
		DriveIcon,
		PlayIcon,
		PlayListAddIcon,
		RefreshIcon,
		ShuffleIcon
	} from '@hugeicons/core-free-icons';
	import { Button } from '$lib/components/ui/button';
	import * as Tabs from '$lib/components/ui/tabs';
	import MediaCard from './MediaCard.svelte';
	import MediaCardSkeleton from './MediaCardSkeleton.svelte';
	import ErrorState from './ErrorState.svelte';
	import TrackFilter from './TrackFilter.svelte';
	import TrackRow from './TrackRow.svelte';
	import TrackSelectButton from './TrackSelectButton.svelte';
	import TrackSelectionBar from './TrackSelectionBar.svelte';
	import { trackSelection } from '$lib/selection.svelte';
	import * as api from '$lib/api';
	import { indexCards, indexSongs, match } from '$lib/localsearch';
	import { t } from '$lib/i18n.svelte';
	import {
		addLocalFolder,
		local,
		openAddToPlaylist,
		openNewPlaylist,
		openPlayer,
		playback,
		removeLocalFolder,
		scanLocal,
		toast
	} from '$lib/player.svelte';

	// Files come and go while the app runs, so the tab rescans when you open it (a no-op scan is a
	// stat per file). The startup scan is what keeps deleted music out of the home grid.
	onMount(() => {
		scanLocal();
	});

	let view = $state('albums');
	let pickingFolder = $state(false);
	// A local collection can be thousands of files, and WebKitGTK does not enjoy thousands of rows.
	// Render a page at a time — Play all and Shuffle still take every song in the list.
	const PAGE = 100;
	let shown = $state(PAGE);

	// Filtering the collection. It is all in memory already, so this is a scan and not a request:
	// no debounce, no loading state, the lists narrow as you type. The one cost worth dodging is
	// Svelte's — `local` is `$state`, so reading `song.title` for thousands of songs on every
	// keystroke goes through as many proxy traps — so `localsearch.ts` flattens the text into plain
	// strings once per library change and each keystroke scans those. Reading `ix` at all is behind
	// the empty-query check below, so a library nobody searches never pays for the pass.
	let query = $state('');
	const ix = $derived({
		songs: indexSongs(local.songs),
		albums: indexCards(local.albums),
		artists: indexCards(local.artists)
	});
	const q = $derived(query.trim());
	const songs = $derived(q ? match(ix.songs, q) : local.songs);
	const albums = $derived(q ? match(ix.albums, q) : local.albums);
	const artists = $derived(q ? match(ix.artists, q) : local.artists);

	$effect(() => {
		q; // a narrower list starts from the first page again
		shown = PAGE;
	});

	// Same shape as the playlist page and home: one page per approach to the bottom. Nothing is
	// fetched here (the whole library is already in memory), so this only grows how much of it is
	// rendered — no loading state, nothing that can fail.
	function sentinel(node: HTMLElement) {
		const io = new IntersectionObserver(([e]) => e.isIntersecting && (shown += PAGE), {
			rootMargin: '600px 0px'
		});
		io.observe(node);
		return () => io.disconnect();
	}
	const nowId = $derived(playback.now?.videoId);
	// Picking songs to put in a playlist: the selection bar's "Add to playlist" offers a new one,
	// which for files on this phone is a playlist on this phone.
	const selection = trackSelection(() => songs, () => songs.slice(0, shown), () => 'local');
	// The song list as one queue — what the Play/Shuffle buttons above it do, and what the queue
	// panel calls it. Not a `playFrom`: there's no page behind "the music on this disk", so it has
	// no business landing in recents or the sidebar's last-played order.
	const SOURCE = $derived(t('local.source'));
	const folderLabel = (path: string) => /Android/i.test(navigator.userAgent)
		? path.replaceAll('\\', '/').split('/').filter(Boolean).at(-1) ?? path
		: path;

	async function pickFolder() {
		pickingFolder = true;
		try {
			const picked = /Android/i.test(navigator.userAgent)
				? await api.pickAndroidLocalFolder()
				: await open({ directory: true, multiple: false, title: t('local.pick_folder_dialog') });
			if (typeof picked === 'string') await addLocalFolder(picked);
		} catch (e) {
			const message = String(e);
			toast.error(message.includes('LOCAL_FOLDER_EMPTY') ? t('local.android_empty') : message.includes('LOCAL_FOLDER_READ_FAILED') ? t('local.android_read_failed') : message);
		} finally {
			pickingFolder = false;
		}
	}

	// The filtered list, not the whole library: Play all plays what the list shows.
	function playAll(shuffle: boolean) {
		if (!songs.length) return;
		openPlayer();
		api.playPlaylist(songs, null, undefined, SOURCE, shuffle);
	}

	async function forget(path: string) {
		await removeLocalFolder(path);
		if (/Android/i.test(navigator.userAgent)) {
			try { await api.deleteAndroidLocalFolder(path); }
			catch (e) {
				const message = String(e);
				toast.error(message.includes('LOCAL_FOLDER_DELETE') ? t('local.android_delete_failed') : message);
				return;
			}
		}
		toast.success(t('toasts.folder_removed'));
	}
</script>

<div class="flex flex-col gap-5">
	<!-- Folders -->
	<div class="rounded-xl border bg-card/40 p-4">
		<div class="mb-3 flex flex-col items-stretch justify-between gap-3 sm:flex-row sm:items-center">
			<div class="min-w-0">
				<div class="flex items-center gap-2 font-medium">
					<HugeiconsIcon icon={DriveIcon} class="h-4 w-4" /> {t('local.folders')}
				</div>
				<p class="mt-0.5 text-xs text-muted-foreground">{t('local.folders_hint')}</p>
				{#if /Android/i.test(navigator.userAgent)}
					<p class="mt-1 text-xs text-muted-foreground">{t('local.android_copy_hint')}</p>
				{/if}
			</div>
			<div class="flex flex-wrap gap-2 sm:shrink-0">
				<Button
					variant="ghost"
					size="sm"
					class="gap-2"
					disabled={local.loading || !local.folders.length}
					onclick={() => scanLocal()}
				>
					<HugeiconsIcon icon={RefreshIcon} class="h-4 w-4" />
					{local.loading ? t('local.scanning') : t('local.rescan')}
				</Button>
				{#if local.folders.length}
					<Button variant="outline" size="sm" class="gap-2" onclick={pickFolder} disabled={pickingFolder}>
						<HugeiconsIcon icon={Add01Icon} class="h-4 w-4" /> {pickingFolder ? t('common.loading') : t('local.add_folder')}
					</Button>
				{/if}
			</div>
		</div>
		{#if local.folders.length}
			<ul class="flex flex-col gap-1">
				{#each local.folders as folder (folder)}
					<li class="flex items-center justify-between gap-3 rounded-lg px-2 py-1.5 hover:bg-accent/10">
						<span class="truncate font-mono text-xs text-muted-foreground" title={folder}>
							{folderLabel(folder)}
						</span>
						<button
							class="flex h-11 w-11 shrink-0 cursor-pointer items-center justify-center rounded-full text-muted-foreground transition hover:bg-destructive/10 hover:text-destructive"
							aria-label={t('a11y.remove_folder')}
							onclick={() => forget(folder)}
						>
							<HugeiconsIcon icon={Delete02Icon} class="h-4 w-4" />
						</button>
					</li>
				{/each}
			</ul>
		{:else}
			<div class="mt-4 flex flex-col items-start gap-3 rounded-xl border border-dashed bg-background/35 p-4">
				<p class="text-sm leading-relaxed text-muted-foreground">{t('local.no_folders')}</p>
				<Button class="gap-2" onclick={pickFolder} disabled={pickingFolder}>
					<HugeiconsIcon icon={Add01Icon} class="h-4 w-4" />
					{pickingFolder ? t('common.loading') : t('local.add_folder')}
				</Button>
			</div>
		{/if}
	</div>

	{#if local.error}
		<ErrorState message={local.error} onRetry={() => scanLocal()} />
	{:else if local.loading && !local.scanned}
		<div class="card-grid">
			{#each Array(6) as _, i (i)}
				<MediaCardSkeleton />
			{/each}
		</div>
	{:else if local.songs.length}
		<Tabs.Root bind:value={view}>
			<!-- The counts follow the filter, so the tabs say where the matches are. -->
			<div class="mb-4 flex flex-wrap items-center justify-between gap-3">
				<Tabs.List>
					<Tabs.Trigger value="albums">{t('local.albums_count', { count: albums.length })}</Tabs.Trigger>
					<Tabs.Trigger value="artists">{t('local.artists_count', { count: artists.length })}</Tabs.Trigger>
					<Tabs.Trigger value="songs">{t('local.songs_count', { count: songs.length })}</Tabs.Trigger>
				</Tabs.List>
				<TrackFilter bind:value={query} placeholder={t('common.search_your_music')} />
			</div>
			{#if q && !songs.length && !albums.length && !artists.length}
				<p class="text-sm text-muted-foreground">{t('local.nothing_matches_device', { query: q })}</p>
			{/if}
			<!-- Gated on `view`: bits-ui hides an inactive panel rather than unmounting it, so all three
			     views of the same collection would be built on every visit. -->
			<Tabs.Content value="albums">
				{#if view === 'albums'}
					<div class="card-grid content-in">
						{#each albums as album (album.id)}
							<MediaCard item={album} />
						{/each}
					</div>
				{/if}
			</Tabs.Content>
			<Tabs.Content value="artists">
				{#if view === 'artists'}
					<div class="card-grid content-in">
						{#each artists as artist (artist.id)}
							<MediaCard item={artist} />
						{/each}
					</div>
				{/if}
			</Tabs.Content>
			<Tabs.Content value="songs">
				{#if view === 'songs'}
					<div class="mb-3 flex flex-wrap items-center gap-2">
						<Button
							size="sm"
							class="gap-2 rounded-full"
							disabled={!songs.length}
							onclick={() => playAll(false)}
						>
							<HugeiconsIcon icon={PlayIcon} class="h-4 w-4" /> {t('common.play_all')}
						</Button>
						<Button
							size="sm"
							variant="outline"
							class="gap-2 rounded-full"
							disabled={!songs.length}
							onclick={() => playAll(true)}
						>
							<HugeiconsIcon icon={ShuffleIcon} class="h-4 w-4" /> {t('common.shuffle')}
						</Button>
						<!-- Every song the list shows (so a search narrows it first) into a new playlist. -->
						<Button
							size="sm"
							variant="outline"
							class="gap-2 rounded-full"
							disabled={!songs.length}
							onclick={() => openNewPlaylist(songs)}
						>
							<HugeiconsIcon icon={PlayListAddIcon} class="h-4 w-4" /> {t('local.create_playlist')}
						</Button>
						<TrackSelectButton {selection} />
					</div>
					<div class="content-in">
						{#each songs.slice(0, shown) as song, i (song.video_id)}
							<TrackRow
								{song}
								index={i}
								lazy
								active={song.video_id === nowId}
								{selection}
								selectionKey={selection.visibleKeys[i]}
								onplay={() => {
									openPlayer();
									api.playPlaylist(songs, i, undefined, SOURCE);
								}}
								onAdd={() => openAddToPlaylist(song)}
							/>
						{/each}
					</div>
					{#if songs.length > shown}
						<div {@attach sentinel}></div>
					{/if}
					<TrackSelectionBar {selection} from={SOURCE} />
				{/if}
			</Tabs.Content>
		</Tabs.Root>
	{:else if local.folders.length}
		<p class="text-sm text-muted-foreground">{t('local.nothing_playable')}</p>
	{/if}
</div>
