-- LiMusic friends: profiles, friendships, what each friend is playing, and Listen Together invites.
--
-- Paste the whole file into the Supabase SQL Editor of the project and run it once. It needs
-- "Allow anonymous sign-ins" on (Authentication ▸ Sign In / Providers): each phone gets an anonymous
-- account and the person picks a LiMusic @username; nothing from Google is stored.
--
-- Privacy lives here, in row level security, not in the app: anyone signed in can look a username up
-- (that is how friends are added), but what someone is playing is visible to their accepted friends
-- only, and invites only between friends.

create extension if not exists citext;

create table public.profiles (
	id uuid primary key references auth.users (id) on delete cascade,
	username citext not null unique check (username ~ '^[A-Za-z0-9_.]{3,20}$'),
	display_name text not null default '' check (char_length(display_name) <= 40),
	bio text not null default '' check (char_length(bio) <= 160),
	color text not null default '#7c3aed' check (color ~ '^#[0-9a-fA-F]{6}$'),
	created_at timestamptz not null default now()
);

create table public.friendships (
	requester uuid not null references public.profiles (id) on delete cascade,
	addressee uuid not null references public.profiles (id) on delete cascade,
	status text not null default 'pending' check (status in ('pending', 'accepted')),
	created_at timestamptz not null default now(),
	primary key (requester, addressee),
	check (requester <> addressee)
);

create table public.now_playing (
	user_id uuid primary key references public.profiles (id) on delete cascade,
	video_id text check (char_length(video_id) <= 64),
	title text check (char_length(title) <= 300),
	artist text check (char_length(artist) <= 300),
	thumbnail text check (char_length(thumbnail) <= 600),
	is_playing boolean not null default false,
	updated_at timestamptz not null default now()
);

create table public.invites (
	id uuid primary key default gen_random_uuid(),
	from_user uuid not null references public.profiles (id) on delete cascade,
	to_user uuid not null references public.profiles (id) on delete cascade,
	invite text not null check (char_length(invite) <= 200),
	created_at timestamptz not null default now()
);

-- Whether two users are accepted friends. security definer so the policies below can ask it
-- without each needing read access to the other's friendships.
create or replace function public.are_friends (a uuid, b uuid) returns boolean
language sql stable security definer set search_path = public as $$
	select exists (
		select 1 from friendships
		where status = 'accepted'
			and ((requester = a and addressee = b) or (requester = b and addressee = a))
	);
$$;

alter table public.profiles enable row level security;
alter table public.friendships enable row level security;
alter table public.now_playing enable row level security;
alter table public.invites enable row level security;

create policy "profiles: anyone signed in can look one up" on public.profiles
	for select to authenticated using (true);
create policy "profiles: create your own" on public.profiles
	for insert to authenticated with check (id = auth.uid());
create policy "profiles: edit your own" on public.profiles
	for update to authenticated using (id = auth.uid()) with check (id = auth.uid());
create policy "profiles: delete your own" on public.profiles
	for delete to authenticated using (id = auth.uid());

create policy "friendships: see your own" on public.friendships
	for select to authenticated using (auth.uid() in (requester, addressee));
create policy "friendships: send a request" on public.friendships
	for insert to authenticated with check (requester = auth.uid() and status = 'pending');
create policy "friendships: accept one sent to you" on public.friendships
	for update to authenticated using (addressee = auth.uid())
	with check (addressee = auth.uid() and status = 'accepted');
create policy "friendships: decline or unfriend" on public.friendships
	for delete to authenticated using (auth.uid() in (requester, addressee));

create policy "now playing: yours and your friends'" on public.now_playing
	for select to authenticated using (user_id = auth.uid() or public.are_friends(auth.uid(), user_id));
create policy "now playing: publish yours" on public.now_playing
	for insert to authenticated with check (user_id = auth.uid());
create policy "now playing: update yours" on public.now_playing
	for update to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy "now playing: clear yours" on public.now_playing
	for delete to authenticated using (user_id = auth.uid());

create policy "invites: the ones you sent or got" on public.invites
	for select to authenticated using (auth.uid() in (from_user, to_user));
create policy "invites: invite a friend" on public.invites
	for insert to authenticated
	with check (from_user = auth.uid() and public.are_friends(from_user, to_user));
create policy "invites: dismiss" on public.invites
	for delete to authenticated using (auth.uid() in (from_user, to_user));

-- Live updates in the app (Realtime applies the select policies above to what each user receives).
alter publication supabase_realtime add table public.friendships, public.now_playing, public.invites;
