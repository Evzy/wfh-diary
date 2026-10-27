# Backup & restore

**Prefix:** `BAK` · **Last updated:** 2026-10-27

## Purpose & scope

The Android app is the only place diary data lives (Room v2 tables `day_entries`, `leave_periods`, `settings`), so this capability keeps it recoverable. The app writes versioned JSON backups (format v1) to a folder the user chooses, automatically after changes and daily, and also on demand or through the share sheet. The user can restore from any backup file, which replaces all data after a safety backup. A phase-1-only importer copies the existing server data onto the phone through the same restore path. Out of scope: encryption of backup files, multi-device sync or merge-on-restore, and cloud storage beyond whatever the chosen folder's document provider (for example Google Drive) syncs.

## Requirements

### BAK-001 — Backup document v1

Given app data, when a backup is produced, then it is JSON with `format`, `version: 1`, `exportedAt`, `appVersion`, `settings`, `days` (only non-empty days), `leavePeriods`, and `legacy`.

- **Given** app data with settings, days, leave periods and (optionally) legacy content
- **When** a backup is produced
- **Then** the JSON has `format` `wfh-diary-backup`, `version` `1`, `exportedAt`, `appVersion`, `settings`, `days` (non-empty days only), `leavePeriods` and `legacy` (always written; an empty object when there is no legacy content, and a missing `legacy` reads as empty)

### BAK-002 — Choose backup folder

Given Settings → Backup, when the user picks a folder, then its URI permission is persisted and used for automatic backups.

- **Given** Settings → Backup
- **When** the user taps **Choose backup folder** and picks a folder
- **Then** the folder's URI permission is persisted and used for automatic backups; a backup file is written immediately

### BAK-003 — Automatic backup after changes

Given a folder is set, when data changes, then a backup is written about 10 minutes after the last change.

- **Given** a backup folder is set
- **When** data changes (any `DiaryStore` write)
- **Then** a backup is written about 10 minutes after the last change (a unique WorkManager job is re-enqueued with a 10-minute delay on every change)

### BAK-004 — Daily backup

Given a folder is set, when the 24-hour periodic job runs, then a backup is written even without changes.

- **Given** a backup folder is set
- **When** the 24-hour periodic job runs
- **Then** a backup is written even without changes

### BAK-005 — File naming and retention

Given backups in the folder, when a backup is written, then it is named `wfh-diary-backup-YYYY-MM-DD.json`, overwrites same-day, and only the newest 30 such files are kept.

- **Given** backups in the folder
- **When** a backup is written
- **Then** it is named `wfh-diary-backup-YYYY-MM-DD.json`, overwrites a same-day file, and only the newest 30 such files are kept (other files are never deleted)

### BAK-006 — Backup status and warning

Given backup state, then Settings shows last success/error, and the month grid shows a warning when no folder is set, the last attempt failed, or the last success is older than 3 days.

- **Given** backup state (folder, last success time, last error)
- **When** Settings or the month grid is shown
- **Then** Settings shows last success/error, and the month grid shows a warning when no folder is set, the last attempt failed, or the last success is older than 3 days

### BAK-007 — Manual backup and share

Given Settings → Backup, when "Back up now" or "Share backup…" is tapped, then a backup is written to the folder or shared via the share sheet.

- **Given** Settings → Backup
- **When** the user taps **Back up now** or **Share backup…**
- **Then** a backup is written to the folder, or a freshly written cache file is shared via the share sheet

### BAK-008 — Restore validation

Given a selected file, when it has the wrong `format`, an unsupported `version`, or invalid dates/times/break hours, then restore is refused with a message and no data changes.

- **Given** a selected file
- **When** it has the wrong `format`, an unsupported `version`, or invalid dates/times/break hours
- **Then** restore is refused with a message and no data changes

### BAK-009 — Restore replaces all data safely

Given a backup folder and a valid file, when the user confirms the summary, then a safety backup of current data is written to the folder first and all tables are replaced in one transaction; notifications are rescheduled and a fresh backup is written.

