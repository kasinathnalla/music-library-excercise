# Phase 7: User Journey

> **For agentic workers:** implement this plan task by task, in order. Steps use checkbox (`- [ ]`)
> syntax for tracking. Task 4's failing test is the one to watch closely -- it is checking that an
> event fires exactly once per real sign-in, not once per page load, and that property is the whole
> reason this design works.

**Goal:** every signed-in user can open **My Activity** and see their own history: each time they
signed in (when, and from what browser and IP), every track they pressed play on during that
session and when, and when they signed out. Nobody sees anyone else's.

**Scope, as asked for:** self-view only. There is no admin console for inspecting another user's
journey in this phase -- see [What this is not](#what-this-is-not).

**Estimate:** ~0.5 day.

---

## The shape of the data

One journey entry is a **session**: a login, the plays that happened during it, and (once it
happens) a logout. Rendered newest first:

```
Signed in Sep 8, 4:12 PM from Chrome on macOS · 192.168.1.42
  4:13 PM  Prelude in C
  4:15 PM  Interlude No. 2
  4:19 PM  Reference Tone
Signed out 4:24 PM

Signed in Sep 7, 9:02 AM from Safari on iOS · 10.0.0.15
  9:03 AM  Prelude in C
Still signed in
```

A session with no logout yet (the current one, or one that never closed cleanly) shows as still
open rather than guessing at an end time.

---

## What "machine" means here

The application is a browser talking to a server. It cannot know a device's hostname, and it
should not pretend to. What it *can* know, on every request, is the caller's IP address and the
`User-Agent` header the browser sends. "Machine" in this phase means those two things, with the
User-Agent parsed into something readable -- "Chrome on macOS" rather than the raw string.

That parse is best-effort, not a claim of precision. It is confined to one class
(`UserAgentSummarizer`) for the same reason `AudioTagReader` confines jaudiotagger: a single seam
where a messy, adversarial input format is handled, so getting it wrong or replacing it later
touches one file. No third-party UA-parsing library is pulled in for this -- the handful of
browsers and operating systems worth naming is small enough to hand-write, and a library here
would be materially more code than the problem justifies.

---

## The key mechanism: a login event that only fires once

The hard part of this feature is not storage, it is knowing **when a login actually happens**.
The frontend calls `GET /api/auth/me` for two different reasons -- signing in with Basic
credentials, and checking on page load whether an existing session is still valid (see D16 in
`docs/DECISIONS.md`) -- and both hit the same endpoint. Recording a "login" inside
`AuthController.me()` would double- and triple-count: once for the real sign-in, and again every
time the page is reloaded while already signed in.

Spring Security already draws this line, for a different reason. `AuthenticationSuccessEvent` is
published only when a request's credentials are actually checked against the
`AuthenticationManager` -- which is what happens on a Basic-authenticated request. A request that
is authenticated purely by an existing session's stored `SecurityContext` (every page reload,
every `/api/tracks` call, everything the audio element fetches) never touches the
`AuthenticationManager` and never re-publishes that event. It fires exactly once per real sign-in,
for free, because that is what it already means to Spring Security. This plan listens for it
instead of instrumenting `/me`.

The matching problem on the way out -- knowing when a session ends -- is solved by
`HttpSessionDestroyedEvent`, which fires on an explicit `POST /api/auth/logout` and on an idle
session timing out, without needing to special-case either.

---

## What this is not

- **No admin visibility into other users' journeys.** Self-view only, per the scope chosen for
  this phase. The data model does not prevent adding that later (`user_session.user_id` already
  identifies whose row is whose); this phase just does not build the screen or the endpoint for it.
- **No "listened to" for anything but the web UI's play button.** A play is recorded by an
  explicit call the frontend makes when the play button is pressed (see D-explicit-play below),
  not inferred from requests to the streaming endpoint. Playing a track's stream URL directly --
  `curl`, a future non-browser client -- will not appear in anyone's journey. Documented, not
  silently gapped.
- **No retention or purge policy.** IP addresses and play history accumulate with no expiry and no
  way for a user to clear their own history. Reasonable for a local exercise; a real deployment
  storing IP addresses would need to say how long it keeps them.
- **No `X-Forwarded-For` handling.** `request.getRemoteAddr()` is correct as long as the browser
  talks to the app directly, which is the whole deployment in this exercise. Behind a reverse
  proxy this would need to trust a forwarded-for header from a known proxy, which is a decision
  with real security consequences (spoofing) and is out of scope until there is an actual proxy.

