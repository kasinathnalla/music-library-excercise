#!/usr/bin/env python3
"""
The conventions in AGENTS.md that can be checked mechanically.

Every rule here earned its place by having already caused a real bug in this repository. This runs
without Docker, without the network, and in under a second, so it is the one check that always
runs even when the test suites cannot.

Comments are stripped before matching. The first version of these checks flagged a javadoc warning
*about* a trap as if it were the trap, and flagged the comment telling you not to call
withNoXsrfProtection() as if it were the call. If you add a rule, make it fail on real code and
pass on a comment describing that code.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BACKEND_MAIN = ROOT / "backend/src/main/java"
BACKEND_TEST = ROOT / "backend/src/test/java"
FRONTEND = ROOT / "frontend/src"

failures: list[str] = []
checks_run = 0


def check(label: str, offenders: list[str]) -> None:
    global checks_run
    checks_run += 1
    if offenders:
        failures.append(label)
        print(f"  FAIL  {label}")
        for o in offenders[:8]:
            print(f"          {o}")
        if len(offenders) > 8:
            print(f"          ... and {len(offenders) - 8} more")
    else:
        print(f"  PASS  {label}")


def strip_java_comments(text: str) -> str:
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    return re.sub(r"//.*", "", text)


def strip_ts_comments(text: str) -> str:
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    return re.sub(r"(?<!:)//.*", "", text)


def java_files(base: Path):
    return sorted(base.rglob("*.java")) if base.exists() else []


def rel(p: Path) -> str:
    return str(p.relative_to(ROOT))


# --------------------------------------------------------------------------- backend
print("backend — java-spring and code-quality skills")

# Constructor injection only. Spring's test support may use @Autowired fields; src/main may not.
offenders = [f"{rel(f)}:{i}" for f in java_files(BACKEND_MAIN)
             for i, line in enumerate(strip_java_comments(f.read_text()).splitlines(), 1)
             if "@Autowired" in line]
check("no @Autowired in backend/src/main (constructor injection only)", offenders)

# Injected collaborators are final. Applies to Spring-managed classes, not JPA entities.
offenders = []
for f in java_files(BACKEND_MAIN):
    src = strip_java_comments(f.read_text())
    if not re.search(r"@(Service|RestController|Component|Configuration|ControllerAdvice|RestControllerAdvice)\b", src):
        continue
    for i, line in enumerate(src.splitlines(), 1):
        if re.match(r"\s*private\s+(?!final|static)[A-Z]\w*[\w<>,\s]*\s+\w+\s*;", line):
            offenders.append(f"{rel(f)}:{i}:{line.strip()}")
check("injected collaborators are final", offenders)

# A track with no album is legitimate; an inner join silently drops it.
offenders = [f"{rel(f)}:{i}" for f in java_files(BACKEND_MAIN)
             for i, line in enumerate(strip_java_comments(f.read_text()).splitlines(), 1)
             if "join fetch" in line and "left join fetch" not in line]
check("every 'join fetch' is a 'left join fetch'", offenders)

# Inside a transaction the catch is too late: it fails at commit as UnexpectedRollbackException.
offenders = []
for f in java_files(BACKEND_MAIN):
    src = strip_java_comments(f.read_text())
    if "@Transactional" in src and "catch (DataIntegrityViolationException" in src:
        offenders.append(rel(f))
check("no catch of DataIntegrityViolationException in a @Transactional class", offenders)

# One naming style per concept.
allowed = re.compile(r"(NotFound|Duplicate\w*|Invalid\w*|Unreadable\w*)Exception\.java$")
offenders = [rel(f) for f in java_files(BACKEND_MAIN)
             if f.name.endswith("Exception.java") and not allowed.search(f.name)]
check("exception names follow <Thing>NotFound / Duplicate<Thing> / Invalid<Thing>", offenders)

# An entity on the wire reaches AppUser, which holds a password hash.
offenders = []
for f in java_files(BACKEND_MAIN / "com/kasi/musiclibrary/controller"):
    src = strip_java_comments(f.read_text())
    if re.search(r"@(Get|Post|Put|Patch|Delete)Mapping", src):
        for entity in ("Playlist ", "Track ", "AppUser ", "Album ", "Artist "):
            if re.search(rf"(ResponseEntity<{entity.strip()}>|public {entity}\w+\()", src):
                offenders.append(f"{rel(f)} returns {entity.strip()}")
check("controllers never return a JPA entity", offenders)

# Schema changes go through Flyway; ddl-auto is validate.
cfg = list((ROOT / "backend/src/main/resources").glob("application*.y*ml"))
offenders = [rel(c) for c in cfg
             if re.search(r"ddl-auto:\s*(update|create|create-drop)", c.read_text())]
check("ddl-auto is never update/create (Flyway owns the schema)", offenders)

# Layer packages (DECISIONS 25). A class that fits none of them is usually doing two things.
LAYERS = {"controller","service","repository","entity","dto","exception","advice","security","config"}
offenders = []
for f in java_files(BACKEND_MAIN):
    parts = f.relative_to(BACKEND_MAIN / "com/kasi/musiclibrary").parts
    if len(parts) == 1:
        if f.name != "MusicLibraryApplication.java":
            offenders.append(f"{rel(f)} sits at the package root")
    elif parts[0] not in LAYERS:
        offenders.append(f"{rel(f)} is in '{parts[0]}/', not a layer package")
check("every backend class is in a layer package", offenders)

# --------------------------------------------------------------------------- backend tests
print("\nbackend tests — testing skill")

# @WithMockUser installs Spring's own User, so @AuthenticationPrincipal binds to null.
principal_endpoints = {"playlist"}
offenders = []
for f in java_files(BACKEND_TEST):
    if not any(part in str(f).lower() for part in principal_endpoints):
        continue
    if "@WithMockUser" in strip_java_comments(f.read_text()):
        offenders.append(rel(f))
check("owner-scoped tests authenticate for real, not with @WithMockUser", offenders)

# Real Postgres via Testcontainers; H2 would not prove the migrations work.
offenders = [rel(f) for f in java_files(BACKEND_TEST)
             if re.search(r"\bh2\b|H2Dialect", strip_java_comments(f.read_text()), re.I)]
check("no H2 in tests (Testcontainers Postgres only)", offenders)

# --------------------------------------------------------------------------- frontend
print("\nfrontend — angular skill")

ts_files = sorted(FRONTEND.rglob("*.ts")) if FRONTEND.exists() else []
html_files = sorted(FRONTEND.rglob("*.html")) if FRONTEND.exists() else []

# Omitting the track expression is NG5002 and fails the build.
offenders = [f"{rel(f)}:{i}" for f in html_files
             for i, line in enumerate(f.read_text().splitlines(), 1)
             if "@for (" in line and "; track " not in line]
check("every @for has a track expression", offenders)

offenders = [f"{rel(f)}:{i}" for f in ts_files
             for i, line in enumerate(strip_ts_comments(f.read_text()).splitlines(), 1)
             if re.search(r"\b(localStorage|sessionStorage)\b", line)]
check("no localStorage or sessionStorage for application state", offenders)

offenders = [f"{rel(f)}:{i}" for f in ts_files
             for i, line in enumerate(strip_ts_comments(f.read_text()).splitlines(), 1)
             if re.search(r":\s*any\b", line)]
check("no bare 'any'", offenders)

offenders = [rel(f) for f in ts_files if "NgModule" in strip_ts_comments(f.read_text())]
check("no NgModule (standalone components only)", offenders)

# The comment warning against it must not trip this; the call must.
offenders = [rel(f) for f in ts_files
             if "withNoXsrfProtection(" in strip_ts_comments(f.read_text())]
check("withNoXsrfProtection is never called", offenders)

# One service per feature owns that feature's URLs.
for prefix, owner in (("/api/playlists", "playlist.service.ts"), ("/api/tracks", "track.service.ts")):
    holders = sorted({rel(f) for f in ts_files if prefix in strip_ts_comments(f.read_text())})
    offenders = [h for h in holders if not h.endswith(owner)]
    check(f"{prefix} URLs appear only in {owner}", offenders)

# A missing template is NG2008, and the dev server caches it confusingly.
offenders = []
for f in ts_files:
    src = f.read_text()
    for key in ("templateUrl", "styleUrl"):
        for m in re.finditer(rf"{key}:\s*['\"]([^'\"]+)['\"]", src):
            if not (f.parent / m.group(1)).resolve().exists():
                offenders.append(f"{rel(f)} -> missing {m.group(1)}")
    for m in re.finditer(r"""from\s+['"](\.[^'"]+)['"]""", src):
        target = (f.parent / m.group(1)).resolve()
        if not (Path(str(target) + ".ts").exists() or (target / "index.ts").exists()):
            offenders.append(f"{rel(f)} -> unresolved import {m.group(1)}")
check("every relative import, templateUrl and styleUrl resolves", offenders)

# core / shared / features (DECISIONS 26). Nothing loose at the app root but the shell.
APP = FRONTEND / "app"
ROOTS = {"core", "shared", "features"}
ROOT_FILES = {"app.ts", "app.config.ts", "app.css", "app.html"}
offenders = []
if APP.exists():
    for f in sorted(APP.rglob("*")):
        if not f.is_file():
            continue
        parts = f.relative_to(APP).parts
        if len(parts) == 1:
            if f.name not in ROOT_FILES:
                offenders.append(f"{rel(f)} sits loose at the app root")
        elif parts[0] not in ROOTS:
            offenders.append(f"{rel(f)} is in '{parts[0]}/', not core/shared/features")
check("every frontend file is in core/, shared/ or features/", offenders)

# A feature's own service owns its URLs; a component must not build one. Interceptors are
# exempt: they inspect the outgoing URL to decide how to react, which is not building one.
offenders = []
for f in ts_files:
    if "/services/" in str(f) or "/interceptors/" in str(f) or f.name in ROOT_FILES:
        continue
    if re.search(r"""['"`]/api/""", strip_ts_comments(f.read_text())):
        offenders.append(rel(f))
check("only feature services build /api/ URLs", offenders)

# Colours come from the tokens in src/styles.css, which redefine themselves under
# prefers-color-scheme: dark. A hardcoded hex is invisible or unreadable in the other theme -
# this is exactly how the add-to-playlist panel shipped unusable in dark mode.
# One documented exception: a scrim is meant to darken what is behind it in both themes.
SCRIM = "rgb(0 0 0 / 0.5)"
offenders = []
for f in sorted(FRONTEND.rglob("*.css")) if FRONTEND.exists() else []:
    if f.name == "styles.css":          # where the tokens are defined
        continue
    text = re.sub(r"/\*.*?\*/", "", f.read_text(), flags=re.S).replace(SCRIM, "")
    for m in re.findall(r"#[0-9a-fA-F]{3,8}\b|rgba?\([0-9 ,./]+\)", text):
        offenders.append(f"{rel(f)}: {m}")
check("component CSS uses design tokens, not hardcoded colours", offenders)

# --------------------------------------------------------------------------- result
print()
if failures:
    print(f"{checks_run - len(failures)}/{checks_run} passed. Violations:")
    for f in failures:
        print(f"  - {f}")
    sys.exit(1)
print(f"{checks_run}/{checks_run} standards checks passed")
