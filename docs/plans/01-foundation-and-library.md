# Phase 1: Foundation and Library Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `docker compose up` brings up a music library that has ingested real audio files, parsed their tags into artist, album, and track entities, and serves a browsable, searchable UI over its own REST API.

**Architecture:** A Spring Boot service owns the library: it accepts audio uploads, parses embedded tags, reconciles them into first-class Artist and Album entities, and stores tracks in PostgreSQL with the audio bytes on a mounted volume. An Angular single-page app is the only consumer of that API. The production image is a multi-stage build that compiles the Angular app and copies it into the Spring Boot jar's static resources, so the whole system is two containers (app, db) and one command. No third-party music service is involved and no credentials are required to run.

**Tech Stack:** Java 21 (Gradle toolchain, auto-provisioned), Spring Boot 4.1.1, Spring Data JPA, Flyway, PostgreSQL 16, jaudiotagger 3.0.1, JUnit 5 + Testcontainers, Angular 22 + TypeScript 6.0, Vitest, Docker Compose v2.

---

## Prerequisites

Verify before starting. Two of these are known to be wrong on the development machine as of this
writing.

- [ ] **Node 22.22.3 or newer.** Angular 22 declares `node: ^22.22.3 || ^24.15.0 || >=26.0.0`. The
  machine currently has v18.20.8, which will fail at `ng new`. Fix with `nvm install 22 && nvm use 22`
  or `brew install node@22`. This affects local development only; the Docker build pins its own Node
  and is unaffected.
- [ ] **Docker and Docker Compose v2.** Present (27.4.0 / v2.31.0).
- [ ] **A JDK to run Gradle.** Present (24.0.2). The build itself targets Java 21 via a Gradle
  toolchain and will provision that JDK automatically, so the running JDK version does not need to
  match.
- [ ] **ffmpeg**, for generating test audio fixtures in Task 5. Present (8.1.1). Required only once,
  at development time, to create files that are then committed. Not a build or runtime dependency.

## File Structure

```
kasi-project-github/
  docker-compose.yml            App + Postgres. The one-command entry point.
  Dockerfile                    Multi-stage: Node builds Angular, Gradle builds jar, JRE runs it.
  .gitignore
  backend/
    build.gradle.kts            Single Gradle project. Java toolchain pinned here.
    settings.gradle.kts
    gradlew, gradle/            Committed wrapper. Reviewer needs no Gradle install.
    src/main/java/com/kasi/musiclibrary/
      MusicLibraryApplication.java
      catalog/                  The domain. Entities and their repositories.
        Artist.java, Album.java, Track.java, TrackArtist.java
        ArtistRepository.java, AlbumRepository.java, TrackRepository.java
      ingest/                   Getting audio in. The riskiest area, isolated.
        AudioTagReader.java     Wraps jaudiotagger. The only file that knows it exists.
        ParsedTags.java         Immutable result of parsing. No library types leak out.
        AudioFileStore.java     Bytes on disk. Path allocation and retrieval.
        IngestService.java      Orchestrates: store, parse, reconcile, persist.
        SeedRunner.java         Ingests bundled tracks on first boot.
      api/                      HTTP. Thin. No business logic.
        TrackController.java
        TrackResponse.java, TrackPage.java
      config/
        StorageProperties.java
    src/main/resources/
      application.yaml
      db/migration/V1__catalog.sql
      seed-audio/               Bundled public-domain tracks, ingested on first boot.
      static/                   Angular build output lands here in the Docker build.
    src/test/java/com/kasi/musiclibrary/
      ...                       Mirrors main. Integration tests use Testcontainers.
    src/test/resources/fixtures/  Generated audio fixtures, committed.
  frontend/
    package.json, angular.json, tsconfig.json
    src/app/
      app.ts, app.config.ts, app.routes.ts
      catalog/
        track.service.ts        The only file that talks to the API.
        track.model.ts          Types mirroring the API contract.
        track-list/             The library view.
```

**Boundary rationale.** `ingest/` is isolated because it is the only part touching an unmaintained
third-party library and arbitrary user files. `AudioTagReader` is the single seam where jaudiotagger
appears, so replacing it (see the contingency in Task 5) touches one file and its test. `api/` holds
no logic so that the HTTP contract can change without disturbing the domain, and `catalog/` has no
knowledge of HTTP or of files.

---

## Task 1: A backend that boots and answers

**Files:**
- Create: `backend/` (generated), `backend/src/main/java/com/kasi/musiclibrary/MusicLibraryApplication.java`
- Create: `backend/src/main/resources/application.yaml`
- Test: `backend/src/test/java/com/kasi/musiclibrary/HealthEndpointTest.java`
- Create: `.gitignore`

- [ ] **Step 1: Generate the backend project**

From the repository root:

```bash
curl -sL "https://start.spring.io/starter.zip?type=gradle-project-kotlin&language=java&bootVersion=4.1.1&groupId=com.kasi&artifactId=music-library&name=music-library&packageName=com.kasi.musiclibrary&javaVersion=21&dependencies=web,data-jpa,flyway,postgresql,actuator,validation,testcontainers" -o /tmp/backend.zip
unzip -q /tmp/backend.zip -d backend
rm /tmp/backend.zip
chmod +x backend/gradlew
```

Expected: `backend/build.gradle.kts` and `backend/gradlew` exist.

- [ ] **Step 2: Write the failing test**

Create `backend/src/test/java/com/kasi/musiclibrary/HealthEndpointTest.java`:

```java
package com.kasi.musiclibrary;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"
})
class HealthEndpointTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Test
    void healthEndpointReportsUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
```

The datasource autoconfiguration is excluded here on purpose. This task proves the application
context builds and serves HTTP, nothing more. Task 2 introduces the database and removes the need
for this exclusion in later tests.

- [ ] **Step 3: Run the test and confirm it fails**

```bash
cd backend && ./gradlew test --tests HealthEndpointTest
```

Expected: FAIL. The health endpoint is not exposed by default, so the request returns 404 and the
status assertion never runs.

- [ ] **Step 4: Expose the health endpoint**

Create `backend/src/main/resources/application.yaml`:

```yaml
spring:
  application:
    name: music-library

management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      show-details: never
```

- [ ] **Step 5: Run the test and confirm it passes**

```bash
cd backend && ./gradlew test --tests HealthEndpointTest
```

Expected: PASS, 1 test.

- [ ] **Step 6: Add .gitignore and commit**

Create `.gitignore` at the repository root:

```
# Build output
backend/build/
backend/.gradle/
frontend/dist/
frontend/.angular/
frontend/node_modules/

# Local runtime data
media/

# Editors and OS
.DS_Store
.idea/
.vscode/
*.iml
```

```bash
git add .gitignore backend
git commit -m "feat: scaffold Spring Boot backend with health endpoint

Generated from start.spring.io, pinned to Spring Boot 4.1.1 and Java 21.
The Gradle wrapper is committed so the build needs no local Gradle install."
```

---

## Task 2: Database, migrations, and a reproducible test harness

The application currently has no database. This task adds Postgres, Flyway, and a Testcontainers
base class so every later integration test runs against a real Postgres rather than an in-memory
substitute that behaves differently.

**Files:**
- Create: `docker-compose.yml`
- Modify: `backend/src/main/resources/application.yaml`
- Create: `backend/src/main/resources/db/migration/V1__catalog.sql` (empty baseline, filled in Task 3)
- Test: `backend/src/test/java/com/kasi/musiclibrary/support/PostgresIntegrationTest.java`
- Test: `backend/src/test/java/com/kasi/musiclibrary/MigrationTest.java`

- [ ] **Step 1: Write the failing test**

Create the shared base class `backend/src/test/java/com/kasi/musiclibrary/support/PostgresIntegrationTest.java`:

```java
package com.kasi.musiclibrary.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
public abstract class PostgresIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
```

The container is started in a static initializer rather than with `@Container` so that a single
Postgres is shared across every test class in the run. Starting one per class is the common mistake
and it makes the suite several times slower for no benefit, since Flyway gives each run a known
schema.

Create `backend/src/test/java/com/kasi/musiclibrary/MigrationTest.java`:

```java
package com.kasi.musiclibrary;

import com.kasi.musiclibrary.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationTest extends PostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayAppliedTheBaselineMigration() {
        Integer applied = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where success = true", Integer.class);
        assertThat(applied).isGreaterThanOrEqualTo(1);
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

```bash
cd backend && ./gradlew test --tests MigrationTest
```

Expected: FAIL. There is no migration file, so Flyway finds no migrations, `flyway_schema_history` is
either absent or empty, and the query throws or returns 0.

- [ ] **Step 3: Add the baseline migration and datasource configuration**

Create `backend/src/main/resources/db/migration/V1__catalog.sql`:

```sql
-- Baseline. The catalog schema is added in Task 3.
create table schema_marker (
    id          smallint primary key default 1,
    description text     not null,
    constraint schema_marker_single_row check (id = 1)
);

