# Clarifying Questions

The brief describes a music library management app with playback, playlists, and metadata
editing, built as a web app that "leverages an API," and it has to build and run on a machine
that is not mine.

That is enough to start, but several readings of it lead to materially different projects. Below
are the questions that would change what I build, each with the reason it matters and the
default I will proceed on if I do not hear back. Nothing here blocks me. The defaults are chosen
so that work continues and so that a correction from you is cheap to absorb early.

A tag of `[BLOCKING]` does not mean I am stopping. It means the default is a guess I cannot
cheaply reverse later, so a wrong one costs real rework. If you only answer some of these, answer
those. Everything tagged `[ASSUMED]` is a call I have made and can change late without much pain.

## Answers received

The following were confirmed on 2026-09-07, and the plan in `docs/plans/` is built on them. The
questions below are kept as written, because the reasoning behind each default is still the record of
why the system looks the way it does.

- **Q1, Q2, Q7 (third-party APIs):** No third party. I build the API. Where a third-party integration
  would be illustrative, it is mocked behind an interface rather than actually called.
- **Q18, Q19, Q28 (build and delivery):** Docker. `docker compose up` is the entry point.
- **Sequencing:** Get an MVP working end to end first, then iterate on the harder features. This is
  why basic playback moved from Phase 2 into the Phase 1 MVP, and why queue management, playlists,
  and bulk metadata editing come after.

Everything still marked `[BLOCKING]` below is answered by the default stated with it.

## The short version

| # | Question | Default if unanswered |
|---|---|---|
| Q1 | Does "leverages an API" mean consuming a third-party service, building my own backend, or both? | I build my own backend; third-party is optional enrichment |
| Q2 | If third-party, which one, and who supplies credentials? | No credentials needed to run |
| Q4 | Does audio actually play, or is playback simulated? | Real audio, real files |
| Q5 | Where do audio files come from? | Bundled public-domain samples plus user upload |
| Q8 | Which of the four capabilities are must-have versus nice-to-have? | All four, with library and metadata editing built to depth |
| Q10 | What is the time budget for this? | Roughly two focused days |
| Q13 | Does metadata editing write back to the audio files, or only to the app database? | App database only, with write-back designed for but off |
| Q18 | What does "builds and runs" mean concretely? | `docker compose up`, plus a documented native path |
| Q23 | What library size should this be designed for? | Tens of thousands, demonstrated with a seed script |
| Q29 | How much AI-assisted development is expected? | Substantial, reviewed, and described honestly |

---

## 1. What "leverages an API" means

**Q1. Does "leverages an API" mean the app consumes a third-party music service, that I build a backend API the web client talks to, or both?** `[BLOCKING]`

*Why it matters:* These are three different projects. Consuming Spotify or Tidal makes catalog and
playback someone else's problem but puts the reproducibility requirement at risk, since a reviewer
would need their own developer credentials. Building my own API makes the system self-contained and
reproducible but means I own catalog, storage, and streaming. Both is the largest scope and the
least likely to be finishable well.

*My default:* I build a backend API (REST, JSON) that owns the library, and the web client consumes
only that. Any third-party service is optional enrichment behind an interface, disabled by default,
so the app builds and runs with no external accounts.

**Q2. If a third-party service is expected, which one, and will credentials be provided or is the reviewer expected to supply their own?** `[BLOCKING if Q1 points at third-party]`

*Why it matters:* This is the direct collision between "leverages an API" and "must be replicable on
a different machine." If the reviewer has to register a Spotify app to run my submission, the build
is not actually reproducible, and I would rather solve that on purpose than discover it at
submission time.

*My default:* No third-party credentials required to run. If I integrate one, it degrades gracefully
to a fully working local mode when keys are absent.

**Q3. Is the backend expected to be a real service with persistence, or is a client-side app with a mocked API layer acceptable?** `[ASSUMED]`

*Why it matters:* Determines whether I am being evaluated on system design or on frontend craft. It
also sets the ceiling on how much of the time budget goes to infrastructure versus the user-facing
experience.

*My default:* A real backend with a real datastore, chosen so it runs with no external services
(SQLite by default, Postgres via Compose if you would rather see that).

