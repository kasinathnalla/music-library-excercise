# Design Decisions

The decisions that had real alternatives, and why each went the way it did. Decisions that were
obvious are not listed.

---

## 1. Build my own API rather than consume a third-party service

**Alternatives:** consume Spotify, Tidal, or Qobuz; build my own backend; both.

The brief said "leverages an API," which is ambiguous, and it also said the result must build and
run on someone else's machine. Those two requirements pull against each other: if the reviewer has
to register a Spotify developer application to run the submission, it is not actually reproducible.

I build the API. Any third-party integration sits behind an interface and is off by default, so the
application works completely with no external accounts. This was later confirmed as the intent.

---

## 2. Artists and albums are first-class rows, not track-tag strings

**Alternative:** derive artist and album from each track's tags, as the simplest ID3-shaped model.

Derived entities are less code and they follow the files, but they make "rename this artist" an
update across every track, and they cannot represent things tags do not carry, such as an album
credited to two artists.

An album's identity is its title *plus* its credited artist, because many unrelated albums are
called *Greatest Hits*. Ingest does a case-insensitive match-or-create against existing rows, so a
second track from an album you already own joins it rather than creating a duplicate.

The cost is a reconciliation step on every ingest. It is worth it.

---

## 3. A track is identified by the hash of its contents

**Alternative:** identify by file path, or allow duplicates freely.

Track identity is a SHA-256 of the bytes, unique in the schema. Uploading the same audio under a
different filename is therefore a `409`, not a second copy.

Identity is content, not filename. Building this in from the start was nearly free; retrofitting it
once a library has accumulated duplicates is not. It also makes seeding idempotent for free — the
seed runner re-ingests on every boot and the already-present files simply conflict, so no "have I
seeded yet" flag is needed.

A consequence worth noting: deletion has to remove the stored bytes too, otherwise a deleted track
could never be re-uploaded.

---

## 4. A duplicate upload is an error, not a silent no-op

The user who uploaded the file deserves to be told what happened. `409` carries the id of the
existing track, so a client can navigate to it rather than just reporting failure.

---

## 5. A track with no album gets no album

**Alternative:** create an "Unknown Album" row.

An "Unknown Album" would collect every untagged track in the library into one meaningless grouping
that looks like a real album. A null album is honest, and the UI renders it as an em dash.

This forced a real constraint on querying: every track query must use **left** joins. JPQL path
navigation (`t.album.title`) compiles to an inner join and would silently drop exactly these rows.
There is a regression test guarding it.

---

## 6. Flyway owns the schema; Hibernate only validates

`ddl-auto` is `validate`, never `update`. An entity that disagrees with the schema fails at startup
with a precise message instead of diverging quietly or, worse, mutating the schema at runtime.

This paid for itself immediately: it caught `content_hash char(64)` against a `varchar(64)` mapping.
Postgres `char(n)` space-pads, which is wrong for comparing hex hashes, so the check found a genuine
bug rather than a cosmetic mismatch.

---

## 7. `open-in-view` is off

**Alternative:** leave Spring Boot's default on, which keeps a session open for the whole request.

Open session in view makes lazy loading work anywhere, including during response serialization,
which hides the number of queries a request actually makes and makes N+1 problems invisible until
production.

The cost is that a response needing an association must fetch it deliberately. This bit once: the
upload endpoint re-fetched the saved track with a plain `findById` and then serialized its album,
throwing `LazyInitializationException` and returning a 500 for an upload that had actually
succeeded. The fix was an explicit fetch join, not turning the setting back on.

---

## 8. Response shapes are explicit records

Not serialized JPA entities, and not Spring's `Page`, whose JSON is an implementation detail that
has changed across versions. `TrackResponse` is flattened rather than nested because the library
view renders one row per track with album and artist inline, and nesting would make the client walk
possibly-null objects for every cell.

---

## 9. Deleting a track cleans up what it orphans

**Alternative:** delete only the track row.

Deleting the last track of an album leaves an empty album, and then an artist credited on nothing.
Those rows are invisible in the track list but they inflate the library counts and resurface during
ingest reconciliation. So deletion removes them.

The stored file is deleted last and on a best-effort basis. A file that cannot be removed leaves
unreferenced bytes on disk, which is recoverable; failing the request after the database change has
already committed would be worse.

---

## 10. Metadata edits change the database, not the files

The most consequential decision in the project. Now implemented.

Writing tags back into the user's actual files is what iTunes and MusicBrainz Picard do, and it makes
edits portable to other players. It also means destructive writes to files the user cares about,
format-specific tag handling, and a real chance of corrupting a library.

Database-only edits are safe and reversible, at the cost of the app's view diverging from the files
on disk. Edits are database-only, and the interface says so plainly rather than letting people assume their
files are being rewritten.

Provenance is tracked **per field**, not per track, and user edits are never overwritten by
automated sources. Someone who fixed one misspelled artist name should not lose it because a better
cover image arrived for the same track. Nothing enriches automatically yet, so today the record is
kept and reported through the API; it has to start being kept now, because once an automated source
has overwritten a hand correction the information that it *was* a hand correction is gone.

### Editing an album or artist re-points the track; it does not rename

**Alternative:** treat editing a track's artist name as renaming that artist everywhere.

"This track is on a different album" and "this album is misnamed" are different intentions, and a
per-track edit means the first. So changing a track's album or artist matches an existing row before
creating one, and moves just that track. An album left with no tracks, or an artist left with no
albums, is removed.

