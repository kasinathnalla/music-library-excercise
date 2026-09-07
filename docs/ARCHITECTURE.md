# Architecture

Diagrams of what this system is and how its parts actually interact. Each one is here because it
shows a mechanism that is hard to see from the code alone; none are decoration.

Rendered by GitHub directly. For the reasoning behind these shapes, see [DECISIONS.md](DECISIONS.md).

---

## 1. Deployment: what runs

Two containers and one command. The frontend is not separately served: it is compiled into the
backend jar as static resources, which is why there is no web server to configure and why the
reproducibility requirement is satisfiable at all.

```mermaid
graph TB
    subgraph browser["Browser"]
        SPA["Angular SPA<br/>signals, zoneless"]
    end

    subgraph host["Docker Compose"]
        subgraph appc["app container"]
            JAR["Spring Boot jar"]
            STATIC["static/<br/>compiled Angular"]
            JAR -.serves.-> STATIC
        end
        subgraph dbc["db container"]
            PG[("PostgreSQL 16<br/>catalog")]
        end
        VOL[["media volume<br/>audio bytes"]]
    end

    SPA -->|"REST/JSON<br/>/api/**"| JAR
    SPA -->|"audio, Range requests<br/>/api/tracks/id/stream"| JAR
    JAR -->|JDBC| PG
    JAR -->|"read / write files"| VOL

    style SPA fill:#e8f0ed,stroke:#2f5d50
    style JAR fill:#e8f0ed,stroke:#2f5d50
    style PG fill:#f4f4f2,stroke:#6b6a66
    style VOL fill:#f4f4f2,stroke:#6b6a66
```

**Why the audio is on a volume and not in the database.** Audio files are large and are served with
range requests. Streaming them out of Postgres would mean pulling bytes through JDBC on every seek.
The database holds the catalog; the filesystem holds the bytes; a track row points at a path.

---

## 2. Build pipeline

The multi-stage build is what makes "one command on a stranger's machine" true. Note that tests are
deliberately *not* run inside the image build.

```mermaid
graph LR
    subgraph s1["Stage 1: node:22-alpine"]
        Y["yarn install<br/>yarn build"] --> DIST["dist/browser"]
    end
    subgraph s2["Stage 2: temurin:21-jdk"]
        G["gradle bootJar"]
    end
    subgraph s3["Stage 3: temurin:21-jre"]
        RUN["java -jar app.jar<br/>non-root user"]
    end

    DIST -->|"COPY into<br/>src/main/resources/static"| G
    G -->|"COPY app.jar"| RUN

    style DIST fill:#e8f0ed,stroke:#2f5d50
    style RUN fill:#e8f0ed,stroke:#2f5d50
```

Tests need Docker themselves, via Testcontainers, and running Docker inside a Docker build is a
complication with no payoff. They run on the host instead.

---

## 3. Backend components

Grouped by responsibility rather than by technical layer, so that files which change together live
together. Arrows are dependency direction.

```mermaid
graph TD
    subgraph security["security - the authorization matrix"]
        SEC["SecurityConfig<br/>the filter chain"]
        DUD["DatabaseUserDetailsService"]
        USERS["AppUserRepository"]
    end

    subgraph api["api - HTTP only, no business logic"]
        TC["TrackController"]
        SC["StreamController"]
        STC["StatsController"]
        AC["AuthController<br/>sign-in status, registration, logout"]
        EH["ApiExceptionHandler"]
    end

    subgraph ingest["ingest - getting audio in"]
        IS["IngestService"]
        ATR["AudioTagReader<br/>the only jaudiotagger caller"]
        AFS["AudioFileStore<br/>hashing, sharding, path guard"]
        SR["SeedRunner"]
    end

    SEC -.->|"authorizes every request<br/>before it reaches api"| TC
    SEC -.-> SC
    SEC -.-> AC
    SEC --> DUD
    DUD --> USERS
    AC --> USERS
    TC -->|"records the uploader"| USERS

    subgraph catalog["catalog - the domain"]
        TUS["TrackUpdateService"]
        TDS["TrackDeletionService"]
        REPO["ArtistRepository<br/>AlbumRepository<br/>TrackRepository"]
        ENT["Artist / Album / Track"]
    end

    subgraph prov["provenance"]
        PS["ProvenanceService"]
    end

    TC --> IS
    TC --> TUS
    TC --> TDS
    TC --> REPO
    TC --> PS
    SC --> REPO
    SC --> AFS
    STC --> REPO
    IS --> ATR
    IS --> AFS
    IS --> REPO
    SR --> IS
    TUS --> REPO
    TUS --> PS
    TDS --> REPO
    TDS --> AFS
    TDS --> PS
    REPO --> ENT

    style ATR fill:#fdf0e8,stroke:#a33224
    style AFS fill:#fdf0e8,stroke:#a33224
```

