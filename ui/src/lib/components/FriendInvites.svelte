<script lang="ts">
	// A friend's invite to Listen Together, wherever you are in the app: who, and Join or not now.
	// Invites arrive live (friends.svelte.ts subscribes to them) and the newest is shown first.
	import { fly } from 'svelte/transition';
	import { cubicOut } from 'svelte/easing';
	import { Button } from '$lib/components/ui/button';
	import { toast } from '$lib/player.svelte';
	import { t } from '$lib/i18n.svelte';
	import { acceptInvite, dismissInvite, friends } from '$lib/friends.svelte';

	const invite = $derived(friends.invites[0]);
	const from = $derived(invite ? friends.profileOf(invite.from_user) : undefined);
	let busy = $state(false);

	async function join() {
		if (!invite) return;
		busy = true;
		try {
			await acceptInvite(invite);
		} catch (e) {
			toast.error(t('friends.errors.generic', { error: e instanceof Error ? e.message : String(e) }));
		} finally {
			busy = false;
		}
	}
</script>

{#if invite}
	<div
		transition:fly={{ y: -16, duration: 220, easing: cubicOut }}
		class="pointer-events-auto fixed left-1/2 top-[calc(env(safe-area-inset-top)+0.75rem)] z-[100] flex w-[min(28rem,calc(100vw-2rem))] -translate-x-1/2 flex-col gap-3 rounded-2xl border bg-card p-4 shadow-xl"
		role="alertdialog"
		aria-label={t('friends.invite_title')}
	>
		<p class="text-sm">
			{t('friends.invited_you', { name: from ? from.display_name || `@${from.username}` : t('friends.a_friend') })}
		</p>
		<div class="flex justify-end gap-2">
			<Button variant="ghost" class="min-h-11" disabled={busy} onclick={() => dismissInvite(invite)}>
				{t('friends.not_now')}
			</Button>
			<Button class="min-h-11" disabled={busy} onclick={join}>{t('friends.join')}</Button>
		</div>
	</div>
{/if}