---

## Design decisions taken in this plan

Recorded here for `docs/DECISIONS.md` when the phase lands (Task 10).

### D19. The login event is `AuthenticationSuccessEvent`, not the `/me` endpoint

Covered in full above. The one-line version: this event already means "credentials were actually
checked," which is exactly what "the user logged in" means, and instrumenting it is more reliable
than trying to distinguish a real sign-in from a session-validity check inside the controller.

### D20. A play is an explicit action, not something inferred from streaming

**Alternative considered:** treat every request to `GET /api/tracks/{id}/stream` as a "listen."

Rejected because the browser's `<audio>` element does not make one request per playback. An
initial load, buffering ahead, and every drag of the scrubber each produce their own `Range`
request against the same URL (see `docs/ARCHITECTURE.md` §8). Recording all of them would flood a
session's journey with dozens of entries for one song, and untangling "a fresh play" from "a seek
inside a play already counted" from byte-range heuristics alone is fragile -- and would put
journey logic inside `StreamController`, which this codebase already treats as a narrow,
security-sensitive boundary (see the `AudioFileStore.resolve()` path-traversal guard it's built
around).

An explicit `POST /api/tracks/{id}/plays`, called once when the play button is pressed, matches
how every other consequential action in this app already works: edits and deletes are explicit
calls, not inferred from GET traffic. The cost is the limitation stated above -- a track played by
any other means goes unrecorded.

### D21. A deleted track's plays stay in the journey; the track's row does not have to

`track_play.track_id` is nullable and set null if the track is later deleted (`on delete set
null`), but the track's title is copied onto the play row *at the moment the play is recorded*.
Deleting a track already cleans up its album and artist when they're left empty (D9); this is the
same instinct applied here -- a customer's own activity list should keep saying "Prelude in C"
rather than going blank, throwing, or silently vanishing an entry because an admin removed the
track from the library afterward. The alternative (cascading the play rows away with the track)
would rewrite someone's history because of an action they had no part in.

---

## File structure after this phase

```
backend/src/main/java/com/kasi/musiclibrary/
  journey/                          New package. Recording and reading back the journey.
    UserSession.java                Entity: one signed-in session.
    UserSessionRepository.java
    TrackPlay.java                  Entity: one recorded play within a session.
    TrackPlayRepository.java
    JourneyService.java             recordLogin / recordLogout / recordPlay / journeyFor(userId)
    UserAgentSummarizer.java        The one place a User-Agent string gets parsed.
    LoginTrackingListener.java      ApplicationListener<AuthenticationSuccessEvent>
    SessionEndListener.java         ApplicationListener<HttpSessionDestroyedEvent>
  api/
    JourneyController.java         GET /api/journey, POST /api/tracks/{id}/plays
    JourneySessionResponse.java
    TrackPlayResponse.java
  security/
    SecurityConfig.java             MODIFIED: registers HttpSessionEventPublisher
backend/src/main/resources/db/migration/
  V7__user_journey.sql
backend/src/test/java/com/kasi/musiclibrary/
  journey/
    UserAgentSummarizerTest.java    Plain unit test. No Spring, no database.
    LoginTrackingTest.java          Proves the event fires once per real login, not per refresh.
    JourneyControllerTest.java      Recording and reading back; the cross-user leak test lives here.
frontend/src/app/
  journey/
    journey.model.ts
    journey.service.ts
    journey-view/
      journey-view.ts, .html, .css
  catalog/track-list/track-list.ts  MODIFIED: play() also records the play
  app.ts                            MODIFIED: "My Activity" entry point, new view state
```

---

## Task 1: The schema

**Files:** `backend/src/main/resources/db/migration/V7__user_journey.sql`

- [ ] **Step 1: Write the migration**

