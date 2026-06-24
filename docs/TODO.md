# WFH Diary — continuation backlog

Use this file to resume work (human or agent). **Architecture:** [`ARCHITECTURE.md`](ARCHITECTURE.md). **CI/CD:** [`CI_CD.md`](CI_CD.md).

## Repo state (as of last doc update)

| Area | Status |
| --- | --- |
| .NET solution (`WfhDiary.sln`) | Present |
| `WfhDiary.Api` — settings, diaries, days, leave, summary, xlsx export | Done |
| `WfhDiary.Shared` — DTOs (one type per file under `Dtos/`) | Done |
| API tests | 9 tests (integration + `TimeHelpers` / `SummaryCalculator` unit) |
| Android app (`dev.wfhdiary.app`) | Done — month grid, day edit, settings, notifications, OTA |
| GitHub Actions | `ci.yml`, `deploy-api.yml`, `release-android.yml` |
| GitHub repos | Private [`wfh-diary`](https://github.com/Evzy/wfh-diary), public [`wfh-diary-releases`](https://github.com/Evzy/wfh-diary-releases) |
| Azure App Service | Workflow ready; provision `app-wfh-diary-api-dev` when needed |
| Firebase / `google-services.json` | Optional; dev uses `X-Debug-User-Id` |

## Ordered milestones

| # | Milestone | Status |
| --- | --- | --- |
| 1 | API core + xlsx export | Done |
| 2 | Android — month grid, sync, settings | Done |
| 3 | Morning notification (Home / Office / Add time / Leave) | Done |
| 4 | CI/CD — GitHub Actions + releases repo | Done (secrets TBD) |
| 5 | Full documentation suite | Partial — add `ANDROID.md`, `AZURE_DEPLOY.md`, `API.md` |

## Next manual steps

- [ ] Add GitHub secrets: `WFH_RELEASES_TOKEN`, shared debug keystore (see [`CI_CD.md`](CI_CD.md))
- [ ] Provision Azure `app-wfh-diary-api-dev` + OIDC secrets (copy from Platra [`AZURE_DEPLOY.md`](https://github.com/Evzy/platra/blob/main/docs/AZURE_DEPLOY.md))
- [ ] Add `apps/android/app/google-services.json` + Firebase SHA-1 for release/sign-in
- [ ] First device test: API on `:5222`, install debug APK, log a day, export xlsx

## End improvements (after core ship)

### A. Location-aware auto logging

Office geofence → auto Office; otherwise default Home. See prior spec in git history / plan.

### B. Strong unit testing

Golden-file xlsx test, `DiaryService` tests, Android ViewModel/notification tests. See [`testing/TEST_STRATEGY.md`](testing/TEST_STRATEGY.md).

## Conventions

- **One public type per `.cs` / `.kt` file**
- **Use Platra as the working reference** — see [Platra comparison](#platra-comparison)
- Bump **Last updated** in `ARCHITECTURE.md` when API or sync shape changes

## Platra comparison

| WFH Diary | Platra reference | Notes |
| --- | --- | --- |
| `wfh-diary` monorepo | `platra` | Same layout |
| `wfh-diary-releases` | `platra-releases` | **Public** APK-only; source **private** |
| Firebase Bearer auth | `Platra.Api/Auth/` | Same middleware |
| GitHub Actions | `.github/workflows/` | Same patterns |
| In-app OTA | `GithubApkReleaseUpdater.kt` | Settings → update |
| Android | `apps/android/` | Compose, Ktor, Room, WorkManager |

Local paths: `C:\Users\evanb\platra`, `C:\Users\evanb\wfh-diary`.
