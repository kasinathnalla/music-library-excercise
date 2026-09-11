# Audit — playlists

**Branch goal:** deliver roadmap Phase 3 (per-user playlists), then bring the repository's structure
into line with the conventional layout.

> Started retroactively, after most of the work had landed. The entries below are accurate but were
> not written as the work happened, which is the point of an audit. Noted rather than disguised.

---

## Changes made

### 1. Playlists, end to end
**Paths:** `backend/.../entity/Playlist*`, `repository/Playlist*`, `service/PlaylistService`,
`controller/PlaylistController`, `exception/*`, `dto/Playlist*`, `V6__playlists.sql`,
`frontend/src/app/features/playlists/*`, `core/services/playback.service.ts`,
`shared/components/player/`

**What:** create / rename / delete playlists, add and remove tracks, reorder, and play through with
auto-advance.
**Why:** roadmap Phase 3. Phase 6 had made per-user ownership possible, which the roadmap named as
the precondition.

### 2. Backend repackaged by layer
**What:** 61 files from `catalog/ ingest/ playlist/ provenance/ api/` to `controller/ service/
repository/ entity/ dto/ exception/ advice/ security/ config/`.
**Why:** the feature layout kept surprising readers who opened the repository expecting the
conventional one. DECISIONS 25.

### 3. Frontend restructured
**What:** 39 files from flat feature folders to `core/ shared/ features/`.
**Why:** consistency with 2 — two different answers to "where does this go?" in one repository is
worse than either answer. DECISIONS 26.

### 4. Colour tokens
**What:** 85 hardcoded hex and `rgba()` values across all 8 stylesheets replaced with the tokens in
`src/styles.css`.
**Why:** reported as "adding a song to playlist … dark mode not properly visible". The tokens
redefine themselves under `prefers-color-scheme: dark`; hardcoded values are correct in exactly one
theme. The bug was mine and pre-dated the report in `login.css` and `register.css` too.

### 5. Standards enforcement
**Path:** `scripts/standards-check.py`, `scripts/check.sh`
**What:** 21 conventions checked mechanically; runs first in `check.sh`.
**Why:** documented rules that nothing enforces are suggestions. Two of the rules had to be narrowed
after false positives — the first version flagged a javadoc *warning about* a trap as the trap.

---

## Decisions not to act

| Item | Decision | Rationale |
|---|---|---|
| `SecurityConfig` matcher for `/api/playlists/**` | Not added | `anyRequest().authenticated()` already covers it and both roles should keep playlists; row ownership is the service's job |
| Base `ApplicationException` hierarchy | Not adopted | Translation is already centralised in `ApiExceptionHandler`; a base type adds indirection without removing code |
| Drag-and-drop reordering | Not adopted | Requires `@angular/cdk`; move up/down is keyboard-operable for free |
| Splitting this into three commits | Not done | The repackage moved the same files the feature added, so a clean split was not achievable without a build to verify the intermediate states |
| TypeScript path aliases | Deferred | Would fix `../../../../` imports, but touches `tsconfig.json` and could not be verified here |

---

## Verification actually performed

```text
python3 scripts/standards-check.py
21/21 standards checks passed
(each rule separately negative-tested against a planted violation: all failed as intended)

javac --release 21 (all main + test sources, no dependency classpath)
0 structural errors; 0 unresolved com.kasi.musiclibrary imports
package declaration matches directory for all 61 files
every cross-package project reference has an import

cd backend && ./gradlew test
NOT RUN - no Docker daemon available, so Testcontainers could not start.

cd frontend && yarn test --run  /  yarn build
NOT RUN - yarn could not be installed (blocked by the package registry policy).

Manual pass through the UI
NOT PERFORMED - no browser available in this environment.
```

**The playlist feature has never been executed.** The tests are written but unrun, the Angular
templates have never been compiled, and the dropdown and the dark-mode colours have never been
rendered. That is the single most important thing on this page.

---

## Remaining risk

- Anything a compiler or a test would catch in the Angular half is still latent.
- `docs/api/openapi.json` is stale; it needs a running app to regenerate.
- `.git/objects/` holds leftover `tmp_obj_*` files and a stale `index.lock`, because deletes were
  not permitted in the environment this was committed from. `git gc` and `rm -f .git/index.lock`
  clear them.