The two highlighted classes are the isolation boundaries that matter:

- **`AudioTagReader`** is the only place jaudiotagger appears. It is unmaintained (3.0.1, 2021), so
  confining it means replacing it touches one file and its test.
- **`AudioFileStore`** is the only place a user-influenced value reaches the filesystem. Its
  `resolve()` rejects paths escaping the media root, because `StreamController` serves whatever it
  returns and path traversal would otherwise be an arbitrary file read.

`catalog` knows nothing about HTTP or files. `api` holds no logic, so the wire format can change
without disturbing the domain.

**`security`** holds the whole authorization boundary in one place: `SecurityConfig` is the URL
matrix (who may reach what), and it is deliberately not method-level annotations scattered across
`ingest` and `catalog`, because `SeedRunner` ingests the bundled tracks at boot with no
authenticated user present. See D15.

---

## 4. Data model

```mermaid
erDiagram
    ARTIST ||--o{ ALBUM : "credited on"
    ALBUM  ||--o{ TRACK : contains
    TRACK  }o--o{ ARTIST : "track_artist (unused in v1)"

    ARTIST {
        uuid id PK
        text name "unique on lower(name)"
        text sort_name
        timestamptz created_at
    }
    ALBUM {
        uuid id PK
        text title
        uuid album_artist_id FK "nullable"
        smallint release_year
    }
    TRACK {
        uuid id PK
        text title
        uuid album_id FK "nullable - a track need not have an album"
        smallint track_number
        smallint disc_number
        integer duration_ms
        text file_path "relative, sharded by hash"
        varchar content_hash "UNIQUE - identity is content"
        bigint file_size
        text content_type
    }
    FIELD_EDIT {
        uuid id PK
        text entity_type
        uuid entity_id
        text field_name
        timestamptz edited_at
    }
```

Three things in this model are load-bearing:

1. **`album_id` is nullable.** A track with no album is legitimate, which is why every track query
   must use left joins. JPQL path navigation compiles to an inner join and silently drops these rows.
2. **`content_hash` is unique.** Identity is content, not filename. This makes duplicate upload a
   constraint violation and makes seeding idempotent for free.
3. **`field_edit` is keyed per field**, not per track, so correcting a title does not mark the whole
   track as untouchable by future enrichment.

`track_artist` exists but is unmapped in v1. Per-track credits are needed first by richer metadata
editing; the table is there so adding them is not a migration against live data.

---

## 5. Ingest: upload to library entry

