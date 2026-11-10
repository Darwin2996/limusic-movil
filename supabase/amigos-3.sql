-- LiMusic friends, part 3: full profiles. Each person chooses whether their whole profile (shared
-- playlists and listening stats) is public or for friends only, and the app publishes their stats.
--
-- Run once in the Supabase SQL Editor, after amigos.sql and amigos-2.sql. What someone is playing
-- right now stays for friends only either way.

alter table public.profiles add column if not exists public_profile boolean not null default false;

-- Whether a profile is public. security definer, like are_friends, so policies can ask it.
create or replace function public.is_public (id uuid) returns boolean
language sql stable security definer set search_path = public as $$
	select coalesce((select public_profile from profiles where profiles.id = is_public.id), false);
$$;

-- Who may see someone's full profile: themselves, their accepted friends, or anyone when public.
create or replace function public.can_see_profile (viewer uuid, owner uuid) returns boolean
language sql stable security definer set search_path = public as $$
	select viewer = owner or public.is_public(owner) or public.are_friends(viewer, owner);
$$;

-- Shared playlists follow the same choice.
drop policy if exists "shared playlists: yours and your friends'" on public.shared_playlists;
create policy "shared playlists: who can see the profile" on public.shared_playlists
	for select to authenticated using (public.can_see_profile(auth.uid(), owner));

-- The stats page, per range (week / month / year / all), as the app computed it on that phone,
-- plus the longer artist list the compatibility score is worked out from.
create table if not exists public.profile_stats (
	owner uuid primary key references public.profiles (id) on delete cascade,
	data jsonb not null check (jsonb_typeof(data) = 'object' and octet_length(data::text) <= 400000),
	updated_at timestamptz not null default now()
);

alter table public.profile_stats enable row level security;

create policy "profile stats: who can see the profile" on public.profile_stats
	for select to authenticated using (public.can_see_profile(auth.uid(), owner));
create policy "profile stats: publish yours" on public.profile_stats
	for insert to authenticated with check (owner = auth.uid());
create policy "profile stats: update yours" on public.profile_stats
	for update to authenticated using (owner = auth.uid()) with check (owner = auth.uid());
create policy "profile stats: delete yours" on public.profile_stats
	for delete to authenticated using (owner = auth.uid());
