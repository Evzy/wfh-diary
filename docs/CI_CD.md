# CI/CD (GitHub Actions)

Publish from GitHub — no Android Studio required for releases. Mirrors [`platra` CI/CD](https://github.com/Evzy/platra/blob/main/docs/CI_CD.md).

## Workflows

| Workflow | File | When | Purpose |
|----------|------|------|---------|
| **CI** | [`.github/workflows/ci.yml`](../.github/workflows/ci.yml) | PR + push to `main` | `dotnet test` + Android unit tests |
| **Deploy API** | [`.github/workflows/deploy-api.yml`](../.github/workflows/deploy-api.yml) | PR: test only; push `main`: deploy | Azure App Service `app-wfh-diary-api-dev` |
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
| `AZURE_CLIENT_ID`, `AZURE_TENANT_ID`, `AZURE_SUBSCRIPTION_ID` | OIDC deploy (when Azure app exists) |

## Cut an Android release

1. Bump `versionCode` / `versionName` in [`apps/android/app/build.gradle.kts`](../apps/android/app/build.gradle.kts).
2. Merge to `main`.
3. Workflow publishes `WfhDiary-v{version}-debug.apk` to `wfh-diary-releases` if tag `v{versionName}` is new.

## Local Android + OTA

See [`apps/android/local.properties.example`](../apps/android/local.properties.example) — shared debug keystore + `UPDATE_GITHUB_REPO=wfh-diary-releases`.
