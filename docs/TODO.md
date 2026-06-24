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
| 5 | Full documentation suite | Partial — add `ANDROID.md`, `API.md`; [`AZURE_DEPLOY.md`](AZURE_DEPLOY.md) done |

## Next manual steps

- [ ] Add GitHub secrets: `WFH_RELEASES_TOKEN`, shared debug keystore (see [`CI_CD.md`](CI_CD.md))
- [ ] **Phase 2 Azure:** run [`scripts/azure/provision-wfh-diary-dev.ps1`](../scripts/azure/provision-wfh-diary-dev.ps1) + OIDC in [`CI_CD.md`](CI_CD.md) (see [`AZURE_DEPLOY.md`](AZURE_DEPLOY.md))
- [ ] Add `apps/android/app/google-services.json` + Firebase SHA-1 for release/sign-in
- [ ] First device test: API on `:5222`, install debug APK, log a day, export xlsx

## End improvements (after core ship)

### A. Location-aware auto logging

Office geofence → auto Office; otherwise default Home. See prior spec in git history / plan.

### B. Strong unit testing

Golden-file xlsx test, `DiaryService` tests, Android ViewModel/notification tests. See [`testing/TEST_STRATEGY.md`](testing/TEST_STRATEGY.md).

### C. Work scenario templates (quick pick)

User-defined **repeatable work scenarios** so daily logging is mostly a dropdown, not typing.

**User story:** “Most days I do the same kind of work with the same hours. I want a list of my common scenarios — each with optional times, location, and a pre-filled description — and I pick one when editing a day or from the morning notification.”

**Example templates:**

| Label | Start | Finish | Break | Location | Description |
| --- | --- | --- | --- | --- | --- |
| Standard WFH day | 08:00 | 17:00 | 1.0 | Home | General development |
| Office workshop | 08:30 | 17:00 | 1.0 | Office | In-office SQL workshop |
| Short Friday | 08:00 | 14:00 | 0.5 | Home | Wrapping up weekly tasks |

**UX touchpoints:**

1. **Settings → Work templates** — add / edit / reorder / delete list (label + times + location + description). One template can be marked **default** (used for morning Home dismiss and global defaults).
2. **Day edit sheet** — dropdown or chip row at top: “Apply template…” fills start, finish, break, location, description; user can still tweak before Save.
3. **Morning notification** — optional extra actions or a sub-menu: pick a template in one tap (instead of only generic Home/Office defaults).
4. **Quick add activity** — same template dropdown when adding time from notification.

**Data model (sketch):**

- New table `work_templates` (or JSON array on `user_settings`): `id`, `owner_user_id`, `label`, `sort_order`, `start_time`, `finish_time`, `break_hours`, `location`, `description`, `is_default`.
- API: `GET/POST/PUT/DELETE /v1/work-templates`; applying a template is **client-side** (fills `UpsertDayEntryRequest` then existing day PUT).

**Scope notes:**

- Templates are **per user**, not per FY — reuse across financial years.
- Export/xlsx unchanged: only the resulting day row matters.
- Fits well **before** location geofence (C could ship earlier than A).

**Android:** one type per file — e.g. `WorkTemplateDto.kt`, `WorkTemplateRepository.kt`, `WorkTemplatesSettingsScreen.kt`, `WorkTemplatePicker.kt` (shared composable for day sheet + quick add).

**Platra comparison:** no direct equivalent; this is WFH-diary-specific productivity UX.

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
