# Android test plan

Requirement IDs refer to [capability specs](../specs/README.md).

Companion: [`TEST_STRATEGY.md`](TEST_STRATEGY.md) · **Backlog:** [`../TODO.md`](../TODO.md) § End improvements B.

## Current

JVM and Robolectric tests exist under `apps/android/app/src/test` (e.g. `GeofenceHelperTest`, `DiaryStoreTest`, `BackupCodecTest`, `DayLogNotificationUpdaterTest`); see each spec's Test coverage. Manual smoke: the [app smoke checks](#app-smoke-on-device) below, plus month grid, edit day and export. [DAY-008, DAY-011, XLSX-011]

### App smoke (on device)

1. Fresh install (or clear app data) opens straight to the month grid; there is no sign-in screen. [DAY-008]
2. With a newer release published to `wfh-diary-releases`, launching the app shows the "Update available" dialog once; **Not now** dismisses it and **Download and install** opens the installer. [OTA-009, OTA-011]

The "Couldn't save entry" failure notification is covered by `DayLogNotificationUpdaterTest`; a local write failure can't be triggered on a phone. [NOTIF-005, NOTIF-008]

## Strong unit testing (backlog)

Not yet implemented beyond the tests listed above. Use `./gradlew :app:testDebugUnitTest`.

### Pure utilities (one test class per file)

| Subject | Cases |
| --- | --- |
| FY helpers | current FY from today's date, month list for FY [FY-001, FY-002] |
| Time parsing/formatting | `HH:mm` / `H:mm` in, `HH:mm` out [DAY-003, DAY-005] |
| Total hours | `(finish - start) - break`, clamp at zero [DAY-006] |
| Day badge | Home / Office / Leave / empty from entry fields [DAY-008] |

### ViewModels

| Subject | Cases |
| --- | --- |
| `MonthGridViewModel` | load month, tap day emits edit state, summary from `DiaryStore` [DAY-010, DAY-011, FY-010] |
| `DayEditViewModel` | save builds correct `DayEntryUpdate` [DAY-011] |
| `SettingsViewModel` | defaults load/save [SET-008] |

### Notifications

| Subject | Cases |
| --- | --- |
| Home action | applies default home times + location Home [NOTIF-005] |
| Office action | applies default office times [NOTIF-005] |
| Leave range | reminder worker still runs but shows no reminder through the end date [NOTIF-003] |
| Skip when on leave | no notification fired [NOTIF-003, NOTIF-007] |

### Location auto-log (when Phase A ships)

| Subject | Cases |
| --- | --- |
| Geofence check | inside radius → that place's defaults (Office wins if both); no match → away fallback (default Office) [GEO-001, GEO-002, GEO-008] |

Suggested package: `dev.wfhdiary.app` under `app/src/test/java/...` mirroring production package layout.

## Backup & restore (on-device)

Manual checks for on-device storage and backups ([on-device-only change](../changes/2026-10-27-on-device-only/design.md)). Phase-1 gate results (which included the now-removed server import) are recorded in that change's [`tasks.md`](../changes/2026-10-27-on-device-only/tasks.md#phase-1-gate-manual-on-phone). Automated coverage is listed in each spec's Test coverage table ([`backup`](../specs/backup/spec.md)).

1. Restore is disabled until a backup folder is chosen.
2. Settings → Backup & restore → **Choose backup folder**; pick a folder that syncs off the phone (e.g. Google Drive-synced or Documents). On an empty app the message says "Nothing to back up yet" and no file is written (an existing same-day file is left untouched); otherwise `wfh-diary-backup-YYYY-MM-DD.json` is written. [BAK-002, BAK-005]
3. Drive folder overwrite check: after another backup the same day (e.g. **Back up now**), the day's file in the Drive folder has a new size or modified time, and the file opens as valid JSON. [BAK-005]
4. Export XLSX in the app and open it in Excel or Google Sheets: layout, colours and totals match the in-app FY summary. [XLSX-003..XLSX-009]
5. Edit a day; within about 10–15 minutes the backup file's modified time changes. [BAK-003]
6. Leave the phone for more than 24 hours with a folder set and no edits; the daily job writes a new day's backup. [BAK-004]
7. Clear a field in the edit sheet (e.g. remove the description) and save; it stays cleared after reopening. [DAY-010]
8. **Share backup…** opens the share sheet with a `.json` file. [BAK-007]
9. **Restore from file…** with the backup → the summary shows current phone counts → confirm. Data is unchanged, a `wfh-diary-pre-restore-*.json` file exists in the folder, and the message says a backup was written. [BAK-009]
10. Restore a non-backup file (any other JSON): it is refused with a message. [BAK-008]
11. Morning notification and "Home"/"Office" actions still log days. [NOTIF-005]
12. Airplane mode notification action: turn on airplane mode, tap "Home" or "Office" on the morning notification; the day is logged and a success notification shows. [NOTIF-005]
13. Revoke the folder (move or delete it) and tap **Back up now**. A failure message shows and the month-grid banner appears. [BAK-006]
14. Restore on a fresh install: uninstall (or clear app data), install, choose the same folder, **Restore from file…** with a recent backup. Days, leave periods and settings match. [BAK-009, BAK-010]
