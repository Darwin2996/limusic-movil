<script lang="ts">
	// Friends: your LiMusic profile, adding friends by @username, requests, what each friend is
	// playing, and each friend's profile with "Invite to Listen Together". The data and its privacy
	// rules live in lib/friends.svelte.ts and supabase/amigos.sql.
	import { HugeiconsIcon } from '@hugeicons/svelte';
	import { UserAdd01Icon, PlayIcon, UserGroup02Icon } from '@hugeicons/core-free-icons';
	import * as Dialog from '$lib/components/ui/dialog';
	import { Button } from '$lib/components/ui/button';
	import { Input } from '$lib/components/ui/input';
	import { Switch } from '$lib/components/ui/switch';
	import { playSong, toast } from '$lib/player.svelte';
	import { thumb } from '$lib/thumb';
	import { t } from '$lib/i18n.svelte';
	import {
		acceptFriend,
		addFriend,
		createProfile,
		friends,
		friendsConfigured,
		inviteFriend,
		removeFriend,
		setSharing,
		updateProfile,
		type Friend,
		type NowPlaying,
		type Profile
	} from '$lib/friends.svelte';

	const COLORS = ['#7c3aed', '#db2777', '#ea580c', '#16a34a', '#0891b2', '#2563eb'];

	let username = $state('');
	let displayName = $state('');
	let friendName = $state('');
	let busy = $state(false);
	let editing = $state(false);
	let editName = $state('');
	let editBio = $state('');
	let open = $state<Friend | null>(null);
	let now = $state(Date.now());

	$effect(() => {
		const tick = setInterval(() => (now = Date.now()), 30_000);
		return () => clearInterval(tick);
	});

	const accepted = $derived(friends.list.filter((f) => f.status === 'accepted'));
	const incoming = $derived(friends.list.filter((f) => f.status === 'incoming'));
	const outgoing = $derived(friends.list.filter((f) => f.status === 'outgoing'));

	/** Playing now, or what they last played and when. */
	function activity(n: NowPlaying | null): string {
		if (!n?.title) return t('friends.no_activity');
		const track = n.artist ? `${n.title} · ${n.artist}` : n.title;
		const minutes = Math.round((now - Date.parse(n.updated_at)) / 60_000);
		// A phone that died mid-song never says it stopped: past a while, call it the last one.
		if (n.is_playing && minutes < 15) return t('friends.listening', { track });
		if (minutes < 60) return t('friends.listened_minutes', { track, count: Math.max(1, minutes) });
		const hours = Math.round(minutes / 60);
		if (hours < 48) return t('friends.listened_hours', { track, count: hours });
		return t('friends.listened_days', { track, count: Math.round(hours / 24) });
	}

	async function run(action: () => Promise<unknown>, done?: string) {
		busy = true;
		try {
			await action();
			if (done) toast.success(done);
		} catch (e) {
			const code = e instanceof Error ? e.message : String(e);
			const known = ['username_taken', 'username_invalid', 'not_found', 'self', 'already', 'no_room'];
			toast.error(
				known.includes(code)
					? t(`friends.errors.${code as 'username_taken'}`)
					: t('friends.errors.generic', { error: code })
			);
		} finally {
			busy = false;
		}
	}

	function startEdit() {
		editName = friends.me?.display_name ?? '';
		editBio = friends.me?.bio ?? '';
		editing = true;
	}

	function play(n: NowPlaying) {
		if (!n.video_id || !n.title) return;
		playSong({ video_id: n.video_id, title: n.title, artists: n.artist ?? '', thumbnail: n.thumbnail ?? undefined });
	}
</script>