```sql
-- One row per session that successfully signed in. login_at is set the moment it's created;
-- logout_at stays null until the session actually ends (explicit sign-out, or idle timeout).
create table user_session (
    id         uuid primary key default gen_random_uuid(),
    user_id    uuid        not null references app_user (id) on delete cascade,
    login_at   timestamptz not null default now(),
    logout_at  timestamptz,
    ip_address text        not null,
    -- The raw header, kept alongside the parsed summary so a future, better parser can be
    -- re-run against history without having lost the source string.
    user_agent      text,
    device_summary  text
);

create index user_session_user_idx on user_session (user_id, login_at desc);

-- One row per distinct "pressed play" moment within a session. track_id is nullable and
-- cleared if the track is later deleted; track_title_at_play is a snapshot taken at insert
-- time specifically so that deletion elsewhere doesn't blank or break someone's own history.
create table track_play (
    id                   uuid primary key default gen_random_uuid(),
    user_session_id      uuid        not null references user_session (id) on delete cascade,
    track_id             uuid        references track (id) on delete set null,
    track_title_at_play  text        not null,
    played_at            timestamptz not null default now()
);

create index track_play_session_idx on track_play (user_session_id, played_at);
```

- [ ] **Step 2: Verify**

`docker compose down -v && docker compose up -d db`, boot the backend, confirm Flyway applies V7
cleanly on top of V1-V5.

---

## Task 2: Turning a User-Agent string into something readable

Pure logic, no Spring, no database -- write and verify this in isolation before it is wired to
anything.

**Files:**
- Create: `journey/UserAgentSummarizer.java`
- Create: `backend/src/test/java/com/kasi/musiclibrary/journey/UserAgentSummarizerTest.java`

- [ ] **Step 1: Write the failing test**

Use real captured User-Agent strings, not invented ones -- the actual header text from a current
Chrome, Firefox, Safari, and Edge, on both macOS and Windows, plus one from mobile Safari on iOS
and one from Chrome on Android. Assert the friendly summaries: `"Chrome on macOS"`, `"Safari on
iOS"`, and so on. Also assert the fallback: an empty string, a null, and a string that matches
nothing recognizable all produce `"Unknown browser"` rather than throwing.

**The ordering trap:** Chrome's User-Agent contains the substring `"Safari"`, and Edge's contains
both `"Chrome"` and `"Safari"`. A naive `contains("Safari")` check misidentifies both. Check for
`"Edg/"` before `"Chrome"`, and `"Chrome"` before `"Safari"` -- most specific token first, in that
order, every time.

- [ ] **Step 2: Implement**

```java
public final class UserAgentSummarizer {

    private UserAgentSummarizer() {
    }

    public static String summarize(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "Unknown browser";
        }
        String browser = browserOf(userAgent);
        String os = osOf(userAgent);
        if (browser == null && os == null) {
            return "Unknown browser";
        }
        if (os == null) {
            return browser;
        }
        return browser + " on " + os;
    }

    private static String browserOf(String ua) {
        if (ua.contains("Edg/")) return "Edge";
        if (ua.contains("Chrome/")) return "Chrome";
        if (ua.contains("Firefox/")) return "Firefox";
        if (ua.contains("Safari/")) return "Safari"; // after Chrome/Edge: both also contain this
        return null;
    }

    private static String osOf(String ua) {
        if (ua.contains("iPhone") || ua.contains("iPad")) return "iOS";
        if (ua.contains("Android")) return "Android";
        if (ua.contains("Mac OS X")) return "macOS";
        if (ua.contains("Windows")) return "Windows";
        if (ua.contains("Linux")) return "Linux";
        return null;
    }
}
```

- [ ] **Step 3: Verify**

Test passes. This class needs nothing else in this plan to work, so it's the cheapest possible
place to catch an ordering mistake before it's buried under Spring wiring.

---

## Task 3: Entities and repositories

**Files:**
- Create: `journey/UserSession.java`, `journey/UserSessionRepository.java`
- Create: `journey/TrackPlay.java`, `journey/TrackPlayRepository.java`

- [ ] **Step 1: `UserSession`**

`userId` is a raw `UUID`, not a `@ManyToOne` -- matching `Track.uploadedBy`'s precedent: open-in-view
is off, and an association here is one more lazy proxy a response could touch after the session
closes. Per AGENTS.md, `ddl-auto` is `validate`, so every column here must agree with V7 exactly.

- [ ] **Step 2: `TrackPlay`**

Same reasoning: `trackId` is a raw nullable `UUID`, not an association, both because of
open-in-view and because the whole point of D21 is that this row must go on making sense after
the track it points to is gone.

- [ ] **Step 3: Repositories**

