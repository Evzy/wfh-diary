# On-device only (remove backend) — design

**Status:** in-progress · **Date:** 2026-10-27 · **Touches specs:** [day-entries](../../specs/day-entries/spec.md), [summary-fy](../../specs/summary-fy/spec.md), [leave](../../specs/leave/spec.md), [settings](../../specs/settings/spec.md), [export-xlsx](../../specs/export-xlsx/spec.md), [notifications](../../specs/notifications/spec.md), [ota-updates](../../specs/ota-updates/spec.md), [auth](../../specs/auth/spec.md), [work-templates](../../specs/work-templates/spec.md), [geolocation](../../specs/geolocation/spec.md), new `backup`

## Problem

All diary data lives in an Azure-hosted ASP.NET Core API (Azure SQL, optional Firebase auth). The app is single-user (the owner only), and the backend adds hosting cost, cold starts (a keep-warm workflow exists), network failure modes in every notification action, and operational surface (Azure, Firebase, deploy workflows). Losing the backend must not mean losing data.

## Goals

- The Android app is the **only** place data lives; it works fully offline.
- **Automatic backups** to a user-chosen folder, plus manual export/share and **restore** from a backup file.
- **One-time import** of all existing server data onto the phone.
- **Remove the backend completely**: API code, shared DTO package, solution, deploy/keep-warm workflows, Firebase sign-in, and Azure resources.
- Keep: month grid, day edit, leave, settings, notifications, Maps-based geolocation (on-device, Maps SDK key), on-device xlsx export, GitHub OTA updates.

## Non-goals

- Work templates (dropped; server templates are preserved in backups under `legacy`).
- Multi-device sync or merge-on-restore.
- Fixing existing behavioural gaps recorded in specs (e.g. untrimmed location matching, cross-FY leave) — separate changes. Exception: gaps caused purely by the HTTP layer disappear naturally (e.g. Android cannot clear a field).
- Encryption of backup files.

## Approach

Room becomes the single source of truth. A `DiaryStore` replaces `DiaryRepository` with equivalent suspend functions so ViewModels and workers change minimally. Server rules are ported to Kotlin. Backups are versioned JSON. The xlsx workbook is produced on-device by a small OOXML writer (zip + XML), no third-party spreadsheet library.

```
UI / ViewModels / Workers
        │  getDays, upsertDay, clearDay, createLeave, deleteLeave, getLeavePeriods, getSummary, get/updateSettings
        ▼
  DiaryStore
   ├─ Room DB v2: day_entries · leave_periods · settings (single row)
   ├─ Ported rules: time parsing, total hours, leave mark/clear, FY summary
   └─ change signal ──► BackupScheduler (WorkManager)
  BackupService   ── JSON v1 ──► chosen folder (auto) / share sheet (manual)
  RestoreService  ◄── JSON v1   (parse → validate → safety backup → replace all in one transaction)
  XlsxWriter      (port of XlsxExportService)
  LegacyServerImporter (phase 1 only; Ktor → RestoreService path)
```

### Local data model (Room v2)

| Table | Columns |
| --- | --- |
| `day_entries` | `date` TEXT PK (`yyyy-MM-dd`), `startTime` TEXT?, `finishTime` TEXT?, `breakHours` REAL?, `description` TEXT?, `location` TEXT?, `updatedAtMillis` INTEGER |
| `leave_periods` | `id` TEXT PK (UUID), `startDate` TEXT, `endDate` TEXT, `note` TEXT?, `createdAtMillis` INTEGER |
| `settings` | single row `id = 1`; same fields as current `UserSettingsDto` (display name, notification time/mask, missed-notification fields, geolocation skip flags, Home/Office defaults and descriptions, Home/Office geofences), plus `legacyJson` TEXT? (raw backup `legacy` object) |

Device-local state that is **not** part of backups (backup folder URI, last backup success time, last backup error) lives in `SharedPreferences`.

