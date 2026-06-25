# WFH Diary — continuation backlog

Use this file to resume work (human or agent). **Architecture:** [`ARCHITECTURE.md`](ARCHITECTURE.md). **CI/CD:** [`CI_CD.md`](CI_CD.md).

## Repo state (as of last doc update)

| Area | Status |
| --- | --- |
| .NET solution (`WfhDiary.sln`) | Present |
| `WfhDiary.Api` — settings, diaries, days, leave, summary, xlsx export, **work templates** | Done |
| `WfhDiary.Shared` — DTOs (one type per file under `Dtos/`) | Done |
| API tests | Integration + unit + **auth gate** + **work templates** + contract fixtures |
| Android app (`dev.wfhdiary.app`) | Done — month grid, day edit, settings, notifications, OTA, **work templates + geofence missed-notification** |
| GitHub Actions | `ci.yml`, `deploy-api.yml`, `release-android.yml`, parallel test jobs |
| GitHub repos | Private [`wfh-diary`](https://github.com/Evzy/wfh-diary), public [`wfh-diary-releases`](https://github.com/Evzy/wfh-diary-releases) |
| Azure App Service | `app-wfh-diary-api-dev` provisioned; **SQLite on app disk** (ephemeral) |
| GitHub Releases OTA | **Working** — check/download/install from Settings |
| Firebase / `google-services.json` | **Ready to enable** — app requires sign-in when json present; API verifies Bearer when `Firebase__ServiceAccountJson` set |

## Ordered milestones

| # | Milestone | Status |
| --- | --- | --- |
| 1 | API core + xlsx export | Done |
| 2 | Android — month grid, sync, settings | Done |
| 3 | Morning notification (Home / Office / Add time / Leave) | Done |
| 4 | CI/CD — GitHub Actions + releases repo | Done |
| 5 | Missed notification default + leave delete + calendar nav | Done |
| 6 | Unit/contract tests + parallel CI | Done |
| 7 | Work templates + geofence on missed notification | Done (API + Android); day-edit template picker **not yet** |
| 8 | **Firebase on live API** | **Next manual step** — see [`FIREBASE_SETUP.md`](FIREBASE_SETUP.md) |
| 9 | Full documentation suite | Partial — add `ANDROID.md`, `API.md`; [`AZURE_DEPLOY.md`](AZURE_DEPLOY.md) done |

## Next manual steps

- [ ] **Full device setup:** follow [`DEVICE_AND_FIREBASE_SETUP.md`](DEVICE_AND_FIREBASE_SETUP.md) — new Firebase project, Maps key, sign-in, geofence
- [ ] Add GitHub secrets if not done: `WFH_RELEASES_TOKEN`, shared debug keystore (see [`CI_CD.md`](CI_CD.md))
- [ ] **Phase 2 Azure:** Postgres for durable data — [§ D](#d-managed-azure-database-postgresql)

## End improvements (after core ship)

### A. Location-aware auto logging — **partial (shipped for missed notification)**

Settings → **Home & office locations** — tap **Use current location** at home and/or office (200 m radius). When the morning reminder is ignored, the app checks GPS and applies your Home or Office default times. Optional **work templates** add more geofenced presets.

**Still to do:** proactive logging on arrival; background geofence monitoring.

### B. Strong unit testing — **partial**

| Item | Status |
| --- | --- |
| `TimeHelpers`, `SummaryCalculator`, `FinancialYearHelper` | Done |
| Contract JSON fixtures (incl. `work-template.json`) | Done |
| `AuthGateTests`, `WorkTemplateFlowTests` | Done |
| Android `GeofenceHelperTest`, contract deserialization | Done |
| Golden-file xlsx export | Backlog |
| `DiaryService` in-memory service tests | Backlog |
| Android ViewModel / repository MockEngine tests | Backlog |

See [`testing/TEST_STRATEGY.md`](testing/TEST_STRATEGY.md) and [`testing/API_TEST_PLAN.md`](testing/API_TEST_PLAN.md).

### C. Work scenario templates — **partial**

**Shipped:**

- API: `GET/POST/PUT/DELETE /v1/work-templates` with optional geofence fields
- Settings → **Manage templates** (label, times, location, description, lat/lng/radius)
- Missed notification: geofence match → apply template

**Backlog:**

1. **Day edit sheet** — “Apply template…” dropdown/chips
2. **Morning notification** — optional template quick actions
3. **Quick add activity** — same template picker
4. Reorder templates; mark **default** in UI (API supports `isDefault`)

**Data model:** table `work_templates` — `id`, `owner_user_id`, `label`, `sort_order`, times, location, description, `is_default`, `geofence_latitude`, `geofence_longitude`, `geofence_radius_meters`.

### D. Managed Azure database (PostgreSQL)

Replace **SQLite on App Service local disk** with a **managed online DB** so diary data survives restarts, redeploys, and scale events.

**Why:** App Service **F1** local disk is **not durable** — `wfh_diary_dev.db` can be wiped on restart or deploy.

**App setting:** `WfhDiary__ConnectionString` — API already switches to Npgsql when set.

**Priority:** after Firebase smoke test on device; data durability matters for day-to-day use.

## Conventions

- **One public type per `.cs` / `.kt` file**
- **Use Platra as the working reference** — see [Platra comparison](#platra-comparison)
- Bump **Last updated** in `ARCHITECTURE.md` when API or sync shape changes

## Platra comparison

| WFH Diary | Platra reference | Notes |
| --- | --- | --- |
| `wfh-diary` monorepo | `platra` | Same layout |
| `wfh-diary-releases` | `platra-releases` | **Public** APK-only; source **private** |
| Firebase Bearer auth | `Platra.Api/Auth/` | Same middleware pattern |
| GitHub Actions | `.github/workflows/` | Same patterns |
| In-app OTA | `GithubApkReleaseUpdater.kt` | Settings → update (**working**) |
| Android | `apps/android/` | Compose, Ktor, Room, WorkManager |
| Production DB | SQLite on App Service (both) | WFH: plan **Postgres** — [§ D](#d-managed-azure-database-postgresql) |

Local paths: `C:\Users\evanb\platra`, `C:\Users\evanb\wfh-diary`.
