-- Playlists: a listener's own ordered arrangement of the shared library.
--
-- This phase is only possible now because Phase 6 gave every account a stable id. ROADMAP put it
-- this way: a playlist without an owner is a shared mutable global. Ownership is therefore in the
-- schema from the first row, not retrofitted under a built model.
create table playlist (
    id         uuid primary key default gen_random_uuid(),
    owner_id   uuid        not null references app_user (id) on delete cascade,
    name       text        not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

-- Unique per owner, not globally: two people may both have a playlist called "Focus". Lowercased
-- for the same reason app_user_username_key is -- "Focus" and "focus" are the same name to a
-- person, and letting both exist makes the list look broken.
create unique index playlist_owner_name_key on playlist (owner_id, lower(name));

-- A row here is one track at one position, and it has its own id rather than being keyed by
-- (playlist_id, track_id). The same track may legitimately appear twice in one playlist, and
-- "remove the second copy" has to be expressible.
create table playlist_item (
    id          uuid primary key default gen_random_uuid(),
    playlist_id uuid        not null references playlist (id) on delete cascade,
    track_id    uuid        not null references track (id) on delete cascade,
    position    integer     not null,
    added_at    timestamptz not null default now()
);

-- Deferred on purpose, and this is the load-bearing line in the file.
--
-- Reordering rewrites several rows in one transaction, and every useful reorder passes through a
-- state that collides with itself: swapping positions 1 and 2 means something sits briefly at a
-- position another row still holds. Checking at commit time rather than per statement is what
-- makes the straightforward implementation correct, instead of one that has to shuffle rows via
-- negative temporary positions.
--
-- It is an alter table rather than a create unique index because Postgres can only defer a table
-- constraint; a unique index cannot be deferred.
alter table playlist_item
    add constraint playlist_item_position_key unique (playlist_id, position)
        deferrable initially deferred;

create index playlist_item_playlist_idx on playlist_item (playlist_id);

-- Deleting a track cascades into every playlist holding it (D9: deleting a track cleans up what it
-- orphans). This index is what keeps that delete from scanning every playlist row. Closing the
-- resulting gap in position is TrackDeletionService's job -- the foreign key removes rows, it does
-- not renumber the survivors.
create index playlist_item_track_idx on playlist_item (track_id);
