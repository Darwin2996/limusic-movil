<script lang="ts">
	// "Download all" on an album or playlist page (Android): queues every track listed there for
	// offline listening (`downloads.svelte.ts` skips what is already saved or on its way).
	import { HugeiconsIcon } from '@hugeicons/svelte';
	import { Download04Icon, CheckmarkCircle01Icon } from '@hugeicons/core-free-icons';
	import type { SongItem } from '$lib/api';
	import { isAndroid } from '$lib/player.svelte';
	import { download, downloads } from '$lib/downloads.svelte';
	import { t } from '$lib/i18n.svelte';

	let { songs, class: cls = '' }: { songs: SongItem[]; class?: string } = $props();
	const all = $derived(songs.length > 0 && songs.every((s) => downloads.has(s.video_id)));
</script>

{#if isAndroid && songs.length}
	<button
		type="button"
		class="{cls} {all ? 'text-primary' : ''}"
		onclick={() => download(songs)}
		aria-label={t('downloads.download_all')}
		title={t('downloads.download_all')}
	>
		<HugeiconsIcon icon={all ? CheckmarkCircle01Icon : Download04Icon} class="h-4 w-4" />
	</button>
{/if}
