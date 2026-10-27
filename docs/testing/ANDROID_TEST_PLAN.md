# Android test plan

Requirement IDs refer to [capability specs](../specs/README.md).

Companion: [`TEST_STRATEGY.md`](TEST_STRATEGY.md) · **Backlog:** [`../TODO.md`](../TODO.md) § End improvements B.

## Phase 1 (current)

JVM tests for pure helpers exist under `apps/android/app/src/test` (e.g. `GeofenceHelperTest`, `ApiErrorClassifierTest`); see each spec's Test coverage. Manual smoke: sign-in, month grid, edit day, export download. [AUTH-010, DAY-008, DAY-011, XLSX-011]

## Phase 2 — strong unit testing (deferred)

Implement after Compose UI + sync ship. Use `./gradlew :app:testDebugUnitTest`.

### Pure utilities (one test class per file)

| Subject | Cases |
| --- | --- |
| FY helpers | current FY from today's date, month list for FY [FY-001, FY-002] |
| Time parsing/formatting | match API `HH:mm` contract [DAY-003, DAY-005] |
| Total hours | `(finish - start) - break`, clamp at zero [DAY-006] |
| Day badge | Home / Office / Leave / empty from entry fields [DAY-008] |

### ViewModels

| Subject | Cases |
| --- | --- |
| `MonthGridViewModel` | load month, tap day emits edit state, summary from API [DAY-010, DAY-011, FY-010] |
| `DayEditViewModel` | save builds correct `UpsertDayEntryRequest` [DAY-011] |
| `SettingsViewModel` | defaults load/save [SET-008] |

### Notifications

| Subject | Cases |
| --- | --- |
| Home action | applies default home times + location Home [NOTIF-005] |
| Office action | applies default office times [NOTIF-005] |
| Leave range | reminder worker still runs but shows no reminder through the end date [NOTIF-003] |
| Skip when on leave | no notification fired [NOTIF-003, NOTIF-007] |

### Repository (fake Ktor)

| Subject | Cases |
| --- | --- |
| `DiaryRepository` | GET days deserializes; PUT sends camelCase JSON; Bearer header attached [AUTH-007] |

### Location auto-log (when Phase A ships)

| Subject | Cases |
| --- | --- |
| Geofence check | inside radius → that place's defaults (Office wins if both); no match → away fallback (default Office) [GEO-001, GEO-002, GEO-008] |

Suggested package: `dev.wfhdiary.app` under `app/src/test/java/...` mirroring production package layout.

## Backup & restore (on-device)

Manual checks for the [on-device-only change](../changes/2026-10-27-on-device-only/design.md) phase-1 gate. Results are recorded in that change's [`tasks.md`](../changes/2026-10-27-on-device-only/tasks.md#phase-1-gate-manual-on-phone). Automated coverage is listed in each spec's Test coverage table ([`backup`](../specs/backup/spec.md)). Phase 2 may only be planned once steps 2–5 pass. Do steps 2 and 3 before logging any day on the new build (the import replaces everything on the phone; notification actions count as logging).

1. Install the new build over the old one (GitHub Releases OTA or `adb install -r`). The Room v1 cache is dropped on first launch, so the grid is empty; that is expected. Restore and import are disabled until a folder is chosen.
2. Settings → Backup & restore → **Choose backup folder**; pick a folder that syncs off the phone (e.g. Google Drive-synced or Documents). The app is still empty on the upgrade path, so the message says "Nothing to back up yet" and no file is written (an existing same-day file is left untouched); the first `wfh-diary-backup-YYYY-MM-DD.json` appears after the import in step 3. [BAK-002, BAK-005]
3. **Import from old server** → confirm → the replace summary shows the current phone counts (0 days, 0 leave periods) → confirm. The month grid shows historic days for several FYs, Settings shows your name and geofences, and the result message says the backup was written. [BAK-011, BAK-009]
4. Drive folder overwrite check: after another backup the same day (e.g. **Back up now**), the day's file in the Drive folder has a new size or modified time, and the file opens as valid JSON. [BAK-005]
5. The in-app FY summary matches an XLSX export from the old server for the same FY; then export XLSX in the app and open it in Excel or Google Sheets: layout, colours and totals match. [XLSX-003..XLSX-009]
6. Edit a day; within about 10–15 minutes the backup file's modified time changes. [BAK-003]
7. Leave the phone for more than 24 hours with a folder set and no edits; the daily job writes a new day's backup. [BAK-004]
8. Clear a field in the edit sheet (e.g. remove the description) and save; it stays cleared after reopening. [DAY-010]
9. **Share backup…** opens the share sheet with a `.json` file. [BAK-007]
10. **Restore from file…** with the backup → the summary shows current phone counts → confirm. Data is unchanged, a `wfh-diary-pre-restore-*.json` file exists in the folder, and the message says a backup was written. [BAK-009]
11. Restore a non-backup file (any other JSON): it is refused with a message. [BAK-008]
12. Morning notification and "Home"/"Office" actions still log days. [NOTIF-005]
13. Airplane mode notification action: turn on airplane mode, tap "Home" or "Office" on the morning notification; the day is logged and a success notification shows. [NOTIF-005]
14. Revoke the folder (move or delete it) and tap **Back up now**. A failure message shows and the month-grid banner appears. [BAK-006]
15. Restore the post-import backup on a fresh install: uninstall (or clear app data), install, choose the same folder, **Restore from file…** with the backup written after step 3. Days, leave periods and settings match. [BAK-009, BAK-010]