## 2. Where the music comes from

**Q4. Does the app need to actually play audio, or is a simulated player acceptable?** `[BLOCKING]`

*Why it matters:* Real playback pulls in streaming, range requests, seek, format support, and
buffering behavior. A simulated transport with a moving progress bar takes an afternoon. If you want
to see real audio work, I will spend the budget differently.

*My default:* Real audio. Streaming from my own backend over HTTP range requests, played through the
browser's audio element.

**Q5. Where do the audio files come from: files I bundle, files the user uploads, a local folder the server scans, or a remote service?** `[BLOCKING]`

*Why it matters:* This decides the ingestion story, which is most of the backend. It also decides
whether the app is useful the moment the reviewer opens it or whether they have to go find music
first. An empty app on first run is a bad review.

*My default:* Both bundled and uploaded. A small set of freely licensed tracks ships with the repo so
the app is populated on first run, and upload works so the reviewer can add their own.

**Q6. Is bundling audio files in the repository acceptable, and are there licensing constraints I should respect?** `[ASSUMED]`

*Why it matters:* Bundled audio makes the first run good but adds repo weight, and using anything
not clearly licensed would be a poor signal on a submission.

*My default:* A handful of public-domain or Creative Commons tracks, small files, with the license
recorded next to them.

**Q7. Should track metadata come only from the files themselves, or should the app enrich it from an external catalog such as MusicBrainz or Discogs?** `[ASSUMED]`

*Why it matters:* Enrichment is where a library app gets genuinely useful, and it is also where the
interesting problems live: matching, confidence, conflict with what the user typed. But it
reintroduces an external dependency into a build that must be reproducible.

*My default:* Read embedded tags on ingest. Design an enrichment interface and, if there is time,
implement a MusicBrainz-backed one that is optional and cached.

## 3. Scope of v1

**Q8. Of the four capabilities named (library management, playback, playlists, metadata editing), which are must-have and which are nice-to-have?** `[BLOCKING]`

*Why it matters:* All four done shallowly and two done well are very different submissions, and I
would rather you choose than guess. This is the single largest determinant of how the time is spent.

*My default:* All four present, with library and metadata editing built to depth and playback and
playlists built to a solid but unadorned baseline.

**Q9. Single user, or multiple users with accounts and authentication?** `[ASSUMED]`

*Why it matters:* Multi-user changes the data model, adds auth, and adds a whole surface of
ownership and sharing questions for playlists. It is a lot of work that is not really about music.

*My default:* Single user, no auth, but the schema carries an owner concept so multi-user is not a
rewrite. I will say so explicitly in the README rather than leave it looking like an oversight.

**Q10. What is the expected time budget for this?** `[BLOCKING]`

*Why it matters:* This is the most useful number you can give me. Everything above is really a
question about how to spend it, and calibrating it wrong in either direction reflects badly:
underbuilt looks careless, overbuilt looks like someone who cannot scope.

*My default:* Roughly two focused days of work.

**Q11. Is there a specific capability you are hoping to see, something that would make a submission stand out to you?** `[ASSUMED]`

*Why it matters:* Gapless playback, smart playlists, bulk tag editing, duplicate detection, and
last-played history are all defensible things to build, and they show very different skills. If one
of them is what you actually care about, I would rather build that one.

*My default:* I build bulk metadata editing to depth, since it is the part of this problem that most
rewards careful design.

**Q12. Is a design or visual bar part of the evaluation, or is a clean functional interface sufficient?** `[ASSUMED]`

*Why it matters:* Making a music player look like a music player is real work, and it competes
directly with the backend for time.

*My default:* Clean, restrained, keyboard-friendly, not styled to a product bar.

## 4. Metadata editing semantics

**Q13. When a user edits metadata, should the change be written back into the audio file's tags, or stored only in the application's database?** `[BLOCKING]`

*Why it matters:* This is the most consequential design question in the brief and the one I would
most like an answer to. Writing tags back to files makes the app authoritative over the user's
actual media and makes edits portable to other players, which is the behavior iTunes and MusicBrainz
Picard have. It also means destructive writes, format-specific tag handling, and the possibility of
corrupting someone's library. Database-only edits are safe and reversible but mean the app's view of
the library silently diverges from the files on disk.