- **Given** a chosen backup folder, a valid file and the confirmation summary (days, leave periods, settings present, `exportedAt`, and the number of days and leave periods currently on the phone that will be replaced)
- **When** the user confirms
- **Then** a safety backup of current data is written to the backup folder first and all tables are replaced in one transaction; notifications are rescheduled; a backup is then written and the result message says whether it succeeded (with the reason if not)
- Restore and import are disabled until a backup folder is chosen, and are refused if attempted without one. If the safety backup cannot be written to the folder, the restore is refused with the reason and nothing is changed (there is no cache fallback). A notification-rescheduling failure after the data is committed does not turn the restore into a failure

### BAK-010 — Legacy passthrough

Given a backup with `legacy` content, when restored and later backed up, then `legacy` is preserved unchanged.

- **Given** a backup with `legacy` content
- **When** it is restored and a later backup is produced (including after a settings update)
- **Then** `legacy` is preserved unchanged (stored raw in `settings.legacyJson`)

### BAK-011 — One-time server import (phase 1 only)

**Phase 1 only; REMOVED in phase 2.** Given the old API is reachable, when "Import from old server" is confirmed, then settings, leave periods, templates (into `legacy`), and non-empty days for FY 2020..current + 1 are imported via the restore path and a backup is written; any fetch failure writes nothing.

- **Given** the old API is reachable and a backup folder is chosen
- **When** the user confirms **Import from old server** (and the replace summary)
- **Then** settings, leave periods, templates (into `legacy`), and non-empty days for FY 2020 through the next FY (current + 1) are imported via the restore path and a backup is written; any fetch failure writes nothing

## Interfaces

| Kind | Location |
| --- | --- |
| Backup format | `apps/android/app/src/main/java/dev/wfhdiary/app/backup/` — `BackupDocument.kt`, `BackupDay.kt`, `BackupLeavePeriod.kt`, `BackupParseResult.kt`, `BackupCodec.kt` |
| Folder & status | `backup/BackupPreferences.kt`, `BackupFolder.kt`, `SafBackupFolder.kt`, `BackupRetention.kt`, `BackupOutcome.kt`, `BackupHealth.kt` |
| Scheduling & write | `backup/BackupService.kt`, `BackupWorker.kt`, `BackupScheduler.kt`, `BackupShareHelper.kt` |
| Restore | `backup/RestoreService.kt`, `RestoreOutcome.kt`; `data/store/DiaryStore.kt` (`exportDocument`, `replaceAll`) |
| Import (phase 1 only) | `backup/LegacyServerImporter.kt` |
| Android UI | `apps/android/app/src/main/java/dev/wfhdiary/app/ui/backup/` — `BackupSection.kt`, `BackupViewModel.kt`, `BackupUiState.kt`; month-grid banner in `ui/month/MonthGridScreen.kt` |
| Storage | `data/local/` (Room v2: `DayEntryEntity`, `LeavePeriodEntity`, `SettingsEntity`); device-local backup state (folder URI, last success, last error) lives in `SharedPreferences` and is not part of backups |

## Test coverage

