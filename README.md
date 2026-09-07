# Music Library

A self-contained music library that ingests your own audio files, reads their embedded tags into
real artist and album records, searches across the catalog, streams playback with working seek, and
lets you remove things again.

No third-party music service, no account, and no API keys. The application is its own API.

---

## TL;DR — run it

You need **Docker** and nothing else.

```bash
git clone https://github.com/Anupchat/music-library-exercise.git
cd music-library-exercise
docker compose up --build
```

Then open **<http://localhost:8080>**.

First build takes a few minutes (it downloads Node, a JDK, and the Gradle dependencies). Later
builds are cached and take seconds. The app starts with six tracks already loaded, so there is
something to look at immediately.

| What | Where |
|---|---|
| The app | <http://localhost:8080> |
| API documentation (Swagger UI) | <http://localhost:8080/swagger-ui/index.html> |
| OpenAPI spec | <http://localhost:8080/v3/api-docs> |
| Health | <http://localhost:8080/actuator/health> |

To stop it, and to throw away the database and uploaded audio:

```bash
docker compose down -v
```

---

## Running it for development

Two processes, so the frontend hot-reloads.

**Requirements:** Docker, a JDK (any recent one — the build pins its own Java 21 toolchain and will
fetch it), and Node 22.22.3 or newer.

```bash
# 1. Database only
docker compose up -d db

# 2. Backend on :8080
cd backend
DATABASE_URL=jdbc:postgresql://localhost:55432/musiclibrary ./gradlew bootRun

# 3. Frontend on :4200, proxying /api to the backend
cd frontend
yarn install
yarn start
```

Open <http://localhost:4200>.

The database is published on host port **55432**, not 5432, so it does not collide with any Postgres
you already have running. Inside Docker the app still reaches it as `db:5432`.

### Tests

```bash
cd backend && ./gradlew test     # integration tests, real Postgres via Testcontainers
cd frontend && yarn test --run   # component and service tests
```

The backend tests need Docker running: they start a real PostgreSQL container rather than
substituting an in-memory database, because the schema uses Postgres-specific features and an
in-memory stand-in would not prove the migrations work.

---

## What it does

**Ingest.** Upload an audio file and its embedded tags are parsed and reconciled against what is
already in the library, so a second track from an album you own joins that album rather than
creating a duplicate one.

**Real artists and albums.** These are their own rows, not strings copied onto every track. Renaming
an artist is one update, and an album can be credited to an artist properly. An album's identity is
its title plus its credited artist, because plenty of different albums are called *Greatest Hits*.

**Search.** One query matches track title, album title, and artist name at once. Tracks with no
album still appear; the joins are left joins specifically so they are not silently dropped.

**Playback.** Audio is streamed with `Accept-Ranges`, so a `Range` request returns `206 Partial
Content` and the browser's scrubber works instead of only playing from the start.

**No duplicates.** A track is identified by the SHA-256 of its contents. Uploading the same audio
under a different filename is recognised and rejected with a `409`, not copied.

**Metadata editing.** Track title, artist, album, track and disc number, and release year can all
be corrected. Editing the artist or album *moves* the track to that artist or album, matching an
existing one before creating a new one, because "this track is on a different album" and "this album
is misnamed" are different intentions. Anything left with nothing on it is cleaned up.

Every hand edit is recorded per field, and automated enrichment is required to leave recorded fields
alone. Edits change the library only; the tags inside your audio files are never rewritten.

**Deletion.** Removing a track deletes its stored bytes too, and cleans up what it leaves behind: an
album with no remaining tracks is removed, and an artist credited on no remaining albums goes with
it. This is permanent; there is no undo in this version.

---

## Documents in this repository

| File | What it is |
|---|---|
| `README.md` | This file: what it is and how to run it |
| `AGENTS.md` | Operating context for AI agents working in this repo, plus how it was actually built |
| `docs/QUESTIONS.md` | The clarifying questions I would have asked, each with the default I proceeded on |
| `docs/plans/ROADMAP.md` | The five-phase plan, why it is ordered that way, and what gets cut first |
| `docs/plans/01-foundation-and-library.md` | The full Phase 1 implementation plan, task by task |
| `docs/api/openapi.json` / `.yaml` | The generated OpenAPI 3.1 spec, committed so it can be read without running anything |
| `docs/DECISIONS.md` | The design decisions that had real alternatives, and why each went the way it did |
| `backend/src/main/resources/seed-audio/CREDITS.md` | Where the bundled audio came from |

Start with `docs/QUESTIONS.md` if you want to know what I thought was ambiguous about the brief, and
`docs/DECISIONS.md` if you want to know why the code looks the way it does.

---

## Architecture

```
Browser ──▶ Angular 22 SPA ──▶ REST/JSON ──▶ Spring Boot ──▶ PostgreSQL (catalog)
                                                        └──▶ Disk volume (audio bytes)
```

One app container and one database container. The production image is a multi-stage build: Node
compiles the Angular app, Gradle builds the jar with that output inside it as static resources, and
a JRE image runs the result. That is why the whole system is one command and why there is no
separate web server to configure.

**Backend layout**, by responsibility rather than by technical layer:

| Package | Holds |
|---|---|
| `catalog` | The domain: `Artist`, `Album`, `Track`, their repositories, and deletion |
| `ingest` | Getting audio in: tag reading, file storage, reconciliation, seeding |
| `api` | HTTP only. Request handling and response shapes. No business logic |
| `config` | Storage properties, OpenAPI metadata, SPA forwarding |

`ingest` is isolated because it is the only part that touches an unmaintained third-party library
and arbitrary user files. `AudioTagReader` is the single place jaudiotagger appears, so replacing it
would touch one file and its test.

**Stack:** Java 21, Spring Boot 4.1.1, Spring Data JPA, Flyway, PostgreSQL 16, jaudiotagger,
springdoc-openapi, JUnit 5 with Testcontainers, Angular 22 with signals and zoneless change
detection, Vitest.

---

## Deliberate limitations

Called out because they are choices, not oversights. The reasoning for each is in
`docs/QUESTIONS.md` and `docs/DECISIONS.md`.

- **Single user, no authentication.** The schema carries an owner concept so multi-user is not a
  rewrite, but there is no login.
- **Tag edits do not write back to files.** Edits change the database only. Writing tags into the
  user's actual files is what iTunes and MusicBrainz Picard do and it makes edits portable, but it
  also means destructive writes to files people care about. The trade-off is discussed in Q13 and
  decision 10.
- **No bulk editing.** Edits are one track at a time. Bulk editing across a selection, with a
  preview step, is the next thing I would build.
- **Provenance is recorded but nothing consumes it yet.** Hand-edited fields are tracked and
  reported through the API so enrichment can respect them; there is no enrichment to respect them
  yet.
- **The library view fetches one page.** The API pages and clamps page size from day one; the UI
  does not yet have a pager or infinite scroll.
- **A track's artist is its album's artist.** The `track_artist` join table exists but nothing maps
  it yet, so per-track credits are not modelled. Needed first by metadata editing.
- **No pagination, sorting, or grouping by album in the UI.** The data supports it; the interface
  does not expose it yet.
