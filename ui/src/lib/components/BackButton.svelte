<script lang="ts">
	// The visible way back on a phone's detail pages (artist, album, playlist): the desktop's lives
	// in the titlebar, which a phone doesn't have. Android's Back does the same thing.
	import { HugeiconsIcon } from '@hugeicons/svelte';
	import { ArrowLeft01Icon } from '@hugeicons/core-free-icons';
	import { t } from '$lib/i18n.svelte';

	let { class: cls = '' }: { class?: string } = $props();
</script>

<button
	type="button"
	onclick={() => history.back()}
	aria-label={t('common.back')}
	class="flex size-11 shrink-0 cursor-pointer items-center justify-center rounded-full bg-background/60 text-foreground backdrop-blur transition-colors hover:bg-background/80 md:hidden {cls}"
>
	<HugeiconsIcon icon={ArrowLeft01Icon} strokeWidth={2.5} class="h-5 w-5" />
</button>