`UserSessionRepository extends JpaRepository<UserSession, UUID>` needs
`findByUserIdOrderByLoginAtDesc(UUID userId)` for Task 7. `TrackPlayRepository` needs
`findByUserSessionIdInOrderByPlayedAtAsc(Collection<UUID> sessionIds)` -- one query for every play
across every session shown, rather than one query per session.

- [ ] **Step 4: Verify**

Compiles; no test yet, there's no behavior here to test until Task 4 gives these a caller.

---

## Task 4: Recording a login exactly once

**Files:**
- Create: `journey/LoginTrackingListener.java`
- Create/extend: `journey/JourneyService.java` (just `recordLogin` for now)
- Create: `backend/src/test/java/com/kasi/musiclibrary/journey/LoginTrackingTest.java`

- [ ] **Step 1: Write the failing test**

`LoginTrackingTest extends SecuredMockMvcTest`:

- `signingInCreatesASessionRecord` -- `GET /api/auth/me` with `httpBasic("customer",
  "customer")`; assert `userSessionRepository.count()` increased by one, and the new row's
  `userId` matches the customer's id, `ipAddress` is non-blank, and `deviceSummary` is
  `UserAgentSummarizer.summarize(...)` of whatever MockMvc sent as the User-Agent (set one
  explicitly with `.header("User-Agent", "...")` so the assertion isn't guessing at MockMvc's
  default).
- `refreshingAnExistingSessionDoesNotCreateASecondRecord` -- sign in once (Basic), capture the
  count, then reuse the returned `MockHttpSession` for a second `GET /api/auth/me` with **no**
  credentials, exactly like the frontend's `refresh()` call on page load. Assert the count is
  unchanged. **This is the test that matters most in this task** -- if it fails, the mechanism
  in D19 is not actually working and every login is about to be recorded multiple times.
- `twoSeparateSignInsCreateTwoRecords` -- two independent `MockHttpSession`s (i.e., two separate
  requests with no shared session), both `httpBasic("customer", "customer")`. Assert two rows.

- [ ] **Step 2: `JourneyService.recordLogin`**

```java
@Transactional
public UUID recordLogin(UUID userId, String ipAddress, String userAgent) {
    UserSession session = new UserSession(userId, ipAddress, userAgent,
            UserAgentSummarizer.summarize(userAgent));
    return sessions.save(session).getId();
}
```

- [ ] **Step 3: The listener**

```java
@Component
public class LoginTrackingListener implements ApplicationListener<AuthenticationSuccessEvent> {

    private static final String SESSION_ATTRIBUTE = "journeySessionId";

    private final JourneyService journey;

    public LoginTrackingListener(JourneyService journey) {
        this.journey = journey;
    }

    @Override
    public void onApplicationEvent(AuthenticationSuccessEvent event) {
        if (!(event.getAuthentication().getPrincipal() instanceof AppUserPrincipal principal)) {
            return; // not a real account -- nothing in this app authenticates any other way
        }
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return; // defensive: there is always a request in this app's real traffic
        }
        HttpServletRequest request = attrs.getRequest();
        UUID sessionRecordId = journey.recordLogin(
                principal.id(), request.getRemoteAddr(), request.getHeader("User-Agent"));

        // Written to the HttpSession now, read back by SessionEndListener in Task 5. This is
        // the only link between the two: there is no other way to know, at destruction time,
        // which user_session row this HttpSession corresponds to.
        request.getSession(true).setAttribute(SESSION_ATTRIBUTE, sessionRecordId);
    }
}
```

- [ ] **Step 4: Verify, and the contingency if the event never fires**

Run the test. If `signingInCreatesASessionRecord` fails with zero rows rather than the assertion
failure you expected, `AuthenticationSuccessEvent` is not being published at all -- check for an
`AuthenticationEventPublisher` bean. Modern Spring Security registers
`DefaultAuthenticationEventPublisher` automatically as part of `@EnableWebSecurity`, but if this
turns out not to be the case in this Boot version, add explicitly to `SecurityConfig`:

```java
@Bean
AuthenticationEventPublisher authenticationEventPublisher(ApplicationEventPublisher publisher) {
    return new DefaultAuthenticationEventPublisher(publisher);
}
```

Per AGENTS.md: run the application for real and sign in by hand, then check the table with
`psql`, before trusting the test alone.

---

## Task 5: Recording a logout