*My default:* Database only for now, with the write path designed for and stubbed behind an explicit
interface, and the reasoning documented. I would rather show that I understood the trade-off than
quietly pick the easy side of it.

**Q14. Is bulk editing across many tracks expected, or is per-track editing enough?** `[ASSUMED]`

*Why it matters:* Bulk editing is the feature that makes library management tools worth using, and
it is substantially harder: partial selections, mixed values, find and replace across a field,
previewing before applying.

*My default:* Bulk editing is in scope, with a preview step before anything is applied.

**Q15. If a track is re-scanned or re-enriched and the incoming metadata conflicts with a user's edit, which wins?** `[ASSUMED]`

*Why it matters:* Any library app that ingests more than once has to answer this, and getting it
wrong means silently destroying the user's work. It needs per-field provenance, not a per-track
flag, because a user who fixed one misspelled artist name should not lose it when a better cover
image arrives.

*My default:* User edits win and are never overwritten by automated sources. Provenance is tracked
per field.

**Q16. Is undo, edit history, or an audit trail expected?** `[ASSUMED]`

*Why it matters:* It is genuinely useful for bulk edits, where one mistake touches hundreds of
tracks, and it is cheap if the data model has provenance already. It is expensive if bolted on
later.

*My default:* Edits are recorded with enough history to undo the last bulk operation. No general
time-travel.

**Q17. Should albums and artists be first-class entities, or derived from track-level tags?** `[ASSUMED]`

*Why it matters:* This is the core modeling decision. Derived entities are simple and follow the
files, but they make "rename this artist" an update across every track and they cannot represent
things tags do not carry, such as an album by two credited artists. First-class entities model
reality better and cost more, including a reconciliation step on ingest.

*My default:* First-class artist and album entities, with tracks referencing them, and ingest doing
a match-or-create against existing entities.

## 5. Reproducibility and the run story

**Q18. What does "builds and is runnable" mean concretely to you: a single command, a container, or a documented multi-step setup?** `[BLOCKING]`

*Why it matters:* This is the one requirement stated explicitly, so I want to hit it precisely rather
than approximately. "It builds" means something different for a Docker image, a Node monorepo, and a
JVM service.

*My default:* `docker compose up` brings the whole thing up with seeded data and nothing else
installed. A native path (language toolchain, one build command, one run command) is documented as
an alternative.

**Q19. What can I assume is present on the reviewing machine, and what architecture is it?** `[ASSUMED]`

*Why it matters:* If Docker is not available, the container path is not a path. If the reviewer is on
Apple Silicon and I build x86-only images, the submission fails to run for reasons that have nothing
to do with the app.

*My default:* Docker and a recent language toolchain available, macOS or Linux, multi-arch images.
Both paths tested from a clean checkout before I submit.

**Q20. Should the app come up with a populated library, or start empty?** `[ASSUMED]`

*Why it matters:* An empty app makes the reviewer do setup work before they can evaluate anything,
and some of them will not bother.

*My default:* Seeded on first run with the bundled tracks, and a documented way to reset to empty.

**Q21. If there are any environment variables or secrets, is a committed `.env.example` with working defaults acceptable?** `[ASSUMED]`

*Why it matters:* Any required secret is a hole in the reproducibility requirement, and I want the
handling of that to be an explicit choice rather than an accident.

*My default:* No secrets are required to run. Anything configurable has a working default committed,
and real secrets, if any are ever added, are documented but optional.

**Q22. Does it need to work fully offline?** `[ASSUMED]`

*Why it matters:* Offline forbids CDN-hosted fonts and libraries and any runtime call to an external
catalog. It is an easy requirement to satisfy if known up front and annoying to retrofit.

*My default:* Fully offline after the initial build. All assets are local.

## 6. Non-functionals

**Q23. What library size should the app be designed for: hundreds of tracks, tens of thousands, or more?** `[BLOCKING]`

