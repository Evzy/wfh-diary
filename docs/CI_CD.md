# CI/CD (GitHub Actions)

Publish from GitHub — no Android Studio required for releases. Mirrors [`platra` CI/CD](https://github.com/Evzy/platra/blob/main/docs/CI_CD.md).

## Workflows

| Workflow | File | When | Purpose |
|----------|------|------|---------|
| **CI** | [`.github/workflows/ci.yml`](../.github/workflows/ci.yml) | PR + push to `main` | Android unit tests + `assembleDebug` (via [`reusable-tests.yml`](../.github/workflows/reusable-tests.yml)) |
| **Release Android APK** | [`.github/workflows/release-android.yml`](../.github/workflows/release-android.yml) | push `main` / tag `v*` | Build debug APK → **`Evzy/wfh-diary-releases`** (public) |

## Two-repo release model

| Repo | Visibility | Role |
|------|------------|------|
| `wfh-diary` | Private | Source + CI |
| `wfh-diary-releases` | **Public** | APK assets for anonymous in-app download (free GitHub) |

## Secrets (`wfh-diary` repo)

| Secret | Purpose |
|--------|---------|
| `WFH_RELEASES_TOKEN` | PAT with **Contents: write** on `wfh-diary-releases` |
| `WFH_SHARED_DEBUG_KEYSTORE_B64` (+ passwords, alias) | Same debug cert for CI + local OTA upgrades |
| `WFH_MAPS_API_KEY` | Google Maps SDK key for **Pick on map** in CI-built APKs (same key as `local.properties`) |

## Versioning (required for CI release)

Single source of truth: [`version.json`](../version.json) at repo root (`versionName` + `versionCode`).

| Consumer | How it reads the version |
|----------|--------------------------|
| Android APK | `apps/android/app/build.gradle.kts` reads `version.json` at build time |
| Release workflow | Tag `v{versionName}` from `version.json` on push to `main` |

**Every commit must bump the version** or the Android release job skips publish (tag already exists). A **pre-commit hook** does this automatically:

```powershell
# One-time after clone
./scripts/setup-git-hooks.ps1
```

The hook runs [`scripts/bump-version.ps1`](../scripts/bump-version.ps1): increments `versionCode`, bumps patch `versionName` (e.g. `0.1.0` → `0.1.1`), and stages `version.json`. Skip once with `SKIP_VERSION_BUMP=1 git commit ...` (avoid on `main`).

## Cut an Android release

1. Commit to `main` (hook bumps `version.json` automatically).
2. Workflow builds the APK and publishes `WfhDiary-v{versionName}-debug.apk` to `wfh-diary-releases` if tag `v{versionName}` is new.

## Local Android + OTA

Create **`apps/android/local.properties`** (gitignored; see [`local.properties.example`](../apps/android/local.properties.example)). Android Studio usually adds `sdk.dir` when you open the project.

```properties
sdk.dir=C\:\\Users\\YOUR_USER\\AppData\\Local\\Android\\Sdk

# In-app "Check for update" (Settings). Rebuild after changing.
UPDATE_GITHUB_OWNER=Evzy
UPDATE_GITHUB_REPO=wfh-diary-releases

# Same debug signing as CI — keystore at apps/android/app/ci-shared-debug.keystore
debug.storeFile=ci-shared-debug.keystore
debug.storePassword=YOUR_STORE_PASSWORD
debug.keyAlias=wfh_shared_debug
debug.keyPassword=YOUR_KEY_PASSWORD

# Optional: only if wfh-diary-releases is not public
# UPDATE_GITHUB_TOKEN=ghp_...

# Google Maps — Settings → Pick on map (enable Maps SDK for Android in Google Cloud, then):
# MAPS_API_KEY=AIza...
# Also add the same value as GitHub secret WFH_MAPS_API_KEY for CI release APKs.
```

Generate the shared debug keystore once (from `apps/android/app`):

```powershell
keytool -genkeypair -v -keystore ci-shared-debug.keystore -alias wfh_shared_debug `
  -keyalg RSA -keysize 2048 -validity 10000 `
  -storepass YOUR_STORE_PASSWORD -keypass YOUR_KEY_PASSWORD `
  -dname "CN=WFH Diary Debug, OU=Dev, O=WFH Diary"
```

Base64-encode for GitHub secret `WFH_SHARED_DEBUG_KEYSTORE_B64` (see [Secrets](#secrets-wfh-diary-repo) above). CI env vars `WFH_SHARED_DEBUG_*` override `local.properties` on the runner.

## Retired backend

The API and its Azure resources are no longer used. Delete them once, by hand, with [`scripts/azure/decommission.ps1`](../scripts/azure/decommission.ps1) after the phone holds all data and a backup file exists; then remove the `AZURE_*` secrets from the repo.
