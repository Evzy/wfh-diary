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
| `WFH_MAPS_API_KEY` | Google Maps SDK key for **Pick on map** in CI-built APKs (same key as `local.properties`) |
| `WFH_FIREBASE_WEB_CLIENT_ID` | OAuth Web client id for Google Sign-In in CI builds (only if `google-services.json` lacks `oauth_client`) |
| `AZURE_CLIENT_ID`, `AZURE_TENANT_ID`, `AZURE_SUBSCRIPTION_ID` | OIDC deploy (when Azure app exists) |

## Versioning (required for CI release)

Single source of truth: [`version.json`](../version.json) at repo root (`versionName` + `versionCode`).

| Consumer | How it reads the version |
|----------|--------------------------|
| Android APK | `apps/android/app/build.gradle.kts` reads `version.json` at build time |
| API | `AppVersion.cs` + `<Version>` in `WfhDiary.Api.csproj` (synced from `version.json`) |
| Release workflow | Tag `v{versionName}` from `version.json` on push to `main` |

**Every commit must bump the version** or the Android release job skips publish (tag already exists). A **pre-commit hook** does this automatically:

```powershell
# One-time after clone
./scripts/setup-git-hooks.ps1
```

The hook runs [`scripts/bump-version.ps1`](../scripts/bump-version.ps1): increments `versionCode`, bumps patch `versionName` (e.g. `0.1.0` → `0.1.1`), syncs API files, and stages them. Skip once with `SKIP_VERSION_BUMP=1 git commit ...` (avoid on `main`).

Manual sync without bump: `./scripts/bump-version.ps1 -SyncOnly`

## Cut an Android release

1. Commit to `main` (hook bumps `version.json` automatically).
2. Workflow builds the APK and publishes `WfhDiary-v{versionName}-debug.apk` to `wfh-diary-releases` if tag `v{versionName}` is new.

## Local Android + OTA

Create **`apps/android/local.properties`** (gitignored). Android Studio usually adds `sdk.dir` when you open the project.

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

# Optional: only if google-services.json has no oauth_client (client_type 3) — or set GitHub secret WFH_FIREBASE_WEB_CLIENT_ID for CI.
# FIREBASE_WEB_CLIENT_ID=....apps.googleusercontent.com

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

---

## One-time setup: Azure OIDC for Deploy API

Use the same tenant as your dev subscription. WFH Diary gets its **own** resource group — scope IAM to that RG only.

| Placeholder | WFH Diary value |
|-------------|-----------------|
| `GITHUB_ORG` | `Evzy` |
| `GITHUB_REPO` | `wfh-diary` |
| `AZURE_RG` | `rg-wfh-diary-dev-australiaeast` |

Run in PowerShell after `az login` and `az account set --subscription platra-dev`.  
**Prerequisite:** Azure resources exist — see [`AZURE_DEPLOY.md`](AZURE_DEPLOY.md).

### 1. Register an application + service principal

```powershell
$AppName = "github-wfh-diary-deploy"
$AppId = az ad app create --display-name $AppName --query appId --output tsv
az ad sp create --id $AppId
Write-Host "AZURE_CLIENT_ID: $AppId"
Write-Host "AZURE_TENANT_ID: $(az account show --query tenantId --output tsv)"
Write-Host "AZURE_SUBSCRIPTION_ID: $(az account show --query id --output tsv)"
```

### 2. Federated credential (GitHub → Azure, `main` branch)

Subject: `repo:Evzy/wfh-diary:ref:refs/heads/main`

```powershell
$AppId = "PASTE_APPLICATION_CLIENT_ID_FROM_STEP_1"
$FicPath = Join-Path $env:TEMP "gh-wfh-diary-fic.json"
@'
{
  "name": "github-main",
  "issuer": "https://token.actions.githubusercontent.com",
  "subject": "repo:Evzy/wfh-diary:ref:refs/heads/main",
  "description": "GitHub Actions Deploy WFH Diary API",
  "audiences": ["api://AzureADTokenExchange"]
}
'@ | Set-Content -Path $FicPath -Encoding utf8
az ad app federated-credential create --id $AppId --parameters $FicPath
```

### 3. Role on the WFH resource group only

```powershell
$AppId = "PASTE_APPLICATION_CLIENT_ID_FROM_STEP_1"
$SubId = az account show --query id --output tsv
$RgId = "/subscriptions/$SubId/resourceGroups/rg-wfh-diary-dev-australiaeast"
az role assignment create `
  --assignee $AppId `
  --role "Website Contributor" `
  --scope $RgId
```

### 4. GitHub repository secrets (`Evzy/wfh-diary`)

**Settings** → **Secrets and variables** → **Actions**:

| Name | Value |
|------|--------|
| `AZURE_CLIENT_ID` | Application (client) ID from step 1 |
| `AZURE_TENANT_ID` | Directory (tenant) ID |
| `AZURE_SUBSCRIPTION_ID` | Subscription ID |

### 5. Verify deploy

Push to `main` (or run **Deploy API** workflow manually). Then:

```powershell
curl https://app-wfh-diary-api-dev.azurewebsites.net/health
curl https://app-wfh-diary-api-dev.azurewebsites.net/v1/version
```

Official reference: [OpenID Connect with Azure + GitHub](https://learn.microsoft.com/en-us/azure/developer/github/connect-from-azure-openid-connect).