insert into schema_marker (description) values ('music library catalog');
```

Replace `backend/src/main/resources/application.yaml` with:

```yaml
spring:
  application:
    name: music-library
  datasource:
    url: ${DATABASE_URL:jdbc:postgresql://localhost:5432/musiclibrary}
    username: ${DATABASE_USER:musiclibrary}
    password: ${DATABASE_PASSWORD:musiclibrary}
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
  flyway:
    enabled: true

management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      show-details: never
```

`ddl-auto: validate` is deliberate. Hibernate never creates or alters a table; Flyway owns the
schema and Hibernate is checked against it. A mismatch fails at startup rather than silently
diverging.

- [ ] **Step 4: Run the test and confirm it passes**

```bash
cd backend && ./gradlew test --tests MigrationTest
```

Expected: PASS. First run pulls the `postgres:16-alpine` image and takes longer.

- [ ] **Step 5: Add Docker Compose**

Create `docker-compose.yml` at the repository root:

```yaml
services:
  db:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: musiclibrary
      POSTGRES_USER: musiclibrary
      POSTGRES_PASSWORD: musiclibrary
    volumes:
      - db-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U musiclibrary -d musiclibrary"]
      interval: 5s
      timeout: 3s
      retries: 20

  app:
    build:
      context: .
    environment:
      DATABASE_URL: jdbc:postgresql://db:5432/musiclibrary
      DATABASE_USER: musiclibrary
      DATABASE_PASSWORD: musiclibrary
      MEDIA_ROOT: /var/lib/musiclibrary/media
    ports:
      - "8080:8080"
    volumes:
      - media-data:/var/lib/musiclibrary/media
    depends_on:
      db:
        condition: service_healthy

volumes:
  db-data:
  media-data:
```

The `Dockerfile` this references is written in Task 13. Compose is added now so the database is
available for local development from this point on, via `docker compose up db`.

- [ ] **Step 6: Verify the database starts**

```bash
docker compose up -d db && docker compose ps
```

Expected: the `db` service reaches state `running (healthy)` within about 15 seconds.

- [ ] **Step 7: Commit**

```bash
git add docker-compose.yml backend
git commit -m "feat: add Postgres, Flyway, and Testcontainers harness

Schema is owned by Flyway; Hibernate is set to validate so a drift
between entity and schema fails at startup. Integration tests share one
Postgres container across the run."
```

---

## Task 3: The catalog schema

Artists and albums are first-class rows, not strings derived from track tags. The reasoning is in
Q17 of `docs/QUESTIONS.md`: derived entities make "rename this artist" an update across every track
and cannot represent an album credited to two artists.

**Files:**
- Modify: `backend/src/main/resources/db/migration/V1__catalog.sql`
- Test: `backend/src/test/java/com/kasi/musiclibrary/catalog/CatalogSchemaTest.java`

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/com/kasi/musiclibrary/catalog/CatalogSchemaTest.java`:

```java
package com.kasi.musiclibrary.catalog;

import com.kasi.musiclibrary.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogSchemaTest extends PostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void catalogTablesExist() {
        List<String> tables = jdbcTemplate.queryForList(
                "select table_name from information_schema.tables where table_schema = 'public'",
                String.class);
        assertThat(tables).contains("artist", "album", "track", "track_artist");
    }

    @Test
    void trackFilePathIsUniqueSoTheSameFileCannotBeIngestedTwice() {
        List<String> indexes = jdbcTemplate.queryForList(
                "select indexname from pg_indexes where tablename = 'track'", String.class);
        assertThat(indexes).contains("track_content_hash_key");
    }

    @Test
    void artistNameIsUniqueCaseInsensitively() {
        jdbcTemplate.update("insert into artist (id, name) values (gen_random_uuid(), 'Nina Simone')");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from artist where lower(name) = 'nina simone'", Integer.class))
                .isEqualTo(1);
        jdbcTemplate.update("delete from artist where lower(name) = 'nina simone'");
    }
}
```

- [ ] **Step 2: Run the tests and confirm they fail**

```bash
cd backend && ./gradlew test --tests CatalogSchemaTest
```

Expected: FAIL on all three. The tables do not exist.

- [ ] **Step 3: Write the schema**

Replace `backend/src/main/resources/db/migration/V1__catalog.sql`:

```sql
create extension if not exists pgcrypto;

create table artist (
    id         uuid primary key default gen_random_uuid(),
    name       text        not null,
    sort_name  text,
    created_at timestamptz not null default now()
);

-- Reconciliation on ingest matches case-insensitively, so the constraint has to as well.
create unique index artist_name_key on artist (lower(name));

create table album (
    id              uuid primary key default gen_random_uuid(),
    title           text        not null,
    album_artist_id uuid        references artist (id) on delete set null,
    release_year    smallint,
    created_at      timestamptz not null default now()
);

-- An album title alone is not unique (there are many albums called "Greatest Hits").
-- The identity of an album is its title plus its credited artist.
create unique index album_title_artist_key
    on album (lower(title), coalesce(album_artist_id, '00000000-0000-0000-0000-000000000000'::uuid));

create table track (
    id            uuid primary key default gen_random_uuid(),
    title         text        not null,
    album_id      uuid        references album (id) on delete set null,
    track_number  smallint,
    disc_number   smallint,
    duration_ms   integer,
    file_path     text        not null,
    file_size     bigint      not null,
    content_type  text        not null,
    content_hash  char(64)    not null unique,
    added_at      timestamptz not null default now()
);

-- The queries the library view actually runs: sort by title, filter by album.
create index track_title_idx on track (lower(title));
create index track_album_idx on track (album_id);

create table track_artist (
    track_id  uuid     not null references track (id) on delete cascade,
    artist_id uuid     not null references artist (id) on delete cascade,
    role      text     not null default 'PRIMARY',
    position  smallint not null default 0,
    primary key (track_id, artist_id, role)
);

create index track_artist_artist_idx on track_artist (artist_id);
```

`content_hash` is a SHA-256 of the file bytes and is unique. Identity is content, not filename, so
uploading the same audio twice under two names is caught. That is the behavior a library app needs
and it is far easier to build in now than to retrofit once duplicates exist.

- [ ] **Step 4: Reset the database and run the tests**

Flyway will not re-run a migration whose checksum changed, so the local volume has to be discarded.

```bash
docker compose down -v && docker compose up -d db
cd backend && ./gradlew test --tests CatalogSchemaTest
```

Expected: PASS, 3 tests. Testcontainers uses a fresh database regardless, but resetting the Compose
volume keeps local development consistent with the tests.

- [ ] **Step 5: Commit**

```bash
git add backend/src
git commit -m "feat: add catalog schema with first-class artist and album

Artists and albums are rows rather than track-tag strings so that renames
are single updates and multi-artist credits are representable. Track
identity is a SHA-256 of file content, which makes duplicate ingest a
constraint violation rather than a silent second copy."
```

---

## Task 4: Catalog entities and repositories

**Files:**
- Create: `backend/src/main/java/com/kasi/musiclibrary/catalog/Artist.java`
- Create: `backend/src/main/java/com/kasi/musiclibrary/catalog/Album.java`
- Create: `backend/src/main/java/com/kasi/musiclibrary/catalog/Track.java`
- Create: `backend/src/main/java/com/kasi/musiclibrary/catalog/ArtistRepository.java`
- Create: `backend/src/main/java/com/kasi/musiclibrary/catalog/AlbumRepository.java`
- Create: `backend/src/main/java/com/kasi/musiclibrary/catalog/TrackRepository.java`
- Test: `backend/src/test/java/com/kasi/musiclibrary/catalog/CatalogRepositoryTest.java`

`track_artist` is deliberately not mapped as an entity in this phase. Nothing yet reads per-track
artist credits separately from the album artist, and mapping a join table with attributes costs more
than it returns until something needs it. It is added in Phase 4 alongside metadata editing.

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/com/kasi/musiclibrary/catalog/CatalogRepositoryTest.java`:

```java
package com.kasi.musiclibrary.catalog;

import com.kasi.musiclibrary.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogRepositoryTest extends PostgresIntegrationTest {

    @Autowired
    private ArtistRepository artists;
    @Autowired
    private AlbumRepository albums;
    @Autowired
    private TrackRepository tracks;

    @Test
    void savesAndFindsAnArtistIgnoringCase() {
        artists.save(new Artist("Alice Coltrane"));

        Optional<Artist> found = artists.findByNameIgnoreCase("alice coltrane");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Alice Coltrane");
        assertThat(found.get().getId()).isNotNull();
    }

    @Test
    void savesATrackWithItsAlbumAndFindsItByContentHash() {
        Artist artist = artists.save(new Artist("Bill Evans"));
        Album album = albums.save(new Album("Sunday at the Village Vanguard", artist, 1961));

        Track track = new Track(
                "Gloria's Step", album, 1, 1, 372_000,
                "ab/cd/abcdef.mp3", 5_242_880L, "audio/mpeg",
                "a".repeat(64));
        tracks.save(track);

        Optional<Track> found = tracks.findByContentHash("a".repeat(64));

        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("Gloria's Step");
        assertThat(found.get().getAlbum().getTitle()).isEqualTo("Sunday at the Village Vanguard");
        assertThat(found.get().getAlbum().getAlbumArtist().getName()).isEqualTo("Bill Evans");
    }

    @Test
    void findsAnAlbumByTitleAndArtistSoIngestCanReconcile() {
        Artist artist = artists.save(new Artist("Ahmad Jamal"));
        albums.save(new Album("At the Pershing", artist, 1958));

        Optional<Album> found = albums.findByTitleIgnoreCaseAndAlbumArtist("at the pershing", artist);

        assertThat(found).isPresent();
        assertThat(found.get().getReleaseYear()).isEqualTo(1958);
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

```bash
cd backend && ./gradlew test --tests CatalogRepositoryTest
```

Expected: FAIL to compile. `Artist`, `Album`, `Track`, and the three repositories do not exist.

- [ ] **Step 3: Write the entities**

Create `backend/src/main/java/com/kasi/musiclibrary/catalog/Artist.java`:

```java
package com.kasi.musiclibrary.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

import java.time.Instant;
import java.util.UUID;

@Entity
public class Artist {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String name;

    private String sortName;

    @Column(nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected Artist() {
        // Required by JPA.
    }

    public Artist(String name) {
        this.name = name;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSortName() {
        return sortName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
```

Create `backend/src/main/java/com/kasi/musiclibrary/catalog/Album.java`:

```java
package com.kasi.musiclibrary.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import java.time.Instant;
import java.util.UUID;

@Entity
public class Album {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "album_artist_id")
    private Artist albumArtist;

    private Short releaseYear;

    @Column(nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected Album() {
        // Required by JPA.
    }

    public Album(String title, Artist albumArtist, Integer releaseYear) {
        this.title = title;
        this.albumArtist = albumArtist;
        this.releaseYear = releaseYear == null ? null : releaseYear.shortValue();
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public Artist getAlbumArtist() {
        return albumArtist;
    }

    public Integer getReleaseYear() {
        return releaseYear == null ? null : releaseYear.intValue();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
```

Create `backend/src/main/java/com/kasi/musiclibrary/catalog/Track.java`:

```java
package com.kasi.musiclibrary.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import java.time.Instant;
import java.util.UUID;

@Entity
public class Track {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "album_id")
    private Album album;

    private Short trackNumber;
    private Short discNumber;
    private Integer durationMs;

    @Column(nullable = false)
    private String filePath;

    @Column(nullable = false)
    private long fileSize;

    @Column(nullable = false)
    private String contentType;

    @Column(nullable = false, length = 64)
    private String contentHash;

    @Column(nullable = false, insertable = false, updatable = false)
    private Instant addedAt;

    protected Track() {
        // Required by JPA.
    }

    public Track(String title, Album album, Integer trackNumber, Integer discNumber,
                 Integer durationMs, String filePath, long fileSize, String contentType,
                 String contentHash) {
        this.title = title;
        this.album = album;
        this.trackNumber = trackNumber == null ? null : trackNumber.shortValue();
        this.discNumber = discNumber == null ? null : discNumber.shortValue();
        this.durationMs = durationMs;
        this.filePath = filePath;
        this.fileSize = fileSize;
        this.contentType = contentType;
        this.contentHash = contentHash;
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public Album getAlbum() {
        return album;
    }

    public Integer getTrackNumber() {
        return trackNumber == null ? null : trackNumber.intValue();
    }

    public Integer getDiscNumber() {
        return discNumber == null ? null : discNumber.intValue();
    }

    public Integer getDurationMs() {
        return durationMs;
    }

    public String getFilePath() {
        return filePath;
    }

    public long getFileSize() {
        return fileSize;
    }

    public String getContentType() {
        return contentType;
    }

    public String getContentHash() {
        return contentHash;
    }

    public Instant getAddedAt() {
        return addedAt;
    }
}
```

- [ ] **Step 4: Write the repositories**

Create `backend/src/main/java/com/kasi/musiclibrary/catalog/ArtistRepository.java`:

```java
package com.kasi.musiclibrary.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ArtistRepository extends JpaRepository<Artist, UUID> {
    Optional<Artist> findByNameIgnoreCase(String name);
}
```

Create `backend/src/main/java/com/kasi/musiclibrary/catalog/AlbumRepository.java`:

```java
package com.kasi.musiclibrary.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AlbumRepository extends JpaRepository<Album, UUID> {
    Optional<Album> findByTitleIgnoreCaseAndAlbumArtist(String title, Artist albumArtist);
}
```

Create `backend/src/main/java/com/kasi/musiclibrary/catalog/TrackRepository.java`:

```java
package com.kasi.musiclibrary.catalog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TrackRepository extends JpaRepository<Track, UUID> {

    Optional<Track> findByContentHash(String contentHash);

    /**
     * Album and its artist are fetched in the same query. Without the fetch joins the
     * track list issues one query per row for the album and another for the artist,
     * which is the difference between one query and two thousand on a page of a
     * thousand tracks.
     *
     * The joins are LEFT joins, not the implicit inner joins that path navigation
     * (t.album.title) would produce. A track with no album is a legitimate row and
     * must not disappear from search results.
     */
    @Query(value = """
            select t from Track t
            left join fetch t.album a
            left join fetch a.albumArtist ar
            where :query is null
               or lower(t.title) like lower(concat('%', cast(:query as string), '%'))
               or lower(a.title) like lower(concat('%', cast(:query as string), '%'))
               or lower(ar.name) like lower(concat('%', cast(:query as string), '%'))
            """,
            countQuery = """
            select count(t) from Track t
            left join t.album a
            left join a.albumArtist ar
            where :query is null
               or lower(t.title) like lower(concat('%', cast(:query as string), '%'))
               or lower(a.title) like lower(concat('%', cast(:query as string), '%'))
               or lower(ar.name) like lower(concat('%', cast(:query as string), '%'))
            """)
    Page<Track> search(@Param("query") String query, Pageable pageable);
}
```

- [ ] **Step 5: Run the test and confirm it passes**

```bash
cd backend && ./gradlew test --tests CatalogRepositoryTest
```

Expected: PASS, 3 tests. If Hibernate reports a schema validation failure, the entity and the
migration have diverged; the message names the offending column. Fix the entity, not the migration,
unless the migration is genuinely wrong.

- [ ] **Step 6: Commit**

```bash
git add backend/src
git commit -m "feat: add catalog entities and repositories

Track search fetches album and album artist with left join fetch in a
single query. Left, not inner: a track with no album is legitimate and
must still be findable. The join table for per-track artist credits is
intentionally unmapped until Phase 4 needs it."
```

---

## Task 5: Reading tags off real audio files

This is the highest-risk task in the phase. jaudiotagger's most recent release is 3.0.1 from 2021,
and it is being run on Java 21. That is very likely fine, and this task exists to prove it before
anything depends on it.

**Contingency:** if jaudiotagger fails on the JDK (typically an `IllegalAccessError` or a reflection
warning turned error), switch to `org.apache.tika:tika-core` plus `tika-parsers-standard-package`,
which reads the same tags through a maintained library. Only `AudioTagReader` changes; the test
below and everything downstream stay as written. Do not spend more than thirty minutes fighting
jaudiotagger before switching, and record the decision in an ADR.

**Files:**
- Modify: `backend/build.gradle.kts`
- Create: `backend/src/main/java/com/kasi/musiclibrary/ingest/ParsedTags.java`
- Create: `backend/src/main/java/com/kasi/musiclibrary/ingest/AudioTagReader.java`
- Create: `backend/src/test/resources/fixtures/tagged.mp3` (generated)
- Create: `backend/src/test/resources/fixtures/untagged.mp3` (generated)
- Test: `backend/src/test/java/com/kasi/musiclibrary/ingest/AudioTagReaderTest.java`

- [ ] **Step 1: Generate the test fixtures**

These are synthesized rather than downloaded so the test suite depends on no network and no
third-party file surviving at a URL. ffmpeg is needed once, here; the resulting files are committed.

```bash
mkdir -p backend/src/test/resources/fixtures
ffmpeg -f lavfi -i "sine=frequency=440:duration=3" -codec:a libmp3lame -q:a 9 \
  -metadata title="Test Tone A" \
  -metadata artist="The Oscillators" \
  -metadata album="Sine Qua Non" \
  -metadata album_artist="The Oscillators" \
  -metadata date="1998" \
  -metadata track="3/9" \
  -metadata disc="1/2" \
  -y backend/src/test/resources/fixtures/tagged.mp3

ffmpeg -f lavfi -i "sine=frequency=220:duration=2" -codec:a libmp3lame -q:a 9 \
  -map_metadata -1 \
  -y backend/src/test/resources/fixtures/untagged.mp3

ls -la backend/src/test/resources/fixtures/
```

Expected: two files, each well under 50 KB.

- [ ] **Step 2: Write the failing test**

Create `backend/src/test/java/com/kasi/musiclibrary/ingest/AudioTagReaderTest.java`:

```java
package com.kasi.musiclibrary.ingest;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

class AudioTagReaderTest {

    private final AudioTagReader reader = new AudioTagReader();

    private Path fixture(String name) {
        return Paths.get("src/test/resources/fixtures").resolve(name);
    }

    @Test
    void readsEveryTagWeCareAboutFromATaggedFile() {
        ParsedTags tags = reader.read(fixture("tagged.mp3"));

        assertThat(tags.title()).isEqualTo("Test Tone A");
        assertThat(tags.artist()).isEqualTo("The Oscillators");
        assertThat(tags.album()).isEqualTo("Sine Qua Non");
        assertThat(tags.albumArtist()).isEqualTo("The Oscillators");
        assertThat(tags.releaseYear()).isEqualTo(1998);
        assertThat(tags.trackNumber()).isEqualTo(3);
        assertThat(tags.discNumber()).isEqualTo(1);
        assertThat(tags.durationMs()).isBetween(2_800, 3_200);
    }

    @Test
    void returnsEmptyFieldsRatherThanThrowingOnAnUntaggedFile() {
        ParsedTags tags = reader.read(fixture("untagged.mp3"));

        assertThat(tags.title()).isNull();
        assertThat(tags.artist()).isNull();
        assertThat(tags.album()).isNull();
        assertThat(tags.trackNumber()).isNull();
        // Duration comes from the audio stream, not the tags, so it survives.
        assertThat(tags.durationMs()).isBetween(1_800, 2_200);
    }

    @Test
    void reportsAReadableErrorForAFileThatIsNotAudio() throws Exception {
        Path notAudio = java.nio.file.Files.createTempFile("not-audio", ".mp3");
        java.nio.file.Files.writeString(notAudio, "this is plain text");

        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> reader.read(notAudio))
                .isInstanceOf(UnreadableAudioException.class)
                .hasMessageContaining("not-audio");
    }
}
```

The untagged case matters more than it looks. A real library contains files with missing tags, and
an ingest pipeline that throws on the first one is useless. The third case defines what happens when
a user uploads something that is not audio at all, which is a thing users do.

- [ ] **Step 3: Run the test and confirm it fails**

```bash
cd backend && ./gradlew test --tests AudioTagReaderTest
```

Expected: FAIL to compile. `AudioTagReader`, `ParsedTags`, and `UnreadableAudioException` do not
exist.

- [ ] **Step 4: Add the dependency**

In `backend/build.gradle.kts`, inside the existing `dependencies { }` block:

```kotlin
    implementation("net.jthink:jaudiotagger:3.0.1")
```

- [ ] **Step 5: Write the reader**

Create `backend/src/main/java/com/kasi/musiclibrary/ingest/ParsedTags.java`:

```java
package com.kasi.musiclibrary.ingest;

/**
 * What we managed to read from a file. Every field is nullable because real files
 * are incomplete. No jaudiotagger type appears here, so the library stays behind
 * {@link AudioTagReader}.
 */
public record ParsedTags(
        String title,
        String artist,
        String album,
        String albumArtist,
        Integer releaseYear,
        Integer trackNumber,
        Integer discNumber,
        Integer durationMs) {
}
```

Create `backend/src/main/java/com/kasi/musiclibrary/ingest/UnreadableAudioException.java`:

```java
package com.kasi.musiclibrary.ingest;

public class UnreadableAudioException extends RuntimeException {
    public UnreadableAudioException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

Create `backend/src/main/java/com/kasi/musiclibrary/ingest/AudioTagReader.java`:

```java
package com.kasi.musiclibrary.ingest;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
public class AudioTagReader {

    static {
        // jaudiotagger logs a warning for every absent frame in every file, which at
        // library scale drowns out everything else in the log.
        Logger.getLogger("org.jaudiotagger").setLevel(Level.SEVERE);
    }

    public ParsedTags read(Path file) {
        AudioFile audioFile;
        try {
            audioFile = AudioFileIO.read(file.toFile());
        } catch (Exception e) {
            throw new UnreadableAudioException(
                    "Could not read audio from " + file.getFileName(), e);
        }

        Tag tag = audioFile.getTag();
        Integer durationMs = audioFile.getAudioHeader() == null
                ? null
                : audioFile.getAudioHeader().getTrackLength() * 1000;

        if (tag == null) {
            return new ParsedTags(null, null, null, null, null, null, null, durationMs);
        }

        return new ParsedTags(
                text(tag, FieldKey.TITLE),
                text(tag, FieldKey.ARTIST),
                text(tag, FieldKey.ALBUM),
                text(tag, FieldKey.ALBUM_ARTIST),
                number(text(tag, FieldKey.YEAR)),
                number(text(tag, FieldKey.TRACK)),
                number(text(tag, FieldKey.DISC_NO)),
                durationMs);
    }

    private String text(Tag tag, FieldKey key) {
        try {
            String value = tag.getFirst(key);
            return value == null || value.isBlank() ? null : value.trim();
        } catch (Exception e) {
            // An unsupported key on this tag format is normal, not an error.
            return null;
        }
    }

    /**
     * Tag values are free text. "3/9" means track 3 of 9, "1998-04-12" is a full date,
     * and anything else is discarded rather than allowed to fail an ingest.
     */
    private Integer number(String value) {
        if (value == null) {
            return null;
        }
        String head = value.split("[/-]")[0].trim();
        try {
            return Integer.valueOf(head);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
```

- [ ] **Step 6: Run the test and confirm it passes**

```bash
cd backend && ./gradlew test --tests AudioTagReaderTest
```

Expected: PASS, 3 tests. If this fails with a JDK access error rather than an assertion failure,
apply the Tika contingency described at the top of this task.

- [ ] **Step 7: Commit**

```bash
git add backend
git commit -m "feat: read embedded tags from audio files

jaudiotagger is confined to AudioTagReader so it can be swapped without
touching callers. Missing tags yield nulls rather than exceptions, since
real libraries are full of incompletely tagged files."
```

---

## Task 6: Storing audio bytes on disk

**Files:**
- Create: `backend/src/main/java/com/kasi/musiclibrary/config/StorageProperties.java`
- Create: `backend/src/main/java/com/kasi/musiclibrary/ingest/StoredAudio.java`
- Create: `backend/src/main/java/com/kasi/musiclibrary/ingest/AudioFileStore.java`
- Modify: `backend/src/main/resources/application.yaml`
- Test: `backend/src/test/java/com/kasi/musiclibrary/ingest/AudioFileStoreTest.java`

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/com/kasi/musiclibrary/ingest/AudioFileStoreTest.java`:

```java
package com.kasi.musiclibrary.ingest;

import com.kasi.musiclibrary.config.StorageProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AudioFileStoreTest {

    @TempDir
    Path mediaRoot;

    private AudioFileStore store() {
        return new AudioFileStore(new StorageProperties(mediaRoot.toString()));
    }

    @Test
    void writesBytesAndReportsSizeAndHash() throws Exception {
        byte[] content = "pretend this is an mp3".getBytes(StandardCharsets.UTF_8);

        StoredAudio stored = store().store(new ByteArrayInputStream(content), "song.mp3");

        assertThat(stored.size()).isEqualTo(content.length);
        assertThat(stored.contentHash()).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(Files.readAllBytes(mediaRoot.resolve(stored.relativePath()))).isEqualTo(content);
    }

    @Test
    void shardsPathsByHashSoOneDirectoryNeverHoldsEveryTrack() throws Exception {
        StoredAudio stored = store().store(
                new ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8)), "song.mp3");

        // sha256("abc") = ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
        assertThat(stored.relativePath()).isEqualTo(
                "ba/78/ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad.mp3");
    }

    @Test
    void resolvesAStoredPathBackToAnAbsolutePathForReading() throws Exception {
        AudioFileStore store = store();
        StoredAudio stored = store.store(
                new ByteArrayInputStream("xyz".getBytes(StandardCharsets.UTF_8)), "song.flac");

        Path resolved = store.resolve(stored.relativePath());

        assertThat(resolved).exists().isAbsolute();
        assertThat(resolved).hasExtension("flac");
    }

    @Test
    void refusesAPathThatEscapesTheMediaRoot() {
        AudioFileStore store = store();

        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> store.resolve("../../etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
```

The last test is not decoration. `resolve` takes a value that reaches the filesystem, and Phase 2
adds an endpoint that streams whatever it points at. Path traversal is the obvious way to turn that
into an arbitrary file read, and the check belongs at the boundary rather than in each caller.

- [ ] **Step 2: Run the test and confirm it fails**

```bash
cd backend && ./gradlew test --tests AudioFileStoreTest
```

Expected: FAIL to compile.

- [ ] **Step 3: Write the configuration properties**

Create `backend/src/main/java/com/kasi/musiclibrary/config/StorageProperties.java`:

```java
package com.kasi.musiclibrary.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "musiclibrary.storage")
public record StorageProperties(String mediaRoot) {
}
```

- [ ] **Step 4: Write the store**

Create `backend/src/main/java/com/kasi/musiclibrary/ingest/StoredAudio.java`:

```java
package com.kasi.musiclibrary.ingest;

public record StoredAudio(String relativePath, long size, String contentHash) {
}
```

Create `backend/src/main/java/com/kasi/musiclibrary/ingest/AudioFileStore.java`:

```java
package com.kasi.musiclibrary.ingest;

import com.kasi.musiclibrary.config.StorageProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

@Component
public class AudioFileStore {

    private final Path mediaRoot;

    public AudioFileStore(StorageProperties properties) {
        this.mediaRoot = Path.of(properties.mediaRoot()).toAbsolutePath().normalize();
    }

    /**
     * Writes to a temporary file while hashing, then moves it into its final
     * hash-derived location. The name cannot be known until the bytes have all been
     * read, and a partial file must never appear at a real path.
     */
    public StoredAudio store(InputStream input, String originalFilename) {
        try {
            Files.createDirectories(mediaRoot);
            Path staging = Files.createTempFile(mediaRoot, "upload-", ".part");

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long size;
            try (DigestInputStream hashing = new DigestInputStream(input, digest)) {
                size = Files.copy(hashing, staging, StandardCopyOption.REPLACE_EXISTING);
            }

            String hash = HexFormat.of().formatHex(digest.digest());
            String relativePath = shardedPath(hash, extensionOf(originalFilename));
            Path destination = mediaRoot.resolve(relativePath);

            Files.createDirectories(destination.getParent());
            Files.move(staging, destination, StandardCopyOption.REPLACE_EXISTING);

            return new StoredAudio(relativePath, size, hash);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store " + originalFilename, e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    public Path resolve(String relativePath) {
        Path resolved = mediaRoot.resolve(relativePath).normalize();
        if (!resolved.startsWith(mediaRoot)) {
            throw new IllegalArgumentException("Path escapes the media root: " + relativePath);
        }
        return resolved;
    }

    /**
     * Two levels of 256 directories. A flat directory of a hundred thousand files is
     * slow to list and unpleasant on several filesystems.
     */
    private String shardedPath(String hash, String extension) {
        return hash.substring(0, 2) + "/" + hash.substring(2, 4) + "/" + hash + extension;
    }

    private String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        String extension = filename.substring(dot).toLowerCase(Locale.ROOT);
        return extension.matches("\\.[a-z0-9]{1,5}") ? extension : "";
    }
}
```

- [ ] **Step 5: Register the properties and set the default root**

In `backend/src/main/java/com/kasi/musiclibrary/MusicLibraryApplication.java`, add the annotation
and its import:

```java
package com.kasi.musiclibrary;

import com.kasi.musiclibrary.config.StorageProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(StorageProperties.class)
public class MusicLibraryApplication {

    public static void main(String[] args) {
        SpringApplication.run(MusicLibraryApplication.class, args);
    }
}
```

Append to `backend/src/main/resources/application.yaml`:

```yaml
musiclibrary:
  storage:
    media-root: ${MEDIA_ROOT:./media}
```

- [ ] **Step 6: Run the test and confirm it passes**

```bash
cd backend && ./gradlew test --tests AudioFileStoreTest
```

Expected: PASS, 4 tests.

- [ ] **Step 7: Commit**

```bash
git add backend
git commit -m "feat: store audio bytes under a content-addressed path

Files are named by SHA-256 and sharded two levels deep. Writes stage to a
temp file and move into place, so a partial upload never occupies a real
path. resolve() rejects paths escaping the media root."
```

---

## Task 7: Ingest, with artist and album reconciliation

This is where a file becomes a library entry. The reconciliation rule matters: two tracks tagged
with the same artist must end up pointing at one artist row, not two.

**Files:**
- Create: `backend/src/main/java/com/kasi/musiclibrary/ingest/IngestService.java`
- Create: `backend/src/main/java/com/kasi/musiclibrary/ingest/IngestResult.java`
- Test: `backend/src/test/java/com/kasi/musiclibrary/ingest/IngestServiceTest.java`

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/com/kasi/musiclibrary/ingest/IngestServiceTest.java`:

```java
package com.kasi.musiclibrary.ingest;

import com.kasi.musiclibrary.catalog.AlbumRepository;
import com.kasi.musiclibrary.catalog.ArtistRepository;
import com.kasi.musiclibrary.catalog.Track;
import com.kasi.musiclibrary.catalog.TrackRepository;
import com.kasi.musiclibrary.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IngestServiceTest extends PostgresIntegrationTest {

    @Autowired
    private IngestService ingest;
    @Autowired
    private TrackRepository tracks;
    @Autowired
    private ArtistRepository artists;
    @Autowired
    private AlbumRepository albums;

    private byte[] taggedBytes;

    @BeforeEach
    void setUp() throws Exception {
        tracks.deleteAll();
        albums.deleteAll();
        artists.deleteAll();
        taggedBytes = Files.readAllBytes(Path.of("src/test/resources/fixtures/tagged.mp3"));
    }

    @Test
    void ingestsAFileIntoTrackAlbumAndArtist() {
        IngestResult result = ingest.ingest(new ByteArrayInputStream(taggedBytes), "tone.mp3");

        Track track = tracks.findById(result.trackId()).orElseThrow();
        assertThat(track.getTitle()).isEqualTo("Test Tone A");
        assertThat(track.getTrackNumber()).isEqualTo(3);
        assertThat(track.getAlbum().getTitle()).isEqualTo("Sine Qua Non");
        assertThat(track.getAlbum().getAlbumArtist().getName()).isEqualTo("The Oscillators");
        assertThat(track.getAlbum().getReleaseYear()).isEqualTo(1998);
    }

    @Test
    void reusesTheSameArtistAndAlbumForASecondTrackFromThatAlbum() throws Exception {
        ingest.ingest(new ByteArrayInputStream(taggedBytes), "tone.mp3");

        // Same tags, different bytes, so it is a different track from the same album.
        byte[] second = Files.readAllBytes(Path.of("src/test/resources/fixtures/tagged.mp3"));
        second[second.length - 1] = (byte) (second[second.length - 1] ^ 0x01);
        ingest.ingest(new ByteArrayInputStream(second), "tone2.mp3");

        assertThat(tracks.count()).isEqualTo(2);
        assertThat(artists.count()).isEqualTo(1);
        assertThat(albums.count()).isEqualTo(1);
    }

    @Test
    void rejectsTheSameBytesTwice() {
        ingest.ingest(new ByteArrayInputStream(taggedBytes), "tone.mp3");

        assertThatThrownBy(() -> ingest.ingest(new ByteArrayInputStream(taggedBytes), "copy.mp3"))
                .isInstanceOf(DuplicateTrackException.class);

        assertThat(tracks.count()).isEqualTo(1);
    }

    @Test
    void fallsBackToTheFilenameWhenTheFileHasNoTitleTag() throws Exception {
        byte[] untagged = Files.readAllBytes(Path.of("src/test/resources/fixtures/untagged.mp3"));

        IngestResult result = ingest.ingest(new ByteArrayInputStream(untagged), "Nocturne No 2.mp3");

        Track track = tracks.findById(result.trackId()).orElseThrow();
        assertThat(track.getTitle()).isEqualTo("Nocturne No 2");
        assertThat(track.getAlbum()).isNull();
    }
}
```

The last two tests encode decisions worth being explicit about. A duplicate is an error the caller
must handle, not a silent no-op, because the user who uploaded it deserves to be told. An untitled
track falls back to its filename because a library row labelled "Unknown" is useless, and a file with
no album tag gets no album rather than an "Unknown Album" row that would then collect unrelated
tracks.

- [ ] **Step 2: Run the test and confirm it fails**

```bash
cd backend && ./gradlew test --tests IngestServiceTest
```

Expected: FAIL to compile. `IngestService`, `IngestResult`, and `DuplicateTrackException` do not
exist.

- [ ] **Step 3: Write the result type and the duplicate exception**

Create `backend/src/main/java/com/kasi/musiclibrary/ingest/IngestResult.java`:

```java
package com.kasi.musiclibrary.ingest;

import java.util.UUID;

public record IngestResult(UUID trackId, String title) {
}
```

Create `backend/src/main/java/com/kasi/musiclibrary/ingest/DuplicateTrackException.java`:

```java
package com.kasi.musiclibrary.ingest;

import java.util.UUID;

public class DuplicateTrackException extends RuntimeException {

    private final UUID existingTrackId;

    public DuplicateTrackException(UUID existingTrackId) {
        super("This audio is already in the library");
        this.existingTrackId = existingTrackId;
    }

    public UUID getExistingTrackId() {
        return existingTrackId;
    }
}
```

- [ ] **Step 4: Write the ingest service**

Create `backend/src/main/java/com/kasi/musiclibrary/ingest/IngestService.java`:

```java
package com.kasi.musiclibrary.ingest;

import com.kasi.musiclibrary.catalog.Album;
import com.kasi.musiclibrary.catalog.AlbumRepository;
import com.kasi.musiclibrary.catalog.Artist;
import com.kasi.musiclibrary.catalog.ArtistRepository;
import com.kasi.musiclibrary.catalog.Track;
import com.kasi.musiclibrary.catalog.TrackRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.nio.file.Path;

@Service
public class IngestService {

    private final AudioFileStore fileStore;
    private final AudioTagReader tagReader;
    private final TrackRepository tracks;
    private final AlbumRepository albums;
    private final ArtistRepository artists;

    public IngestService(AudioFileStore fileStore, AudioTagReader tagReader,
                         TrackRepository tracks, AlbumRepository albums,
                         ArtistRepository artists) {
        this.fileStore = fileStore;
        this.tagReader = tagReader;
        this.tracks = tracks;
        this.albums = albums;
        this.artists = artists;
    }

    @Transactional
    public IngestResult ingest(InputStream audio, String originalFilename) {
        StoredAudio stored = fileStore.store(audio, originalFilename);

        tracks.findByContentHash(stored.contentHash()).ifPresent(existing -> {
            throw new DuplicateTrackException(existing.getId());
        });

        Path onDisk = fileStore.resolve(stored.relativePath());
        ParsedTags tags = tagReader.read(onDisk);

        Album album = resolveAlbum(tags);
        String title = tags.title() != null ? tags.title() : titleFromFilename(originalFilename);

        Track track = tracks.save(new Track(
                title,
                album,
                tags.trackNumber(),
                tags.discNumber(),
                tags.durationMs(),
                stored.relativePath(),
                stored.size(),
                contentTypeFor(stored.relativePath()),
                stored.contentHash()));

        return new IngestResult(track.getId(), track.getTitle());
    }

    /**
     * A track with no album tag gets no album. Creating an "Unknown Album" row would
     * collect every untagged track in the library into one meaningless grouping.
     */
    private Album resolveAlbum(ParsedTags tags) {
        if (tags.album() == null) {
            return null;
        }
        Artist albumArtist = resolveArtist(
                tags.albumArtist() != null ? tags.albumArtist() : tags.artist());

        return albums.findByTitleIgnoreCaseAndAlbumArtist(tags.album(), albumArtist)
                .orElseGet(() -> albums.save(new Album(tags.album(), albumArtist, tags.releaseYear())));
    }

    private Artist resolveArtist(String name) {
        if (name == null) {
            return null;
        }
        return artists.findByNameIgnoreCase(name)
                .orElseGet(() -> artists.save(new Artist(name)));
    }

    private String titleFromFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "Untitled";
        }
        String base = Path.of(filename).getFileName().toString();
        int dot = base.lastIndexOf('.');
        return dot > 0 ? base.substring(0, dot) : base;
    }

    private String contentTypeFor(String relativePath) {
        String lower = relativePath.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".mp3")) {
            return "audio/mpeg";
        }
        if (lower.endsWith(".flac")) {
            return "audio/flac";
        }
        if (lower.endsWith(".m4a") || lower.endsWith(".mp4")) {
            return "audio/mp4";
        }
        if (lower.endsWith(".ogg") || lower.endsWith(".oga")) {
            return "audio/ogg";
        }
        if (lower.endsWith(".wav")) {
            return "audio/wav";
        }
        return "application/octet-stream";
    }
}
```

- [ ] **Step 5: Run the test and confirm it passes**

```bash
cd backend && ./gradlew test --tests IngestServiceTest
```

Expected: PASS, 4 tests.

- [ ] **Step 6: Run the whole suite**

```bash
cd backend && ./gradlew test
```

Expected: PASS. Everything written so far still passes together.

- [ ] **Step 7: Commit**

```bash
git add backend
git commit -m "feat: ingest audio into track, album, and artist

Reconciliation matches artists and albums case-insensitively so a second
track from the same album reuses both rows. Duplicate content raises
rather than silently no-oping. Untitled files fall back to their filename;
untagged files get no album rather than an Unknown Album bucket."
```

---

## Task 8: Upload and list endpoints

**Files:**
- Create: `backend/src/main/java/com/kasi/musiclibrary/api/TrackResponse.java`
- Create: `backend/src/main/java/com/kasi/musiclibrary/api/TrackPage.java`
- Create: `backend/src/main/java/com/kasi/musiclibrary/api/TrackController.java`
- Create: `backend/src/main/java/com/kasi/musiclibrary/api/ApiExceptionHandler.java`
- Modify: `backend/src/main/resources/application.yaml`
- Test: `backend/src/test/java/com/kasi/musiclibrary/api/TrackControllerTest.java`

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/com/kasi/musiclibrary/api/TrackControllerTest.java`:

```java
package com.kasi.musiclibrary.api;

import com.kasi.musiclibrary.catalog.AlbumRepository;
import com.kasi.musiclibrary.catalog.ArtistRepository;
import com.kasi.musiclibrary.catalog.TrackRepository;
import com.kasi.musiclibrary.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class TrackControllerTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TrackRepository tracks;
    @Autowired
    private AlbumRepository albums;
    @Autowired
    private ArtistRepository artists;

    private byte[] tagged;

    @BeforeEach
    void setUp() throws Exception {
        tracks.deleteAll();
        albums.deleteAll();
        artists.deleteAll();
        tagged = Files.readAllBytes(Path.of("src/test/resources/fixtures/tagged.mp3"));
    }

    private MockMultipartFile upload(byte[] bytes, String filename) {
        return new MockMultipartFile("file", filename, "audio/mpeg", bytes);
    }

    @Test
    void uploadReturnsCreatedWithTheTrack() throws Exception {
        mockMvc.perform(multipart("/api/tracks").file(upload(tagged, "tone.mp3")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Test Tone A"))
                .andExpect(jsonPath("$.albumTitle").value("Sine Qua Non"))
                .andExpect(jsonPath("$.artistName").value("The Oscillators"))
                .andExpect(jsonPath("$.durationMs").isNumber())
                .andExpect(jsonPath("$.id").isNotEmpty());
    }

    @Test
    void uploadingTheSameBytesTwiceIsAConflictNotAServerError() throws Exception {
        mockMvc.perform(multipart("/api/tracks").file(upload(tagged, "tone.mp3")))
                .andExpect(status().isCreated());

        mockMvc.perform(multipart("/api/tracks").file(upload(tagged, "again.mp3")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This audio is already in the library"));
    }

    @Test
    void uploadingSomethingThatIsNotAudioIsABadRequest() throws Exception {
        mockMvc.perform(multipart("/api/tracks")
                        .file(upload("plain text".getBytes(), "notes.mp3")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void listReturnsAPageWithTotalCount() throws Exception {
        mockMvc.perform(multipart("/api/tracks").file(upload(tagged, "tone.mp3")));

        mockMvc.perform(get("/api/tracks").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.items[0].title").value("Test Tone A"));
    }

    @Test
    void listFiltersByQueryAcrossTitleAlbumAndArtist() throws Exception {
        mockMvc.perform(multipart("/api/tracks").file(upload(tagged, "tone.mp3")));

        mockMvc.perform(get("/api/tracks").param("query", "oscillat"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get("/api/tracks").param("query", "nothing matches this"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void listIncludesTracksThatHaveNoAlbum() throws Exception {
        byte[] untagged = Files.readAllBytes(Path.of("src/test/resources/fixtures/untagged.mp3"));
        mockMvc.perform(multipart("/api/tracks").file(upload(untagged, "Solo Piece.mp3")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/tracks").param("query", "Solo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].albumTitle").doesNotExist());
    }
}
```

The last test is the regression guard for the left-join decision in Task 4. It would pass with an
inner join only by accident and fails loudly if someone simplifies the query later.

- [ ] **Step 2: Run the test and confirm it fails**

```bash
cd backend && ./gradlew test --tests TrackControllerTest
```

Expected: FAIL to compile. The `api` package is empty.

- [ ] **Step 3: Write the response types**

Create `backend/src/main/java/com/kasi/musiclibrary/api/TrackResponse.java`:

```java
package com.kasi.musiclibrary.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.kasi.musiclibrary.catalog.Track;

import java.util.UUID;

/**
 * Flattened on purpose. The library view shows one row per track with its album and
 * artist inline, and nesting them would make the client walk possibly-null objects
 * for every cell it renders.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TrackResponse(
        UUID id,
        String title,
        String albumTitle,
        String artistName,
        Integer trackNumber,
        Integer discNumber,
        Integer durationMs,
        Integer releaseYear) {

    public static TrackResponse from(Track track) {
        var album = track.getAlbum();
        var artist = album == null ? null : album.getAlbumArtist();
        return new TrackResponse(
                track.getId(),
                track.getTitle(),
                album == null ? null : album.getTitle(),
                artist == null ? null : artist.getName(),
                track.getTrackNumber(),
                track.getDiscNumber(),
                track.getDurationMs(),
                album == null ? null : album.getReleaseYear());
    }
}
```

Create `backend/src/main/java/com/kasi/musiclibrary/api/TrackPage.java`:

```java
package com.kasi.musiclibrary.api;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * A deliberate, stable shape rather than Spring's serialized Page, whose JSON is an
 * implementation detail that has changed across versions.
 */
public record TrackPage(
        List<TrackResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static TrackPage from(Page<com.kasi.musiclibrary.catalog.Track> source) {
        return new TrackPage(
                source.getContent().stream().map(TrackResponse::from).toList(),
                source.getNumber(),
                source.getSize(),
                source.getTotalElements(),
                source.getTotalPages());
    }
}
```

- [ ] **Step 4: Write the controller and the error handler**

Create `backend/src/main/java/com/kasi/musiclibrary/api/TrackController.java`:

```java
package com.kasi.musiclibrary.api;

import com.kasi.musiclibrary.catalog.TrackRepository;
import com.kasi.musiclibrary.ingest.IngestResult;
import com.kasi.musiclibrary.ingest.IngestService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/tracks")
public class TrackController {

    private static final int MAX_PAGE_SIZE = 200;

    private final IngestService ingest;
    private final TrackRepository tracks;

    public TrackController(IngestService ingest, TrackRepository tracks) {
        this.ingest = ingest;
        this.tracks = tracks;
    }

    @PostMapping
    public ResponseEntity<TrackResponse> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The uploaded file is empty");
        }
        try (var stream = file.getInputStream()) {
            IngestResult result = ingest.ingest(stream, file.getOriginalFilename());
            TrackResponse body = TrackResponse.from(tracks.findById(result.trackId()).orElseThrow());
            return ResponseEntity.status(HttpStatus.CREATED).body(body);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @GetMapping
    public TrackPage list(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        // An unbounded page size is a denial-of-service knob on a large library.
        int effectiveSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        String effectiveQuery = (query == null || query.isBlank()) ? null : query.trim();

        var pageable = PageRequest.of(Math.max(page, 0), effectiveSize,
                Sort.by(Sort.Order.asc("title").ignoreCase()));

        return TrackPage.from(tracks.search(effectiveQuery, pageable));
    }

    @GetMapping("/{id}")
    public TrackResponse get(@PathVariable UUID id) {
        return tracks.findById(id)
                .map(TrackResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such track"));
    }
}
```

Create `backend/src/main/java/com/kasi/musiclibrary/api/ApiExceptionHandler.java`:

```java
package com.kasi.musiclibrary.api;

import com.kasi.musiclibrary.ingest.DuplicateTrackException;
import com.kasi.musiclibrary.ingest.UnreadableAudioException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(DuplicateTrackException.class)
    public ResponseEntity<Map<String, Object>> duplicate(DuplicateTrackException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", e.getMessage(), "existingTrackId", e.getExistingTrackId()));
    }

    @ExceptionHandler(UnreadableAudioException.class)
    public ResponseEntity<Map<String, Object>> unreadable(UnreadableAudioException e) {
        return ResponseEntity.badRequest()
                .body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> tooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Map.of("message", "That file is larger than the upload limit"));
    }
}
```

- [ ] **Step 5: Raise the upload size limit**

Spring Boot's default multipart limit is 1 MB, which rejects essentially every real audio file.
Append to `backend/src/main/resources/application.yaml`, under the existing `spring:` key:

```yaml
  servlet:
    multipart:
      max-file-size: 100MB
      max-request-size: 100MB
```

The resulting `spring:` block should read:

```yaml
spring:
  application:
    name: music-library
  datasource:
    url: ${DATABASE_URL:jdbc:postgresql://localhost:5432/musiclibrary}
    username: ${DATABASE_USER:musiclibrary}
    password: ${DATABASE_PASSWORD:musiclibrary}
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
  flyway:
    enabled: true
  servlet:
    multipart:
      max-file-size: 100MB
      max-request-size: 100MB
```

- [ ] **Step 6: Run the test and confirm it passes**

```bash
cd backend && ./gradlew test --tests TrackControllerTest
```

Expected: PASS, 6 tests.

- [ ] **Step 7: Commit**

```bash
git add backend
git commit -m "feat: add track upload and paginated search endpoints

Response shapes are explicit records rather than serialized JPA entities
or Spring's Page, so the wire format is a decision instead of an accident.
Page size is clamped; duplicate uploads return 409 and non-audio 400."
```

---

## Task 9: Seed the library on first boot

An app that starts empty makes the reviewer do setup work before they can evaluate anything. This
task makes `docker compose up` produce a populated library.

**Files:**
- Create: `backend/src/main/resources/seed-audio/` (generated audio)
- Create: `backend/src/main/resources/seed-audio/CREDITS.md`
- Create: `backend/src/main/java/com/kasi/musiclibrary/ingest/SeedRunner.java`
- Modify: `backend/src/main/resources/application.yaml`
- Test: `backend/src/test/java/com/kasi/musiclibrary/ingest/SeedRunnerTest.java`

- [ ] **Step 1: Generate the seed audio**

Six short synthesized tracks across two albums and two artists, enough to show grouping, sorting,
and search working. Synthesized rather than downloaded so the repository carries no licensing
question at all, which is a cleaner answer than "these are probably public domain."

```bash
mkdir -p backend/src/main/resources/seed-audio

seed() {
  ffmpeg -loglevel error -f lavfi -i "sine=frequency=$1:duration=$2" -codec:a libmp3lame -q:a 9 \
    -metadata title="$3" -metadata artist="$4" -metadata album="$5" \
    -metadata album_artist="$4" -metadata date="$6" -metadata track="$7" \
    -y "backend/src/main/resources/seed-audio/$8"
}

seed 261 4 "Prelude in C"      "The Oscillators" "Sine Qua Non"    1998 1 01-prelude.mp3
seed 293 5 "Second Movement"   "The Oscillators" "Sine Qua Non"    1998 2 02-second.mp3
seed 329 3 "Interlude"         "The Oscillators" "Sine Qua Non"    1998 3 03-interlude.mp3
seed 349 6 "Low Frequency"     "Square Wave"     "Harmonic Series" 2004 1 04-low.mp3
seed 392 4 "Odd Harmonics"     "Square Wave"     "Harmonic Series" 2004 2 05-odd.mp3
seed 440 5 "Reference Pitch"   "Square Wave"     "Harmonic Series" 2004 3 06-reference.mp3

ls -la backend/src/main/resources/seed-audio/
du -sh backend/src/main/resources/seed-audio/
```

Expected: six mp3 files, total well under 1 MB.

Create `backend/src/main/resources/seed-audio/CREDITS.md`:

```markdown
# Seed audio

These six files are synthesized sine tones generated with ffmpeg, not recordings. They exist so the
application has a populated library on first run without shipping anyone else's music and without
requiring the reviewer to supply their own files first.

They carry no copyright and need no attribution. The commands that produced them are in
`docs/plans/01-foundation-and-library.md`, Task 9.

To load your own music instead, use the upload control in the app, or empty this directory before
building.
```

- [ ] **Step 2: Write the failing test**

Create `backend/src/test/java/com/kasi/musiclibrary/ingest/SeedRunnerTest.java`:

```java
package com.kasi.musiclibrary.ingest;

import com.kasi.musiclibrary.catalog.AlbumRepository;
import com.kasi.musiclibrary.catalog.ArtistRepository;
import com.kasi.musiclibrary.catalog.TrackRepository;
import com.kasi.musiclibrary.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = "musiclibrary.seed.enabled=true")
class SeedRunnerTest extends PostgresIntegrationTest {

    @Autowired
    private SeedRunner seedRunner;
    @Autowired
    private TrackRepository tracks;
    @Autowired
    private AlbumRepository albums;
    @Autowired
    private ArtistRepository artists;

    @Test
    void seedsSixTracksAcrossTwoAlbumsAndTwoArtists() {
        seedRunner.seed();

        assertThat(tracks.count()).isEqualTo(6);
        assertThat(albums.count()).isEqualTo(2);
        assertThat(artists.count()).isEqualTo(2);
    }

    @Test
    void seedingTwiceDoesNotDuplicateAnything() {
        seedRunner.seed();
        long afterFirst = tracks.count();

        seedRunner.seed();

        assertThat(tracks.count()).isEqualTo(afterFirst);
    }
}
```

Idempotence is the point of the second test. The runner executes on every boot, and a container
that restarts must not multiply the library.

- [ ] **Step 3: Run the test and confirm it fails**

```bash
cd backend && ./gradlew test --tests SeedRunnerTest
```

Expected: FAIL to compile. `SeedRunner` does not exist.

- [ ] **Step 4: Write the seed runner**

Create `backend/src/main/java/com/kasi/musiclibrary/ingest/SeedRunner.java`:

```java
package com.kasi.musiclibrary.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Component
public class SeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedRunner.class);
    private static final String SEED_PATTERN = "classpath:seed-audio/*.mp3";

    private final IngestService ingest;
    private final boolean enabled;

    public SeedRunner(IngestService ingest,
                      @Value("${musiclibrary.seed.enabled:true}") boolean enabled) {
        this.ingest = ingest;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (enabled) {
            seed();
        }
    }

    /**
     * Idempotent by way of content hashing: a track already present raises
     * DuplicateTrackException, which here is the expected outcome rather than a
     * failure. The runner therefore needs no "has this been seeded" flag.
     */
    public void seed() {
        int added = 0;
        int alreadyPresent = 0;
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources(SEED_PATTERN);
            for (Resource resource : resources) {
                try (InputStream stream = resource.getInputStream()) {
                    ingest.ingest(stream, resource.getFilename());
                    added++;
                } catch (DuplicateTrackException e) {
                    alreadyPresent++;
                } catch (Exception e) {
                    log.warn("Could not seed {}: {}", resource.getFilename(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Seed audio could not be listed: {}", e.getMessage());
            return;
        }
        log.info("Seed complete: {} added, {} already present", added, alreadyPresent);
    }
}
```

`DuplicateTrackException` is caught inside the per-file loop rather than around the whole thing, so
one already-present file does not stop the rest from loading.

- [ ] **Step 5: Add the toggle**

Append to `backend/src/main/resources/application.yaml`, under the existing `musiclibrary:` key:

```yaml
  seed:
    enabled: ${SEED_ENABLED:true}
```

The resulting block should read:

```yaml
musiclibrary:
  storage:
    media-root: ${MEDIA_ROOT:./media}
  seed:
    enabled: ${SEED_ENABLED:true}
```

Then disable seeding for the rest of the test suite, so tests that count rows are not perturbed by
six seed tracks. Create `backend/src/test/resources/application.yaml`:

```yaml
musiclibrary:
  seed:
    enabled: false
```

`SeedRunnerTest` re-enables it with `@TestPropertySource`, which is why that annotation is on the
class.

- [ ] **Step 6: Run the full suite and confirm it passes**

```bash
cd backend && ./gradlew test
```

Expected: PASS. All tests written so far, including the two new ones.

- [ ] **Step 7: Commit**

```bash
git add backend
git commit -m "feat: seed the library from bundled audio on boot

Six synthesized tracks across two albums so the app is populated on first
run without shipping anyone else's music. Idempotent by content hash, so
restarts do not multiply the library. Disabled by default in tests."
```

---

## Task 10: Streaming audio, so the MVP can actually play

Playback belongs in the MVP: a library app that cannot play a track is not testable end to end.
This task adds the one endpoint that makes it possible. Queue management, gapless playback, and
seek polish are Phase 2.

**Files:**
- Create: `backend/src/main/java/com/kasi/musiclibrary/api/StreamController.java`
- Test: `backend/src/test/java/com/kasi/musiclibrary/api/StreamControllerTest.java`

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/com/kasi/musiclibrary/api/StreamControllerTest.java`:

```java
package com.kasi.musiclibrary.api;

import com.kasi.musiclibrary.catalog.TrackRepository;
import com.kasi.musiclibrary.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class StreamControllerTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TrackRepository tracks;

    private UUID trackId;

    @BeforeEach
    void setUp() throws Exception {
        tracks.deleteAll();
        byte[] tagged = Files.readAllBytes(Path.of("src/test/resources/fixtures/tagged.mp3"));
        MvcResult result = mockMvc.perform(multipart("/api/tracks")
                        .file(new MockMultipartFile("file", "tone.mp3", "audio/mpeg", tagged)))
                .andExpect(status().isCreated())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        trackId = UUID.fromString(body.replaceAll(".*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1"));
    }

    @Test
    void servesTheWholeFileWithTheRightContentType() throws Exception {
        mockMvc.perform(get("/api/tracks/{id}/stream", trackId))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "audio/mpeg"))
                .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"));
    }

    @Test
    void honoursARangeRequestSoTheBrowserCanSeek() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/tracks/{id}/stream", trackId)
                        .header(HttpHeaders.RANGE, "bytes=0-99"))
                .andExpect(status().isPartialContent())
                .andReturn();

        assertThat(result.getResponse().getContentAsByteArray()).hasSize(100);
        assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_RANGE)).startsWith("bytes 0-99/");
    }

    @Test
    void returnsNotFoundForAnUnknownTrack() throws Exception {
        mockMvc.perform(get("/api/tracks/{id}/stream", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
```

The range test is the one that matters. Without `Accept-Ranges` and 206 handling, the browser will
play a track from the beginning but the seek bar will not work, which is the kind of half-working
that is worse than absent.

- [ ] **Step 2: Run the test and confirm it fails**

```bash
cd backend && ./gradlew test --tests StreamControllerTest
```

Expected: FAIL. The endpoint does not exist, so every request 404s.

- [ ] **Step 3: Write the controller**

Create `backend/src/main/java/com/kasi/musiclibrary/api/StreamController.java`:

```java
package com.kasi.musiclibrary.api;

import com.kasi.musiclibrary.catalog.Track;
import com.kasi.musiclibrary.catalog.TrackRepository;
import com.kasi.musiclibrary.ingest.AudioFileStore;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.util.UUID;

@RestController
public class StreamController {

    private final TrackRepository tracks;
    private final AudioFileStore fileStore;

    public StreamController(TrackRepository tracks, AudioFileStore fileStore) {
        this.tracks = tracks;
        this.fileStore = fileStore;
    }

    /**
     * Returns a Resource rather than writing bytes by hand. Spring's resource handling
     * implements conditional and range requests correctly, including multi-range and
     * unsatisfiable-range cases that are easy to get subtly wrong.
     */
    @GetMapping("/api/tracks/{id}/stream")
    public ResponseEntity<Resource> stream(@PathVariable UUID id) {
        Track track = tracks.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such track"));

        Path file = fileStore.resolve(track.getFilePath());
        Resource resource = new FileSystemResource(file);
        if (!resource.exists()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Audio is missing from storage");
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(track.getContentType()))
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .body(resource);
    }
}
```

- [ ] **Step 4: Run the test and confirm it passes**

```bash
cd backend && ./gradlew test --tests StreamControllerTest
```

Expected: PASS, 3 tests.

- [ ] **Step 5: Commit**

```bash
git add backend
git commit -m "feat: stream track audio with range request support

Serves a Resource so Spring handles range and conditional requests rather
than reimplementing 206 semantics. Without Accept-Ranges the browser plays
from the start but cannot seek."
```

---

## Task 11: Angular application scaffold

**Files:**
- Create: `frontend/` (generated)
- Create: `frontend/proxy.conf.json`
- Modify: `frontend/angular.json`

Requires Node 22.22.3 or newer. See Prerequisites.

- [ ] **Step 1: Generate the application**

From the repository root:

```bash
npx --yes @angular/cli@22 new music-library-web \
  --directory frontend \
  --style=css \
  --routing=false \
  --ssr=false \
  --zoneless \
  --skip-git \
  --package-manager=npm
```

If prompted to choose a test runner, choose **Vitest**. If prompted about analytics, decline.

Expected: `frontend/package.json` and `frontend/src/app/app.ts` exist.

- [ ] **Step 2: Verify the generated tests run**

```bash
cd frontend && npm test -- --run
```

Expected: PASS. This is the baseline; if the generated suite does not run, fix that before adding
anything, because every later test depends on this working.

- [ ] **Step 3: Add the dev proxy**

The Angular dev server runs on 4200 and the API on 8080. Proxying avoids CORS configuration that
would exist only for development and would not reflect production, where both are served from the
same origin.

Create `frontend/proxy.conf.json`:

```json
{
  "/api": {
    "target": "http://localhost:8080",
    "secure": false
  }
}
```

In `frontend/angular.json`, find `projects.music-library-web.architect.serve.options` and add the
proxy configuration. If the `options` key does not exist under `serve`, create it:

```json
"options": {
  "proxyConfig": "proxy.conf.json"
}
```

- [ ] **Step 4: Provide the HTTP client**

Replace `frontend/src/app/app.config.ts`:

```typescript
import { ApplicationConfig, provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient, withFetch } from '@angular/common/http';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZonelessChangeDetection(),
    provideHttpClient(withFetch()),
  ],
};
```

- [ ] **Step 5: Confirm the app builds and serves**

```bash
cd frontend && npm run build
```

Expected: build succeeds, output in `frontend/dist/music-library-web/browser`.

- [ ] **Step 6: Commit**

```bash
git add frontend
git commit -m "chore: scaffold Angular 22 frontend

Zoneless change detection with signals. The dev server proxies /api to the
backend so development matches production, where one origin serves both."
```

---

## Task 12: The library view

**Files:**
- Create: `frontend/src/app/catalog/track.model.ts`
- Create: `frontend/src/app/catalog/track.service.ts`
- Create: `frontend/src/app/catalog/track-list/track-list.ts`
- Create: `frontend/src/app/catalog/track-list/track-list.html`
- Create: `frontend/src/app/catalog/track-list/track-list.css`
- Modify: `frontend/src/app/app.ts`, `frontend/src/app/app.html`
- Test: `frontend/src/app/catalog/track.service.spec.ts`
- Test: `frontend/src/app/catalog/track-list/track-list.spec.ts`

- [ ] **Step 1: Write the failing service test**

Create `frontend/src/app/catalog/track.service.spec.ts`:

```typescript
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TrackService } from './track.service';
import { TrackPage } from './track.model';