{#snippet avatar(p: Profile, size: string)}
	<span
		class="flex shrink-0 items-center justify-center rounded-full font-heading font-bold text-white {size}"
		style="background:{p.color}"
		aria-hidden="true"
	>
		{(p.display_name || p.username).trim()[0]?.toUpperCase() ?? '?'}
	</span>
{/snippet}

{#snippet friendRow(f: Friend)}
	<button
		type="button"
		class="flex w-full min-w-0 items-center gap-3 rounded-xl p-2 text-left hover:bg-muted"
		onclick={() => (open = f)}
	>
		{@render avatar(f.profile, 'size-11 text-lg')}
		<span class="min-w-0 flex-1">
			<span class="block truncate font-medium">{f.profile.display_name || f.profile.username}</span>
			<span class="block truncate text-xs text-muted-foreground">
				{f.status === 'accepted' ? activity(f.nowPlaying) : `@${f.profile.username}`}
			</span>
		</span>
		{#if f.status === 'accepted' && f.nowPlaying?.is_playing}
			<span class="size-2 shrink-0 rounded-full bg-primary" aria-hidden="true"></span>
		{/if}
	</button>
{/snippet}

<div class="mx-auto flex max-w-2xl flex-col gap-6 p-4 sm:p-6">
	<h1 class="font-heading text-3xl font-bold tracking-tight">{t('friends.title')}</h1>

	{#if !friendsConfigured}
		<p class="text-sm text-muted-foreground">{t('friends.not_configured')}</p>
	{:else if !friends.ready}
		<p class="text-sm text-muted-foreground">{t('common.loading')}</p>
	{:else if friends.error && !friends.me}
		<p class="text-sm text-destructive">{t('friends.errors.generic', { error: friends.error })}</p>
	{:else if !friends.me}
		<!-- First visit: pick the @username friends will find you by. -->
		<form
			class="flex flex-col gap-3 rounded-2xl border bg-card p-4"
			onsubmit={(e) => {
				e.preventDefault();
				run(() => createProfile(username, displayName));
			}}
		>
			<h2 class="font-heading text-lg font-semibold">{t('friends.setup_title')}</h2>
			<p class="text-sm text-muted-foreground">{t('friends.setup_hint')}</p>
			<label class="flex flex-col gap-1 text-sm">
				{t('friends.username')}
				<Input bind:value={username} placeholder="@usuario" autocomplete="off" class="h-11" />
			</label>
			<label class="flex flex-col gap-1 text-sm">
				{t('friends.display_name')}
				<Input bind:value={displayName} maxlength={40} class="h-11" />
			</label>
			<Button type="submit" class="min-h-11" disabled={busy || !username.trim()}>
				{t('friends.create_profile')}
			</Button>
		</form>
	{:else}
		{@const me = friends.me}
		<!-- You -->
		<section class="flex flex-col gap-4 rounded-2xl border bg-card p-4">
			<div class="flex items-center gap-3">
				{@render avatar(me, 'size-14 text-2xl')}
				<div class="min-w-0 flex-1">
					<div class="truncate font-heading text-lg font-semibold">{me.display_name || me.username}</div>
					<div class="truncate text-sm text-muted-foreground">@{me.username}</div>
				</div>
				{#if !editing}
					<Button variant="outline" size="sm" class="min-h-11" onclick={startEdit}>{t('friends.edit')}</Button>
				{/if}
			</div>
			{#if editing}
				<label class="flex flex-col gap-1 text-sm">
					{t('friends.display_name')}
					<Input bind:value={editName} maxlength={40} class="h-11" />
				</label>
				<label class="flex flex-col gap-1 text-sm">
					{t('friends.bio')}
					<Input bind:value={editBio} maxlength={160} class="h-11" />
				</label>
				<div class="flex gap-2" role="group" aria-label={t('friends.color')}>
					{#each COLORS as c (c)}
						<button
							type="button"
							class="size-11 rounded-full border-2 {me.color === c ? 'border-foreground' : 'border-transparent'}"
							style="background:{c}"
							aria-label={c}
							aria-pressed={me.color === c}
							onclick={() => run(() => updateProfile({ color: c }))}
						></button>
					{/each}
				</div>
				<div class="flex justify-end gap-2">
					<Button variant="ghost" class="min-h-11" onclick={() => (editing = false)}>{t('common.cancel')}</Button>
					<Button
						class="min-h-11"
						disabled={busy}
						onclick={() =>
							run(async () => {
								await updateProfile({ display_name: editName.trim(), bio: editBio.trim() });
								editing = false;
							})}>{t('common.save')}</Button
					>
				</div>
			{:else if me.bio}
				<p class="text-sm">{me.bio}</p>
			{/if}
			<div class="flex items-center justify-between gap-4">
				<span class="text-sm">
					<span class="block font-medium">{t('friends.share')}</span>
					<span class="block text-xs text-muted-foreground">{t('friends.share_hint')}</span>
				</span>
				<Switch checked={friends.sharing} onCheckedChange={(on) => run(() => setSharing(on))} />
			</div>
		</section>

		<!-- Add a friend -->
		<form
			class="flex gap-2"
			onsubmit={(e) => {
				e.preventDefault();
				run(async () => {
					await addFriend(friendName);
					friendName = '';
				}, t('friends.request_sent'));
			}}
		>
			<Input bind:value={friendName} placeholder={t('friends.add_placeholder')} autocomplete="off" class="h-11" />
			<Button type="submit" class="min-h-11 shrink-0" disabled={busy || !friendName.trim()}>
				<HugeiconsIcon icon={UserAdd01Icon} class="h-4 w-4" />
				{t('friends.add')}
			</Button>
		</form>

		{#if incoming.length}
			<section class="flex flex-col gap-2">
				<h2 class="text-sm font-semibold text-muted-foreground">{t('friends.requests')}</h2>
				{#each incoming as f (f.profile.id)}
					<div class="flex items-center gap-2">
						<div class="min-w-0 flex-1">{@render friendRow(f)}</div>
						<Button class="min-h-11" disabled={busy} onclick={() => run(() => acceptFriend(f.profile.id))}>
							{t('friends.accept')}
						</Button>
						<Button variant="ghost" class="min-h-11" disabled={busy} onclick={() => run(() => removeFriend(f.profile.id))}>
							{t('friends.decline')}
						</Button>
					</div>
				{/each}
			</section>
		{/if}

		<section class="flex flex-col gap-1">
			<h2 class="text-sm font-semibold text-muted-foreground">{t('friends.your_friends', { count: accepted.length })}</h2>
			{#each accepted as f (f.profile.id)}
				{@render friendRow(f)}
			{:else}
				<p class="flex items-center gap-2 py-4 text-sm text-muted-foreground">
					<HugeiconsIcon icon={UserGroup02Icon} class="h-5 w-5 shrink-0" />
					{t('friends.empty')}
				</p>
			{/each}
		</section>

		{#if outgoing.length}
			<section class="flex flex-col gap-2">
				<h2 class="text-sm font-semibold text-muted-foreground">{t('friends.sent')}</h2>
				{#each outgoing as f (f.profile.id)}
					<div class="flex items-center gap-2">
						<div class="min-w-0 flex-1">{@render friendRow(f)}</div>
						<Button variant="ghost" class="min-h-11" disabled={busy} onclick={() => run(() => removeFriend(f.profile.id))}>
							{t('common.cancel')}
						</Button>
					</div>
				{/each}
			</section>
		{/if}
	{/if}
</div>

<!-- A friend's profile -->
<Dialog.Root open={open !== null} onOpenChange={(o) => !o && (open = null)}>
	<Dialog.Content
		class="sm:max-w-md max-md:fixed max-md:inset-x-0 max-md:bottom-0 max-md:top-auto max-md:mb-0 max-md:max-w-none max-md:translate-y-0 max-md:rounded-t-3xl max-md:rounded-b-none max-md:pb-[max(env(safe-area-inset-bottom),1rem)]"
	>
		{#if open}
			{@const f = open}
			<div class="flex flex-col items-center gap-2 pt-2 text-center">
				{@render avatar(f.profile, 'size-20 text-4xl')}
				<Dialog.Title class="font-heading text-xl">{f.profile.display_name || f.profile.username}</Dialog.Title>
				<Dialog.Description>@{f.profile.username}</Dialog.Description>
				{#if f.profile.bio}<p class="text-sm">{f.profile.bio}</p>{/if}
			</div>
			{#if f.status === 'accepted'}
				{#if f.nowPlaying?.title}
					{@const n = f.nowPlaying}
					<div class="flex items-center gap-3 rounded-xl border p-3">
						{#if n.thumbnail}
							<img src={thumb(n.thumbnail, 96)} alt="" class="size-12 shrink-0 rounded-md object-cover" />
						{/if}
						<span class="min-w-0 flex-1 text-sm">{activity(n)}</span>
						{#if n.video_id}
							<Button variant="ghost" size="icon" class="size-11 shrink-0" aria-label={t('player.play')} onclick={() => play(n)}>
								<HugeiconsIcon icon={PlayIcon} class="h-5 w-5" />
							</Button>
						{/if}
					</div>
				{/if}
				<Button
					class="min-h-11"
					disabled={busy}
					onclick={() => run(() => inviteFriend(f.profile.id), t('friends.invite_sent'))}
				>
					<HugeiconsIcon icon={UserGroup02Icon} class="h-4 w-4" />
					{t('friends.invite')}
				</Button>
			{/if}
			<Button
				variant="ghost"
				class="min-h-11 text-destructive"
				disabled={busy}
				onclick={() =>
					run(async () => {
						await removeFriend(f.profile.id);
						open = null;
					})}
			>
				{f.status === 'accepted' ? t('friends.remove') : t('common.cancel')}
			</Button>
		{/if}
	</Dialog.Content>
</Dialog.Root>