| Requirement | Tests |
| --- | --- |
| BAK-001 | `BackupCodecTest.encode_includes_format_and_version`, `BackupCodecTest.round_trip_preserves_document`, `BackupCodecTest.encode_always_includes_legacy`, `BackupCodecTest.missing_legacy_decodes_as_empty`; `DiaryStoreTest.export_document_and_replace_all_round_trip` |
| BAK-002 | Manual — [Android test plan](../../testing/ANDROID_TEST_PLAN.md#backup--restore-on-device) (Settings → Choose backup folder) |
| BAK-003 | Manual — [Android test plan](../../testing/ANDROID_TEST_PLAN.md#backup--restore-on-device) (`DiaryStoreTest.writes_emit_change_events` covers the change signal only) |
| BAK-004 | Manual — [Android test plan](../../testing/ANDROID_TEST_PLAN.md#backup--restore-on-device) |
| BAK-005 | `BackupRetentionTest.file_name_uses_iso_date`, `BackupRetentionTest.keeps_newest_thirty_and_ignores_other_files`; `BackupServiceTest.writes_todays_file_and_records_success`, `BackupServiceTest.prunes_old_files`, `BackupServiceTest.prune_failure_after_write_is_still_success` |
| BAK-006 | `BackupHealthTest.no_folder_warns`, `BackupHealthTest.last_error_warns`, `BackupHealthTest.stale_after_three_days`; `BackupServiceTest.failure_is_recorded`, `BackupServiceTest.no_folder_returns_no_folder`; banner and Settings status text are manual |
| BAK-007 | Manual — [Android test plan](../../testing/ANDROID_TEST_PLAN.md#backup--restore-on-device) (`BackupServiceTest.writes_todays_file_and_records_success` covers the folder write) |
| BAK-008 | `BackupCodecTest.malformed_json_is_refused`, `wrong_format_is_refused`, `newer_version_is_refused`, `invalid_day_date_is_refused`, `invalid_time_is_refused`, `negative_break_is_refused`; `RestoreServiceTest.invalid_document_is_refused_without_changes` |
| BAK-009 | `RestoreServiceTest.restore_writes_safety_backup_replaces_data_and_reschedules`, `RestoreServiceTest.restore_is_refused_without_a_backup_folder`, `RestoreServiceTest.restore_is_refused_when_safety_write_fails`, `RestoreServiceTest.on_restored_failure_does_not_fail_committed_restore`, `RestoreServiceTest.on_restored_cancellation_is_rethrown`; `BackupServiceTest.safety_backup_goes_to_folder`, `BackupServiceTest.safety_backup_needs_folder_and_reports_write_failure`; `DiaryStoreTest.replace_all_rolls_back_when_a_step_fails`, `DiaryStoreTest.local_counts_reports_days_and_leave_periods`; disabled buttons, summary counts and post-restore backup message are manual |
| BAK-010 | `BackupCodecTest.legacy_section_is_preserved`; `DiaryStoreTest.update_settings_preserves_legacy`, `DiaryStoreTest.export_document_and_replace_all_round_trip`; `RestoreServiceTest.restore_round_trips_leave_periods_and_legacy` |
| BAK-011 | `LegacyServerImporterTest.imports_all_fys_skipping_empty_days`, `LegacyServerImporterTest.any_failure_throws` |

## Known gaps

- **Stricter validation than the design:** `BackupCodec` also refuses `version` < 1, a non-primitive `format` or `version` (object or array), negative settings break hours, and duplicate or blank leave ids; it swaps a reversed leave range (as LEAVE-003) rather than refusing. These cases are covered by `BackupCodecTest` but go beyond the BAK-008 text.
- **Import refreshes the Firebase ID token first:** the phase-1 import flow force-refreshes the token before fetching so an expired token does not abort the import. Removed together with the importer in phase 2.
- **Backup dialogs reuse an existing string:** their cancel button uses `time_picker_cancel` rather than a dedicated `common_cancel` string.
- **Folder selection failures show a message:** a failed or revoked folder grant shows a message in Settings instead of crashing.
- **Daily and after-change scheduling is not unit-tested:** WorkManager scheduling (BAK-003, BAK-004) and the SAF folder (BAK-002) rely on the manual device checklist.
- **No encryption:** backup files are plain JSON in the chosen folder.
- **Work templates are not editable on-device:** server templates are carried only inside `legacy` (BAK-010) until a future change decides otherwise.

## Revision log

- 2026-10-27: Initial spec for on-device storage, backups and restore; BAK-011 is phase 1 only ([change](../../changes/2026-10-27-on-device-only/design.md)).
- 2026-10-27: Final-review fixes ([change](../../changes/2026-10-27-on-device-only/design.md)). BAK-001: `legacy` is always written. BAK-009: restore and import require a chosen backup folder; the safety backup has no cache fallback and its failure refuses the restore with the reason; the summary shows the counts being replaced; the post-restore backup result is reported. BAK-011: import fetches FY 2020 through current + 1. A prune failure after a successful write no longer records a backup failure (BAK-005).