describe('TrackService', () => {
  let service: TrackService;
  let http: HttpTestingController;

  const emptyPage: TrackPage = {
    items: [],
    page: 0,
    size: 50,
    totalElements: 0,
    totalPages: 0,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [TrackService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(TrackService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('requests the first page with no query by default', () => {
    service.list().subscribe();

    const request = http.expectOne(
      (r) => r.url === '/api/tracks' && r.params.get('page') === '0',
    );
    expect(request.request.params.has('query')).toBe(false);
    request.flush(emptyPage);
  });

  it('sends the search term when one is given', () => {
    service.list({ query: 'coltrane' }).subscribe();

    const request = http.expectOne(
      (r) => r.url === '/api/tracks' && r.params.get('query') === 'coltrane',
    );
    request.flush(emptyPage);
  });

  it('builds a stream url for a track', () => {
    expect(service.streamUrl('abc-123')).toBe('/api/tracks/abc-123/stream');
  });
});
```

- [ ] **Step 2: Run it and confirm it fails**

```bash
cd frontend && npm test -- --run
```

Expected: FAIL. `./track.service` and `./track.model` do not exist.

- [ ] **Step 3: Write the model and the service**

Create `frontend/src/app/catalog/track.model.ts`:

```typescript
/** Mirrors TrackResponse on the backend. Fields absent from the JSON are optional here. */
export interface Track {
  id: string;
  title: string;
  albumTitle?: string;
  artistName?: string;
  trackNumber?: number;
  discNumber?: number;
  durationMs?: number;
  releaseYear?: number;
}

/** Mirrors TrackPage on the backend. */
export interface TrackPage {
  items: Track[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ListOptions {
  query?: string;
  page?: number;
  size?: number;
}
```

Create `frontend/src/app/catalog/track.service.ts`:

```typescript
import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ListOptions, TrackPage } from './track.model';

/** The only place in the application that knows the API's shape or its URLs. */
@Injectable({ providedIn: 'root' })
export class TrackService {
  private readonly http = inject(HttpClient);

  list(options: ListOptions = {}): Observable<TrackPage> {
    let params = new HttpParams().set('page', String(options.page ?? 0));
    if (options.size !== undefined) {
      params = params.set('size', String(options.size));
    }
    if (options.query) {
      params = params.set('query', options.query);
    }
    return this.http.get<TrackPage>('/api/tracks', { params });
  }

  upload(file: File): Observable<unknown> {
    const body = new FormData();
    body.append('file', file);
    return this.http.post('/api/tracks', body);
  }

  streamUrl(trackId: string): string {
    return `/api/tracks/${trackId}/stream`;
  }
}
```

- [ ] **Step 4: Run the service test and confirm it passes**

```bash
cd frontend && npm test -- --run
```

Expected: PASS, 3 tests.

- [ ] **Step 5: Write the failing component test**

Create `frontend/src/app/catalog/track-list/track-list.spec.ts`:

```typescript
import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TrackList } from './track-list';
import { TrackPage } from '../track.model';

describe('TrackList', () => {
  let fixture: ComponentFixture<TrackList>;
  let http: HttpTestingController;

  const page: TrackPage = {
    items: [
      {
        id: '1',
        title: 'Prelude in C',
        albumTitle: 'Sine Qua Non',
        artistName: 'The Oscillators',
        durationMs: 245_000,
      },
      { id: '2', title: 'Untethered' },
    ],
    page: 0,
    size: 50,
    totalElements: 2,
    totalPages: 1,
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TrackList],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(TrackList);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function loadWith(response: TrackPage) {
    fixture.detectChanges();
    http.expectOne((r) => r.url === '/api/tracks').flush(response);
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it('renders a row per track', async () => {
    await loadWith(page);

    const rows = fixture.nativeElement.querySelectorAll('[data-testid="track-row"]');
    expect(rows.length).toBe(2);
    expect(rows[0].textContent).toContain('Prelude in C');
    expect(rows[0].textContent).toContain('The Oscillators');
  });

  it('renders a track with no album without printing undefined', async () => {
    await loadWith(page);

    const rows = fixture.nativeElement.querySelectorAll('[data-testid="track-row"]');
    expect(rows[1].textContent).toContain('Untethered');
    expect(rows[1].textContent).not.toContain('undefined');
  });

  it('formats duration as minutes and seconds', async () => {
    await loadWith(page);

    const rows = fixture.nativeElement.querySelectorAll('[data-testid="track-row"]');
    expect(rows[0].textContent).toContain('4:05');
  });

  it('shows an empty state rather than a blank page', async () => {
    await loadWith({ items: [], page: 0, size: 50, totalElements: 0, totalPages: 0 });

    expect(
      fixture.nativeElement.querySelector('[data-testid="empty-state"]'),
    ).not.toBeNull();
  });
});
```

The second and fourth tests exist because both cases are real and both are commonly missed: the
backend genuinely returns tracks with no album, and an empty library is what a reviewer sees if
seeding fails.

- [ ] **Step 6: Run it and confirm it fails**

```bash
cd frontend && npm test -- --run
```

Expected: FAIL. `./track-list` does not exist.

- [ ] **Step 7: Write the component**

Create `frontend/src/app/catalog/track-list/track-list.ts`:

```typescript
import { Component, inject, signal } from '@angular/core';
import { TrackService } from '../track.service';
import { Track } from '../track.model';

@Component({
  selector: 'app-track-list',
  imports: [],
  templateUrl: './track-list.html',
  styleUrl: './track-list.css',
})
export class TrackList {
  private readonly trackService = inject(TrackService);

  protected readonly tracks = signal<Track[]>([]);
  protected readonly total = signal(0);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly nowPlayingId = signal<string | null>(null);
  protected readonly nowPlayingUrl = signal<string | null>(null);

  private searchTimer: ReturnType<typeof setTimeout> | undefined;

  constructor() {
    this.load('');
  }

  protected onSearch(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    // Debounced so typing does not issue a request per keystroke.
    clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => this.load(value), 250);
  }

  protected play(track: Track): void {
    this.nowPlayingId.set(track.id);
    this.nowPlayingUrl.set(this.trackService.streamUrl(track.id));
  }

  protected onUpload(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) {
      return;
    }
    this.trackService.upload(file).subscribe({
      next: () => {
        input.value = '';
        this.load('');
      },
      error: (e) => this.error.set(e?.error?.message ?? 'Upload failed'),
    });
  }

  protected formatDuration(ms: number | undefined): string {
    if (!ms || ms < 0) {
      return '';
    }
    const totalSeconds = Math.round(ms / 1000);
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;
    return `${minutes}:${String(seconds).padStart(2, '0')}`;
  }

  private load(query: string): void {
    this.loading.set(true);
    this.error.set(null);
    this.trackService.list({ query }).subscribe({
      next: (page) => {
        this.tracks.set(page.items);
        this.total.set(page.totalElements);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Could not load the library');
        this.loading.set(false);
      },
    });
  }
}
```

Create `frontend/src/app/catalog/track-list/track-list.html`:

```html
<header class="library-header">
  <h1>Library</h1>
  <div class="controls">
    <label class="visually-hidden" for="search">Search the library</label>
    <input
      id="search"
      type="search"
      placeholder="Search title, album, or artist"
      (input)="onSearch($event)"
    />
    <label class="upload">
      Add music
      <input type="file" accept="audio/*" (change)="onUpload($event)" />
    </label>
  </div>
</header>

@if (error()) {
  <p class="error" role="alert">{{ error() }}</p>
}

@if (loading()) {
  <p class="status" role="status">Loading…</p>
}

@if (!loading() && tracks().length === 0) {
  <p class="status" data-testid="empty-state">
    Nothing here yet. Add music with the button above.
  </p>
} @else {
  <p class="status">{{ total() }} tracks</p>
  <table class="tracks">
    <thead>
      <tr>
        <th scope="col"><span class="visually-hidden">Play</span></th>
        <th scope="col">Title</th>
        <th scope="col">Artist</th>
        <th scope="col">Album</th>
        <th scope="col">Length</th>
      </tr>
    </thead>
    <tbody>
      @for (track of tracks(); track track.id) {
        <tr data-testid="track-row" [class.playing]="track.id === nowPlayingId()">
          <td>
            <button type="button" (click)="play(track)" [attr.aria-label]="'Play ' + track.title">
              ▶
            </button>
          </td>
          <td>{{ track.title }}</td>
          <td>{{ track.artistName ?? '' }}</td>
          <td>{{ track.albumTitle ?? '' }}</td>
          <td>{{ formatDuration(track.durationMs) }}</td>
        </tr>
      }
    </tbody>
  </table>
}

@if (nowPlayingUrl(); as url) {
  <footer class="player">
    <audio [src]="url" controls autoplay></audio>
  </footer>
}
```

`?? ''` rather than leaving the binding undefined is what makes the no-album test pass. Angular
renders `undefined` as an empty string in an interpolation, but the explicit fallback documents that
the case is expected rather than accidental.

Create `frontend/src/app/catalog/track-list/track-list.css`:

```css
:host {
  display: block;
  max-width: 60rem;
  margin: 0 auto;
  padding: 1.5rem 1rem 6rem;
  font-family: system-ui, sans-serif;
}

.library-header {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  justify-content: space-between;
  gap: 1rem;
}

.controls {
  display: flex;
  gap: 0.75rem;
  align-items: center;
}

input[type='search'] {
  padding: 0.4rem 0.6rem;
  min-width: 16rem;
  border: 1px solid #999;
  border-radius: 4px;
}

.upload input[type='file'] {
  display: none;
}

.upload {
  padding: 0.4rem 0.75rem;
  border: 1px solid #999;
  border-radius: 4px;
  cursor: pointer;
}

.tracks {
  width: 100%;
  border-collapse: collapse;
}

.tracks th,
.tracks td {
  text-align: left;
  padding: 0.4rem 0.5rem;
  border-bottom: 1px solid #e5e5e5;
}

.tracks tr.playing {
  background: #eef4ff;
}

.status,
.error {
  color: #555;
}

.error {
  color: #a00;
}

.player {
  position: fixed;
  inset: auto 0 0 0;
  padding: 0.5rem 1rem;
  background: #fff;
  border-top: 1px solid #ddd;
}

.player audio {
  width: 100%;
}

.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
}

*:focus-visible {
  outline: 2px solid #0b57d0;
  outline-offset: 2px;
}
```

- [ ] **Step 8: Mount the component**

Replace `frontend/src/app/app.ts`:

```typescript
import { Component } from '@angular/core';
import { TrackList } from './catalog/track-list/track-list';

@Component({
  selector: 'app-root',
  imports: [TrackList],
  templateUrl: './app.html',
})
export class App {}
```

Replace `frontend/src/app/app.html`:

```html
<app-track-list />
```

If the generated `app.spec.ts` asserts on the default Angular welcome page, delete those assertions
or the file; it is testing scaffolding that no longer exists.

- [ ] **Step 9: Run the tests and confirm they pass**

```bash
cd frontend && npm test -- --run
```

Expected: PASS, 7 tests across both spec files.

- [ ] **Step 10: Verify against the real backend**

```bash
docker compose up -d db
cd backend && ./gradlew bootRun &
cd frontend && npm start
```

Open http://localhost:4200. Expected: six seeded tracks listed, search narrows them, the play button
starts audio, and the seek bar works.

Stop both processes when done.

- [ ] **Step 11: Commit**

```bash
git add frontend
git commit -m "feat: add the library view with search, upload, and playback

Signals over a service that is the only holder of API knowledge. Search is
debounced. Tracks with no album render blank rather than undefined, and an
empty library gets an explicit empty state instead of a blank page."
```

---

## Task 13: One command, one image

The requirement the brief states outright: it has to build and run on a machine that is not mine.
This task is where that becomes true, and it is verified from a clean clone rather than asserted.

**Files:**
- Create: `Dockerfile`
- Create: `.dockerignore`
- Modify: `backend/build.gradle.kts`

- [ ] **Step 1: Pin the Java toolchain**

So the build does not depend on whichever JDK the reviewer happens to have. In
`backend/build.gradle.kts`, replace the existing `java { }` block:

```kotlin
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}
```

And in `backend/settings.gradle.kts`, add at the top so Gradle can fetch that JDK if it is absent:

```kotlin
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}
```

- [ ] **Step 2: Write the Dockerfile**

Create `Dockerfile` at the repository root:

```dockerfile
# syntax=docker/dockerfile:1

