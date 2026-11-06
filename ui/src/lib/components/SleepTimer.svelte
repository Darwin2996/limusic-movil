<script lang="ts">
	// The sleep timer's button in the now-playing view and the sheet of choices it opens. The timer
	// itself runs in the backend (`sleep.rs`), so it holds with the screen off; this only arms it
	// and counts down the deadline it was given.
	import { onMount } from 'svelte';
	import { HugeiconsIcon } from '@hugeicons/svelte';
	import { Moon02Icon } from '@hugeicons/core-free-icons';
	import * as Dialog from '$lib/components/ui/dialog';
	import * as api from '$lib/api';
	import { toast } from '$lib/player.svelte';
	import { t } from '$lib/i18n.svelte';

	const MINUTES = [15, 30, 45, 60];
	let open = $state(false);
	let armed = $state<api.SleepTimer | null>(null);
	let now = $state(Date.now());

	const left = $derived(
		armed?.untilMs ? Math.max(1, Math.ceil((armed.untilMs - now) / 60_000)) : null
	);

	onMount(() => {
		api.getSleepTimer().then((a) => (armed = a)).catch(() => {});
		const tick = setInterval(() => (now = Date.now()), 15_000);
		const un = api.onSleepTimerFired(() => {
			armed = null;
			toast(t('sleep_timer.fired'));
		});
		return () => {
			clearInterval(tick);
			un.then((u) => u());
		};
	});

	async function set(minutes: number | null, endOfTrack: boolean) {
		open = false;
		armed = await api.setSleepTimer(minutes, endOfTrack).catch(() => armed);
		now = Date.now();
	}
</script>

<button
	type="button"
	onclick={() => (open = true)}
	class="flex h-9 min-w-9 cursor-pointer items-center justify-center gap-1.5 justify-self-end rounded-full px-2 text-muted-foreground transition-colors hover:bg-foreground/10 hover:text-foreground max-md:h-11 max-md:min-w-11 {armed
		? 'bg-primary/15 text-primary'
		: ''}"
	aria-label={t('sleep_timer.title')}
	title={t('sleep_timer.title')}
	data-np-keep
>
	<HugeiconsIcon icon={Moon02Icon} class="h-5 w-5" />
	{#if left}<span class="text-xs font-medium tabular-nums">{t('sleep_timer.left', { count: left })}</span>{/if}
</button>

<Dialog.Root bind:open>
	<Dialog.Content
		class="sm:max-w-sm max-md:fixed max-md:inset-x-0 max-md:bottom-0 max-md:top-auto max-md:mb-0 max-md:max-w-none max-md:translate-y-0 max-md:rounded-t-3xl max-md:rounded-b-none max-md:pb-[max(env(safe-area-inset-bottom),1rem)]"
	>
		<Dialog.Header>
			<Dialog.Title>{t('sleep_timer.title')}</Dialog.Title>
			{#if armed}
				<Dialog.Description>
					{armed.endOfTrack
						? t('sleep_timer.armed_track')
						: t('sleep_timer.armed_minutes', { count: left ?? 0 })}
				</Dialog.Description>
			{/if}
		</Dialog.Header>
		<div class="flex flex-col gap-1">
			{#each MINUTES as m (m)}
				<button
					type="button"
					class="min-h-12 rounded-xl px-3 text-left hover:bg-muted"
					onclick={() => set(m, false)}
				>
					{t('sleep_timer.minutes', { count: m })}
				</button>
			{/each}
			<button
				type="button"
				class="min-h-12 rounded-xl px-3 text-left hover:bg-muted"
				onclick={() => set(null, true)}
			>
				{t('sleep_timer.end_of_track')}
			</button>
			{#if armed}
				<button
					type="button"
					class="min-h-12 rounded-xl px-3 text-left text-destructive hover:bg-muted"
					onclick={() => set(null, false)}
				>
					{t('sleep_timer.off')}
				</button>
			{/if}
		</div>
	</Dialog.Content>
</Dialog.Root>
