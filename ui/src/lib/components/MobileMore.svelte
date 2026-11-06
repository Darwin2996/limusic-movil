<script lang="ts">
	import { goto } from '$app/navigation';
	import { HugeiconsIcon } from '@hugeicons/svelte';
	import {
		MoreHorizontalIcon,
		Clock01Icon,
		Link04Icon,
		UserGroup02Icon,
		CameraVideoIcon,
		UserMultiple02Icon,
		Settings02Icon
	} from '@hugeicons/core-free-icons';
	import * as Dialog from '$lib/components/ui/dialog';
	import AccountMenu from './AccountMenu.svelte';
	import LastFmIcon from './LastFmIcon.svelte';
	import { Button } from '$lib/components/ui/button';
	import { playback, ui } from '$lib/player.svelte';
	import { t } from '$lib/i18n.svelte';

	// Queue, lyrics and comments are tabs of the full player on Android, and Discord needs a desktop
	// client, so neither has an entry here.
	let open = $state(false);

	function settings(tab?: 'scrobbling') {
		open = false;
		ui.settingsFocus = tab ?? null;
		ui.settingsOpen = true;
	}
</script>

<Dialog.Root bind:open>
	<Dialog.Trigger class="flex min-w-0 flex-1 flex-col items-center justify-center gap-1 rounded-lg py-1 text-xs font-medium text-muted-foreground hover:text-foreground" aria-label={t('common.more')}>
		<span class="flex h-7 w-14 items-center justify-center rounded-full">
			<HugeiconsIcon icon={MoreHorizontalIcon} class="h-5 w-5" />
		</span>
		<span class="max-w-full truncate">{t('common.more')}</span>
	</Dialog.Trigger>
	<Dialog.Content class="max-md:fixed max-md:inset-x-0 max-md:bottom-0 max-md:top-auto max-md:mb-0 max-md:max-h-[85dvh] max-md:max-w-none max-md:rounded-t-3xl max-md:rounded-b-none max-md:pb-[max(env(safe-area-inset-bottom),1rem)]">
		<Dialog.Header>
			<Dialog.Title>{t('common.more')}</Dialog.Title>
		</Dialog.Header>
		<div class="flex min-h-0 flex-col gap-2 overflow-y-auto overscroll-y-contain">
			<div class="flex min-h-11 items-center justify-between gap-3 rounded-xl border px-3">
				<span class="text-sm font-medium">{t('nav.account')}</span>
				<div class="h-10 shrink-0"><AccountMenu /></div>
			</div>
			<button class="flex min-h-12 items-center gap-3 rounded-xl px-3 text-left hover:bg-muted" onclick={() => (open = false, goto('/friends'))}>
				<HugeiconsIcon icon={UserMultiple02Icon} class="size-5" />{t('nav.friends')}
			</button>
			<button class="flex min-h-12 items-center gap-3 rounded-xl px-3 text-left hover:bg-muted" onclick={() => (open = false, goto('/history'))}>
				<HugeiconsIcon icon={Clock01Icon} class="size-5" />{t('nav.history')}
			</button>
			<button class="flex min-h-12 items-center gap-3 rounded-xl px-3 text-left hover:bg-muted" onclick={() => (open = false, ui.linkOpen = true)}>
				<HugeiconsIcon icon={Link04Icon} class="size-5" />{t('dialogs.link.title')}
			</button>
			<button class="flex min-h-12 items-center gap-3 rounded-xl px-3 text-left hover:bg-muted" onclick={() => (open = false, ui.ltOpen = true)}>
				<HugeiconsIcon icon={UserGroup02Icon} class="size-5" />{t('nav.listen_together')}
			</button>
			<button class="flex min-h-12 items-center gap-3 rounded-xl px-3 text-left hover:bg-muted disabled:opacity-50" disabled={!playback.now} onclick={() => (open = false, ui.theaterOpen = true)}>
				<HugeiconsIcon icon={CameraVideoIcon} class="size-5" />{t('player.theater_mode')}
			</button>
			<button class="flex min-h-12 items-center gap-3 rounded-xl px-3 text-left hover:bg-muted" onclick={() => settings('scrobbling')}>
				<LastFmIcon class="size-5" /><span class="min-w-0 truncate">{t('settings.tabs.scrobbling')}</span>
			</button>
			<Button variant="outline" class="min-h-11" onclick={() => settings()}>
				<HugeiconsIcon icon={Settings02Icon} class="size-4" />{t('nav.settings')}
			</Button>
		</div>
	</Dialog.Content>
</Dialog.Root>
