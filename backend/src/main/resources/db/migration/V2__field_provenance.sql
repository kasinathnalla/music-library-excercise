-- Records which fields a person edited by hand.
--
-- Provenance is per field, not per track, on purpose: someone who corrects a misspelled
-- title should not lose that correction because an automated source later supplies a
-- better cover image for the same track. Anything listed here is off limits to
-- automated enrichment and re-scanning.
create table field_edit (
    id          uuid primary key default gen_random_uuid(),
    entity_type text        not null,
    entity_id   uuid        not null,
    field_name  text        not null,
    edited_at   timestamptz not null default now(),
    constraint field_edit_unique unique (entity_type, entity_id, field_name)
);

create index field_edit_entity_idx on field_edit (entity_type, entity_id);
