# AGENTS.md

Operating context for AI agents working in this repository. Written to be useful to an agent that
has just arrived with no history, and honest about how the code got here.

---

## Conventions, and how they are enforced

The conventions below are the highest authority in this repository. Where one can be checked by a
machine, it is: `python3 scripts/standards-check.py` runs in under a second, needs neither Docker
nor the network, and is the first thing `scripts/check.sh` does. A standard nobody can fail is a
suggestion.

---

## What this is

A music library: Spring Boot backend, Angular frontend, PostgreSQL, packaged as one Docker image
plus a database. See `README.md` for what it does and how to run it.

## Commands

```bash
# Everything, the way a reviewer runs it
docker compose up --build

# Development: database, then backend, then frontend
docker compose up -d db
cd backend && DATABASE_URL=jdbc:postgresql://localhost:55432/musiclibrary ./gradlew bootRun
cd frontend && yarn start

# Tests
cd backend && ./gradlew test
cd frontend && yarn test --run

# Regenerate the committed OpenAPI spec after changing any controller
curl -s localhost:8080/v3/api-docs | python3 -m json.tool > docs/api/openapi.json
curl -s localhost:8080/v3/api-docs.yaml > docs/api/openapi.yaml
```

**Use yarn, not npm, in `frontend/`.** npm 10.9.8 fails on this dependency tree with
`Cannot read properties of null (reading 'edgesOut')`. A `yarn.lock` is committed; do not add a
`package-lock.json`.

**Node 22.22.3 or newer is required.** Angular 22 declares
`node: ^22.22.3 || ^24.15.0 || >=26.0.0` and fails outright below that.

**The database is on host port 55432**, not 5432, to avoid colliding with other local Postgres
instances. Inside the compose network it is still `db:5432`.

---

## Conventions

**Flyway owns the schema. Hibernate never creates or alters a table.** `ddl-auto` is `validate`, so
an entity that disagrees with a migration fails at startup rather than diverging silently. When you
change an entity, write a migration. When you change a migration that has already run locally, reset
the volume with `docker compose down -v`, because Flyway rejects a changed checksum.

**`open-in-view` is off.** A lazy association touched during response serialization throws
`LazyInitializationException`, which surfaces as a 500. If a response needs an association, fetch it
explicitly — see `TrackRepository.findByIdWithAlbum`. This has already caused one production bug;
do not "fix" a lazy-loading error by turning open-in-view back on.

**Use left joins when querying tracks.** A track with no album is legitimate. Path navigation in
JPQL (`t.album.title`) compiles to an inner join and will silently drop those rows. There is a
regression test for this: `TrackControllerTest.listIncludesTracksThatHaveNoAlbum`.

**Response shapes are explicit records**, never serialized JPA entities and never Spring's `Page`.
The wire format should be a decision, not a consequence of the persistence model.

**Authorization rules live in `SecurityConfig`, not on domain services.** The matrix (who may
reach which endpoint) is expressed once, as URL-and-method rules, so it can be read in one screen
and diffed in one place. Do not add `@PreAuthorize` to `IngestService`, `TrackUpdateService`, or
similar — `SeedRunner` calls them at boot with no authenticated user present, and method security
would either refuse it or need its own bypass. See D15.

**jaudiotagger is confined to `AudioTagReader`.** It is unmaintained (3.0.1, 2021). Keeping it
behind one class means replacing it touches one file. Do not import it anywhere else.

**Not every test needs Spring.** `AudioFileStoreTest` touches the filesystem but not the database,
so it is a plain JUnit test with `@TempDir` and runs in milliseconds. Only use
`PostgresIntegrationTest` when the test genuinely needs the database.

**Tests use a real PostgreSQL** via Testcontainers, shared across the run through Spring's test
context cache. Do not substitute H2 or an in-memory database; the schema uses Postgres-specific
features and the point of these tests is that the migrations actually work.

---

## Things that will bite you

**Testcontainers overrides `application.yaml`.** `@ServiceConnection` injects datasource properties
directly, so a broken datasource block in the config file will not fail any test. The suite passed
for a while against an application that could not start. If you change `application.yaml`, boot the
app for real before believing it.

