# OTA updates & versioning

**Prefix:** `OTA` · **Last updated:** 2026-10-27

## Purpose & scope

Keeps the sideloaded Android app current without an app store, and makes the deployed version visible. A single `version.json` drives the Android `versionName`/`versionCode` and the API's `AppVersion`. A pre-commit hook bumps it on every commit. CI publishes a debug APK for each new version to the public `wfh-diary-releases` GitHub repo, and the app checks that repo's latest release, then downloads and installs the APK. The API exposes `/health` (liveness) and `/v1/version`. Out of scope: Play Store distribution, release-signed builds, API deployment (see [CI_CD.md](../../CI_CD.md)), and forced or minimum-version updates.

## Requirements

### OTA-001 — Health endpoint

`GET /health` returns `200` with `{ "status": "ok", "service": "WfhDiary.Api" }` and needs no authentication.

- **Given** the API is running
- **When** a client calls `GET /health` with no auth headers
- **Then** the response is `200` with `status` `ok` and `service` `WfhDiary.Api`

### OTA-002 — Version endpoint

`GET /v1/version` returns the API's build version as `{ "version": <versionName>, "versionCode": <int>, "service": "WfhDiary.Api" }` from the generated `AppVersion` constants.

- **Given** the API was built with `AppVersion.VersionName` `0.1.23` and `VersionCode` `24`
- **When** a client calls `GET /v1/version` (dev mode, no user id needed)
- **Then** the response is `200` with `version` `0.1.23`, `versionCode` `24`, `service` `WfhDiary.Api`

### OTA-003 — `version.json` is the single version source

`scripts/bump-version.ps1` increments `versionCode` by 1 and the patch part of a `major.minor.patch` `versionName`, then regenerates `AppVersion.cs` and the csproj `<Version>`; `-SyncOnly` only regenerates. The Android build reads `version.json` directly.

- **Given** `version.json` is `{"versionName":"0.1.23","versionCode":24}`
- **When** `bump-version.ps1` runs (without `-SyncOnly`)
- **Then** `version.json` becomes `0.1.24` / `25` and `AppVersion.cs` and `WfhDiary.Api.csproj` match. A `versionName` that isn't `x.y.z` gets `.<versionCode>` appended instead.

### OTA-004 — Pre-commit hook bumps the version

When git hooks are enabled, every commit runs `bump-version.ps1` via PowerShell (`pwsh` or `powershell.exe`) and stages `version.json`, `AppVersion.cs`, and the API csproj, unless `SKIP_VERSION_BUMP=1`.

- **Given** hooks are enabled (`scripts/setup-git-hooks.ps1`)
- **When** the developer commits without / with `SKIP_VERSION_BUMP=1`
- **Then** the commit includes the bumped version files / is left unchanged. If neither PowerShell is available, the commit is aborted.

### OTA-005 — CI publishes a debug APK per new version

On push to `main`, the release workflow builds the debug APK and publishes it as `WfhDiary-v{versionName}-debug.apk` under tag `v{versionName}` in `<owner>/wfh-diary-releases`. It skips the publish if that tag already exists or `WFH_RELEASES_TOKEN` is unset. Pushing a `v*` tag publishes under that tag.

- **Given** a push to `main` with `version.json` `versionName` `0.1.24`
- **When** the Release Android APK workflow runs
- **Then** a release `v0.1.24` with asset `WfhDiary-v0.1.24-debug.apk` is created, unless `v0.1.24` already exists or the token secret is missing. Manual `workflow_dispatch` runs only upload the APK as a workflow artifact.

### OTA-006 — Fetch latest release APK

The app calls the GitHub `releases/latest` API for the configured owner/repo and selects the first asset whose name ends in `.apk` (case-insensitive). Failures, including a release with no `.apk` asset, become an error.

- **Given** `UPDATE_GITHUB_OWNER` and `UPDATE_GITHUB_REPO` are set (defaults `Evzy` / `wfh-diary-releases`)
- **When** the app checks for an update
- **Then** it gets the release's tag, title, APK asset name and download URL. A release without an `.apk` asset fails with `No .apk asset on latest release (<tag>)`. A non-2xx response fails the check. An optional `UPDATE_GITHUB_TOKEN` is sent as a Bearer header.

### OTA-007 — Version comparison

The release tag is compared to the installed `versionName` using only the numeric `major.minor.patch` core. It ignores a leading `v`, anything after `-`, and components beyond the third, and treats missing components as `0`.

- **Given** a remote tag and the installed version name
- **When** they are compared
- **Then** the result is `UPDATE_AVAILABLE` if remote > installed, `INSTALLED_NEWER` if remote < installed, `UP_TO_DATE` if equal, and `UNKNOWN` if either has no numeric parts (e.g. `v0.1.24` vs `0.1.23` gives `UPDATE_AVAILABLE`, and `v0.1.23-debug` vs `0.1.23` gives `UP_TO_DATE`)

### OTA-008 — Check outcome by mode

An automatic check prompts with a dialog only when an update is available (or the comparison is unknown) and is otherwise silent. A manual check never opens the dialog and always reports a status message.

