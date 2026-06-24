# Deploy WfhDiary.Api on Azure App Service

WFH Diary’s HTTP API lives in [`services/api/WfhDiary.Api/`](../services/api/WfhDiary.Api/). This doc covers **Azure CLI** provisioning (Linux App Service, **Australia East**), **app settings**, and **GitHub Actions** deploy with **OIDC**.

**Related:** Android OTA + keystore → [`CI_CD.md`](CI_CD.md). OIDC app registration → [`CI_CD.md` — One-time setup: Azure OIDC](CI_CD.md#one-time-setup-azure-oidc-for-deploy-api).

## Subscription and region

Use your dev subscription (e.g. display name **`platra-dev`** — same subscription as Platra is fine; WFH gets its **own resource group**):

```powershell
az login
az account set --subscription "platra-dev"
az account show --output table
```

If **`az login`** fails with **MFA required** (`AADSTS50076`), use device code:

```powershell
az login --use-device-code --tenant YOUR_TENANT_ID
```

Open **https://login.microsoft.com/device**, enter the code, complete MFA.

**Region:** **Australia East** (`australiaeast`).

## Naming (WFH-identifiable)

| Resource | Name |
|----------|------|
| Resource group | `rg-wfh-diary-dev-australiaeast` |
| App Service plan (Linux) | `asp-wfh-diary-dev-linux` |
| Web app | `app-wfh-diary-api-dev` |

The **web app name** must be **globally unique** under `azurewebsites.net`. If taken, pick another (e.g. `app-wfhdiary-api-dev-evzy`) and use it consistently in Azure and [`.github/workflows/deploy-api.yml`](../.github/workflows/deploy-api.yml) (`AZURE_WEBAPP_NAME`).

## Phase 2 — programmatic setup (checklist)

Run these in order in **your** PowerShell (after `az login`). Automated agents may not see your browser token.

| Step | What | Command / doc |
|------|------|----------------|
| **1** | Log in + select subscription | § [Subscription and region](#subscription-and-region) |
| **2** | Create RG + plan + web app | **One-shot:** [`scripts/azure/provision-wfh-diary-dev.ps1`](../scripts/azure/provision-wfh-diary-dev.ps1) — or CLI steps below |
| **3** | Verify API responds | Push to `main` (after step 4) or `dotnet publish` + deploy; then `GET /health` and `GET /v1/version` |
| **4** | GitHub OIDC for deploy | [`CI_CD.md` — Azure OIDC](CI_CD.md#one-time-setup-azure-oidc-for-deploy-api) |
| **5** | GitHub Android secrets | [`CI_CD.md` — Secrets](CI_CD.md#secrets-wfh-diary-repo) (`WFH_RELEASES_TOKEN`, keystore) |
| **6** | Firebase (optional until sign-in) | § [Firebase Admin](#firebase-admin-optional) below |
| **7** | Postgres (optional, durable DB) | § [Database](#database-sqlite-vs-postgresql) below |

### One-shot provision script

From repo root (same session as `az login`):

```powershell
./scripts/azure/provision-wfh-diary-dev.ps1
```

Optional parameters:

```powershell
./scripts/azure/provision-wfh-diary-dev.ps1 -Subscription "platra-dev" -WebAppName "app-wfh-diary-api-dev"
```

### Manual CLI (same as the script)

#### 1. Resource group

```powershell
az group create `
  --name rg-wfh-diary-dev-australiaeast `
  --location australiaeast `
  --tags Project=wfh-diary Environment=dev ManagedBy=manual
```

#### 2. App Service plan (Linux, Free F1)

```powershell
az appservice plan create `
  --resource-group rg-wfh-diary-dev-australiaeast `
  --name asp-wfh-diary-dev-linux `
  --location australiaeast `
  --is-linux `
  --sku F1
```

**Upgrade later:** `--sku B1` for Always On and steadier uptime.

#### 3. Web app (.NET on Linux)

List runtimes, then create:

```powershell
az webapp list-runtimes --os-type linux | Select-String -Pattern dotnet

az webapp create `
  --resource-group rg-wfh-diary-dev-australiaeast `
  --plan asp-wfh-diary-dev-linux `
  --name app-wfh-diary-api-dev `
  --runtime "DOTNETCORE:9.0"
```

#### 4. Base app settings

```powershell
az webapp config appsettings set `
  --resource-group rg-wfh-diary-dev-australiaeast `
  --name app-wfh-diary-api-dev `
  --settings ASPNETCORE_ENVIRONMENT=Production
```

#### 5. Smoke test (after first deploy)

```powershell
curl https://app-wfh-diary-api-dev.azurewebsites.net/health
curl https://app-wfh-diary-api-dev.azurewebsites.net/v1/version
```

## Database: SQLite vs PostgreSQL

By default the API uses **SQLite** (`wfh_diary_dev.db` under the app content root). On App Service, local disk is **ephemeral** — fine for early dev, not for production data.

**Postgres (when ready):**

```powershell
# Example — adjust SKU/name; or create Flexible Server in portal
az postgres flexible-server create `
  --resource-group rg-wfh-diary-dev-australiaeast `
  --name pg-wfh-diary-dev `
  --location australiaeast `
  --admin-user wfhadmin `
  --admin-password "YOUR_STRONG_PASSWORD" `
  --sku-name Standard_B1ms `
  --tier Burstable `
  --version 16 `
  --storage-size 32

az webapp config appsettings set `
  --resource-group rg-wfh-diary-dev-australiaeast `
  --name app-wfh-diary-api-dev `
  --settings WfhDiary__ConnectionString="Host=pg-wfh-diary-dev.postgres.database.azure.com;Database=wfh_diary;Username=wfhadmin;Password=YOUR_STRONG_PASSWORD;SSL Mode=Require"
```

Allow the web app outbound to Postgres (firewall rule for Azure services or the app’s outbound IPs).

## Firebase Admin (optional)

Full walkthrough: **[`FIREBASE_SETUP.md`](FIREBASE_SETUP.md)**.

Until Firebase is configured, the API accepts **`X-Debug-User-Id`** for dev. For production token verification:

1. Create Key Vault in `rg-wfh-diary-dev-australiaeast` (e.g. `kv-wfh-diary-dev`).
2. Secret `wfh-firebase-adminsdk` = full Firebase service account JSON.
3. Web app → **Identity** → System assigned → **On**.
4. Grant the identity **Key Vault Secrets User** on the vault.
5. App setting `Firebase__ServiceAccountJson` = Key Vault reference, e.g.  
   `@Microsoft.KeyVault(SecretUri=https://kv-wfh-diary-dev.vault.azure.net/secrets/wfh-firebase-adminsdk/)`
6. Optional: `Firebase__RequireConfigured=true` so misconfiguration fails fast.

## GitHub Actions (OIDC)

Workflow: [`.github/workflows/deploy-api.yml`](../.github/workflows/deploy-api.yml).

- **PRs** to `main`: build + test only.
- **Push** to `main` / **workflow_dispatch**: build, then deploy to `app-wfh-diary-api-dev`.

Full OIDC steps: [**CI/CD — One-time setup: Azure OIDC**](CI_CD.md#one-time-setup-azure-oidc-for-deploy-api).

## Android client

Default Azure base URL is compiled in [`apps/android/app/build.gradle.kts`](../apps/android/app/build.gradle.kts):

`https://app-wfh-diary-api-dev.azurewebsites.net/`

Rebuild the app if you change the host name.

## Troubleshooting: `/health` 404 and default Azure page

If **Log stream** shows `hostingstart.dll`, **`/home/site/wwwroot` has no published API** (no `WfhDiary.Api.runtimeconfig.json`).

1. Confirm **Deploy API** workflow succeeded and `AZURE_WEBAPP_NAME` matches Azure.
2. Kudu → `site/wwwroot` should contain `WfhDiary.Api.dll` and dependencies.
3. Restart the app; optional startup command: `dotnet /home/site/wwwroot/WfhDiary.Api.dll`.

## Tags (optional)

The provision script tags the resource group: `Project=wfh-diary`, `Environment=dev`, `ManagedBy=manual`. Add cost alerts in the portal filtered by that RG.