**MockMvc skips the Spring Security filter chain unless told not to.**
`MockMvcBuilders.webAppContextSetup(context).build()` alone builds a chain with no security
filters, so an unauthenticated request succeeds and every authorization test passes for the wrong
reason. Extend `support.SecuredMockMvcTest`, which applies `springSecurity()` — every HTTP test in
this codebase does. To confirm this is still wired, comment out that configurer and check that
`AuthenticationTest` goes red; if it stays green, security is not actually being exercised.

**Do not append to structured files with `cat >>`.** Appending a block to `application.yaml` put it
under the wrong parent key, and appending to `angular.json` produced invalid JSON. Both looked fine
in a diff. Edit structured files with a parser, or rewrite them whole.

**Do not write signals during template render.** `{{ someMethodThatSetsSignals() }}` throws
`NG0600`, and the symptom is a form that renders with every field empty rather than an obvious
error. For writable state derived from an input, use `linkedSignal`. See
`features/library/components/track-edit/track-edit.ts`.

**The Angular dev server does not always see newly created files.** This repository is in a Google
Drive folder, and a new component file can produce a persistent `NG2008: Could not find template
file` even though the file is on disk. Restart the dev server (`rm -rf .angular` first) rather than
debugging the component.

**`.DS_Store` files appear everywhere.** This repository lives in a Google Drive folder. They are
gitignored; do not commit them.

---

## Layout

```
backend/src/main/java/com/kasi/musiclibrary/     Organised by layer
  controller/  AuthController, PlaylistController, StatsController, StreamController, TrackController
  service/     Business logic, plus the adapters it depends on (AudioTagReader, AudioFileStore, SeedRunner)
  repository/  Spring Data interfaces and their projections
  entity/      JPA entities and the enums persisted with them
  dto/         Every request and response record, plus internal value records
  exception/   Every application exception, flat
  advice/      ApiExceptionHandler - the one @RestControllerAdvice
  security/    SecurityConfig, AppUserPrincipal, DatabaseUserDetailsService
  config/      StorageProperties, OpenApiConfig, SpaForwardingConfig
frontend/src/app/
  core/        App-wide singletons: interceptors/, models/, services/ (auth, playback)
  shared/      components/player/ - only what is genuinely shared, not empty scaffolding
  features/    auth/ library/ playlists/ welcome/, each with components/ models/ services/
  Each feature's own service is the only file that knows that feature's API URLs.
```

Both halves were reorganised after Phase 3: the backend from feature packages to layers
(DECISIONS 25), the frontend from flat feature folders to core/shared/features (DECISIONS 26).
Both moved for the same reason - the previous layout was defensible but kept surprising people who
opened the repository expecting the conventional one.

---

## Where the requirements live

`docs/QUESTIONS.md` records what was ambiguous in the original brief and the default chosen for
each. `docs/DECISIONS.md` records the design decisions that had real alternatives.
`docs/plans/ROADMAP.md` is the phase plan; Phase 1 is what exists, Phases 2 to 5 are not built.

Before adding a feature, check whether the roadmap already places it in a later phase and whether
`QUESTIONS.md` already records a decision about it.

---

## How this was actually built

Substantially with an AI agent (Claude), directed and reviewed by me. Worth knowing because it
shaped the result in specific ways:

The plan in `docs/plans/` was written before any code, and the code was then written against it
task by task. That was the right call: the reconciliation rules and the provenance design were
settled while they were still cheap to change.

Where it went wrong is more instructive. Compressing several planned tasks into single large
batches produced code without the tests the plan specified, and four defects reached a "passing"
build as a result: a misindented `application.yaml`, a `char(64)` versus `varchar(64)` schema
mismatch, an unpublished database port, and malformed `angular.json`. None were caught by the test
suite. All four were found within minutes of actually starting the application, and a fifth — the
lazy-loading 500 on upload — was found by a human clicking the button.

The lesson recorded here for the next agent: the plan's test-first ordering is not ceremony. Write
the test, watch it fail, then write the code. And run the application before claiming it works.