The most involved flow in the system, and the one where order matters. Bytes are stored *before*
tags are parsed, because the hash is needed to detect a duplicate and the tag reader wants a real
file on disk.

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant TC as TrackController
    participant IS as IngestService
    participant FS as AudioFileStore
    participant TR as AudioTagReader
    participant DB as Repositories

    C->>TC: POST /api/tracks (multipart)
    TC->>TC: reject if empty → 400
    TC->>IS: ingest(stream, filename)

    rect rgb(240, 244, 248)
        note over IS,FS: store while hashing
        IS->>FS: store(stream, filename)
        FS->>FS: write to temp file, SHA-256 as it streams
        FS->>FS: move to sharded path ab/cd/abcd....mp3
        FS-->>IS: StoredAudio(path, size, hash)
    end

    IS->>DB: findByContentHash(hash)
    alt already present
        DB-->>IS: existing track
        IS--xTC: DuplicateTrackException
        TC-->>C: 409 + existingTrackId
    else new content
        IS->>TR: read(path)
        alt not audio
            TR--xTC: UnreadableAudioException
            TC-->>C: 400
        else parsed
            TR-->>IS: ParsedTags (all fields nullable)
            IS->>IS: reconcile artist and album (see §6)
            IS->>DB: save(Track)
            DB-->>IS: Track
            IS-->>TC: IngestResult
            TC->>DB: findByIdWithAlbum(id)
            note right of TC: explicit fetch: open-in-view is off,<br/>a lazy album would throw on serialization
            TC-->>C: 201 + TrackResponse
        end
    end
```

Step 5 is where a temp file is used rather than writing straight to the final path: the destination
name is derived from the hash, which is not known until all the bytes have been read, and a partial
upload must never occupy a real path.

The note on the last step records a bug that actually happened. Re-fetching with a plain `findById`
and then serializing the album threw `LazyInitializationException`, returning 500 for an upload that
had already committed. The track appeared in the library while the UI reported failure.

---

## 6. Artist and album reconciliation

This is the decision that makes a library rather than a list of files. It runs on every ingest and
on every edit that touches album or artist.

```mermaid
flowchart TD
    START([album tag present?]) -->|no| NOALBUM["album = null"]
    START -->|yes| ARTIST{"artist known?<br/>album_artist, else artist"}

    ARTIST -->|no| NOARTIST["artist = null"]
    ARTIST -->|yes| FINDART["findByNameIgnoreCase"]
    FINDART --> ARTEXISTS{found?}
    ARTEXISTS -->|yes| REUSEART["reuse artist row"]
    ARTEXISTS -->|no| NEWART["create artist"]

    REUSEART --> FINDALB
    NEWART --> FINDALB
    NOARTIST --> FINDALB

    FINDALB["findByTitleIgnoreCase<br/>AndAlbumArtist"] --> ALBEXISTS{found?}
    ALBEXISTS -->|yes| REUSEALB["reuse album row"]
    ALBEXISTS -->|no| NEWALB["create album"]

    NOALBUM --> DONE([attach to track])
    REUSEALB --> DONE
    NEWALB --> DONE

    style NOALBUM fill:#f4f4f2,stroke:#6b6a66
    style REUSEART fill:#e8f0ed,stroke:#2f5d50
    style REUSEALB fill:#e8f0ed,stroke:#2f5d50
```

Two subtleties encoded here:

- **An album is matched on title *and* credited artist**, not title alone. Many unrelated albums are
  called *Greatest Hits*.
- **A missing album tag yields no album, not an "Unknown Album" row.** An Unknown Album would
  collect every untagged track in the library into one grouping that looks real but means nothing.

---

## 7. Metadata edit: re-point, not rename

The subtlest behaviour in the system. Editing a track's album moves *that track*; it does not rename
the album for everyone. The old album is then cleaned up only if nothing is left on it.

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant TC as TrackController
    participant US as TrackUpdateService
    participant DB as Repositories
    participant PS as ProvenanceService

    C->>TC: PATCH /api/tracks/{id} {albumTitle: "New"}
    TC->>TC: reject blank title → 400
    TC->>US: update(id, Edit)
    US->>DB: findByIdWithAlbum(id)
    US->>US: remember previousAlbum

    rect rgb(240, 244, 248)
        note over US,DB: fields absent from the request are untouched
        US->>US: apply title / numbers if present
        US->>DB: reconcile new album + artist (§6)
        US->>DB: save(track)
    end

    US->>PS: recordUserEdit(TRACK, id, "albumTitle")

    rect rgb(253, 240, 232)
        note over US,DB: cleanup of what the track left behind
        US->>DB: countByAlbumId(previousAlbum)
        alt no tracks remain
            US->>DB: delete previousAlbum
            US->>DB: countByAlbumArtistId(artist)
            alt no albums remain
                US->>DB: delete artist
            end
        end
    end

    US-->>TC: updated Track
    TC->>PS: userEditedFields(TRACK, id)
    TC-->>C: 200 + TrackResponse incl. userEditedFields
```

