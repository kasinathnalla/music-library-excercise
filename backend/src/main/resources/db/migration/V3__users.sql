-- Two kinds of user: an ADMIN who curates the library, and a CUSTOMER who listens to it.
--
-- "user" is a reserved word in Postgres and would need quoting at every use site, so the
-- table is app_user.
--
-- Role is a column rather than a join table because there are exactly two roles and nobody
-- holds both. When that stops being true the change is additive: an app_user_role table
-- backfilled from this column, with no rewrite of the entity.
create table app_user (
    id            uuid primary key default gen_random_uuid(),
    username      text        not null,
    password_hash text        not null,
    role          text        not null,
    enabled       boolean     not null default true,
    created_at    timestamptz not null default now(),
    constraint app_user_role_check check (role in ('ADMIN', 'CUSTOMER'))
);

create unique index app_user_username_key on app_user (lower(username));

-- Seeded here, in the migration, rather than by an ApplicationRunner like the bundled audio.
-- Flyway runs before the application serves its first request, so the accounts exist by the
-- time anyone can try to sign in, and Flyway's own bookkeeping makes the insert run exactly
-- once per database without needing an "if not exists" dance.
--
-- The hashes are BCrypt literals, not computed at migration time: Flyway checksums this file,
-- and a migration that produced a different hash on every run would change its own checksum
-- and be rejected on the next startup.
--
-- These are demo credentials for a local exercise and are documented in the README. A real
-- deployment would seed nothing and provision the first admin out of band.
insert into app_user (username, password_hash, role) values
    ('admin',    '$2b$10$VRu.gFiQMxwEjFjGOp95N.JCUWptTzAVp2gJJgMrHcdul8707BTWO', 'ADMIN'),
    ('customer', '$2b$10$u0e3vNj2e1WymNowC7Lch.rgrRciJeJfU78/rgHRJ5AIwG2tZJlte', 'CUSTOMER');
