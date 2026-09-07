create extension if not exists pgcrypto;

create table artist (
    id         uuid primary key default gen_random_uuid(),
    name       text        not null,
    sort_name  text,
    created_at timestamptz not null default now()
);

create unique index artist_name_key on artist (lower(name));

create table album (
    id              uuid primary key default gen_random_uuid(),
    title           text        not null,
    album_artist_id uuid        references artist (id) on delete set null,
    release_year    smallint,
    created_at      timestamptz not null default now()
);

create unique index album_title_artist_key
    on album (lower(title), coalesce(album_artist_id, '00000000-0000-0000-0000-000000000000'::uuid));

create table track (
    id            uuid primary key default gen_random_uuid(),
    title         text        not null,
    album_id      uuid        references album (id) on delete set null,
    track_number  smallint,
    disc_number   smallint,
    duration_ms   integer,
    file_path     text        not null,
    file_size     bigint      not null,
    content_type  text        not null,
    content_hash  varchar(64) not null unique,
    added_at      timestamptz not null default now()
);

create index track_title_idx on track (lower(title));
create index track_album_idx on track (album_id);

create table track_artist (
    track_id  uuid     not null references track (id) on delete cascade,
    artist_id uuid     not null references artist (id) on delete cascade,
    role      text     not null default 'PRIMARY',
    position  smallint not null default 0,
    primary key (track_id, artist_id, role)
);

create index track_artist_artist_idx on track_artist (artist_id);
