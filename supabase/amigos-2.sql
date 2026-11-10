-- LiMusic friends, part 2: a profile photo and playlists shared with friends.
--
-- Run once in the Supabase SQL Editor, after amigos.sql. As there, privacy lives in row level
-- security: a photo is public (it is a profile picture), but a shared playlist is visible to its
-- owner's accepted friends only.

-- The photo: one uploaded to the `avatars` bucket below, or the person's Google account picture.
-- Only those hosts are accepted, so a profile can't point friends' phones at an arbitrary server.
alter table public.profiles add column if not exists avatar_url text
	check (
		char_length(avatar_url) <= 600
		and avatar_url ~ '^https://([a-z0-9]+\.supabase\.co/storage/v1/object/public/avatars/|lh3\.googleusercontent\.com/|yt3\.ggpht\.com/|yt3\.googleusercontent\.com/)'
	);

-- Public bucket: anyone with the link can see a photo; only its owner can write to their folder
-- (`<user id>/…`). 512 KB is plenty for the 256 px JPEG the app uploads.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('avatars', 'avatars', true, 524288, array['image/jpeg', 'image/png', 'image/webp'])
on conflict (id) do nothing;

create policy "avatars: see your own folder" on storage.objects
	for select to authenticated
	using (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text);
create policy "avatars: upload to your own folder" on storage.objects
	for insert to authenticated
	with check (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text);
create policy "avatars: replace your own" on storage.objects
	for update to authenticated
	using (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text)
	with check (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text);
create policy "avatars: delete your own" on storage.objects
	for delete to authenticated
	using (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text);

-- A playlist someone chose to share: a copy of its tracks, so friends can see and play it even
-- when it is private on YouTube or lives only on that phone. Capped at 1000 tracks.
create table if not exists public.shared_playlists (
	owner uuid not null references public.profiles (id) on delete cascade,
	source_id text not null check (char_length(source_id) <= 100),
	title text not null check (char_length(title) <= 200),
	thumbnail text check (char_length(thumbnail) <= 600),
	track_count integer not null default 0 check (track_count >= 0),
	tracks jsonb not null default '[]'::jsonb
		check (jsonb_typeof(tracks) = 'array' and jsonb_array_length(tracks) <= 1000 and octet_length(tracks::text) <= 1500000),
	updated_at timestamptz not null default now(),
	primary key (owner, source_id)
);

alter table public.shared_playlists enable row level security;

create policy "shared playlists: yours and your friends'" on public.shared_playlists
	for select to authenticated using (owner = auth.uid() or public.are_friends(auth.uid(), owner));
create policy "shared playlists: share yours" on public.shared_playlists
	for insert to authenticated with check (owner = auth.uid());
create policy "shared playlists: update yours" on public.shared_playlists
	for update to authenticated using (owner = auth.uid()) with check (owner = auth.uid());
create policy "shared playlists: stop sharing yours" on public.shared_playlists
	for delete to authenticated using (owner = auth.uid());
