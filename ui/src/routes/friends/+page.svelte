<script lang="ts">
	// Friends: your LiMusic profile (photo, the playlists you share), adding friends by @username,
	// requests and what each friend is playing. Someone's full profile is its own page,
	// routes/friends/[username]. The data and its privacy rules live in lib/friends.svelte.ts and
	// supabase/amigos*.sql.
	import { goto } from '$app/navigation';
	import { HugeiconsIcon } from '@hugeicons/svelte';
	import { MusicNote01Icon, UserAdd01Icon, UserGroup02Icon } from '@hugeicons/core-free-icons';
	import * as Dialog from '$lib/components/ui/dialog';
	import { Button } from '$lib/components/ui/button';
	import { Input } from '$lib/components/ui/input';
	import { Switch } from '$lib/components/ui/switch';
	import { auth, toast } from '$lib/player.svelte';
	import * as api from '$lib/api';
	import { thumb } from '$lib/thumb';
	import { t } from '$lib/i18n.svelte';
	import {
		acceptFriend,
		addFriend,
		createProfile,
		friends,
		friendsConfigured,
		hasGooglePicture,
		removeAvatar,
		removeFriend,
		setSharing,
		sharePlaylist,
		unsharePlaylist,
		updateProfile,
		uploadAvatar,
		useGoogleAvatar,
		useGoogleAvatarByDefault,
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
	let now = $state(Date.now());
	let photoInput = $state<HTMLInputElement>();
	/** "Share playlists": the library's playlists, loaded when the dialog opens. */
	let choosing = $state(false);
	let library = $state<api.BrowseItem[] | null>(null);
	/** The playlist being copied or removed right now, which disables its switch. */
	let sharingId = $state<string | null>(null);

	$effect(() => {
		const tick = setInterval(() => (now = Date.now()), 30_000);
		return () => clearInterval(tick);
	});

	// The account can arrive after the profile did: give it its Google picture then.
	$effect(() => {
		if (auth.account?.signedIn && friends.me) useGoogleAvatarByDefault().catch(() => {});
	});

	$effect(() => {
		if (!choosing || library) return;
		api
			.getLibrary()
			.then((items) => (library = items.filter((i) => i.kind === 'playlist')))
			.catch((e) => {
				toast.error(t('friends.errors.generic', { error: String(e) }));
				choosing = false;
			});
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

	async function pickPhoto(e: Event) {
		const input = e.currentTarget as HTMLInputElement;
		const file = input.files?.[0];
		input.value = '';
		if (file) await run(() => uploadAvatar(file), t('friends.photo_saved'));
	}

	async function toggleShared(item: api.BrowseItem, on: boolean) {
		sharingId = item.id;
		await run(() => (on ? sharePlaylist(item) : unsharePlaylist(item.id)));
		sharingId = null;
	}
</script>

{#snippet avatar(p: Profile, size: string)}
	{#if p.avatar_url}
		<img
			src={p.avatar_url}
			alt=""
			class="shrink-0 rounded-full object-cover {size}"
			style="background:{p.color}"
			referrerpolicy="no-referrer"
		/>
	{:else}
		<span
			class="flex shrink-0 items-center justify-center rounded-full font-heading font-bold text-white {size}"
			style="background:{p.color}"
			aria-hidden="true"
		>
			{(p.display_name || p.username).trim()[0]?.toUpperCase() ?? '?'}
		</span>
	{/if}
{/snippet}

{#snippet listCover(thumbnail: string | null | undefined)}
	{#if thumbnail}
		<img src={thumb(thumbnail, 96)} alt="" class="size-12 shrink-0 rounded-md object-cover" />
	{:else}
		<span class="flex size-12 shrink-0 items-center justify-center rounded-md bg-muted">
			<HugeiconsIcon icon={MusicNote01Icon} class="h-5 w-5 text-muted-foreground" />
		</span>
	{/if}
{/snippet}

{#snippet friendRow(f: Friend)}
	<button
		type="button"
		class="flex w-full min-w-0 items-center gap-3 rounded-xl p-2 text-left hover:bg-muted"
		onclick={() => goto(`/friends/${f.profile.username}`)}
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
					<Button variant="ghost" size="sm" class="min-h-11" onclick={() => goto(`/friends/${me.username}`)}>
						{t('friends.my_profile')}
					</Button>
					<Button variant="outline" size="sm" class="min-h-11" onclick={startEdit}>{t('friends.edit')}</Button>
				{/if}
			</div>
			{#if editing}
				<div class="flex flex-wrap gap-2" role="group" aria-label={t('friends.photo')}>
					<input bind:this={photoInput} type="file" accept="image/*" class="hidden" onchange={pickPhoto} />
					<Button variant="outline" class="min-h-11" disabled={busy} onclick={() => photoInput?.click()}>
						{t('friends.photo_upload')}
					</Button>
					{#if hasGooglePicture()}
						<Button
							variant="outline"
							class="min-h-11"
							disabled={busy}
							onclick={() => run(useGoogleAvatar, t('friends.photo_saved'))}>{t('friends.photo_google')}</Button
						>
					{/if}
					{#if me.avatar_url}
						<Button variant="ghost" class="min-h-11" disabled={busy} onclick={() => run(removeAvatar)}>
							{t('friends.photo_remove')}
						</Button>
					{/if}
				</div>
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
			<div class="flex items-center justify-between gap-4">
				<span class="text-sm">
					<span class="block font-medium">{t('friends.playlists_shared', { count: friends.shared.length })}</span>
					<span class="block text-xs text-muted-foreground">{t('friends.playlists_hint')}</span>
				</span>
				<Button variant="outline" size="sm" class="min-h-11 shrink-0" onclick={() => (choosing = true)}>
					{t('friends.playlists_choose')}
				</Button>
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
			<Button
				variant="outline"
				class="min-h-11 shrink-0"
				disabled={!friendName.trim()}
				onclick={() => goto(`/friends/${friendName.trim().replace(/^@/, '')}`)}
			>
				{t('friends.view_profile')}
			</Button>
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

<!-- Which of your playlists friends can see -->
<Dialog.Root open={choosing} onOpenChange={(o) => (choosing = o)}>
	<Dialog.Content
		class="sm:max-w-md max-md:fixed max-md:inset-x-0 max-md:bottom-0 max-md:top-auto max-md:mb-0 max-md:max-w-none max-md:translate-y-0 max-md:rounded-t-3xl max-md:rounded-b-none max-md:pb-[max(env(safe-area-inset-bottom),1rem)]"
	>
		<Dialog.Title class="font-heading text-xl">{t('friends.playlists_choose')}</Dialog.Title>
		<Dialog.Description>{t('friends.playlists_choose_hint')}</Dialog.Description>
		<div class="-mx-2 max-h-[60vh] overflow-y-auto">
			{#if !library}
				<p class="p-2 text-sm text-muted-foreground">{t('common.loading')}</p>
			{:else}
				{#each library as item (item.id)}
					{@const on = friends.shared.some((p) => p.source_id === item.id)}
					<label class="flex min-w-0 items-center gap-3 rounded-lg p-2 hover:bg-muted">
						{@render listCover(item.thumbnail)}
						<span class="min-w-0 flex-1">
							<span class="block truncate text-sm font-medium">{item.title}</span>
							{#if item.subtitle}
								<span class="block truncate text-xs text-muted-foreground">{item.subtitle}</span>
							{/if}
						</span>
						<Switch
							checked={on}
							disabled={sharingId !== null}
							aria-label={item.title}
							onCheckedChange={(v) => toggleShared(item, v)}
						/>
					</label>
				{:else}
					<p class="p-2 text-sm text-muted-foreground">{t('friends.no_playlists')}</p>
				{/each}
			{/if}
		</div>
	</Dialog.Content>
</Dialog.Root>
