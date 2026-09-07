# Phase 6: Users and Roles

> **For agentic workers:** implement this plan task by task, in order. Steps use checkbox (`- [ ]`)
> syntax for tracking. Task 1 deliberately breaks the existing test suite and Task 2 repairs it —
> do not skip ahead, and do not compress tasks into a single batch. AGENTS.md records what happened
> the last time that was tried.

**Goal:** the library gets two kinds of user. An **admin** can do everything the app does today. A
**customer** can browse, search, and listen, and nothing else. Both must sign in; there is no
anonymous access. Authentication is HTTP Basic against accounts stored in the database.

**Non-goal:** this is not the final auth story. Basic auth is chosen because it is the smallest
thing that establishes real identity and a real authorization boundary, and because everything
built on top of it — the role model, the endpoint matrix, the UI gating — survives being moved onto
tokens or OIDC later. What does *not* survive is called out under [What this is not](#what-this-is-not).

**Estimate:** ~0.5 day.

---

## The rules, stated once

| Capability | Endpoint | Anonymous | CUSTOMER | ADMIN |
|---|---|---|---|---|
| Browse and search | `GET /api/tracks` | 401 | ✅ | ✅ |
| Fetch one track | `GET /api/tracks/{id}` | 401 | ✅ | ✅ |
| Stream audio | `GET /api/tracks/{id}/stream` | 401 | ✅ | ✅ |
| Library counts | `GET /api/stats` | 401 | ✅ | ✅ |
| Who am I | `GET /api/auth/me` | 401 | ✅ | ✅ |
| Upload | `POST /api/tracks` | 401 | **403** | ✅ |
| Edit metadata | `PATCH /api/tracks/{id}` | 401 | **403** | ✅ |
| Delete | `DELETE /api/tracks/{id}` | 401 | **403** | ✅ |
| Health | `GET /actuator/health` | ✅ | ✅ | ✅ |
| API docs | `/swagger-ui/**`, `/v3/api-docs/**` | ✅ | ✅ | ✅ |
| The SPA itself | `/`, `/index.html`, static assets | ✅ | ✅ | ✅ |

**401 versus 403 is load-bearing.** Not signed in is 401 and the UI shows the login screen. Signed
in but not allowed is 403 and the UI says so. Collapsing them means a customer who clicks something
they should not see gets bounced to a login screen they are already past, which reads as a broken
session rather than a refused action.

**Editing is admin-only** because a hand edit is recorded in `field_edit` and thereby becomes
off-limits to future automated enrichment (see D10). That is a durable, library-wide consequence,
and it is not something a listener should be able to cause.

**The API docs and the SPA shell stay public** so UC-6 and UC-7 survive: a reviewer can still read
the API without credentials, and the login screen has to be servable to someone who has none.

---

## What this is not

Recorded here so the next reader does not mistake a deliberate floor for an oversight.

- **No signup.** Accounts are seeded by migration. Creating users through the API is a later phase.
- **No password change, reset, or lockout.** No rate limiting on failed logins.
- **One role per user**, not a set. The schema note in Task 2 says what changes when that stops
  being true.
- **No per-user library.** There is one shared catalog. Customers see everything an admin uploaded.
  `track.uploaded_by` records who added a row but nothing filters on it yet.
- **No TLS.** Basic auth over plain HTTP puts the password on the wire on every request. Acceptable
  for a local exercise and stated plainly in the README rather than left to be noticed.
- **Playlists (Phase 3) will need per-user ownership.** This phase is what makes that possible; it
  does not build it.

---

## Sequencing

This runs **next, ahead of Phases 2 through 5**. Playback depth and playlists both need to know who
is acting — a playlist without an owner is a shared mutable global — and retrofitting identity under
a built playlist model is more expensive than the half day it costs now.

---

## Design decisions taken in this plan

Each of these had a real alternative. They belong in `docs/DECISIONS.md` when the phase lands
(Task 10).

### D14. Accounts are rows, seeded by migration

In-memory users in `application.yaml` would be less code. But a user is data, the app already owns a
database and a Flyway pipeline, and the moment anyone wants a third account, admin-managed accounts,
or `uploaded_by`, in-memory users have to be thrown away. Seeding via migration keeps
`docker compose up` demoable on a clean volume without a signup screen existing.

### D15. Authorization lives in the filter chain, not on the domain services

The rules are URL-and-method rules, expressed once in `SecurityConfig`, where the whole matrix can
be read in one screen and diffed in one place. Method security on `IngestService` would look tidier
but would also apply to `SeedRunner`, which ingests the bundled tracks at boot with no
authentication present, and would push an HTTP concern into `catalog/` and `ingest/` — which
AGENTS.md says know nothing about HTTP.

The trade-off: a new controller that forgets to add a matcher is open to any signed-in user rather
than closed by default. Mitigated by `anyRequest().authenticated()` as the final rule (so it is
never open to anonymous) and by the authorization test being a matrix rather than a happy path.

### D16. Basic credentials establish a session; the session is what the audio element uses

This is the subtle one, and it is forced by a feature the README treats as load-bearing.

Seeking works because the browser's `<audio>` element issues its own `Range` requests directly to
`/api/tracks/{id}/stream`. Those requests are made by the browser, not by Angular's `HttpClient`, so
**there is no way to attach an `Authorization` header to them.** Three ways out:

1. **Let the server challenge with `WWW-Authenticate: Basic`** and have the browser cache the
   credentials. Works, but pops the native browser credential dialog over the SPA, and there is no
   reliable way to log out of it.
2. **Fetch the audio as a blob through `HttpClient`** so the header can be set. Kills range requests
   and therefore kills seeking — the thing UC-3 exists to demonstrate.
3. **Authenticate once with Basic, persist the security context in an HTTP session**, and let the
   session cookie authenticate everything the browser issues on its own, the audio element included.

Option 3 is taken. In effect the login call is Basic and everything after it is a cookie session,
which is why CSRF protection has to come back on (Task 5) — a cookie is an ambient credential and
Basic on its own is not.

When this moves to tokens, the replacement for the cookie is a short-lived signed stream URL, and
the seam is `TrackService.streamUrl()` in the frontend plus `StreamController`. Nothing else in this
phase changes.

---

## File structure after this phase

```
backend/src/main/java/com/kasi/musiclibrary/
  security/                       New package. Everything auth, in one place.
    SecurityConfig.java           The filter chain. The authorization matrix lives here.
    AppUser.java                  Entity.
    AppUserRepository.java
    Role.java                     enum { ADMIN, CUSTOMER }
    DatabaseUserDetailsService.java
    CurrentUser.java              Response record for /api/auth/me
  api/
    AuthController.java           GET /api/auth/me, POST /api/auth/logout
backend/src/main/resources/db/migration/
  V3__users.sql                   app_user table, seeded admin and customer
  V4__track_uploaded_by.sql       nullable FK, so the schema carries an owner concept
backend/src/test/java/com/kasi/musiclibrary/
  support/
    PostgresIntegrationTest.java  MODIFIED: nothing
    SecuredMockMvcTest.java       New base: builds MockMvc with the real filter chain
  security/
    AuthorizationMatrixTest.java  The table above, as tests
    AuthenticationTest.java       401s, bad credentials, /api/auth/me
frontend/src/app/
  auth/
    auth.service.ts               Session state as a signal; login, logout, refresh
    auth.model.ts
    login/                        The sign-in view
    unauthorized.interceptor.ts   401 clears the session; 403 surfaces a message
  app.ts                          MODIFIED: login → welcome → library
  catalog/track-list/             MODIFIED: admin controls gated on role
```

`security/` is its own package for the same reason `ingest/` is: it is the part where a mistake is
expensive, and keeping it in one directory means a reviewer can read the whole boundary without
opening `api/`.

---

## Task 1: Lock everything down, and make the tests notice

The first thing to establish is that the test harness actually exercises security. This is the
trap that matters most in this phase.

**Files:**
- Modify: `backend/build.gradle.kts`
- Create: `backend/src/main/java/com/kasi/musiclibrary/security/SecurityConfig.java`
- Create: `backend/src/test/java/com/kasi/musiclibrary/support/SecuredMockMvcTest.java`
- Create: `backend/src/test/java/com/kasi/musiclibrary/security/AuthenticationTest.java`

- [ ] **Step 1: Add the dependency**

```kotlin
implementation("org.springframework.boot:spring-boot-starter-security")
testImplementation("org.springframework.security:spring-security-test")
```

Boot 4 uses modular test starters (`spring-boot-starter-webmvc-test` and friends). If
`org.springframework.boot:spring-boot-starter-security-test` resolves, prefer it for consistency
with the rest of the file. Verify by resolving, not by assuming.

- [ ] **Step 2: The harness that makes security real in tests**

**This is the trap.** Every existing test builds MockMvc as:

```java
MockMvcBuilders.webAppContextSetup(context).build();
```

That bypasses the Spring Security filter chain entirely. Add security to the application and every
one of those tests keeps passing, green and meaningless, exactly the way the suite once passed
against an application that could not start (AGENTS.md, "Things that will bite you"). The fix is one
line and it must be applied everywhere:

```java
package com.kasi.musiclibrary.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

/**
 * Base for tests that go through HTTP.
 *
 * <p>The {@code springSecurity()} configurer is the whole point: without it MockMvc skips the
 * security filter chain, so an unauthenticated request succeeds and every authorization test in
 * this suite passes for the wrong reason.
 */
public abstract class SecuredMockMvcTest extends PostgresIntegrationTest {

    @Autowired
    protected WebApplicationContext context;

    protected MockMvc mockMvc;

    @BeforeEach
    void buildMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }
}
```

- [ ] **Step 3: Write the failing test**

`AuthenticationTest extends SecuredMockMvcTest`:

- `anonymousRequestsAreRejected` — `GET /api/stats` → 401
- `anonymousBrowsingIsRejected` — `GET /api/tracks` → 401
- `theHealthEndpointStaysOpen` — `GET /actuator/health` → 200
- `theApiDocsStayOpen` — `GET /v3/api-docs` → 200

Run it. The first two fail (200, no security yet). That is the point.

- [ ] **Step 4: The filter chain**

```java
package com.kasi.musiclibrary.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/v3/api-docs", "/swagger-ui/**",
                                         "/swagger-ui.html").permitAll()
                        // The SPA shell must be reachable by someone who has no credentials yet,
                        // or the login screen itself 401s.
                        .requestMatchers("/", "/index.html", "/favicon.ico",
                                         "/*.js", "/*.css", "/assets/**").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(basic -> {})
                .formLogin(form -> form.disable())
                .build();
    }
}
```

Leave CSRF and session handling alone for now; Task 5 sets both deliberately.

- [ ] **Step 5: Verify**

`AuthenticationTest` passes. The rest of the suite is now red — expected, and repaired next.

---

## Task 2: Bring the existing suite back to green under the filter chain

**Files:**
- Modify: `api/TrackControllerTest.java`, `api/TrackEditTest.java`, `api/TrackDeletionTest.java`,
  `api/StreamControllerTest.java`, `HealthEndpointTest.java`

- [ ] **Step 1: Move each HTTP test onto the new base class**

Extend `SecuredMockMvcTest` instead of `PostgresIntegrationTest`, drop the local
`WebApplicationContext` field and the local `mockMvc` construction, and keep whatever else the
`@BeforeEach` does (repository cleanup, fixture loading) by calling it from an ordinary
`@BeforeEach` — JUnit runs the superclass's first.

`AudioFileStoreTest` does not go through HTTP and must stay a plain JUnit test. Do not touch it.

- [ ] **Step 2: Give each test an identity**

Annotate the class with the least privilege the tests in it actually need:

```java
@WithMockUser(roles = "ADMIN")     // upload / edit / delete tests
@WithMockUser(roles = "CUSTOMER")  // list / search / stream tests
```

Prefer `@WithMockUser` over real credentials here: these are catalog tests, and they should not
also depend on the seeded accounts existing. Real credentials are used in Task 4, where
authenticating *is* what is under test.

- [ ] **Step 3: Verify**

`./gradlew test` is green, and — the check that matters — temporarily comment out `.apply(springSecurity())`
in `SecuredMockMvcTest` and confirm `AuthenticationTest` goes red. If it stays green, the harness is
not wired and everything after this is theatre. Restore it.

---

## Task 3: The user schema

**Files:**
- Create: `backend/src/main/resources/db/migration/V3__users.sql`

- [ ] **Step 1: Write the migration**

```sql
-- "user" is reserved in Postgres and would need quoting at every use site. app_user is not.
create table app_user (
    id            uuid primary key default gen_random_uuid(),
    username      text        not null,
    password_hash text        not null,
    role          text        not null,
    enabled       boolean     not null default true,
    created_at    timestamptz not null default now(),
    constraint app_user_role_check check (role in ('ADMIN', 'CUSTOMER'))
);

create unique index app_user_username_key on app_user (lower(username));

-- Seeded so `docker compose up` on a clean volume is immediately usable, matching the six
-- seeded tracks. Hashes are BCrypt literals, not computed here: Flyway checksums the file,
-- and a migration that generated a different hash on every run would change its own checksum.
--
-- These are demo credentials for a local exercise and are documented in the README. A real
-- deployment seeds nothing and provisions the first admin out of band.
insert into app_user (username, password_hash, role) values
    ('admin',    '$2y$10$REPLACE_WITH_REAL_HASH_FOR_admin',    'ADMIN'),
    ('customer', '$2y$10$REPLACE_WITH_REAL_HASH_FOR_customer', 'CUSTOMER');
```

- [ ] **Step 2: Generate the two hashes and paste them in**

```bash
htpasswd -bnBC 10 "" 'admin'    | tr -d ':\n'; echo
htpasswd -bnBC 10 "" 'customer' | tr -d ':\n'; echo
```

`BCryptPasswordEncoder` accepts `$2a`, `$2b`, and `$2y` prefixes, so `htpasswd` output goes in
verbatim. Without `htpasswd`, use `python3 -c "import bcrypt;print(bcrypt.hashpw(b'admin', bcrypt.gensalt(10)).decode())"`.

- [ ] **Step 3: One role per user, and what changes when that stops being true**

`role` is a column, not a join table, because there are exactly two roles and no user has both.
When roles become a set, the migration is `app_user_role (user_id, role)` plus a backfill from this
column — additive, no rewrite of the entity. Recorded so the shortcut is visible as a choice.

- [ ] **Step 4: Verify**

`docker compose down -v && docker compose up -d db`, boot the backend, and confirm Flyway applied V3
and both rows exist. Flyway rejects a changed checksum on an already-applied migration, so if V3 is
edited after this point the volume must be reset again.

---

## Task 4: Identity, and the authorization matrix

**Files:**
- Create: `security/Role.java`, `security/AppUser.java`, `security/AppUserRepository.java`,
  `security/DatabaseUserDetailsService.java`
- Modify: `security/SecurityConfig.java`
- Create: `backend/src/test/java/com/kasi/musiclibrary/security/AuthorizationMatrixTest.java`

- [ ] **Step 1: Write the failing test**

`AuthorizationMatrixTest extends SecuredMockMvcTest`, using real seeded credentials via
`.with(httpBasic("customer", "customer"))`. One test per cell that has a rule behind it:

| Test | Expects |
|---|---|
| `aCustomerCanBrowseTheLibrary` | `GET /api/tracks` → 200 |
| `aCustomerCanFetchOneTrack` | `GET /api/tracks/{id}` → 200 |
| `aCustomerCanStreamAudio` | `GET /api/tracks/{id}/stream` → 200, `Accept-Ranges: bytes` |
| `aCustomerCanSeekWhileStreaming` | `Range: bytes=1000-` → 206 |
| `aCustomerCanReadLibraryStats` | `GET /api/stats` → 200 |
| `aCustomerCannotUpload` | `POST /api/tracks` → 403 |
| `aCustomerCannotEditMetadata` | `PATCH /api/tracks/{id}` → 403 |
| `aCustomerCannotDeleteATrack` | `DELETE /api/tracks/{id}` → 403 |
| `anAdminCanUpload` | 201 |
| `anAdminCanEditMetadata` | 200 |
| `anAdminCanDeleteATrack` | 204 |
| `theWrongPasswordIsUnauthorizedNotForbidden` | 401 |
| `anUnknownUserIsUnauthorized` | 401 |
| `aRefusedUploadDoesNotReachTheLibrary` | after the 403, `trackRepository.count()` is unchanged |

The last one is the one that would catch a filter chain that returns 403 *after* the multipart body
has already been ingested. Cheap to write, and it is the difference between an authorization rule
and a cosmetic one.

Seed the fixture track as an admin in `@BeforeEach` so the customer tests have something to read.

- [ ] **Step 2: Role and entity**

`Role` is `enum { ADMIN, CUSTOMER }`. `AppUser` maps `app_user` with `@Enumerated(EnumType.STRING)`
on `role`. Per AGENTS.md, `ddl-auto` is `validate`, so the entity must agree with V3 exactly —
`text` columns map to `String` with no `columnDefinition` guessing, and a mismatch fails at startup
rather than silently.

- [ ] **Step 3: `DatabaseUserDetailsService`**

Implements `UserDetailsService`, looks up by lowercased username, and returns a Spring
`User` with authority `ROLE_ + role.name()` and the `enabled` flag honoured. Throw
`UsernameNotFoundException` when absent — Spring turns that into a 401, and it must not be
distinguishable from a wrong password.

- [ ] **Step 4: `PasswordEncoder` bean**

```java
@Bean
PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(10);
}
```

Strength 10 matches the seeded hashes. Bumping it means regenerating them.

- [ ] **Step 5: The matrix**

Add above `anyRequest().authenticated()`:

```java
.requestMatchers(HttpMethod.POST,   "/api/tracks").hasRole("ADMIN")
.requestMatchers(HttpMethod.PATCH,  "/api/tracks/*").hasRole("ADMIN")
.requestMatchers(HttpMethod.DELETE, "/api/tracks/*").hasRole("ADMIN")
```

`hasRole("ADMIN")` matches the authority `ROLE_ADMIN`; the prefix is added for you in the matcher
and must be present in the `UserDetails`. Getting this half-right yields a 403 for a genuine admin,
which is the most common way this configuration goes wrong.

Order matters: the first matching rule wins, so these must precede `anyRequest()`.

- [ ] **Step 6: Verify**

The matrix test passes. Then boot the application for real and try it by hand — `curl -u customer:customer`
against upload, `curl -u admin:admin` against the same. AGENTS.md is explicit that a passing suite is
not evidence the application runs.

---

## Task 5: A session for the audio element, and the CSRF that follows from it

The reason for this task is D16 above. Read it before writing code.

**Files:**
- Modify: `security/SecurityConfig.java`
- Create: `backend/src/test/java/com/kasi/musiclibrary/security/SessionAuthenticationTest.java`

- [ ] **Step 1: Write the failing test**

- `aBasicAuthenticatedRequestEstablishesASession` — the login call returns a `Set-Cookie` for
  `JSESSIONID`.
- `theSessionAloneAuthenticatesTheNextRequest` — replaying only that cookie, with no
  `Authorization` header, `GET /api/tracks/{id}/stream` returns 200. **This is the test that proves
  playback works for a signed-in user**, because the browser's audio element cannot send a header.
- `logoutInvalidatesTheSession` — after `POST /api/auth/logout`, the same cookie gets 401.

- [ ] **Step 2: Persist the security context from Basic**

Since Spring Security 6, `BasicAuthenticationFilter` defaults to
`RequestAttributeSecurityContextRepository` — the authentication lives for one request and is *not*
written to the session. That default is right for stateless APIs and wrong here, so say so:

```java
.securityContext(sc -> sc.securityContextRepository(new HttpSessionSecurityContextRepository()))
.httpBasic(basic -> basic.securityContextRepository(new HttpSessionSecurityContextRepository()))
.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
```

Use one shared `HttpSessionSecurityContextRepository` instance for both. If step 1's second test
still fails, this line is why — it is the single most likely thing to get wrong in this phase.

- [ ] **Step 3: Do not let the browser pop its own credential dialog**

A 401 carrying `WWW-Authenticate: Basic` makes the browser show its native login box over the SPA,
and there is no way to dismiss or log out of it. Supply an entry point that returns a bare 401:

```java
.exceptionHandling(ex -> ex.authenticationEntryPoint(
        (request, response, authException) ->
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized")))
```

Consequence to note in the code comment: `curl -u user:pass` still works (it sends the header
preemptively), but a browser typing the URL will get a blank 401 rather than a prompt. That is the
intent — the SPA owns the login experience.

Swagger UI's "Authorize" button needs the scheme declared in the spec, which Task 9 does; it does
not need the challenge header.

- [ ] **Step 4: CSRF comes back on**

A session cookie is an ambient credential: the browser attaches it to a cross-site form post
whether or not the user meant it. So CSRF protection is required, and the Angular-compatible
configuration is:

```java
.csrf(csrf -> csrf
        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
```

Two details, both of which produce a 403 on upload if missed:

- `withHttpOnlyFalse()` is what lets Angular's `HttpClient` read the `XSRF-TOKEN` cookie. Angular
  sends it back as `X-XSRF-TOKEN`, which is exactly what Spring expects — no client configuration
  is needed beyond that, but the names must not be changed on either side.
- The plain `CsrfTokenRequestAttributeHandler` opts out of the BREACH-protection deferred-token
  behaviour that otherwise means the cookie is not issued until something reads the token. Without
  it, the first mutating request after login fails and the second succeeds, which is a maddening
  bug to chase.

Confirm the seeded upload path end to end in the browser, not just in MockMvc: **file upload is the
one request that exercises multipart and CSRF together.**

- [ ] **Step 5: Verify**

Session tests pass. Boot the app, sign in, press play on a track, and drag the scrubber. If audio
plays from the start but seeking does nothing, the range request is being rejected — check the
session repository from step 2 first.

---

## Task 6: `/api/auth/me` and logout

**Files:**
- Create: `security/CurrentUser.java`, `api/AuthController.java`
- Create: `backend/src/test/java/com/kasi/musiclibrary/security/AuthControllerTest.java`

- [ ] **Step 1: Write the failing test**

- `meReturnsTheUsernameAndRole` for both an admin and a customer
- `meIsUnauthorizedWithoutCredentials` → 401
- `logoutEndsTheSession` → 204, and the cookie no longer authenticates

- [ ] **Step 2: The response record**

Per AGENTS.md, response shapes are explicit records, never serialized entities — and here that is
not a style rule: `AppUser` carries `passwordHash`, and returning it would put every hash on the
wire.

```java
public record CurrentUser(String username, String role) {}
```

- [ ] **Step 3: The controller**

`GET /api/auth/me` reads the `Authentication` and maps it. `POST /api/auth/logout` invalidates the
session and clears the context; Spring Security's `logout()` DSL can own this instead, but an
explicit endpoint keeps the SPA's contract in one file and returns 204 rather than a redirect.

The frontend calls `/api/auth/me` with Basic credentials to sign in — a successful response is what
mints the session — and calls it again with no credentials on page load to discover whether an
existing session is still valid. That is the whole login protocol.

- [ ] **Step 4: Verify**

Tests pass; `curl -u admin:admin localhost:8080/api/auth/me` returns `{"username":"admin","role":"ADMIN"}`.

---

## Task 7: Record who uploaded a track

Small, and it makes true a claim the README already makes — that the schema carries an owner
concept so multi-user is not a rewrite. Right now it does not.

**Files:**
- Create: `backend/src/main/resources/db/migration/V4__track_uploaded_by.sql`
- Modify: `catalog/Track.java`, `ingest/IngestService.java`, `api/TrackController.java`

- [ ] **Step 1: Migration**

```sql
alter table track
    add column uploaded_by uuid references app_user (id) on delete set null;
```

Nullable, and `on delete set null`: the six seeded tracks have no uploader, and deleting an admin
must not delete the library.

- [ ] **Step 2: Thread the uploader through ingest**

`IngestService.ingest(stream, filename, uploaderId)` with `uploaderId` nullable. `SeedRunner` passes
`null`. `TrackController` passes the authenticated user's id.

- [ ] **Step 3: Do not expose it yet**

`TrackResponse` stays as it is. Nothing reads `uploaded_by` in this phase; it is recorded so a later
phase does not need a migration against populated data — the same reasoning as `track_artist` in V1.

- [ ] **Step 4: Verify**

`uploadRecordsTheUploader` — an admin upload lands with `uploaded_by` set to that admin's id;
seeded tracks have it null.

---

## Task 8: Signing in, in the browser

**Files:**
- Create: `frontend/src/app/auth/auth.model.ts`, `auth/auth.service.ts`,
  `auth/login/login.ts|html|css`, `auth/unauthorized.interceptor.ts`
- Modify: `frontend/src/app/app.ts`, `app.config.ts`

- [ ] **Step 1: `AuthService`**

State is a signal, matching the rest of this codebase (`Welcome`, `TrackList`):

```ts
readonly user = signal<CurrentUser | null>(null);
readonly isAdmin = computed(() => this.user()?.role === 'ADMIN');
```

- `login(username, password)` → `GET /api/auth/me` with a one-off
  `Authorization: Basic ${btoa(...)}` header. On 200, store the returned user in the signal. **The
  password is never stored** — the session cookie carries everything afterwards, which is the whole
  reason for D16 and removes any temptation to keep a base64 password in `sessionStorage`.
- `refresh()` → the same call with no header, run once at startup, to discover an existing session.
- `logout()` → `POST /api/auth/logout`, then clear the signal.

- [ ] **Step 2: The interceptor**

A functional interceptor registered with `withInterceptors([unauthorizedInterceptor])`. On 401,
clear the user signal so the app falls back to the login view — this is what handles a session that
expired while the tab was open. On 403, rethrow with a readable message; do not clear the session,
because the user is legitimately signed in.

Note for the implementer: Angular's XSRF handling is on by default in `provideHttpClient` and needs
no configuration given the cookie and header names in Task 5.

- [ ] **Step 3: The login view**

Username, password, submit, and one error line. Reactive forms are already a dependency
(`@angular/forms` is in `package.json`). Keyboard-operable and labelled, matching the accessibility
default in Q25. On success, `App` moves to the welcome view.

Copy note: the welcome screen currently says "No account, no streaming service, no credentials to go
and register." That is now false and must be rewritten in Task 10 — it is the kind of stale sentence
that makes a reviewer distrust everything else on the page.

- [ ] **Step 4: Wire `App`**

The view signal becomes `'login' | 'welcome' | 'library'`. On construction, `refresh()` first, then
load stats only if authenticated — otherwise the stats call 401s on every cold load and the console
fills with errors before the user has done anything wrong.

- [ ] **Step 5: Verify**

Vitest: `AuthService` sets the user on a successful login and leaves it null on 401;
`unauthorizedInterceptor` clears the session on a 401 and does not on a 403.

---

## Task 9: A UI that matches the rules

The server is the boundary. This task is about not showing people buttons that will refuse them.

**Files:**
- Modify: `catalog/track-list/track-list.ts|html`, `welcome/welcome.html`
- Modify: `config/OpenApiConfig.java`
- Modify: `docs/api/openapi.json`, `docs/api/openapi.yaml`

- [ ] **Step 1: Gate the admin controls**

Inject `AuthService` into `TrackList` and wrap the upload input, the edit button, and the delete
confirmation in `@if (auth.isAdmin())`. A customer sees a list, a search box, and a play button.

- [ ] **Step 2: Show who is signed in**

Username, role, and a sign-out control in the header. A customer who cannot see an upload button
needs to be able to tell that this is because of who they are, not because the feature is missing.

- [ ] **Step 3: Handle the 403 that will still happen**

Two admins, one browser tab left open, a role changed underneath it — the button is visible and the
request is refused. Surface the message in the existing error signal rather than swallowing it.

- [ ] **Step 4: Declare the scheme in the OpenAPI spec**

```java
.components(new Components().addSecuritySchemes("basicAuth",
        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("basic")))
.addSecurityItem(new SecurityRequirement().addList("basicAuth"))
```

This is what makes Swagger UI's Authorize button work, so UC-7 remains true for an endpoint that
now needs credentials.

- [ ] **Step 5: Regenerate the committed spec**

Per AGENTS.md, after changing any controller:

```bash
curl -s localhost:8080/v3/api-docs | python3 -m json.tool > docs/api/openapi.json
curl -s localhost:8080/v3/api-docs.yaml > docs/api/openapi.yaml
```

- [ ] **Step 6: Verify by hand, as both users**

Sign in as `customer`: no upload control, no edit, no delete, play works, seeking works. Sign in as
`admin`: everything works. Sign out and confirm the library is not reachable.

---

## Task 10: Make the documents true again

Several documents currently assert there is no authentication. Leaving them is worse than never
having written them.

- [ ] **`README.md`** — remove "No account" from the opening; delete "Single user, no authentication"
  from Deliberate limitations and replace it with what is now true and what is still missing (no
  signup, no TLS, no password reset). Add the demo credentials to the TL;DR table so the reviewer
  can get in. State plainly that Basic over HTTP sends the password on every request and is a local
  exercise choice.
- [ ] **`docs/QUESTIONS.md`** — Q9's default said single user, no auth. Add an entry under "Answers
  received" recording that two roles with Basic auth were requested on 2026-09-07, and leave the
  original text as the record of why it was defaulted the other way.
- [ ] **`docs/USE-CASES.md`** — the actor diagram has one human actor. Split it into Admin and
  Customer, add UC-9 (sign in) and UC-10 (a customer is refused a write), each naming its test.
- [ ] **`docs/DECISIONS.md`** — add D14, D15, D16 from this plan.
- [ ] **`docs/ARCHITECTURE.md`** — add the filter chain to diagram 3, and a note on diagram 8 that
  the stream request authenticates by session cookie and why.
- [ ] **`AGENTS.md`** — add to Conventions: *authorization rules live in `SecurityConfig`, not on
  domain services*. Add to Things that will bite you: *MockMvc skips the security filter chain
  unless `.apply(springSecurity())` is used; extend `SecuredMockMvcTest`.*
- [ ] **`docs/plans/ROADMAP.md`** — mark this phase built.

---

## Phase exit criteria

- [ ] `docker compose down -v && docker compose up --build` on a clean clone reaches a login screen.
- [ ] `admin` / `admin` can upload, edit, and delete. `customer` / `customer` can do none of those
      and gets a 403, not a 401.
- [ ] A customer can play a track **and drag the scrubber to the middle of it**. This is the one
      that silently breaks; test it by hand every time.
- [ ] An anonymous request to any `/api/**` endpoint except the docs returns 401.
- [ ] No browser-native credential dialog appears at any point.
- [ ] `./gradlew test` and `yarn test --run` are green, and commenting out `.apply(springSecurity())`
      turns the authorization tests red.
- [ ] `docs/api/openapi.json` and `.yaml` are regenerated and committed.
- [ ] No document in the repository still claims the app has no authentication.