An empty string for `albumTitle` or `artistName` means *detach*, not *set to blank*. That is how a
track ends up with no album through the API, and it takes the same cleanup path.

---

## 8. Playback and seeking

Short, but the reason it works is worth showing. Returning a `Resource` rather than writing bytes by
hand is what makes range handling correct.

```mermaid
sequenceDiagram
    autonumber
    participant B as Browser audio element
    participant SC as StreamController
    participant DB as TrackRepository
    participant FS as AudioFileStore

    B->>SC: GET /api/tracks/{id}/stream
    SC->>DB: findById
    alt no such track
        SC-->>B: 404
    else found
        SC->>FS: resolve(track.filePath)
        FS->>FS: reject if path escapes media root
        alt file missing on disk
            SC-->>B: 404
        else
            SC-->>B: 200, Accept-Ranges: bytes
        end
    end

    note over B: user drags the scrubber
    B->>SC: GET same URL, Range: bytes=50000-
    SC-->>B: 206 Partial Content<br/>Content-Range: bytes 50000-.../total
```

Without `Accept-Ranges`, the browser plays from the start but the scrubber does nothing, which is
the kind of half-working that is worse than an absent feature.

**Not shown above, and worth stating explicitly: every request to this endpoint requires an
authenticated session.** The browser's audio element cannot attach an `Authorization` header to
the `Range` requests it issues on its own, so authentication happens once, at sign-in, via HTTP
Basic, and the resulting security context is persisted to an HTTP session
(`HttpSessionSecurityContextRepository`, configured explicitly in `SecurityConfig`). The session
cookie is what authenticates every request in the sequence above, including the ranged one the
browser sends unprompted while the user drags the scrubber. See D16 for why, and
`SessionAuthenticationTest.theSessionAloneAuthenticatesTheAudioRequest` for the regression guard.

---

## 9. Track lifecycle

```mermaid
stateDiagram-v2
    [*] --> Uploaded: POST /api/tracks
    Uploaded --> Rejected_Duplicate: hash already present
    Uploaded --> Rejected_Unreadable: tags unparseable
    Rejected_Duplicate --> [*]
    Rejected_Unreadable --> [*]

    Uploaded --> InLibrary: stored, parsed, reconciled

    InLibrary --> InLibrary: GET, stream, search
    InLibrary --> Edited: PATCH
    Edited --> Edited: PATCH again
    Edited --> InLibrary: (same state, provenance grows)

    InLibrary --> Deleted: DELETE
    Edited --> Deleted: DELETE
    Deleted --> [*]

    note right of Edited
        Each changed field is recorded in
        field_edit. Automated enrichment
        must leave recorded fields alone.
    end note

    note right of Deleted
        Row, stored bytes, and provenance
        all removed. Freeing the hash is
        what allows re-upload later.
    end note
```

---

## 10. Album and artist lifecycle

Albums and artists are never created or deleted directly. They come into existence through
reconciliation and disappear when nothing references them, which is why the same cleanup routine
appears in both editing and deletion.

```mermaid
stateDiagram-v2
    [*] --> Created: reconciliation found no match
    Created --> Referenced: track attached

    Referenced --> Referenced: more tracks attached
    Referenced --> Orphaned: last track moved away or deleted
    Orphaned --> [*]: removed in the same transaction

    note right of Orphaned
        An empty album is invisible in the
        track list but still inflates library
        counts and reappears during ingest
        reconciliation, so it is not left behind.
    end note
```