**Files:**
- Modify: `security/SecurityConfig.java`
- Create: `journey/SessionEndListener.java`
- Extend: `journey/JourneyService.java` (`recordLogout`)
- Extend: `LoginTrackingTest.java` or a new `LogoutTrackingTest.java`

- [ ] **Step 1: Write the failing test**

`signingOutClosesTheSessionRecord` -- sign in, capture the `MockHttpSession`, `POST
/api/auth/logout` with it, then look up the `user_session` row by the id that was stored in the
session attribute (or, simpler: assert the customer's most recent `user_session` row now has a
non-null `logoutAt`).

- [ ] **Step 2: Register `HttpSessionEventPublisher`**

Without this, the servlet container never tells Spring that a session was destroyed, and
`HttpSessionDestroyedEvent` never fires -- explicit logout included, not just idle timeout. Add to
`SecurityConfig`:

```java
@Bean
ServletListenerRegistrationBean<HttpSessionEventPublisher> httpSessionEventPublisher() {
    return new ServletListenerRegistrationBean<>(new HttpSessionEventPublisher());
}
```

- [ ] **Step 3: `JourneyService.recordLogout`**

```java
@Transactional
public void recordLogout(UUID sessionRecordId) {
    sessions.findById(sessionRecordId).ifPresent(s -> s.setLogoutAt(Instant.now()));
}
```

A missing id is not an error here -- a session created before this feature shipped, or one whose
attribute was never set for some other reason, should not make sign-out fail for the person using
it.

- [ ] **Step 4: The listener**

```java
@Component
public class SessionEndListener implements ApplicationListener<HttpSessionDestroyedEvent> {

    private final JourneyService journey;

    public SessionEndListener(JourneyService journey) {
        this.journey = journey;
    }

    @Override
    public void onApplicationEvent(HttpSessionDestroyedEvent event) {
        Object id = event.getSession().getAttribute("journeySessionId");
        if (id instanceof UUID sessionRecordId) {
            journey.recordLogout(sessionRecordId);
        }
    }
}
```

The attribute name is duplicated as a literal between this class and `LoginTrackingListener`
rather than shared as a constant across two files that otherwise have no reason to import each
other; if that starts to feel fragile, promote it to a constant on `JourneyService` instead.

- [ ] **Step 5: Verify**

Test passes. Boot the app, sign in, sign out, and check `user_session.logout_at` with `psql`
directly -- `HttpSessionDestroyedEvent` behavior is one of the easier things to get subtly wrong
(it fires on invalidate, but the timing relative to idle timeout is server-controlled), so confirm
it by hand once.

---

## Task 6: Recording a play

**Files:**
- Create: `api/JourneyController.java` (just the POST for now)
- Create: `api/TrackPlayResponse.java`
- Extend: `journey/JourneyService.java` (`recordPlay`)
- Create: `backend/src/test/java/com/kasi/musiclibrary/journey/JourneyControllerTest.java`

- [ ] **Step 1: Write the failing test**

- `pressingPlayRecordsIt` -- sign in as customer, `POST /api/tracks/{id}/plays` for a seeded
  track, expect 204 (or 201 if the response carries the play back -- pick one and be consistent
  with the rest of the API's convention for a side-effecting POST with no useful body to return;
  204 matches `DELETE`'s precedent here since there's nothing to hand back).
