# WFH Diary — continuation backlog

Use this file to resume work (human or agent). **Product vision:** [`WFH_DIARY_PLAN.md`](WFH_DIARY_PLAN.md). **Architecture:** [`ARCHITECTURE.md`](ARCHITECTURE.md).

## Repo state (as of last doc update)

| Area | Status |
| --- | --- |
| .NET solution (`WfhDiary.sln`) | Present |
| `WfhDiary.Api` — settings, diaries, days, leave, summary, xlsx export | Done (local SQLite dev) |
| `WfhDiary.Shared` — DTOs (one type per file under `Dtos/`) | Done |
| API integration tests | 3 smoke tests (`HealthTests`, `DiaryFlowTests`) |
| Android app | Not started |
| Docs (full Platra-equivalent suite) | Partial |
| CI/CD + GitHub repos | Done — [`Evzy/wfh-diary`](https://github.com/Evzy/wfh-diary), [`Evzy/wfh-diary-releases`](https://github.com/Evzy/wfh-diary-releases) |
| Firebase auth enforcement (prod) | Middleware present; optional in local dev |

## Ordered milestones

| # | Milestone | Status |
| --- | --- | --- |
| 1 | API core + xlsx export | Done |
| 2 | Android app — month grid, sync, settings | Pending |
| 3 | Morning notification (Home / Office / Add time / Leave) | Pending |
| 4 | CI/CD — GitHub Actions, Azure deploy, release workflow | Pending (repos created) |
| 5 | Full documentation suite | Pending |

## End improvements (after core ship)

These are **explicitly deferred** until milestones 1–5 are done. Do not block the first usable release on them.

### A. Location-aware auto logging

At the configured morning time (or when the user opens the app), use **device location** to pre-fill the day:

- If the phone is inside a user-configured **office geofence** → mark **Office** with default office times.
- Otherwise → mark **Home** with default home times (same as dismiss today).
- Requires: location permission, office lat/lng + radius in Settings, battery/permission UX notes in [`ANDROID.md`](ANDROID.md).
- User can always override via month grid or notification actions.

### B. Strong unit testing

Expand from current smoke tests to a **docs-driven test suite** (see [`testing/TEST_STRATEGY.md`](testing/TEST_STRATEGY.md) and [`testing/API_TEST_PLAN.md`](testing/API_TEST_PLAN.md)).

**API (priority order):**

- [ ] Pure unit tests: `TimeHelpers`, `FinancialYearHelper`, `SummaryCalculator` (edge cases: leave, zero hours, logic check)
- [ ] `DiaryService` with in-memory DB: FY creation, leave blocks, upsert partial fields
- [ ] xlsx golden-file test: fixture diary → export → assert key cells/formulas match reference
- [ ] Auth gate: `/v1/*` rejects missing Bearer when Firebase configured

**Android (priority order):**

- [ ] JVM unit tests: time math, FY month navigation, total-hours calculation
- [ ] ViewModel tests: month grid state, day edit save payload, summary display
- [ ] Notification action handler tests: Home dismiss applies defaults, leave suppresses schedule
- [ ] Repository tests (fake Ktor): sync PUT/GET mapping

**CI:** run `dotnet test` + `./gradlew :app:testDebugUnitTest` on every PR (see [`CI_CD.md`](CI_CD.md)).

## Conventions

- **One public type per `.cs` / `.kt` file** (DTOs, entities, endpoints, test classes).
- **Use Platra as the working reference** — same monorepo layout, auth, CI, OTA, and Android patterns. See [Platra comparison](#platra-comparison) below before inventing new approaches.
- Bump **Last updated** in `ARCHITECTURE.md` when API or sync shape changes.

## Platra comparison

The sibling repo [`platra`](https://github.com/Evzy/platra) is the **canonical example** for infrastructure that already works. Copy and adapt rather than redesign.

| WFH Diary | Platra reference | Notes |
| --- | --- | --- |
| `wfh-diary` monorepo | `platra` | `apps/android/`, `services/api/`, `packages/shared/`, `docs/` |
| `wfh-diary-releases` | `platra-releases` | APK-only repo for GitHub Releases + in-app updater |
| Firebase Bearer auth | `Platra.Api/Auth/` | Same middleware pattern |
| GitHub Actions CI | `.github/workflows/` | `deploy-api.yml`, `release-android.yml`, shared debug keystore |
| In-app OTA | `GithubApkReleaseUpdater.kt` | Settings → check/download/install |
| Android shell | `apps/android/` | Compose, Ktor, Room, DataStore, Firebase sign-in |
| Docs-driven backlog | `docs/TODO.md`, `docs/CI_CD.md` | Same maintenance rules |

Local path for side-by-side work: `C:\Users\evanb\platra` and `C:\Users\evanb\wfh-diary`.