*Why it matters:* This is the number that decides the architecture. Hundreds means load everything
and filter in the browser. Tens of thousands means server-side pagination, real indexing, and a
virtualized list. Millions means a different storage and search story entirely. Designing for the
wrong one is either wasted work or a demo that falls over.

*My default:* Designed and tested for tens of thousands, with a seed script that can generate a large
library so the claim is demonstrable rather than asserted.

**Q24. Browser support and responsive expectations?** `[ASSUMED]`

*Why it matters:* Mobile layouts for a music library are a real chunk of work, and older browser
support constrains the audio and layout approach.

*My default:* Current evergreen browsers, desktop-first, degrading acceptably on a narrow viewport.

**Q25. Is there an accessibility bar I should meet?** `[ASSUMED]`

*Why it matters:* A media player is one of the harder things to make accessible: custom transport
controls, a virtualized list, live-updating progress. Doing it properly is a deliberate choice, and
retrofitting it is expensive.

*My default:* Full keyboard operation, correct semantics and labels on controls, visible focus. Not a
formal WCAG audit.

**Q26. Is there a stack you want me to use or avoid, or is the choice mine?** `[ASSUMED]`

*Why it matters:* If you are evaluating for a specific team, seeing their stack is more informative
than seeing mine, and I would rather write in yours.

*My default:* My own choice, with the reasoning documented in an ADR.

**Q27. What level of testing do you expect to see?** `[ASSUMED]`

*Why it matters:* Test strategy on a timeboxed exercise is itself a judgment call being evaluated,
and "no tests" and "everything tested" are both wrong answers.

*My default:* Real tests on the parts where correctness is not obvious (ingest, tag parsing,
provenance and conflict resolution, playlist ordering), a thin end-to-end test proving the app runs,
and no tests written for coverage's sake. The reasoning goes in the README.

**Q28. Is a deployed, hosted instance expected, or is running locally from the repository sufficient?** `[ASSUMED]`

*Why it matters:* The brief asks for something that builds and runs on another machine, which reads
as local. A hosted URL is a different amount of work and reintroduces secrets and infrastructure.

*My default:* Local only. No deployment.

## 7. Process, README, and the AGENTS file

**Q29. How much AI-assisted development is expected or acceptable?** `[BLOCKING]`

*Why it matters:* You asked for a CLAUDE or AGENTS file, which suggests agent-assisted work is at
least expected and possibly part of what is being evaluated. But that spans everything from "used it
for boilerplate" to "the agent wrote most of it under review," and I would rather work to your actual
expectation than guess at it. I am happy either way; I just want to describe it honestly.

*My default:* I use agents substantially and review everything, and the AGENTS file plus the README
describe that process plainly, including what I directed and what I checked.

**Q30. Should the AGENTS file document how the project was built, or serve as forward-looking instructions for an agent working in the repo later?** `[ASSUMED]`

*Why it matters:* Those are different documents. One is a record, the other is operational context:
conventions, commands, boundaries, things an agent should not touch. Writing the wrong one misses
the point of the request.

*My default:* Forward-looking operational instructions, since that is what the file format is for,
with a short section on how the project was actually built.

**Q31. Beyond setup instructions, what do you want the README to cover?** `[ASSUMED]`

*Why it matters:* You said to call out the purpose of each document, which suggests the README is
the entry point you will actually read. I want it to answer your questions, not mine.

*My default:* What it does, how to run it, the architecture in brief, the significant trade-offs and
why, what I deliberately left out, and what I would do next with more time.

**Q32. Are decision records useful to you, or is prose in the README enough?** `[ASSUMED]`

*Why it matters:* ADRs are the natural place for the reasoning behind the choices above, and they
keep the README readable. They are also easy to overdo on a project this size.

*My default:* A small number of ADRs for the decisions that genuinely had alternatives, linked from
the README.

**Q33. Is the commit history part of what you look at?** `[ASSUMED]`

*Why it matters:* It changes how I work. Small, coherent, well-described commits take deliberate
effort, and if nobody reads them that effort is better spent elsewhere.

*My default:* I work in coherent commits with real messages, on the assumption that you might read
them.
