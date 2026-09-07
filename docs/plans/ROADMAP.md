# Implementation Roadmap

The brief covers four capabilities (library management, playback, playlists, metadata
editing) that each produce working, demonstrable software on their own. Building them as one
undifferentiated push would mean nothing runs until everything runs, which is the wrong shape for a
timeboxed submission. Each phase below ends with an app that builds, runs, and does something a
reviewer can see.

Phases are ordered so that the riskiest and most load-bearing work happens first. If time runs out,
it runs out at the end, and what exists is coherent rather than half-wired.

| Phase | Plan | Ends with | Est. |
|---|---|---|---|
| **1 (MVP)** | [01-foundation-and-library.md](01-foundation-and-library.md) | `docker compose up` on a clean clone serves a searchable library that ingests uploads and plays audio | ~1 day |
| 2 | 02-playback-depth.md | Queue, next and previous, shuffle, keyboard transport, gapless where the format allows | ~0.5 day |
| 3 | 03-playlists.md | Create, reorder, and play playlists | ~0.25 day |
| 4 | 04-metadata-editing.md | Per-track and bulk editing with per-field provenance and undo | ~0.5 day |
| 5 | 05-docs-and-polish.md | README, AGENTS.md, ADRs, large-library seed, accessibility pass | ~0.25 day |
| **6 (next)** | [06-users-and-auth.md](06-users-and-auth.md) | Two roles over HTTP Basic: admins upload and edit, customers browse and listen, nobody browses anonymously | ~0.5 day |

Phase 1 is the MVP and it is deliberately end-to-end rather than a backend with no face. It includes
basic playback, because a music app that cannot play a track is not testable end to end and a
reviewer cannot tell whether it works. Phase 2 is playback *depth*, not playback itself.

## Why this order

**Phase 1 first** because ingest is where the real problems are. Parsing tags off arbitrary audio
files, reconciling them into artist and album entities, and storing them so that tens of thousands
of rows still paginate quickly is the substance of a library app. Everything after it is comparatively
well-understood work. It is also the phase that proves the reproducible-build requirement, and I want
that proven on day one rather than discovered broken on the last afternoon.

**Playback inside the MVP, depth after it.** A single streaming endpoint with range-request support
plus a browser audio element is a small amount of work and it is what makes the whole system
demonstrable. Queue management and transport polish are a separate, larger job that can wait. A
playlist that cannot play is a list, so playlists come after playback either way.

**Metadata editing last among the features** even though it is the depth area. It depends on the
entity model settling first, and per-field provenance (see Q15 and Q17 in
[QUESTIONS.md](../QUESTIONS.md)) is much easier to add once ingest has been running long enough to
show what actually conflicts.

**Phase 6 runs next, ahead of Phases 2 to 5.** It was added after Phase 1 shipped, when two user
types were asked for. It jumps the queue because every phase after it needs to know who is acting: a
playlist without an owner is a shared mutable global, and retrofitting identity underneath a built
playlist model costs considerably more than the half day it costs now. Phase 1 was deliberately
built single-user (Q9), so this is the correction, not a change of mind.

**Docs last but not skipped.** Phase 5 is fixed scope, not buffer. The brief asks explicitly for a
README and an AGENTS file, so they get their own time rather than the ten minutes left over.

## Cut lines

If the budget compresses, the cuts happen in this order, and each one gets recorded in the README
under what I left out and why:

1. Bulk metadata editing preview (Phase 4) degrades to per-track editing only
2. Playlist reordering (Phase 3) degrades to append and remove
3. The large-library seed script (Phase 5) degrades to a documented claim rather than a demonstration

The reproducible build, real playback, and per-field provenance do not get cut. They are the parts
that make this a staff-level answer rather than a CRUD app with an audio element.

## Known issues carried forward

Recorded here rather than left to be discovered, and fixed in the phase noted.

- **Orphaned files on failed ingest.** `IngestService` stores bytes before parsing tags, so a file
  that turns out not to be audio leaves the bytes on disk while the transaction rolls back the
  database. Harmless at review scale and wrong in principle. Fixed in Phase 4, alongside the storage
  work that metadata write-back needs anyway, by deleting the stored file when ingest fails.
- **No pagination control in the UI.** The API pages and clamps page size from Phase 1; the library
  view requests the first page only. Fixed in Phase 2 with infinite scroll or a pager, whichever the
  virtualized list needs.
- **Several documents assert there is no authentication.** True as of Phase 1 and false as soon as
  Phase 6 lands. README, QUESTIONS Q9, USE-CASES, and ARCHITECTURE all say so in their own words.
  Fixed in Phase 6, Task 10, which is scope rather than tidying.
- **Per-track artist credits are unmapped.** The `track_artist` table exists from Phase 1 but no
  entity maps it, so a track's artist is currently its album's artist. Fixed in Phase 4, which is the
  first phase that needs to edit credits.
