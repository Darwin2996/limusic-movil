<script lang="ts">
	// Library ▸ Downloads: the songs saved for offline listening, played as one list. Removing one is
	// in its ⋯ menu (TrackMenu), next to downloading it.
	import TrackRow from './TrackRow.svelte';
	import * as api from '$lib/api';
	import { openPlayer } from '$lib/player.svelte';
	import { downloads } from '$lib/downloads.svelte';
	import { t } from '$lib/i18n.svelte';

	const size = $derived(
		downloads.bytes >= 1e9
			? `${(downloads.bytes / 1e9).toFixed(1)} GB`
			: `${Math.max(1, Math.round(downloads.bytes / 1e6))} MB`
	);
	const queued = $derived(Object.keys(downloads.pending).length);

	function play(start: number) {
		openPlayer();
		api.playPlaylist(downloads.songs, start, undefined, t('downloads.title'));
	}
</script>

{#if downloads.songs.length || queued}
	<p class="mb-3 text-sm text-muted-foreground">
		{t('downloads.summary', { count: downloads.songs.length, size })}
		{#if queued}· {t('downloads.in_progress', { count: queued })}{/if}
	</p>
	{#each downloads.songs as song, i (song.video_id)}
		<TrackRow {song} onplay={() => play(i)} />
	{/each}
{:else}
	<p class="text-sm text-muted-foreground">{t('downloads.empty')}</p>
{/if}
