# Phase 3: Playlists

> **For agentic workers:** implement this plan task by task, in order. Task 1 and Task 4 are the
> two that repay care -- the deferred unique constraint in Task 1 is what makes reordering
> possible at all, and the cross-user test in Task 4 is the one that must never be allowed to go
> green for the wrong reason.

**Goal:** a signed-in user can create playlists, add tracks to them from the library, reorder and
remove those tracks, and play a playlist straight through. Playlists belong to the person who made
them. Nobody sees anyone else's.

**Scope, as asked for:** private playlists only. No sharing, no collaborative lists, and no admin
console over another user's playlists -- see [What this is not](#what-this-is-not).

**Estimate:** ~0.25 day, per ROADMAP. The reorder cut line (cut #2) was offered and declined; full
reordering is in scope.

---

## Why this phase can happen now

ROADMAP says it plainly: *"a playlist without an owner is a shared mutable global."* Phase 6 gave
every account a stable `app_user.id` and put an `AppUserPrincipal` carrying that id into the
security context. Ownership is therefore available from the first commit of this phase rather than
retrofitted underneath a built model, which is the expensive order and the reason Phase 6 jumped
the queue in the first place.

---

## The shape of the data

A playlist is a name, an owner, and an ordered list of tracks:

```
Road trip                      (owner: customer)
  1  Prelude in C
  2  Reference Tone
  3  Interlude No. 2

Focus                          (owner: customer)
  1  Reference Tone
```

The order is explicit, stored, and editable. It is not "the order you added them in" -- that is
merely the order a new list happens to start in.

The same track may appear in many playlists, and may appear in more than one position of the same
playlist. A playlist row is therefore identified by its own id, not by `(playlist_id, track_id)`:
removing "the second copy of Reference Tone" has to mean something.

---

## Two numbering collisions, settled here

**Flyway version.** `07-user-journey.md` already claims `V6__user_journey.sql`, but Phase 7 is
planned and not built, and this phase lands first. Flyway rejects an out-of-order migration by
default, so a database that has run V7 will refuse a V6 arriving afterwards. Playlists take **V6**
and Task 0 renumbers Phase 7's plan to **V7**.

**Decision numbers.** `DECISIONS.md` ends at 18 and Phase 7's plan reserves D19 to D21. Decision
numbers have no ordering constraint, so this phase takes **D22 to D24** and leaves Phase 7's
reservations alone.

---

## What this is not

**Not shared or collaborative.** Every query is scoped by owner. There is no "share this playlist"
and no public list. If that is wanted later it is an additive change -- a visibility column and a
widened query -- not a rework.

**Not an admin view over other people's playlists.** An admin curates the library; a playlist is a
listener's private arrangement of it. This matches the self-view precedent Phase 7 sets for the
activity journey.

**Not smart playlists.** No rules, no auto-population. QUESTIONS.md Q10 lists smart playlists among
the things explicitly out of scope for this exercise.

**Not a full play queue.** Task 7 extracts just enough playback state to advance through a list.
Shuffle, repeat, previous, and a visible editable queue remain Phase 2.

---

## Design decisions taken in this plan

### D22. Row ownership is checked in the service; a stranger's playlist is 404, not 403

AGENTS.md says the authorization matrix lives in `SecurityConfig`, and it still does: `/api/playlists/**`
is covered by `anyRequest().authenticated()`, so both roles reach it and no new matcher is added.
That rule is about *which endpoints a role may reach*, and it is the right place for that.

But "is this playlist yours" is a fact about a row, and no URL-and-method rule can express it.
`PlaylistService` therefore takes the caller's `UUID ownerId` on every method and scopes every
query by it. This is not a hole in D15; it is the boundary of what D15 was ever about.

A playlist owned by someone else returns **404, not 403**. A 403 confirms that the id exists, which
tells a prober the shape of another user's library one guess at a time. 404 is the honest answer to
"show me *my* playlist with this id": there isn't one.

### D23. Reordering sends the whole order, not a move

`PUT /api/playlists/{id}/items` takes the complete ordered list of item ids and rewrites every
position. The alternative -- `POST /items/{id}/move?to=3` -- is smaller on the wire and worse
everywhere else: two browser tabs reordering the same list interleave into an order neither user
asked for, and a retried request moves the item twice.

Sending the whole order makes the request idempotent and makes the server's job a single
transaction with no conflict resolution in it. The list is a handful of items; the bytes are not
the constraint.

### D24. The audio element is extracted to a shared service, borrowing a slice of Phase 2

Playback today lives inside `catalog/track-list/track-list.ts`: a `nowPlayingUrl` signal and an
`<audio>` in that component's footer. A playlist that advances on its own needs the same element
and a queue behind it.

Copying the element into the playlist view would put two `<audio>` elements in one application,
which is not a styling problem but an audible one -- both can play at once. So playback moves to
`playback/playback.service.ts` and one `<app-player>`, used by both views.

That is Phase 2 work arriving early, and deliberately only a sliver of it: a queue, an index, and
advance-on-ended. Shuffle, repeat, previous, and the visible queue stay in Phase 2.

---

## File structure after this phase

```text
backend/src/main/java/com/kasi/musiclibrary/
  entity/
    Playlist.java                        A name, an owner id, ordered items.
    PlaylistItem.java                    One track at one position.
  repository/
    PlaylistRepository.java              Owner-scoped finders, plus the left-join fetch.
    PlaylistSummary.java                 Projection for the list view - one query, no N+1.
  service/
    PlaylistService.java                 create / rename / delete / addTrack / removeItem / reorder
  exception/
    PlaylistNotFoundException.java       Becomes a 404.
    PlaylistItemNotFoundException.java   Becomes a 404.
    TrackNotFoundException.java          Becomes a 404.
    DuplicatePlaylistNameException.java  Becomes a 409.
    InvalidReorderException.java         Becomes a 400.
  controller/
    PlaylistController.java              /api/playlists
  dto/
    PlaylistSummaryResponse.java         id, name, trackCount, updatedAt
    PlaylistResponse.java                id, name, ordered items
    PlaylistItemResponse.java            itemId, position, and the track, nested
    CreatePlaylistRequest.java   RenamePlaylistRequest.java
    AddItemRequest.java          ReorderRequest.java
  advice/
    ApiExceptionHandler.java             MODIFIED: three new handlers
backend/src/main/resources/db/migration/
  V6__playlists.sql
backend/src/test/java/com/kasi/musiclibrary/
  playlist/
    PlaylistControllerTest.java          Behaviour, plus the cross-user isolation test.
    PlaylistTrackDeletionTest.java       A deleted track leaves the surviving order contiguous.
frontend/src/app/
  core/services/
    playback.service.ts                  Queue, current index, advance.
  shared/components/player/              The one <audio> element in the application.
  features/playlists/
    models/playlist.model.ts
    services/playlist.service.ts         The only file that knows playlist API URLs.
    components/playlist-list/            Your playlists; create, open, delete.
    components/playlist-detail/          Ordered items; move, remove, play all.
    components/add-to-playlist/          Puts one track into a playlist, or a new one.
  features/library/components/track-list/  MODIFIED: renders <app-add-to-playlist>.
  app.ts                                 MODIFIED: playlist view states, nav, renders <app-player>.
```

> **Note added after the phase shipped.** The backend was organised by feature (`playlist/`,
> `catalog/`, `ingest/`) when this plan was written, and the code landed that way. It moved to the
> layer packages shown above immediately afterwards - see DECISIONS 25. The frontend moved from
> flat feature folders to `core`/`shared`/`features` in the same pass - DECISIONS 26. The plan's
> reasoning is unchanged; only the paths moved.

---

## Task 0: Renumber Phase 7's migration

**Files:** `docs/plans/07-user-journey.md`

- [ ] Replace `V6__user_journey.sql` with `V7__user_journey.sql` (two occurrences: the file
      structure block and Task 1's **Files:** line), and the "confirm Flyway applies V6" line in
      Task 1's verification.

Do this first, so no one implements Phase 7 against a number this phase is about to take.

---

## Task 1: The schema

**Files:** `backend/src/main/resources/db/migration/V6__playlists.sql`

- [ ] **Step 1: Write the migration.**

```sql
create table playlist (
    id         uuid primary key default gen_random_uuid(),
    owner_id   uuid        not null references app_user (id) on delete cascade,
    name       text        not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index playlist_owner_name_key on playlist (owner_id, lower(name));

create table playlist_item (
    id          uuid primary key default gen_random_uuid(),
    playlist_id uuid        not null references playlist (id) on delete cascade,
    track_id    uuid        not null references track (id) on delete cascade,
    position    integer     not null,
    added_at    timestamptz not null default now()
);

alter table playlist_item
    add constraint playlist_item_position_key unique (playlist_id, position)
        deferrable initially deferred;

create index playlist_item_playlist_idx on playlist_item (playlist_id);
create index playlist_item_track_idx on playlist_item (track_id);
```

Three choices worth understanding before changing them:

**`deferrable initially deferred`.** Reordering rewrites several rows in one transaction, and every
useful order passes through a state that collides with itself -- swapping positions 1 and 2 means
something is briefly at a position another row still holds. Deferring the check to commit time is
what makes the straightforward implementation correct. A plain `create unique index` cannot be
deferred in Postgres; only a table constraint can, which is why this is an `alter table`.

**`on delete cascade` from `track`.** Deleting a track removes it from every playlist holding it,
consistent with D9. Task 6 closes the resulting gaps in `position`.

**`on delete cascade` from `app_user`.** The opposite of `track.uploaded_by` in V4, which is
`set null` so the library outlives the uploader. A playlist has no meaning without its owner.

- [ ] **Step 2: Verify it applies.** `docker compose down -v && docker compose up -d db`, boot the
      backend, confirm Flyway logs V6 and the application starts. `ddl-auto` is `validate`, so
      startup is the real test of Task 2.

---

## Task 2: Entities and repositories

**Files:** `playlist/Playlist.java`, `playlist/PlaylistItem.java`, `playlist/PlaylistRepository.java`,
`playlist/PlaylistItemRepository.java`

- [ ] `Playlist`: `@ManyToOne(LAZY)` to `AppUser`, `@OneToMany(mappedBy = "playlist", cascade = ALL,
      orphanRemoval = true)` to items with `@OrderBy("position")`. Domain methods `add(Track)`,
      `removeItem(UUID)`, `reorder(List<UUID>)` that keep positions contiguous from 0.
- [ ] `PlaylistItem`: `@ManyToOne(LAZY)` to both playlist and track, plus `int position`.
- [ ] `PlaylistRepository.findByOwnerIdOrderByUpdatedAtDesc(UUID)`.
- [ ] `PlaylistRepository.findByIdAndOwnerIdWithItems` -- the fetch that makes the response
      serializable with `open-in-view` off:

```java
@Query("""
        select distinct p from Playlist p
        left join fetch p.items i
        left join fetch i.track t
        left join fetch t.album a
        left join fetch a.albumArtist
        where p.id = :id and p.owner.id = :ownerId
        """)
Optional<Playlist> findByIdAndOwnerIdWithItems(@Param("id") UUID id, @Param("ownerId") UUID ownerId);
```

**Every join here is a left join, and that is load-bearing.** A track with no album is legitimate.
Path navigation or an inner join silently drops those rows -- the exact defect
`TrackControllerTest.listIncludesTracksThatHaveNoAlbum` exists to catch. Task 4 has the equivalent
test for playlists.

Note the owner predicate is in the query, not applied after loading. Filtering in Java would still
be correct here but teaches the wrong habit in a file where the next finder might page.

---

## Task 3: The service

**Files:** `playlist/PlaylistService.java`, `playlist/PlaylistNotFoundException.java`,
`playlist/DuplicatePlaylistNameException.java`

- [ ] Every method takes `UUID ownerId` as its first argument. There is no overload that omits it.
- [ ] A playlist that does not exist, and a playlist owned by someone else, both raise
      `PlaylistNotFoundException`. The service does not distinguish them and neither does the API
      (D22).
- [ ] `create` translates the unique-index violation into `DuplicatePlaylistNameException`, the way
      `AuthController.register` translates the username collision.
- [ ] `reorder` validates that the submitted id set is exactly the playlist's current item set --
      no additions, no omissions, no duplicates -- and rejects anything else as a 400. A reorder
      that silently dropped an item would be data loss disguised as a sort.
- [ ] `addTrack`, `removeItem`, `reorder` and `rename` all touch `updatedAt`, which is what the
      list view orders by.

No `@PreAuthorize` anywhere in this class. See D15 and D22.

---

## Task 4: The tests

**Files:** `backend/src/test/java/com/kasi/musiclibrary/playlist/PlaylistControllerTest.java`

Write these before Task 5 and Task 6. AGENTS.md records what happens when that ordering is treated
as ceremony: four defects in a green build.

**Authenticate with `httpBasic("customer", "customer")`, not `@WithMockUser`.** This is the trap in
this phase. `@WithMockUser` installs Spring's own `User` as the principal, so
`@AuthenticationPrincipal AppUserPrincipal` binds to null and every test fails in a way that looks
like a controller bug. The seeded accounts from V3 authenticate for real and carry a real id.

- [ ] A created playlist comes back in the owner's list.
- [ ] Two tracks added come back in the order they were added, with positions 0 and 1.
- [ ] **The cross-user test.** Register a second customer. For that user, every one of `GET`,
      `PATCH`, `DELETE`, `POST /items` and `PUT /items` against the first user's playlist id
      returns **404**, and the first user's list is unaffected. This test is the reason the phase
      is safe; treat a failure here as a release blocker, not a bug.
- [ ] Reordering a **three**-item list succeeds. Two items would pass even with a non-deferred
      constraint; three is what actually exercises it.
- [ ] Reordering with an id that is not in the playlist is 400, and the order is unchanged.
- [ ] Removing the middle item leaves positions `0, 1` -- contiguous, not `0, 2`.
- [ ] The same name twice for one owner is 409; the same name for two different owners is fine.
- [ ] **A playlist holding a track with no album still returns that track.** The left-join
      regression, restated for this endpoint.

---

## Task 5: The controller

**Files:** `api/PlaylistController.java` and the request/response records; `api/ApiExceptionHandler.java`

| Method | Path | Does |
|---|---|---|
| `GET` | `/api/playlists` | Your playlists, most recently touched first |
| `POST` | `/api/playlists` | Create. 409 on a duplicate name |
| `GET` | `/api/playlists/{id}` | One playlist with its ordered items |
| `PATCH` | `/api/playlists/{id}` | Rename |
| `DELETE` | `/api/playlists/{id}` | Delete |
| `POST` | `/api/playlists/{id}/items` | Append a track |
| `DELETE` | `/api/playlists/{id}/items/{itemId}` | Remove, closing the gap |
| `PUT` | `/api/playlists/{id}/items` | Reorder, whole list (D23) |

- [ ] The caller comes from `@AuthenticationPrincipal AppUserPrincipal principal`; pass
      `principal.id()`. `AppUserPrincipal` carries the database id precisely so this needs no
      lookup by username.
- [ ] Responses are explicit records. Never a serialized `Playlist`: it holds `AppUser`, which
      holds the password hash.
- [ ] `ApiExceptionHandler` gains `PlaylistNotFoundException` to 404 and
      `DuplicatePlaylistNameException` to 409, both in the existing `ErrorResponse` shape.
- [ ] **No change to `SecurityConfig`.** `anyRequest().authenticated()` already covers these paths
      and both roles should reach them. Recorded here so the next reader knows it was a decision.

---

## Task 6: Closing the gap when a track is deleted

**Files:** `catalog/TrackDeletionService.java` (modified),
`backend/src/test/java/com/kasi/musiclibrary/playlist/PlaylistTrackDeletionTest.java`

The foreign key cascade removes the rows; it does not renumber what is left, so a playlist that
held positions 0, 1, 2 is left with 0, 2 after the middle track is deleted library-wide.

- [ ] Write the test first: admin deletes a track that sits in the middle of a customer's playlist;
      the playlist returns the two survivors at positions 0 and 1.
- [ ] Then renumber the affected playlists in `TrackDeletionService`, inside the same transaction
      as the delete.

---

## Task 7: Playback, extracted

**Files:** `frontend/src/app/playback/playback.service.ts`,
`frontend/src/app/playback/player/player.ts|.html|.css`,
`frontend/src/app/catalog/track-list/track-list.ts|.html` (modified)

- [ ] `PlaybackService` holds `queue` and `index` signals and exposes `playOne(track)`,
      `playQueue(tracks, from)`, `next()` and a computed `nowPlaying`.
- [ ] `<app-player>` renders the footer and the single `<audio>`, binding `(ended)="next()"`.
- [ ] `track-list` loses its `nowPlayingUrl` signal and its `<audio>`, and calls
      `playback.playOne(track)`. Its "now playing" row highlight reads `playback.nowPlaying()`.
- [ ] `<app-player>` is rendered once, from `app.ts`, so it survives switching views -- a player
      that stops when you open a different screen is worse than no player.

---

## Task 8: The playlist views

**Files:** `frontend/src/app/playlists/*`, `frontend/src/app/app.ts` (modified),
`frontend/src/app/catalog/track-list/track-list.ts|.html` (modified)

- [ ] `playlist.service.ts` is the only file that knows playlist URLs, matching the note in
      AGENTS.md about `track.service.ts`.
- [ ] `playlist-list`: your playlists with track counts, create, open, delete.
- [ ] `playlist-detail`: ordered items, **Move up** / **Move down**, remove, and **Play all**.
- [ ] **Reorder is buttons, not drag-and-drop.** Drag-and-drop means adding `@angular/cdk` to a
      deliberately small dependency tree, and is not keyboard-operable without extra work. Each
      click sends the full new order (D23).
- [ ] `track-list` gains an **Add to playlist** control per row, which opens
      `<app-add-to-playlist>`. That component owns the playlist choices and the create-and-add
      path, so the library view does not need to know how playlists work -- it only knows a track
      can be added somewhere, and which row's panel is open.
- [ ] The chooser is a `<select>` listing your playlists with their track counts, plus
      "+ New playlist...". Choosing an existing one adds immediately; choosing "New" reveals a name
      field. An owner with no playlists yet lands straight on the name field, because there is
      nothing to choose between.
- [ ] **All colours come from the tokens in `src/styles.css`.** A hardcoded hex is readable in one
      theme and not the other.
- [ ] `app.ts` gains `playlists` and `playlist-detail` view states and a nav entry.

**Do not write signals during template render** (AGENTS.md, `NG0600`). `playlist-detail` derives
its editable order from a fetched playlist: use `linkedSignal`, as `catalog/track-edit/track-edit.ts`
does.

---

## Task 9: Make the documents match

- [ ] `ROADMAP.md`: Phase 3 built; note that cut line #2 (reordering) was not taken.
- [ ] `DECISIONS.md`: add D22, D23, D24.
- [ ] `USE-CASES.md`: the Playlists row says "Phase 3 of the roadmap".
- [ ] `README.md`: playlists in what the app does.
- [ ] Regenerate the committed spec, which AGENTS.md requires after any controller change:
      `curl -s localhost:8080/v3/api-docs | python3 -m json.tool > docs/api/openapi.json` and the
      `.yaml` alongside it.

---

## Phase exit criteria

1. `cd backend && ./gradlew test` passes, including the cross-user test.
2. `docker compose down -v && docker compose up --build` boots clean, with Flyway applying V6.
3. As `customer`: create a playlist, add three tracks, reorder, remove one, **Play all**, and hear
   it advance to the next track unaided.
4. As `admin` in a separate browser profile: the customer's playlist is not listed, and its id
   returns 404.
5. As `admin`: delete a track that sits in the customer's playlist; it disappears from the playlist
   and the surviving order is contiguous.
6. `docs/api/openapi.json` regenerated and committed.