- **Given** a successful release fetch
- **When** the check was automatic / manual
- **Then** for an available update: the dialog opens with no status / status "found {tag} {asset}". For up to date: no status / an "up to date" status. For installed newer: an "installed newer" status in both modes. For unknown: the release is kept as pending with a "found" status, and the dialog opens only for automatic checks. A failed fetch clears any pending release and shows the error message.

### OTA-009 — Automatic check on the login screen

When updates are configured, the login screen triggers one automatic update check per `AppUpdateViewModel` instance and offers **Download** for a pending release, plus a manual **Check for update** button.

- **Given** Firebase is configured, no user is signed in, and update owner/repo are set
- **When** the login screen is shown
- **Then** an automatic check runs once. The pending release can be downloaded from the dialog or a "download available" button. The app version and code are shown at the bottom.

### OTA-010 — Manual check from Settings

When updates are configured, Settings offers **Check for update** and, once a release is pending, **Download and install**, showing busy state and status text.

- **Given** the user opens Settings in the main app
- **When** they tap **Check for update**
- **Then** a manual check runs (OTA-008) and its status is shown. If owner/repo are not configured, the section shows a "not configured" note and no check button.

### OTA-011 — Download and install

**Download and install** requires a pending release and permission to install unknown apps. The app downloads the APK to `cacheDir/github_apk_updates/WfhDiary-update.apk`, overwriting any previous file, and launches the system package installer through the app's `FileProvider`.

- **Given** a pending release
- **When** the user taps download/install
- **Then** with no pending release the status asks them to check first. On Android 8+ without install permission the "unknown sources" settings screen opens and the status asks them to enable it. Otherwise the APK downloads with a busy state and the installer opens. A download failure shows the error.

## Interfaces

| Kind | Location |
| --- | --- |
| API | `GET /health` — `services/api/WfhDiary.Api/Endpoints/HealthEndpoints.cs` |
| API | `GET /v1/version` — `services/api/WfhDiary.Api/Endpoints/VersionEndpoints.cs`, generated `services/api/WfhDiary.Api/AppVersion.cs` |
| Version source & tooling | `version.json`, `scripts/bump-version.ps1`, `.githooks/pre-commit`, `apps/android/app/build.gradle.kts` (reads `version.json`; `UPDATE_GITHUB_*` BuildConfig fields) |
| CI | `.github/workflows/release-android.yml` — see [CI_CD.md](../../CI_CD.md) |
| Android | `apps/android/app/src/main/java/dev/wfhdiary/app/update/` (`GithubApkReleaseUpdater.kt`, `GithubApkReleaseInfo.kt`, `ReleaseVersionCompare.kt`, `ReleaseVersusInstalled.kt`), `ui/update/AppUpdateViewModel.kt`, `ui/auth/LoginScreen.kt`, `MainActivity.kt` |

## Test coverage

| Requirement | Tests |
| --- | --- |
| OTA-001 | `HealthTests.Health_returns_ok`, `AuthGateTests.Health_does_not_require_auth` |
| OTA-002 | `VersionEndpointTests.Version_returns_name_and_code` |
| OTA-003 | `untested` |
| OTA-004 | `untested` |
| OTA-005 | `untested` |
| OTA-006 | `untested` |
| OTA-007 | `untested` |
| OTA-008 | `untested` |
| OTA-009 | `untested` |
| OTA-010 | `untested` |
| OTA-011 | `untested` |

## Known gaps

- **Suspected unintended — `/v1/version` needs auth in verified mode.** Because the path is under `/v1/`, the Firebase Bearer middleware requires a token once Firebase is on. `CI_CD.md` shows an unauthenticated `curl` to it.
- **Suspected unintended — releases are not gated on tests.** In `release-android.yml` the `build` job has no `needs: tests`, so an APK can be published even when the reusable test job fails.
- **Risk — the GitHub token ends up in the APK.** `UPDATE_GITHUB_TOKEN`, if set, is compiled into `BuildConfig` and sent on both the release query and the APK download.
- **Comparison ignores `versionCode` and pre-release suffixes.** An `UNKNOWN` comparison is treated like an available update and auto-prompts.
- Published APKs are **debug** builds. They are signed with the shared debug key when `WFH_SHARED_DEBUG_KEYSTORE_B64` is set, and with the CI runner's default debug key otherwise. The download has no checksum or signature check beyond the Android installer.
- `ReleaseVersionCompare` drops non-numeric dot-components before comparing by position, so `1.x.3` compares as `1.3.0`.
- `AppUpdateViewModel.checkForUpdate` has a "not configured" status path that the UI can't reach. Both the login screen and Settings hide the check button when owner/repo are unset.
- The `bump-version.ps1` header says it "syncs API + Android", but it only writes API files. Android picks up `version.json` at build time.
- `ReleaseVersionCompare` is pure logic but has no unit tests. Android ViewModel tests are on the backlog ([TODO § B](../../TODO.md#b-strong-unit-testing--partial)).

## Revision log

- 2026-10-27: Initial spec backfilled from implementation ([change](../../changes/2026-10-27-spec-driven-setup/design.md)).
- 2026-10-27: Final-review consistency fixes ([change](../../changes/2026-10-27-spec-driven-setup/design.md)).
