-- Who added this track. Nullable, because the six seeded tracks are added at boot with no
-- authenticated user present, and because deleting an account must not delete the library.
--
-- Nothing reads this column yet. It exists now so that a later phase which does need it -- a
-- per-user library, or "uploaded by" in the track list -- is not a migration against populated
-- data. Same reasoning as track_artist in V1.
alter table track
    add column uploaded_by uuid references app_user (id) on delete set null;
