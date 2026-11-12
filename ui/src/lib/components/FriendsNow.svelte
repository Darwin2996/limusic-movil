<script lang="ts">
	// Home: the friends listening right now, a tap from their profile. Only there while someone is,
	// so it costs the page nothing the rest of the time. The data is friends.svelte.ts's, kept live
	// by its subscription; nothing is fetched here.
	import { goto } from '$app/navigation';
	import { HugeiconsIcon } from '@hugeicons/svelte';
	import { UserGroup02Icon } from '@hugeicons/core-free-icons';
	import SectionHeading from './SectionHeading.svelte';
	import { friends, type Friend } from '$lib/friends.svelte';
	import { thumb } from '$lib/thumb';
	import { t } from '$lib/i18n.svelte';

	/** Past this, a phone that died mid-song is taken to have stopped (as on the friends page). */
	const STALE_MS = 15 * 60 * 1000;

	let now = $state(Date.now());
	$effect(() => {
		const tick = setInterval(() => (now = Date.now()), 60_000);
		return () => clearInterval(tick);
	});

	const listening = $derived(
		friends.list.filter(
			(f): f is Friend & { nowPlaying: NonNullable<Friend['nowPlaying']> } =>
				f.status === 'accepted' &&
				!!f.nowPlaying?.is_playing &&
				!!f.nowPlaying.title &&
				now - Date.parse(f.nowPlaying.updated_at) < STALE_MS
		)
	);
</script>

{#if listening.length}
	<section>
		<SectionHeading title={t('friends.listening_now')} icon={UserGroup02Icon} onMore={() => goto('/friends')} />
		<div class="rail -mx-1 flex gap-2 overflow-x-auto px-1 pb-1">
			{#each listening as f (f.profile.id)}
				<button
					type="button"
					class="flex w-64 shrink-0 cursor-pointer items-center gap-3 rounded-2xl bg-foreground/[0.04] p-2.5 text-left hover:bg-foreground/[0.07]"
					onclick={() => goto(`/friends/${f.profile.username}`)}
				>
					<span class="relative shrink-0">
						{#if f.nowPlaying.thumbnail}
							<img src={thumb(f.nowPlaying.thumbnail, 96)} alt="" class="size-12 rounded-lg object-cover" />
						{:else}
							<span class="block size-12 rounded-lg bg-muted"></span>
						{/if}
						{#if f.profile.avatar_url}
							<img
								src={f.profile.avatar_url}
								alt=""
								referrerpolicy="no-referrer"
								class="absolute -bottom-1 -right-1 size-6 rounded-full object-cover ring-2 ring-background"
								style="background:{f.profile.color}"
							/>
						{:else}
							<span
								class="absolute -bottom-1 -right-1 flex size-6 items-center justify-center rounded-full text-[0.65rem] font-bold text-white ring-2 ring-background"
								style="background:{f.profile.color}"
							>
								{(f.profile.display_name || f.profile.username).trim()[0]?.toUpperCase() ?? '?'}
							</span>
						{/if}
					</span>
					<span class="min-w-0 flex-1">
						<span class="block truncate text-xs text-muted-foreground">{f.profile.display_name || `@${f.profile.username}`}</span>
						<span class="block truncate text-sm font-medium">{f.nowPlaying.title}</span>
						<span class="block truncate text-xs text-muted-foreground">{f.nowPlaying.artist}</span>
					</span>
				</button>
			{/each}
		</div>
	</section>
{/if}
