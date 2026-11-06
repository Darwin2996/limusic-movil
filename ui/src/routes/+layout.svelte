<script lang="ts">
	import './layout.css';
	import favicon from '$lib/assets/favicon.svg';
	import { ModeWatcher, mode } from 'mode-watcher';
	import { HugeiconsIcon } from '@hugeicons/svelte';
	import {
		CheckmarkCircle02Icon,
		AlertCircleIcon,
		InformationCircleIcon,
		Cancel01Icon,
		Refresh01Icon
	} from '@hugeicons/core-free-icons';
	import { browser } from '$app/environment';
	import { afterNavigate } from '$app/navigation';
	import { cancelRestore, restoreTo } from '$lib/scrollrestore';
	import { onMount } from 'svelte';
	import { getCurrentWindow } from '@tauri-apps/api/window';
	import { onBackButtonPress } from '@tauri-apps/api/app';
	import { fly } from 'svelte/transition';
	import { cubicOut } from 'svelte/easing';
	import {
		appearance,
		applyArtworkAccent,
		prewarmArtworkAccent,
		refreshArtworkAccent,
		initTheme
	} from '$lib/theme.svelte';
	import { loadAppIcon } from '$lib/appicon.svelte';
	import { thumb } from '$lib/thumb';
	import { t } from '$lib/i18n.svelte';
	import { blockForeignDrag, dragScroll } from '$lib/dnd';
	import { suppressNative } from '$lib/menu';
	import Sidebar from '$lib/components/Sidebar.svelte';
	import Titlebar from '$lib/components/Titlebar.svelte';
	import ResizeBorders from '$lib/components/ResizeBorders.svelte';
	import PlayerBar from '$lib/components/PlayerBar.svelte';
	import MobileNav from '$lib/components/MobileNav.svelte';
	import QueuePanel from '$lib/components/QueuePanel.svelte';
	import LyricsPanel from '$lib/components/LyricsPanel.svelte';
	import CommentsPanel from '$lib/components/CommentsPanel.svelte';
	import AddToPlaylist from '$lib/components/AddToPlaylist.svelte';
	import NewPlaylistDialog from '$lib/components/NewPlaylistDialog.svelte';
	import SettingsDialog from '$lib/components/SettingsDialog.svelte';
	import ShareDialog from '$lib/components/ShareDialog.svelte';
	import ChannelPicker from '$lib/components/ChannelPicker.svelte';
	import ListenTogether from '$lib/components/ListenTogether.svelte';
	import LinkDialog from '$lib/components/LinkDialog.svelte';
	import BatteryNotice from '$lib/components/BatteryNotice.svelte';
	import BackButton from '$lib/components/BackButton.svelte';
	import { page } from '$app/state';
	import ImportDialog from '$lib/components/ImportDialog.svelte';
	import { handleImportDrop, initImport } from '$lib/import.svelte';
	import MiniPlayer from '$lib/components/MiniPlayer.svelte';
	import NowPlaying from '$lib/components/NowPlaying.svelte';
	import TheaterMode from '$lib/components/TheaterMode.svelte';
	import VideoSurface from '$lib/components/VideoSurface.svelte';
	import CommandPalette from '$lib/components/CommandPalette.svelte';
	import KeyboardShortcuts from '$lib/components/KeyboardShortcuts.svelte';
	import { Button } from '$lib/components/ui/button';
	import { auth, initApp, isAndroid, np, playback, prefs, refreshView, toast, ui } from '$lib/player.svelte';
	import {
		androidMoveToBackground,
		installApkUpdate,
		isLocalId,
		openExternal,
		setMetered
	} from '$lib/api';
	import { checkApkUpdate, dismissApkUpdate, type ApkRelease } from '$lib/apkupdate';
	import { initDownloads } from '$lib/downloads.svelte';
	import { video } from '$lib/video.svelte';
	import { initWin, windowCorners } from '$lib/win.svelte';
	import { initZoom } from '$lib/zoom.svelte';
	import { initShortcuts } from '$lib/shortcuts';
	import { initErrorLog } from '$lib/errlog';
	import {
		updateState,
		availableMessage,
		installUpdate,
		openDownloadPage,
		checkForUpdatesQuiet,
		QUIET_INTERVAL_MS
	} from '$lib/updater.svelte';

	// SvelteKit's `snapshot` keys a saved scroll to the *history entry*: two visits to the same URL
	// each restore the position they left at, where a URL→scroll Map would collapse both onto the
	// last save. Kit 2.69 reads `.snapshot` off the bound component *instance* (the generated
	// root.svelte does `bind:this`), so it must be exported from this script, not the module. This
	// covers every route that scrolls `<main>` (`$lib/scrollrestore`); /playlist/[id] scrolls its
	// own container and exports a snapshot of its own.
	let scroller = $state<HTMLElement | null>(null);
	export const snapshot = {
		capture: () => (scroller ? scroller.scrollTop : null),
		restore: (y: unknown) => {
			if (!scroller || typeof y !== 'number') return;
			restoreTo(scroller, y);
		}
	};
	afterNavigate((nav) => {
		if (!scroller || !nav.to) return;
		// Kit invokes snapshot.restore after afterNavigate on its own, so back/forward needs
		// nothing here. Direct navigation (a link, the sidebar, a fresh history entry) starts at
		// the top instead.
		if (nav.type === 'popstate') return;
		// A direct navigation mid-restore must kill the settle loop — a link clicked while the
		// previous page's restore was still re-applying would otherwise keep writing y beneath it.
		cancelRestore();
		scroller.scrollTop = 0;
	});

	let { children } = $props();
	// Queue and lyrics toggle independently and both float over the page rather than docking into
	// it — two docked columns squeezed the content down to an unusable strip. At lg+ they sit side
	// by side over the content; narrower, they stack (see QueuePanel / LyricsPanel).
	// Comments take the lyrics' slot rather than a third column, so the two are exclusive with each
	// other (opening one closes the other) and everything that offsets for "the lyrics panel" counts
	// either as that one slot.
	let queueOpen = $state(false);
	let lyricsOpen = $state(false);
	let commentsOpen = $state(false);
	// Two ways the now-playing view and these panels can divide the same two buttons, picked in
	// settings (#62). Tabbed (the default): the view carries queue and lyrics itself, so the panels
	// step aside for it and the bar's buttons switch its tabs. Off: these are the only owner, the
	// buttons always mean the panels, and the panels float over that view like they float over a
	// page, so opening it costs you nothing you had open.
	// Android always uses the tabbed player: it is where queue, lyrics and comments live on a phone.
	const tabbed = $derived(np.open && (appearance.tabbedPlayer || isAndroid));

	// Pages reached from another one, which a phone needs a visible way back from.
	const hasBackBar = $derived(/^\/(artist|album|playlist|list|history)(\/|$)/.test(page.url.pathname));

	// Pull to refresh on a phone's Home and Library: drag down from the very top past PULL_AT and
	// let go. Passive throughout: the page's own scrolling is never held up for it.
	// Android: a newer APK on the mobile port's releases, until downloaded or dismissed.
	let apkUpdate = $state<ApkRelease | null>(null);
	/** Download it and open Android's installer; without an APK on the release, its page. */
	async function updateApk() {
		const release = apkUpdate;
		if (!release) return;
		if (!release.apk) {
			openExternal(release.url).catch(() => {});
			apkUpdate = null;
			return;
		}
		toast(t('apk_update.downloading'));
		try {
			await installApkUpdate(release.apk);
			apkUpdate = null;
		} catch (e) {
			const error = String(e);
			toast.error(
				error.includes('INSTALL_PERMISSION')
					? t('apk_update.allow_installs')
					: t('apk_update.failed', { error })
			);
		}
	}

	const PULL_AT = 64;
	let pull = $state(0);
	let pullRefreshing = $state(false);
	let pullFrom: number | null = null;
	const pullable = $derived(
		isAndroid && (page.url.pathname === '/' || page.url.pathname === '/library')
	);
	function pullStart(e: TouchEvent) {
		pullFrom = pullable && scroller?.scrollTop === 0 ? e.touches[0].clientY : null;
	}
	function pullMove(e: TouchEvent) {
		if (pullFrom === null) return;
		const dy = e.touches[0].clientY - pullFrom;
		// Half the finger's travel, so it reads as resistance; capped a little past the trigger.
		pull = dy > 0 && scroller?.scrollTop === 0 ? Math.min(dy / 2, PULL_AT + 24) : 0;
	}
	function pullEnd() {
		if (pull >= PULL_AT) {
			pullRefreshing = true;
			refreshView();
			setTimeout(() => (pullRefreshing = false), 900);
		}
		pull = 0;
		pullFrom = null;
	}

	/** Android's Back button (`onBackButtonPress` below): close whatever is on top, innermost first,
	 *  and report whether anything was. Only `false` lets Back leave the page. */
	function androidBack(): boolean {
		// The ⋯ menus (TrackMenu, PlaylistMenu…): their full-screen backdrop is what closes them.
		const menuBackdrop = document.querySelector<HTMLElement>('button[data-menu]');
		if (menuBackdrop) {
			menuBackdrop.click();
			return true;
		}
		// bits-ui dialogs, popovers and selects close on Escape, which they listen for on the document.
		const layer = document.querySelector(
			'[data-state="open"]:is([role="dialog"], [role="alertdialog"], [role="listbox"], [role="menu"], [data-popover-content])'
		);
		if (layer) {
			document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
			return true;
		}
		if (ui.theaterOpen) {
			ui.theaterOpen = false;
			return true;
		}
		if (np.open) {
			np.open = false;
			return true;
		}
		return false;
	}
	// Stepping aside hides the panels without closing them, so closing the view brings back
	// whatever was open before it (#365).
	const showQueue = $derived(queueOpen && !tabbed);
	const showLyrics = $derived(lyricsOpen && !tabbed);
	const showComments = $derived(commentsOpen && !tabbed);
	// A local file has no YouTube comments, and the bar and the view hide their comments buttons for
	// it, so nothing could close them: comments count as closed. Docked, the panel goes; tabbed, the
	// view goes back to the tab that was open before comments. Opened again on a YouTube track as usual.
	let tabBeforeComments = $state<'queue' | 'lyrics'>('queue');
	$effect(() => {
		if (np.tab !== 'comments') tabBeforeComments = np.tab;
	});
	$effect(() => {
		if (!playback.now || !isLocalId(playback.now.videoId)) return;
		commentsOpen = false;
		if (np.tab === 'comments') np.tab = tabBeforeComments;
	});

	// "Adapt colors to artwork": re-run on every track change and on the toggle itself. The 120px
	// cover is the one the player bar has already loaded, so this costs no extra request. A Matugen
	// palette owns every colour while it's on, so the artwork stands down.
	const artworkTint = $derived(appearance.artworkAccent && !appearance.matugen);
	$effect(() => {
		applyArtworkAccent(artworkTint ? thumb(playback.now?.thumbnail, 120) : null);
	});
	// The accent is banded against the active theme (a cover's colour that reads on a light page is
	// mud on a dark one, #137), so flipping light/dark has to re-derive it from the same cover.
	$effect(() => {
		refreshArtworkAccent(mode.current === 'dark');
	});
	// Same colour, one track early. Reading it off the queue instead of the track change means the
	// palette starts moving on the frame the artwork swaps, not after a fetch and a decode.
	$effect(() => {
		if (!artworkTint) return;
		const q = playback.queue;
		prewarmArtworkAccent(thumb(q.items[q.currentIndex + 1]?.thumbnail, 120));
	});

	// The mini player runs this same SPA in a second window (Rust `mini.rs`), so the window label is
	// what tells the two apart: `mini` gets the widget instead of the app chrome, and none of the
	// routes below it are ever rendered. Constant for the window's lifetime.
	const isMini = browser && getCurrentWindow().label === 'mini';

	// Apply the saved accent color before the first paint (ssr=false → nothing renders until now).
	if (browser) initTheme();
	// The custom app icon (#173) is a file on disk, so the titlebar has to ask Rust for it.
	if (browser) loadAppIcon();

	// Wire the Tauri event bridge once for the whole app; teardown on destroy. Check for an update
	// on every app open (silent unless one exists).
	onMount(() => {
		// Before the mini-window bail-out: both windows run this SPA and both can throw.
		initErrorLog();
		if (isMini) {
			// The widget gets the transport keys too. No zoom: it is a fixed-size card.
			const teardownMiniApp = initApp(true);
			const teardownMiniKeys = initShortcuts(true);
			return () => {
				teardownMiniApp();
				teardownMiniKeys();
			};
		}
		// First: it reveals the window (see initWin).
		const teardownWin = initWin();
		// Wi-Fi or mobile data, for Settings ▸ Playback ▸ Quality on mobile data. Without the Network
		// Information API the phone reads as Wi-Fi, which leaves the ordinary quality in force.
		const connection = isAndroid
			? (navigator as Navigator & { connection?: EventTarget & { type?: string } }).connection
			: undefined;
		const reportNetwork = () => setMetered(connection?.type === 'cellular').catch(() => {});
		if (connection) {
			reportNetwork();
			connection.addEventListener('change', reportNetwork);
		}
		// Listening is what stops Tauri's own handling, which went straight back a page even with a
		// menu or a dialog open over it.
		const backListener = isAndroid
			? onBackButtonPress(({ canGoBack }) => {
					if (androidBack()) return;
					if (canGoBack) history.back();
					else androidMoveToBackground().catch(() => {});
				})
			: null;
		// The updater only knows the desktop releases; an Android install would be told to download
		// one. Android asks the mobile port's own releases instead (lib/apkupdate.ts).
		if (!isAndroid) checkForUpdatesQuiet();
		else checkApkUpdate().then((r) => (apkUpdate = r));
		// Repeat while the app stays open: ✕ hides to tray by default, so this component can stay
		// mounted for days and a mount-only check would never see a release published in between.
		const updateTimer = isAndroid ? undefined : setInterval(checkForUpdatesQuiet, QUIET_INTERVAL_MS);
		const teardownApp = initApp();
		const teardownDownloads = isAndroid ? initDownloads() : null;
		const teardownZoom = initZoom();
		const teardownShortcuts = initShortcuts();
		initImport();
		return () => {
			clearInterval(updateTimer);
			backListener?.then((l) => l.unregister());
			teardownDownloads?.();
			connection?.removeEventListener('change', reportNetwork);
			teardownApp();
			teardownWin();
			teardownZoom();
			teardownShortcuts();
		};
	});
