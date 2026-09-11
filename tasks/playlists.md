# Tasks — playlists

**Goal:** every signed-in user keeps their own ordered playlists — create, add tracks from the
library, reorder, remove, and play straight through.
**Plan:** [docs/plans/03-playlists.md](../docs/plans/03-playlists.md)

> Written retroactively, part-way through the branch, which is not how this is meant to work — the
> list should exist before the code does. Recorded here rather than quietly back-dated.

## Schema
- [x] `V6__playlists.sql` — `playlist`, `playlist_item`
- [x] `(playlist_id, position)` unique and **deferrable**, so a reorder can pass through a state
      that collides with itself
- [x] Renumber Phase 7's plan to `V7` so the two do not both claim V6

## Backend
- [x] `entity/Playlist`, `entity/PlaylistItem` — add, removeItem, reorder, renumber
- [x] `repository/PlaylistRepository` — owner-scoped finders and the left-join fetch
- [x] `repository/PlaylistSummary` — projection, so listing is one query rather than N+1
- [x] `service/PlaylistService` — owner id first on every method, no overload without it
- [x] Five exceptions in `exception/`, all translated by `advice/ApiExceptionHandler`
- [x] `controller/PlaylistController` — eight endpoints
- [x] `TrackDeletionService` drops the track from every playlist through JPA and closes the gaps
- [ ] ~~Add a `SecurityConfig` matcher for `/api/playlists/**`~~ — `anyRequest().authenticated()`
      already covers it and both roles should keep playlists; row ownership is the service's job

## Tests
- [x] `PlaylistControllerTest` — ordering, the cross-user 404 on every endpoint, three-item reorder,
      contiguity after removal, duplicate name, the album-less track regression
- [x] `PlaylistTrackDeletionTest` — a deleted track leaves the surviving order contiguous
- [ ] **`./gradlew test` — NOT RUN.** No Docker daemon in the environment this was built in, so
      Testcontainers could not start. Every test here is unexecuted.

## Frontend
- [x] `core/services/playback.service.ts` — queue, index, advance-on-ended
- [x] `shared/components/player/` — the one `<audio>`, rendered once from `app.ts`
- [x] `features/playlists/` — service, models, list, detail, add-to-playlist
- [x] Reorder by move up/down, sending the whole new order
- [x] `app.ts` — playlist view states and section nav
- [x] Add-to-playlist is a `<select>` of your playlists plus "+ New playlist…"
- [x] All component colours from the design tokens, so dark mode is readable
- [ ] **`yarn build` / `yarn test --run` — NOT RUN.** yarn could not be installed here.
- [ ] TypeScript path aliases (`@core/*`, `@features/*`) — imports reach `../../../../`

## Structure (same branch, separate concern)
- [x] Backend packages feature → layer: `controller/ service/ repository/ entity/ dto/ exception/
      advice/ security/ config/` (61 files)
- [x] Frontend flat → `core/ shared/ features/` (39 files)
- [x] `scripts/standards-check.py` — 21 conventions enforced, each negative-tested
- [x] `scripts/check.sh` runs standards first, since it needs neither Docker nor network

## Docs
- [x] `docs/plans/03-playlists.md`
- [x] `ARCHITECTURE` — §3 redrawn by layer, §4 gains the playlist tables, §11 the ownership flow
- [x] `DECISIONS` 22–26
- [x] `ROADMAP`, `USE-CASES`, `README`, `AGENTS.md`
- [ ] `docs/api/openapi.json` / `.yaml` — needs a running app; not regenerated

## Not doing on this branch
- Shared or collaborative playlists — every query is owner-scoped; widening is additive
- Smart playlists — out of scope per `docs/QUESTIONS.md` Q10
- Shuffle, repeat, previous, a visible queue — Phase 2
- A base `ApplicationException` hierarchy — see DECISIONS 25 note in `code-quality`
