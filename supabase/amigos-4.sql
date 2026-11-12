-- LiMusic friends, part 4: profiles can no longer be listed, and requests and invites have a limit.
--
-- Run once in the Supabase SQL Editor, after amigos.sql, amigos-2.sql and amigos-3.sql.
--
-- Until now any signed-in phone could read every profile (photo, name, bio) with one query. Now a
-- profile is readable by its owner, by anyone it has a friendship or request with, or by anyone
-- when it is public; everyone else finds someone only by their exact @username (find_profile),
-- which is how adding a friend works.

drop policy if exists "profiles: anyone signed in can look one up" on public.profiles;

-- Whether two users have any link: a friendship, or a request either way.
create or replace function public.are_linked (a uuid, b uuid) returns boolean
language sql stable security definer set search_path = public as $$
	select exists (
		select 1 from friendships
		where (requester = a and addressee = b) or (requester = b and addressee = a)
	);
$$;

create policy "profiles: yours, linked ones and public ones" on public.profiles
	for select to authenticated
	using (id = auth.uid() or public_profile or public.are_linked(auth.uid(), id));

-- One profile by its exact @username (case doesn't matter), for adding a friend or opening their
-- page. security definer: it is the one way to read a profile the policy above doesn't show.
create or replace function public.find_profile (name text) returns setof public.profiles
language sql stable security definer set search_path = public as $$
	select * from profiles where username = name::citext and auth.uid() is not null limit 1;
$$;
revoke all on function public.find_profile (text) from public, anon;
grant execute on function public.find_profile (text) to authenticated;

-- Limits, so one account can't flood others: 30 friend requests and 60 invites an hour.
create or replace function public.limit_requests () returns trigger
language plpgsql security definer set search_path = public as $$
begin
	if (select count(*) from friendships
		where requester = new.requester and created_at > now() - interval '1 hour') >= 30 then
		raise exception 'rate_limited' using errcode = 'P0001';
	end if;
	return new;
end;
$$;
drop trigger if exists limit_requests on public.friendships;
create trigger limit_requests before insert on public.friendships
	for each row execute function public.limit_requests();

create or replace function public.limit_invites () returns trigger
language plpgsql security definer set search_path = public as $$
begin
	if (select count(*) from invites
		where from_user = new.from_user and created_at > now() - interval '1 hour') >= 60 then
		raise exception 'rate_limited' using errcode = 'P0001';
	end if;
	return new;
end;
$$;
drop trigger if exists limit_invites on public.invites;
create trigger limit_invites before insert on public.invites
	for each row execute function public.limit_invites();