</script>

<!-- oncontextmenu: the app's own menus handle their right-click and stop the event, so anything
     that reaches the window is a place where WebKit would have offered back / reload / inspect.
     Text fields and selections keep the native menu (see `suppressNative`). -->
<!-- A Spotify export, CSV or link dropped anywhere opens the import (#375); anything else foreign is
     refused as before. -->
<svelte:window
	ondragover={blockForeignDrag}
	ondrop={(e) => handleImportDrop(e) || blockForeignDrag(e)}
	oncontextmenu={suppressNative}
/>

<svelte:head><link rel="icon" href={favicon} /></svelte:head>
<ModeWatcher />

<!-- The mini player is the whole window when it is the window: no titlebar, no sidebar, no routes,
     and no toasts (a banner would cover most of a 560x180 widget). -->
{#if isMini}
	<MiniPlayer />
{:else}
	<!-- The window itself is transparent; this root paints the background and, when not maximized,
	     rounds the corners (the compositor can't round an undecorated window for us on Linux).
	     Theater mode counts as maximized here: it is fullscreen, and rounding it clips the corners
	     of a view that is meant to reach every edge (#139). With a system frame (win.chrome), or on
	     Windows 11, the compositor rounds for us, so ours would only fight it (#504).
	     Invisible while theater mode shows mpv's picture: theater is transparent over the hole, and
	     the titlebar, sidebar or player bar under it would paint over the video. -->
	<div
		class="relative flex h-[100dvh] flex-col overflow-hidden text-foreground {video.hole
			? ''
			: 'bg-background'} {windowCorners()} {ui.theaterOpen && video.hole ? 'invisible' : ''}"
	>
		{#if video.hole}
			<!-- Linux: mpv draws the music video underneath the page (nativevideo.rs), so the page
			     paints its background everywhere except the picture's box: a spread shadow, not a
			     blur, so it costs one static paint. At -z-10 it sits under everything else, and the
			     root's own background and <main> step aside while it is up (both paint the box). -->
			<div
				aria-hidden="true"
				class="pointer-events-none visible absolute -z-10 rounded-2xl"
				style="left:{video.hole.x}px;top:{video.hole.y}px;width:{video.hole.w}px;height:{video.hole
					.h}px;box-shadow:0 0 0 100vmax var(--background)"
			></div>
		{/if}
		<div class="hidden md:block"><ResizeBorders /></div>
		<div class="hidden md:block"><Titlebar /></div>
		<!-- relative: the queue and lyrics panels are absolute overlays inside it (see QueuePanel).
		     The top inset keeps the pages out from under a phone's status bar, sticky headers included;
		     the now-playing view is absolute from the top edge and pads itself. -->
		<div class="relative flex min-h-0 flex-1 max-md:pt-[env(safe-area-inset-top)]">
			<div class="hidden min-h-0 md:flex"><Sidebar /></div>
			<!-- dragScroll: dragging a card up to home's Shortcuts grid has to be possible from anywhere in
			     the feed, so aiming at the top edge scrolls this container while the drag is in flight. -->
			<main
				class="min-w-0 flex-1 overflow-y-auto {video.hole ? 'invisible' : ''}"
				bind:this={scroller}
				{@attach dragScroll}
				ontouchstart={pullStart}
				ontouchmove={pullMove}
				ontouchend={pullEnd}
				ontouchcancel={() => (pull = 0)}
			>
				<!-- A phone has no titlebar, so its detail pages get their back arrow here. -->
				{#if hasBackBar}
					<div class="flex h-14 items-center px-2 md:hidden"><BackButton /></div>
				{/if}
				<!-- Remount the current page on sign-in/out so it refetches with the new account, and on
				     a refresh (titlebar button / F5), which drops the browse cache first. -->
				{#key `${auth.epoch}:${ui.epoch}`}
					{@render children()}
				{/key}
			</main>
			{#if pull > 0 || pullRefreshing}
				<!-- Pull to refresh: follows the finger down, spins while the page reloads. -->
				<div
					class="pointer-events-none absolute left-1/2 top-[env(safe-area-inset-top)] z-10 flex size-10 -translate-x-1/2 items-center justify-center rounded-full border bg-card shadow-md md:hidden"
					style="transform:translate(-50%, {pullRefreshing ? 16 : pull - 24}px);opacity:{pullRefreshing ? 1 : Math.min(1, pull / PULL_AT)}"
				>
					<HugeiconsIcon
						icon={Refresh01Icon}
						class="h-5 w-5 text-primary {pullRefreshing ? 'animate-spin' : ''}"
						style={pullRefreshing ? '' : `transform:rotate(${pull * 4}deg)`}
					/>
				</div>
			{/if}
			<!-- Always mounted, unlike the player view below it: it owns the one <video> element, which
			     has to keep playing while the view is closed. It renders nothing but a zero-sized
			     parking container until the view borrows the picture. Not on Linux, where mpv draws the
			     picture itself (prefs.nativeVideo). -->
			{#if !prefs.nativeVideo}<VideoSurface />{/if}
			{#if np.open && playback.now}<NowPlaying
					queueOpen={showQueue}
					lyricsOpen={showLyrics || showComments}
				/>{/if}
			<!-- Lyrics before queue: side by side over the page, lyrics on the left, queue on the right. -->
			{#if showLyrics}<LyricsPanel onClose={() => (lyricsOpen = false)} queueOpen={showQueue} />{/if}
			{#if showComments}<CommentsPanel
					onClose={() => (commentsOpen = false)}
					queueOpen={showQueue}
				/>{/if}
			{#if showQueue}<QueuePanel onClose={() => (queueOpen = false)} />{/if}
		</div>
		{#if playback.now}
			<!-- Slides up from its own height on first play; leaves instantly (bar removal is rare).
			     z-20 on the wrapper, not the bar: the intro's transform makes this a stacking context,
			     so a z on the footer inside would be trapped under it. The now-playing view is z-20 and
			     earlier in the DOM, which is what puts it behind the bar as it slides in and out. -->
			<div class="relative z-20" in:fly={{ y: 64, duration: 250, easing: cubicOut }}>
				<PlayerBar
					onToggleQueue={() => (tabbed ? (np.tab = 'queue') : (queueOpen = !queueOpen))}
					queueOpen={tabbed ? np.tab === 'queue' : queueOpen}
					onToggleLyrics={() => {
						if (tabbed) np.tab = 'lyrics';
						else {
							lyricsOpen = !lyricsOpen;
							if (lyricsOpen) commentsOpen = false;
						}
					}}
					lyricsOpen={tabbed ? np.tab === 'lyrics' : lyricsOpen}
					onToggleComments={() => {
						if (tabbed) np.tab = 'comments';
						else {
							commentsOpen = !commentsOpen;
							if (commentsOpen) lyricsOpen = false;
						}
					}}
					commentsOpen={tabbed ? np.tab === 'comments' : commentsOpen}
				/>
			</div>
		{/if}
		<!-- The open player on a phone is full screen: its controls take the navigation's place. -->
		{#if !(np.open && playback.now)}<MobileNav />{/if}
	</div>

	<!-- Theater mode covers everything, titlebar included, and puts the window in fullscreen for as
	     long as it is mounted. Nothing playing means nothing to show, and that guard is also what
	     closes it (and leaves fullscreen) when the queue runs out. -->
	{#if ui.theaterOpen && playback.now}<TheaterMode />{/if}

	<CommandPalette />
	<KeyboardShortcuts />
	<AddToPlaylist />
	<NewPlaylistDialog />
	<ShareDialog />
	<SettingsDialog />
	<ChannelPicker />
	<ListenTogether />
	<LinkDialog />
	{#if isAndroid}<BatteryNotice />{/if}
	<ImportDialog />

	<!-- The two notification banners below run at z-[100]. Dialogs and menus sit at z-50 and portal to
	     <body>, so a z-50 banner loses the tie on DOM order and hides behind an open modal. The update
	     banner also needs pointer-events-auto: an open dialog sets `pointer-events: none` on <body>,
	     so without it the banner shows above the modal but its buttons can't be clicked. -->
	{#if apkUpdate}
		<!-- Android's update notice (lib/apkupdate.ts): the release page, where the APK is. -->
		<div
			transition:fly={{ y: 16, duration: 220, easing: cubicOut }}
			class="pointer-events-auto fixed bottom-24 max-md:bottom-[calc(12rem_+_env(safe-area-inset-bottom))] left-1/2 z-[100] flex w-max max-w-[calc(100vw-2rem)] -translate-x-1/2 items-center gap-3 rounded-lg border bg-card px-4 py-2 text-sm shadow-lg"
		>
			<span>{t('apk_update.available', { version: apkUpdate.version })}</span>
			<Button size="sm" class="max-md:h-11" onclick={updateApk}>
				{apkUpdate.apk ? t('apk_update.update') : t('apk_update.download')}
			</Button>
			<Button
				variant="ghost"
				size="icon-sm"
				class="-mr-2 text-muted-foreground hover:text-foreground max-md:size-11"
				aria-label={t('common.close')}
				onclick={() => {
					dismissApkUpdate(apkUpdate!.version);
					apkUpdate = null;
				}}
			>
				<HugeiconsIcon icon={Cancel01Icon} strokeWidth={2} />
			</Button>
		</div>
	{/if}

	{#if updateState.available}
		<div
			transition:fly={{ y: 16, duration: 220, easing: cubicOut }}
			class="pointer-events-auto fixed bottom-24 max-md:bottom-[calc(12rem_+_env(safe-area-inset-bottom))] left-1/2 z-[100] flex -translate-x-1/2 items-center gap-3 rounded-lg border bg-card px-4 py-2 text-sm shadow-lg"
		>
			<span>{availableMessage(updateState.available)}</span>
			{#if updateState.canInstall}
				<Button size="sm" onclick={installUpdate} disabled={updateState.installing}>
					{updateState.installing ? t('common.loading') : t('settings.about.install_update')}
				</Button>
			{:else}
				<!-- Packaged build (.rpm, AUR): the updater can only rewrite an AppImage, so send them
				     to the releases page and let their package manager do it. -->
				<Button size="sm" onclick={openDownloadPage}>{t('settings.about.download_page')}</Button>
			{/if}
			{#if !updateState.installing}
				<Button
					variant="ghost"
					size="icon-sm"
					class="-mr-2 text-muted-foreground hover:text-foreground"
					aria-label={t('common.close')}
					onclick={() => (updateState.available = null)}
				>
					<HugeiconsIcon icon={Cancel01Icon} strokeWidth={2} />
				</Button>
			{/if}
		</div>
	{/if}

	{#if ui.toast}
		{@const t = ui.toast}
		<div
			transition:fly={{ y: 16, duration: 220, easing: cubicOut }}
			class="fixed bottom-40 max-md:bottom-[calc(12rem_+_env(safe-area-inset-bottom))] left-1/2 z-[100] flex -translate-x-1/2 items-center gap-2 rounded-lg border bg-card px-4 py-2 text-sm shadow-lg"
		>
			<!-- Three branches instead of a ternary on `icon`: HugeiconsIcon freezes `icon` at mount, so a
			     new toast replacing a visible one would keep the old glyph. -->
			{#if t.kind === 'success'}
				<HugeiconsIcon icon={CheckmarkCircle02Icon} class="h-4 w-4 shrink-0 text-primary" />
			{:else if t.kind === 'error'}
				<HugeiconsIcon icon={AlertCircleIcon} class="h-4 w-4 shrink-0 text-destructive" />
			{:else}
				<HugeiconsIcon
					icon={InformationCircleIcon}
					class="h-4 w-4 shrink-0 text-muted-foreground"
				/>
			{/if}
			{t.msg}
		</div>
	{/if}
{/if}