- FY, day name, and total hours are computed, not stored. Only days with data are stored; the grid synthesises empty days.
- Settings defaults equal the server's `UserSettingsEntity` defaults.
- v1 → v2: destructive (v1 `day_entries` was a cache). Real data arrives via import or restore.
- A day row with all fields null is deleted (equivalent of the server's "clear").

### Backup format (v1)

```json
{
  "format": "wfh-diary-backup",
  "version": 1,
  "exportedAt": "2026-10-27T18:40:00+11:00",
  "appVersion": "1.4.12",
  "settings": { "...": "UserSettings fields" },
  "days": [ { "date": "2025-07-01", "startTime": "08:00", "finishTime": "16:30", "breakHours": 0.5, "description": "...", "location": "Home" } ],
  "leavePeriods": [ { "id": "uuid", "startDate": "2025-12-22", "endDate": "2026-01-02", "note": "Xmas" } ],
  "legacy": { "workTemplates": [ ] }
}
```

- Unknown fields are ignored on read; `legacy` is carried through restore → next backup unchanged (stored in `settings.legacyJson`).
- Readers reject `format` ≠ `wfh-diary-backup` or `version` > supported.

### Automatic backup

- Settings → **Backup**: "Choose backup folder" (`ACTION_OPEN_DOCUMENT_TREE`, persisted URI permission). Works with local storage and Google Drive's document provider.
- `BackupScheduler`: unique WorkManager job enqueued with 10-minute delay (REPLACE) after any data change; plus a daily periodic job.
- Writes `wfh-diary-backup-YYYY-MM-DD.json` (same-day overwrite); keeps the newest 30 matching files, deletes older ones.
- Persists last success time and last error. Settings shows status. Month grid shows a warning banner when no folder is set, the last attempt failed, or the last success is older than 3 days.
- Manual: "Back up now" (folder) and "Share backup…" (share sheet with a freshly written cache file).

### Restore

0. Restore (and import) are disabled until a backup folder is chosen.
1. Pick a file (`ACTION_OPEN_DOCUMENT`).
2. Parse and validate: format/version; every date `yyyy-MM-dd`; times `HH:mm`; `breakHours` ≥ 0; leave `startDate ≤ endDate` (swap if reversed, matching LEAVE-003).
3. Show summary (days, leave periods, settings present, `exportedAt`, and the day and leave-period counts currently on the phone that will be replaced) and confirm.
4. Write a safety backup of current data to the backup folder. If that write fails, refuse the restore with the reason; there is no cache fallback.
5. Replace all tables in one Room transaction. Reschedule notifications.
6. Run "Back up now" and report whether that backup was written.

### One-time import (phase 1 only)

- Settings → **Import from old server** (visible only in phase-1 builds).
- Uses the existing Ktor client/auth. Fetches settings, leave periods, work templates, and `GET /v1/diaries/{fy}/days` for FY 2020 through the next FY (current + 1, so leave already booked after 1 July is kept), keeping only days with any non-null field.
- Builds an in-memory backup v1 document and runs it through Restore steps 2–6 (with summary/confirm), so the follow-up "Back up now" result is reported.
- Any HTTP failure aborts with nothing written; the user can retry.

### Rollout

**Phase 1 release** (backend still running): DiaryStore, Room v2, backups, restore, on-device xlsx, importer. Installed via existing OTA. User imports, compares grid with server, confirms a backup file exists in the folder.

**Phase 2 release** (after user confirms phase 1):

- App: remove importer, `data/remote/` Ktor code, Firebase auth (`data/auth/`, `ui/auth/`, login gate), `google-services.json` + Google services plugin, `ApiErrorClassifier`, `DayLogRetryPolicy` and retry/pending notification paths, `AZURE_API_BASE_URL`. Keep Maps key, geolocation, `GithubApkReleaseUpdater` (auto check moves from login screen to app start).
- Repo: delete `services/`, `packages/shared/`, `WfhDiary.sln`, `.github/workflows/deploy-api.yml`, `keep-api-warm.yml`; remove .NET jobs from `ci.yml` / `reusable-tests.yml`; delete or rewrite `AZURE_DEPLOY.md`, `AZURE_DATABASE_PLAN.md`, `FIREBASE_SETUP.md`, `DEVICE_AND_FIREBASE_SETUP.md`, `testing/API_TEST_PLAN.md`; update `ARCHITECTURE.md`, `TODO.md`, `AGENTS.md`, `README.md`, `CONSTITUTION.md` (principle 5 contract fixtures removed).
- Azure: `scripts/azure/decommission.ps1` deletes the resource group after a typed confirmation. Run manually by the user after phase 1 is confirmed.

## Requirement delta

### New spec `backup` (prefix `BAK`)

- **ADDED** `BAK-001` — Backup document v1. Given app data, when a backup is produced, then it is JSON with `format`, `version: 1`, `exportedAt`, `appVersion`, `settings`, `days` (only non-empty days), `leavePeriods`, and `legacy`.
- **ADDED** `BAK-002` — Choose backup folder. Given Settings → Backup, when the user picks a folder, then its URI permission is persisted and used for automatic backups. Folder backups are skipped while the app holds no data, so a fresh install never overwrites a real backup with an empty one.
- **ADDED** `BAK-003` — Automatic backup after changes. Given a folder is set, when data changes, then a backup is written about 10 minutes after the last change.
- **ADDED** `BAK-004` — Daily backup. Given a folder is set, when the 24-hour periodic job runs, then a backup is written even without changes.
- **ADDED** `BAK-005` — File naming and retention. Given backups in the folder, when a backup is written, then it is named `wfh-diary-backup-YYYY-MM-DD.json`, overwrites same-day, and only the newest 30 such files are kept.
- **ADDED** `BAK-006` — Backup status and warning. Given backup state, then Settings shows last success/error, and the month grid shows a warning when no folder is set, the last attempt failed, or the last success is older than 3 days.
- **ADDED** `BAK-007` — Manual backup and share. Given Settings → Backup, when "Back up now" or "Share backup…" is tapped, then a backup is written to the folder or shared via the share sheet.
- **ADDED** `BAK-008` — Restore validation. Given a selected file, when it has the wrong `format`, an unsupported `version`, or invalid dates/times/break hours, then restore is refused with a message and no data changes.
- **ADDED** `BAK-009` — Restore replaces all data safely. Given a valid file, when the user confirms the summary, then a safety backup of current data is written to the backup folder first and all tables are replaced in one transaction; notifications are rescheduled. Restore requires a chosen backup folder and is refused if the safety backup cannot be written.
- **ADDED** `BAK-010` — Legacy passthrough. Given a backup with `legacy` content, when restored and later backed up, then `legacy` is preserved unchanged.
- **ADDED** `BAK-011` — One-time server import (phase 1 only; REMOVED in phase 2). Given the old API is reachable, when "Import from old server" is confirmed, then settings, leave periods, templates (into `legacy`), and non-empty days for FY 2020..current + 1 are imported via the restore path and a backup is written; any fetch failure writes nothing.

### Modified

- **MODIFIED** `DAY-001`..`DAY-007` — Rewritten as `DiaryStore` behaviour: same partial-update, validation, total-hours and clear semantics, but local; no per-user scoping, no HTTP status codes. Partial updates distinguish "not provided" from "clear" (clearing a field works).
- **MODIFIED** `DAY-010` — Room is the source of truth; no sync or cache staleness.
- **MODIFIED** `FY-003` — Summary computed on-device by the ported calculator; same results.
- **MODIFIED** `LEAVE-001`..`LEAVE-008` — Local; "404" cases become no-ops.
- **MODIFIED** `SET-001`, `SET-002`, `SET-007`, `SET-008` — Single local settings row with defaults; auto-save writes locally; contract requirement replaced by backup schema (BAK-001).
- **MODIFIED** `XLSX-001`, `XLSX-002`, `XLSX-011` — Workbook generated on-device by `XlsxWriter` then shared; workbook content requirements `XLSX-003`..`XLSX-009` unchanged.
- **MODIFIED** `NOTIF-005`, `NOTIF-008` — Actions write locally; success notification only.
- **MODIFIED** `OTA-009` — Automatic update check runs at app start (no login screen).
- **MODIFIED** `GEO-006` — Clear writes local settings (server semantics now in SET-006 locally).

### Removed (phase 2)

- **REMOVED** `NOTIF-009`, `NOTIF-010` — No network failures to classify/retry.
- **REMOVED** `OTA-001`, `OTA-002` — No API.
- **REMOVED** `AUTH-001`..`AUTH-011` — No server, no sign-in. Auth spec marked retired.
- **REMOVED** `TPL-001`..`TPL-008` — Work templates dropped. Work-templates spec marked retired.
- **REMOVED** `BAK-011` — Importer removed in phase 2.

## Test impact

- JVM unit tests: ported `TimeHelpers`/`SummaryCalculator` (migrate API test cases), leave mark/clear rules, backup serialize/parse round-trip, restore validation failures, retention selection (which files to delete), xlsx writer (unzip; assert sheet name, headers, key cells and formulas per XLSX-003..009).
- Importer: Ktor `MockEngine` serving `packages/shared/contracts/` fixtures (copied into Android test resources before phase 2 deletes them).
- Room DAO/transaction tests via Robolectric.
- Manual device checklist: import, compare with server, backup to Drive folder, restore on a fresh install.
- Phase 2: all .NET tests deleted with the API; Android contract tests (`ContractDeserializationTest`) replaced by backup-format tests.

## Risks

- **Data loss during migration** — mitigated: phase 1 keeps server running; import is all-or-nothing; immediate backup; user verification gate before phase 2 and before Azure deletion.
- **Backup folder permission revoked / Drive provider unavailable** — status + grid banner surface failures within 3 days; manual share always available.
- **xlsx fidelity** — writer covered by cell/formula tests against existing XLSX requirements.
- **Phone loss before a backup** — limited to at most one day's changes when a folder is set.