- `playingAnUnknownTrackIsNotFound` -- a random UUID, 404.
- `aPlayOnATrackThatIsLaterDeletedKeepsItsTitle` -- record a play, then `DELETE` the track as an
  admin, then confirm (via the repository directly, since Task 7 doesn't exist yet in this task)
  that the `track_play` row still has its `trackTitleAtPlay` and now a null `trackId`. This is
  D21, proven rather than asserted in a comment.
- `recordingAPlayWithNoJourneySessionDoesNotFail` -- construct a request authenticated via
  `@WithMockUser` (which, per the existing convention in `TrackControllerTest` and friends, has no
  real `HttpSession` attribute set by `LoginTrackingListener` because it never went through Basic
  auth) and confirm the endpoint still returns success rather than 500. A play that can't be
  linked to a session is silently dropped, not a reason to break whatever screen called this.

- [ ] **Step 2: `JourneyService.recordPlay`**

```java
@Transactional
public void recordPlay(UUID sessionRecordId, UUID trackId, String trackTitle) {
    if (sessionRecordId == null) {
        return; // see recordingAPlayWithNoJourneySessionDoesNotFail
    }
    plays.save(new TrackPlay(sessionRecordId, trackId, trackTitle));
}
```

- [ ] **Step 3: The endpoint**

```java
@PostMapping("/api/tracks/{id}/plays")
public ResponseEntity<Void> recordPlay(@PathVariable UUID id, HttpServletRequest request) {
    Track track = tracks.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such track"));
    HttpSession session = request.getSession(false);
    UUID sessionRecordId = session == null ? null
            : (UUID) session.getAttribute("journeySessionId");
    journey.recordPlay(sessionRecordId, track.getId(), track.getTitle());
    return ResponseEntity.noContent().build();
}
```

No new entry needed in `SecurityConfig`'s matcher list -- this falls under `anyRequest().authenticated()`,
reachable by admin and customer alike, matching who can already reach the stream endpoint itself.

- [ ] **Step 4: Verify**

Tests pass. By hand: play a track in the browser, then delete it as an admin, and confirm the
server doesn't error on either action.

---

## Task 7: Reading back your own journey

**Files:**
- Extend: `api/JourneyController.java` (add the GET)
- Create: `api/JourneySessionResponse.java`
- Extend: `journey/JourneyService.java` (`journeyFor`)
- Extend: `journey/UserSessionRepository.java`, `journey/TrackPlayRepository.java`

- [ ] **Step 1: Write the failing test**

- `myJourneyListsMySessionsNewestFirst` -- sign in as customer, play a track, sign out, sign back
  in; assert two sessions come back, most recent first, and the first session's plays list has
  the one track with a timestamp.
- `myJourneyNeverIncludesAnotherUsersSessions` -- **the test that matters most in this task.** Sign
  in and act as `admin`, sign in and act as `customer`, then call `GET /api/journey` as customer
  and assert every session returned belongs to the customer's own id -- none of the admin's. This
  is exactly the kind of thing a forgotten `WHERE` clause gets wrong, and it deserves the same
  seriousness `AuthorizationMatrixTest` gave the admin/customer boundary.
- `anOpenSessionHasNoLogoutTime` -- the current session (no logout yet) comes back with
  `logoutAt: null`, not some placeholder.

- [ ] **Step 2: `JourneyService.journeyFor`**

```java
public List<UserSession> journeyFor(UUID userId) {
    return sessions.findByUserIdOrderByLoginAtDesc(userId);
}

public Map<UUID, List<TrackPlay>> playsFor(List<UUID> sessionIds) {
    return plays.findByUserSessionIdInOrderByPlayedAtAsc(sessionIds).stream()
            .collect(Collectors.groupingBy(TrackPlay::getUserSessionId));
}
```

Two queries total for the whole screen (one for sessions, one for every play across all of
them), not one query per session -- the kind of thing that's easy to get right from the start and
tedious to fix once a view is iterating and querying inside a loop.

- [ ] **Step 3: The endpoint**

```java
@GetMapping("/api/journey")
public List<JourneySessionResponse> myJourney(Authentication authentication) {
    UUID userId = ((AppUserPrincipal) authentication.getPrincipal()).id();
    List<UserSession> sessions = journey.journeyFor(userId);
    Map<UUID, List<TrackPlay>> plays = journey.playsFor(
            sessions.stream().map(UserSession::getId).toList());
    return sessions.stream()
            .map(s -> JourneySessionResponse.from(s, plays.getOrDefault(s.getId(), List.of())))
            .toList();
}
```

There is deliberately no `userId` parameter anywhere on this endpoint. Exactly like registration
(D17) can't be asked to create an admin because the request shape has no field for one, this
endpoint can't be asked for someone else's journey because there is no parameter that names a
user -- "whose journey" comes only from who is signed in.

- [ ] **Step 4: Verify**

Tests pass. Boot the app, generate a bit of real history by hand (sign in, play a few things,
sign out, sign back in), and hit `GET /api/journey` with `curl -u customer:customer` to eyeball
the shape before building the screen that renders it.

---

## Task 8: Frontend plumbing

**Files:**
- Create: `frontend/src/app/journey/journey.model.ts`, `journey.service.ts`
- Modify: `frontend/src/app/catalog/track-list/track-list.ts`

- [ ] **Step 1: Models**

```ts
export interface TrackPlayEntry {
  trackTitle: string;
  playedAt: string;
}

export interface JourneySession {
  loginAt: string;
  logoutAt: string | null;
  deviceSummary: string;
  ipAddress: string;
  plays: TrackPlayEntry[];
}
```

- [ ] **Step 2: `JourneyService`**

```ts
@Injectable({ providedIn: 'root' })
export class JourneyService {
  private readonly http = inject(HttpClient);

  myJourney(): Observable<JourneySession[]> {
    return this.http.get<JourneySession[]>('/api/journey');
  }

  /**
   * Fire-and-forget by design: a failed play log must never interrupt or delay playback, which
   * has already started by the time this is called. Errors are swallowed, not surfaced.
   */
  recordPlay(trackId: string): void {
    this.http.post(`/api/tracks/${trackId}/plays`, {}).subscribe({ error: () => {} });
  }
}
```

- [ ] **Step 3: Wire it into `play()`**

In `track-list.ts`:

```ts
protected play(track: Track): void {
  this.nowPlayingId.set(track.id);
  this.nowPlayingTitle.set(track.title);
  this.nowPlayingUrl.set(this.trackService.streamUrl(track.id));
  this.journeyService.recordPlay(track.id);
}
```

- [ ] **Step 4: Verify**

Vitest: pressing play calls both `streamUrl` and `recordPlay`; a `recordPlay` that errors does not
throw out of `play()` and does not prevent `nowPlayingUrl` from being set.

---

## Task 9: The "My Activity" view

**Files:**
- Create: `frontend/src/app/journey/journey-view/journey-view.ts`, `.html`, `.css`
- Modify: `frontend/src/app/app.ts`

- [ ] **Step 1: The component**

Loads `myJourney()` on construction into a signal; renders newest-first, each session as a card
with its login line, its plays as a nested list, and its logout line or "Still signed in." Empty
state: "Nothing here yet -- play something and it'll show up." Loading and error states match the
pattern already established in `TrackList`.

- [ ] **Step 2: Wire it into `App`**

Add `'journey'` to the view union type. Add a button in the account bar next to Sign out: "My
Activity." Both are visible to admin and customer alike -- an admin's own plays and sign-ins are
just as real as a customer's, and this phase draws no distinction between them.

- [ ] **Step 3: Verify**

By hand: sign in, play two tracks, open My Activity, confirm both appear with sensible
timestamps and the current session shows "Still signed in." Sign out and back in; confirm the
first session now shows a sign-out time and a new one has appeared above it.

---

## Task 10: Make the documents match

- [ ] **`docs/DECISIONS.md`** -- add D19, D20, D21 from this plan.
- [ ] **`docs/USE-CASES.md`** -- add a use case for viewing your own activity, naming the tests
      from Task 7 the way every other use case in that document names its tests.
- [ ] **`docs/ARCHITECTURE.md`** -- a short sequence diagram for login-to-session-record
      (mirroring the existing style of diagrams 5 and 7), showing where `AuthenticationSuccessEvent`
      sits relative to the filter chain.
- [ ] **`AGENTS.md`** -- add to Things that will bite you: *`AuthenticationSuccessEvent` fires once
      per real sign-in, not once per authenticated request -- do not move journey-recording logic
      into `AuthController.me()`, which is called on every page load to check an existing session.*
- [ ] **`README.md`** -- one line under what the app does: everyone can see their own sign-in and
      listening history under My Activity.
- [ ] **`docs/plans/ROADMAP.md`** -- mark this phase built.

---

## Phase exit criteria

- [ ] Signing in creates exactly one `user_session` row -- reloading the page afterward, or any
      other request riding the existing session cookie, creates zero more.
- [ ] Signing out (and, separately, an idle session timing out) sets that row's `logout_at`.
- [ ] Pressing play on a track records one `track_play` row tied to the current session, with a
      snapshotted title.
- [ ] Deleting a track leaves every play that referenced it intact, with its title still showing
      and `track_id` now null.
- [ ] `GET /api/journey` never returns another user's sessions, under any account -- proven by a
      test, not just by inspection.
- [ ] `My Activity` renders sessions newest-first with their plays nested underneath, and an
      open session reads as still open rather than showing a blank or invented end time.
- [ ] `./gradlew test` and `yarn test --run` are green.
- [ ] No document in the repository still describes the app as having no activity history.