# Stage 1: build the Angular application.
FROM node:22-alpine AS frontend
WORKDIR /build
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# Stage 2: build the Spring Boot jar, with the frontend inside it.
FROM eclipse-temurin:21-jdk-alpine AS backend
WORKDIR /build
COPY backend/gradle ./gradle
COPY backend/gradlew backend/settings.gradle.kts backend/build.gradle.kts ./
# Resolve dependencies before copying source so an edit does not re-download the world.
RUN ./gradlew dependencies --no-daemon --quiet || true
COPY backend/src ./src
COPY --from=frontend /build/dist/music-library-web/browser ./src/main/resources/static
RUN ./gradlew bootJar --no-daemon -x test

# Stage 3: run.
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=backend /build/build/libs/*.jar app.jar
RUN mkdir -p /var/lib/musiclibrary/media && chown -R app:app /var/lib/musiclibrary
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

Tests are skipped in the image build (`-x test`) because they require Docker themselves, via
Testcontainers, and running Docker inside the build is a complication with no payoff. Tests run in
Step 6 on the host, which is where they belong.

Create `.dockerignore`:

```
**/node_modules
**/dist
**/build
**/.gradle
**/.angular
.git
media
```

- [ ] **Step 3: Serve the Angular app for deep links**

A single-page app served from Spring returns 404 for any path the browser requests directly. There
is no router in this phase, so this is a guard against the problem appearing in Phase 3 rather than
a fix for one that exists now. Create
`backend/src/main/java/com/kasi/musiclibrary/config/SpaForwardingConfig.java`:

```java
package com.kasi.musiclibrary.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SpaForwardingConfig implements WebMvcConfigurer {

    /**
     * Any single-segment path that is not /api and has no file extension is handed to
     * index.html so the client-side router can resolve it.
     */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/{path:[^\\.]*}").setViewName("forward:/index.html");
    }
}
```

- [ ] **Step 4: Build the image**

```bash
docker compose build
```

Expected: all three stages complete. First build takes several minutes.

- [ ] **Step 5: Run it and verify end to end**

```bash
docker compose down -v
docker compose up -d
sleep 30
curl -s localhost:8080/actuator/health
curl -s "localhost:8080/api/tracks?size=100" | head -c 400
curl -sI "localhost:8080/api/tracks/$(curl -s localhost:8080/api/tracks | sed -n 's/.*"id":"\([^"]*\)".*/\1/p' | head -1)/stream" -H "Range: bytes=0-99" | head -5
```

Expected:
- health reports `{"status":"UP"}`
- the track listing reports `"totalElements":6`
- the stream request returns `HTTP/1.1 206 Partial Content`

Then open http://localhost:8080 in a browser. Expected: the library renders, search works, and a
track plays. This is the same URL a reviewer will use, served from the jar, not the dev server.

- [ ] **Step 6: Run the full test suite**

```bash
cd backend && ./gradlew test
cd ../frontend && npm test -- --run
```

Expected: both PASS. Record the counts; they go in the README.

- [ ] **Step 7: Verify from a clean clone**

The actual requirement, tested rather than assumed. This catches uncommitted files, which is the
single most common way a submission fails to build for someone else.

```bash
cd /tmp && rm -rf clean-check
git clone "/Users/Anup/Google Drive/Projects/kasi-project-github" clean-check
cd clean-check && docker compose up -d --build
sleep 45
curl -s localhost:8080/api/tracks | head -c 200
docker compose down -v
```

Expected: `"totalElements":6`. If this fails and the working copy succeeds, something needed is not
committed. Fix that before continuing.

- [ ] **Step 8: Commit**

```bash
git add Dockerfile .dockerignore backend
git commit -m "feat: build and run the whole system with one command

Multi-stage build compiles the Angular app into the jar's static resources,
so the running system is one app container and one database. The Java
toolchain is pinned and auto-provisioned so the build does not depend on
the reviewer's local JDK. Verified from a clean clone."
```

---

## Phase 1 exit criteria

Phase 1 is done when all of these are true. Each is checkable, not a judgment call.

- [ ] `docker compose up` on a clean clone serves a working app at http://localhost:8080
- [ ] The library shows six seeded tracks grouped under two albums and two artists
- [ ] Search narrows results by title, album, and artist
- [ ] Uploading an audio file adds it to the library and it appears without a reload
- [ ] Uploading the same file twice reports a conflict rather than creating a duplicate
- [ ] Clicking play produces audio, and the seek bar works
- [ ] `./gradlew test` and `npm test -- --run` both pass
- [ ] No credentials, accounts, or third-party services are required at any point
