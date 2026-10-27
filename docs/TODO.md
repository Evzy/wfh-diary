# WFH Diary — continuation backlog

Use this file to resume work (human or agent). **Specs (source of truth for behaviour):** [`specs/`](specs/README.md). **Changes:** [`changes/`](changes/README.md). **Architecture:** [`ARCHITECTURE.md`](ARCHITECTURE.md). **CI/CD:** [`CI_CD.md`](CI_CD.md).

New backlog work should become a change folder before implementation — see [`changes/README.md`](changes/README.md).

## Repo state (as of last doc update)

| Area | Status |
| --- | --- |
| Android app (`dev.wfhdiary.app`) | Done — on-device only (Room), month grid, day edit, settings, notifications, xlsx export, file backups, OTA, home/office geofence missed-notification |
| Backend (API, shared DTOs, Azure, Firebase sign-in) | Removed — see [on-device-only change](changes/2026-10-27-on-device-only/design.md) |
| GitHub Actions | `ci.yml`, `release-android.yml`, parallel Android test jobs |
| GitHub repos | Private [`wfh-diary`](https://github.com/Evzy/wfh-diary), public [`wfh-diary-releases`](https://github.com/Evzy/wfh-diary-releases) |
| GitHub Releases OTA | **Working** — checked at app start and from Settings |

## Ordered milestones

| # | Milestone | Status |
| --- | --- | --- |
| 1 | Diary core + xlsx export | Done (on device) |
| 2 | Android — month grid, settings | Done |
| 3 | Morning notification (Home / Office / Add time / Leave) | Done |
| 4 | CI/CD — GitHub Actions + releases repo | Done |
| 5 | Missed notification default + leave delete + calendar nav | Done |
| 6 | Unit tests + parallel CI | Done |
| 7 | Work templates + geofence on missed notification | Home/office geofence done; work templates dropped |
| 8 | On-device only with file backups | Done |
| 9 | Full documentation suite | Partial — add `ANDROID.md` |

## Next manual steps

- [ ] Add GitHub secrets if not done: `WFH_RELEASES_TOKEN`, shared debug keystore, `WFH_MAPS_API_KEY` (see [`CI_CD.md`](CI_CD.md))

## End improvements (after core ship)

### A. Location-aware auto logging — **partial (shipped for missed notification)**

Settings → **Home & office locations** — tap **Use current location** or **Pick on map** (200 m radius). When the morning reminder is ignored, the app checks location and applies your Home or Office default times.

**Still to do:** proactive logging on arrival; background geofence monitoring.

### B. Strong unit testing — **partial**

| Item | Status |
| --- | --- |
| `TimeHelpers`, `FinancialYearHelper`, `DiaryRules` | Done |
| `DiaryStore`, Room database (Robolectric) | Done |
| Backup codec, retention, backup/restore services | Done |
| `GeofenceHelperTest`, notification updater tests | Done |
| `XlsxWriter` cell assertions | Done |
| Android ViewModel tests | Backlog |

See [`testing/TEST_STRATEGY.md`](testing/TEST_STRATEGY.md) and [`testing/ANDROID_TEST_PLAN.md`](testing/ANDROID_TEST_PLAN.md).

### C. Work scenario templates — **dropped**

Templates only existed on the server and were removed with it; any server templates imported into a backup are preserved under `legacy`. See [work-templates spec](specs/work-templates/spec.md).

## Conventions

- **One public type per `.kt` file**
- **Use Platra as the working reference** — see [Platra comparison](#platra-comparison)
- Bump **Last updated** in `ARCHITECTURE.md` when the data model or backup shape changes

## Platra comparison

| WFH Diary | Platra reference | Notes |
| --- | --- | --- |
| `wfh-diary` monorepo | `platra` | Same layout |
| `wfh-diary-releases` | `platra-releases` | **Public** APK-only; source **private** |
| GitHub Actions | `.github/workflows/` | Same patterns |
| In-app OTA | `GithubApkReleaseUpdater.kt` | App start + Settings → update (**working**) |
| Android | `apps/android/` | Compose, Room, WorkManager |

Local paths: `C:\Users\evanb\platra`, `C:\Users\evanb\wfh-diary`.