Renaming across the whole library is a genuinely useful operation, and it belongs on the album or
artist rather than on a track. It is not built yet.

---

## 11. Test fixtures and seed audio are synthesized, not downloaded

Six seed tracks and two test fixtures are generated with ffmpeg as sine tones. They carry no
copyright and need no attribution, so the repository raises no licensing question at all, which is a
cleaner answer than "these are probably public domain." The test suite also depends on no network
and no third-party file continuing to exist at a URL.

ffmpeg is needed once, at authoring time. The generated files are committed, so it is not a build or
runtime dependency.

---

## 12. Tests run against a real PostgreSQL

**Alternative:** H2 or another in-memory database, which is faster to start.

The schema uses `gen_random_uuid()`, partial and expression indexes, and `timestamptz`. An in-memory
substitute would either not support them or would behave differently, and the whole point of these
tests is to prove the migrations work. Testcontainers starts one container shared across the run via
Spring's test context cache.

---

## 13. The database is published on host port 55432

Not 5432. A developer machine frequently already has something on the default port — mine did. The
non-default host port means this project does not have to be the only Postgres running. Inside the
compose network it is still reached as `db:5432`, so nothing about the application changes.

---

## 14. Accounts are rows, seeded by migration

**Alternatives:** two hardcoded users in Spring Security's in-memory `UserDetailsManager`; a
seeded table; a seeded table plus self-registration.

In-memory users are less code, but a user is data, and the application already owns a database and
a Flyway pipeline. The moment anyone wants a third account, admin-managed accounts, or a record of
who uploaded a track, in-memory users have to be thrown away entirely. Seeding two accounts via a
migration (`V3__users.sql`) keeps `docker compose up` demoable on a clean volume with no signup
screen, and the accounts are ordinary rows from day one.

Self-registration for customers was added afterward (decision 17) once the login screen needed
somewhere to send someone who does not have an account. Admin accounts remain provisioned
directly against the database — the asymmetry is deliberate, not a gap.

## 15. Authorization lives in the filter chain, not on the domain services

**Alternatives:** `@PreAuthorize` on `IngestService`, `TrackUpdateService`, and
`TrackDeletionService`; a rule table read at request time; the filter chain.

The rules are URL-and-method rules, so `SecurityConfig` is where the whole matrix can be read and
diffed in one screen. Method security on the domain services would also apply to `SeedRunner`,
which ingests the bundled tracks at boot with no authentication present — that path would either
need its own bypass or would fail at startup. It would also push an HTTP concern into `catalog/`
and `ingest/`, which AGENTS.md is explicit know nothing about HTTP.

The trade-off, stated plainly: a new controller that forgets to add a matcher in `SecurityConfig`
is reachable by any signed-in user, where method security would have failed closed by default.
Mitigated two ways — `anyRequest().authenticated()` is the final rule, so nothing is ever reachable
anonymously by omission, and `AuthorizationMatrixTest` asserts the matrix directly rather than only
a happy path.

## 16. Basic credentials establish a session; the session authenticates the audio element

**Alternatives:** challenge with `WWW-Authenticate` and let the browser cache Basic credentials
itself; fetch audio as a blob through `HttpClient` so an `Authorization` header can be attached;
authenticate once with Basic and persist the result in a session.

This one is forced by a feature the README treats as load-bearing: seeking. The browser's
`<audio>` element issues its own `Range` requests directly against
`/api/tracks/{id}/stream` — the browser makes that request, not Angular, so there is no way to
attach a header to it.

Letting the browser challenge and cache Basic credentials works, but pops the native browser
credential dialog over the SPA, with no way to sign out of it from the page. Fetching audio as a
blob would let a header be attached, but kills range requests and therefore kills seeking — the
feature UC-3 exists to prove works.

So: Basic authenticates once, and the resulting security context is written to an HTTP session
(`HttpSessionSecurityContextRepository`, explicit because the Basic filter's own default since
Spring Security 6 is request-scoped, not session-scoped). Everything after that — including the
audio element's own requests — rides the session cookie. The consequence that follows directly
from this: a cookie is an ambient credential, so CSRF protection has to be turned back on
(`CookieCsrfTokenRepository.withHttpOnlyFalse()`, matched to Angular's default `XSRF-TOKEN` /
`X-XSRF-TOKEN` handling).

When this moves to tokens, the seam is `TrackService.streamUrl()` on the frontend and
`StreamController` on the backend — a short-lived signed URL replaces the cookie there, and
nothing else in this design changes.

## 17. Self-registration exists for customers, not for admins

**Alternatives:** no self-registration at all (decision 14's original position); self-registration
for any role, chosen by the caller; self-registration for customers only.

Leaving registration out entirely was the simplest thing, but a login screen with no path forward
for someone who does not yet have an account is a dead end, and growing the customer population
is a reasonable thing for the application itself to do. Growing the admin population is a
different kind of decision — it is who gets to curate the shared library — and that stays a
deliberate, out-of-band act, provisioned directly against the database exactly like the two
seeded accounts.

The boundary is enforced by the shape of the request, not by a check that a later change could
weaken: `RegisterRequest` has a `username` and a `password` and nothing else. There is no field to
set to `ADMIN`, so a client that sends one anyway is simply ignored — proven directly in
`RegistrationTest.anAttemptToSupplyARoleIsIgnoredNotHonoured`, which posts a `role` field and
asserts the resulting account is a customer regardless.
