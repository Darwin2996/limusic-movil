<script lang="ts" module>
	import type { Range } from '$lib/stats';
	// Kept for the session: whoever checks the month comes back to the month.
	let lastRange: Range = 'month';
</script>

<script lang="ts">
	// Listening stats (#396): how much, what and when, from the plays this device has counted. One
	// local query per visit, range switch or new song (`listening_stats` in Rust). Drawn by
	// StatsView, which profiles share.
	import { untrack } from 'svelte';
	import { HugeiconsIcon } from '@hugeicons/svelte';
	import { Delete02Icon } from '@hugeicons/core-free-icons';
	import * as AlertDialog from '$lib/components/ui/alert-dialog';
	import { Button } from '$lib/components/ui/button';
	import StatsView from '$lib/components/StatsView.svelte';
	import * as api from '$lib/api';
	import type { ListeningStats } from '$lib/api';
	import { playback, toast } from '$lib/player.svelte';
	import { rangeStart } from '$lib/stats';
	import { t } from '$lib/i18n.svelte';

	const STROKE = 2;

	let range = $state<Range>(lastRange);
	let stats = $state.raw<ListeningStats | null>(null);
	// The range `stats` answers. The tabs move on the click, the numbers when the answer lands, and
	// everything drawn from `stats` reads this one so the two never mix.
	let shown = $state<Range>(lastRange);
	let error = $state<string | null>(null);

	let seq = 0;
	async function load(r: Range) {
		const mine = ++seq;
		try {
			const s = await api.listeningStats(rangeStart(r));
			if (mine !== seq) return; // a newer range or song asked since
			stats = s;
			shown = r;
			error = null;
		} catch (e) {
			if (mine === seq) error = String(e);
		}
	}

	// A song's time heard is settled when the next one starts, so each new song refreshes the page
	// as well as each range switch. The old numbers stay up meanwhile: a local query is quicker than
	// a skeleton would be.
	$effect(() => {
		playback.now?.videoId;
		const r = range;
		lastRange = r;
		untrack(() => load(r));
	});

	// Plays made on other devices (ListenBrainz, or the YouTube Music history): fetched on the way
	// in, and the page redrawn when there were any.
	$effect(() => {
		api
			.syncRemoteListens()
			.then((added) => {
				if (added) load(range);
			})
			.catch(() => {});
	});

	let confirming = $state(false);
	let clearing = $state(false);
	async function clearHistory() {
		clearing = true;
		try {
			await api.clearListeningHistory();
			confirming = false;
			toast.success(t('stats.cleared'));
			await load(range);
		} catch (e) {
			toast.error(String(e));
		} finally {
			clearing = false;
		}
	}
</script>

<StatsView {stats} bind:range {shown} {error} onRetry={() => load(range)} title={t('stats.title')}>
	{#snippet footer()}
		<p class="text-xs text-muted-foreground">{t('stats.on_device')}</p>
		<Button
			variant="ghost"
			size="sm"
			class="gap-1.5 text-muted-foreground hover:text-destructive"
			onclick={() => (confirming = true)}
		>
			<HugeiconsIcon strokeWidth={STROKE} icon={Delete02Icon} class="h-4 w-4" />
			{t('stats.clear')}
		</Button>
	{/snippet}
</StatsView>

<AlertDialog.Root bind:open={() => confirming, (v) => !clearing && (confirming = v)}>
	<AlertDialog.Content>
		<AlertDialog.Header>
			<AlertDialog.Media class="bg-destructive/10 text-destructive">
				<HugeiconsIcon strokeWidth={STROKE} icon={Delete02Icon} />
			</AlertDialog.Media>
			<AlertDialog.Title>{t('stats.clear_title')}</AlertDialog.Title>
			<AlertDialog.Description>{t('stats.clear_desc')}</AlertDialog.Description>
		</AlertDialog.Header>
		<AlertDialog.Footer>
			<AlertDialog.Cancel disabled={clearing}>{t('common.cancel')}</AlertDialog.Cancel>
			<AlertDialog.Action variant="destructive" onclick={clearHistory} disabled={clearing}>
				{clearing ? t('common.loading') : t('stats.clear_confirm')}
			</AlertDialog.Action>
		</AlertDialog.Footer>
	</AlertDialog.Content>
</AlertDialog.Root>
