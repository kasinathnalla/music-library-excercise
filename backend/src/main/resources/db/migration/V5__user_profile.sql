-- Profile fields collected at registration: who someone is, not just what they can do.
--
-- All three are nullable. The two seeded accounts (V3) predate this migration and have none of
-- them, and the app falls back to showing the bare username when a name is absent rather than
-- treating that as an error.
alter table app_user
    add column first_name    text,
    add column last_name     text,
    add column date_of_birth date,
    add column address       text;
