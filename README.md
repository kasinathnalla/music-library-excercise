# Music Library

A self-contained music library that ingests your own audio files, reads their embedded tags into
real artist and album records, searches across the catalog, streams playback with working seek, and
lets you remove things again.

No third-party music service and no API keys. The application is its own API and its own
identity provider: sign in with one of the seeded accounts below.

---

## TL;DR — run it

You need **Docker** and nothing else.

```bash
git clone https://github.com/Anupchat/music-library-exercise.git
cd music-library-exercise
docker compose up --build
```

Then open **<http://localhost:8080>** and sign in.

| Username | Password | Can |
|---|---|---|
| `admin` | `admin` | Upload, correct metadata, delete, and listen |
| `customer` | `customer` | Browse, search, and listen |

Seeded by a database migration on first boot, alongside the six starter tracks, so there is
something to sign in to and something to listen to immediately. These are demo credentials for a
local exercise, not production ones — see [Deliberate limitations](#deliberate-limitations).

No account yet? The sign-in screen has a **Create an account** link. Self-registration always
creates a listener account — there is no way to request an admin account through it.

First build takes a few minutes (it downloads Node, a JDK, and the Gradle dependencies). Later
builds are cached and take seconds.

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

**Accounts and roles.** Two roles: an **admin** curates the library (upload, edit, delete); a
**customer** browses, searches, and listens, and nothing else. Two accounts of each are seeded on
first boot, and anyone can create a customer account from the sign-in screen — admin accounts are
provisioned directly against the database, not through the app. Signing in is HTTP Basic; the app
turns that into a session so that the browser's own audio requests (seeking included) are
authenticated without needing a header attached to them. There is no password reset and no
TLS — see [Deliberate limitations](#deliberate-limitations).

---

## Documents in this repository

| File | What it is |
|---|---|
| `README.md` | This file: what it is and how to run it |
| `AGENTS.md` | Operating context for AI agents working in this repo, plus how it was actually built |
| `docs/ARCHITECTURE.md` | Diagrams: deployment, components, data model, and the key interaction and state flows |
| `docs/USE-CASES.md` | What the system does from a user's point of view, with the test behind each case |
| `docs/QUESTIONS.md` | The clarifying questions I would have asked, each with the default I proceeded on |
| `docs/plans/ROADMAP.md` | The phase plan, why it is ordered that way, and what gets cut first |
| `docs/plans/01-foundation-and-library.md` | The full Phase 1 implementation plan, task by task |
| `docs/plans/06-users-and-auth.md` | The users-and-roles implementation plan: why Basic auth becomes a session, and why editing is admin-only |
| `docs/api/openapi.json` / `.yaml` | The generated OpenAPI 3.1 spec, committed so it can be read without running anything |
| `docs/DECISIONS.md` | The design decisions that had real alternatives, and why each went the way it did |
| `backend/src/main/resources/seed-audio/CREDITS.md` | Where the bundled audio came from |

Start with `docs/ARCHITECTURE.md` for how it fits together, `docs/USE-CASES.md` for what it does,
`docs/QUESTIONS.md` for what I thought was ambiguous about the brief, and `docs/DECISIONS.md` for why
the code looks the way it does.

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

- **Customer self-registration only.** Anyone can create a customer account from the sign-in
  screen; an admin account is always provisioned directly against the database. There is no
  password change or reset, and no account lockout after failed attempts. Good enough to
  demonstrate a real authorization boundary, not a production identity system.
- **Basic auth over plain HTTP.** The password is sent on every sign-in request with nothing
  encrypting the connection, because there is no TLS in front of this exercise. Fine for a local
  demo; a real deployment needs HTTPS in front of it before this scheme is safe to use as-is.
- **One shared library, not one library per customer.** Every signed-in user sees the same
  catalog. `track.uploaded_by` records who added a row, but nothing filters on it yet.
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
