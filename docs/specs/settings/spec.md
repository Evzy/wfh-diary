# User settings

**Prefix:** `SET` · **Last updated:** 2026-10-27

## Purpose & scope

Preferences stored on the phone (a single Room settings row, handled by `DiaryStore`) and edited on the Android Settings screen: display name, morning reminder time and working days, missed-reminder response mode and apply-at time, and the default Home and Office schedules (start, finish, break, description) used by quick actions, shown in the **Home & office presets** section. Out of scope: the geolocation settings sheet, map picker and geofence capture UI (see [geolocation](../geolocation/spec.md)), work templates (retired; see [work-templates](../work-templates/spec.md)), notification scheduling and delivery (see [notifications](../notifications/spec.md)), XLSX export ([export-xlsx](../export-xlsx/spec.md)) and OTA updates ([ota-updates](../ota-updates/spec.md)), which share the Settings screen but have their own specs.

## Requirements

### SET-001 — Single local row with defaults

`DiaryStore.getSettings` returns the single local settings row (`id = 1`); when no row exists it returns the defaults, which equal the former server defaults.

- **Given** no stored settings row
- **When** the app reads settings
- **Then** the result has display name `YOUR NAME HERE`, notification time `08:00`, working-days mask `31` (Mon–Fri), missed default `None`, apply-at `12:00`, away default `Office`, both skip flags `false`, Home and Office `08:00`–`17:00` with a 1-hour break, null descriptions and null geofences

### SET-002 — Partial update

`DiaryStore.updateSettings` updates only the fields that are non-null in the request, writes the single local row and returns the full updated settings; any `legacy` content stored with the row is kept.

- **Given** existing settings
- **When** the app saves a request with some fields set and the rest `null`
- **Then** the set fields are changed, `null` fields keep their previous values, and a later read returns the same values

### SET-003 — Unparseable times are ignored

Time fields (`notificationTime`, `missedNotificationApplyAt`, Home/Office start and finish) that are not `HH:mm` / `H:mm` are silently ignored.

- **Given** existing settings
- **When** the app saves a time field such as `"25:99"` or `"soon"`
- **Then** the save still succeeds and that field keeps its previous value

### SET-004 — Mode fields fall back on unknown values

`missedNotificationDefault` accepts only `Home`, `Office`, `Leave`, `Geolocation` or `None` (falling back to `None`); `missedNotificationAwayDefault` accepts only `Home`, `Office` or `Leave` (falling back to `Office`).

- **Given** existing settings
- **When** the app saves an unrecognised value (after trimming) for either field
- **Then** the save succeeds and the stored value becomes `None` or `Office` respectively

### SET-005 — Default descriptions are trimmed or cleared

Home and Office default descriptions are trimmed, and a whitespace-only value clears the description.

- **Given** existing settings
- **When** the app saves `defaultHomeDescription` or `defaultOfficeDescription`
- **Then** a non-blank value is stored trimmed and a blank value (e.g. `" "`) is stored as `null`

### SET-006 — Geofence set requires all three fields; clear wins

A home or office geofence is only updated when latitude, longitude and radius are all supplied, and `clearHomeGeofence` / `clearOfficeGeofence` set to `true` nulls all three, taking precedence over supplied values. This requirement owns the stored geofence semantics (`DiaryRules.applySettingsUpdate`); the app's Clear action is [GEO-006](../geolocation/spec.md#geo-006--clear-geofence).

- **Given** existing settings
- **When** the app saves all three geofence fields, or the clear flag
- **Then** the geofence is stored as sent, or all three fields become `null`; a partial set (e.g. latitude only) changes nothing

### SET-007 — Settings shape is the backup schema

The settings object (`UserSettingsDto`) is the `settings` object of the backup document ([BAK-001](../backup/spec.md#bak-001--backup-document-v1)); its serialized field names are part of the backup format, and backup validation of that object replaces the former contract requirement.

- **Given** stored settings
- **When** they are read or included in a backup
- **Then** the JSON includes every `UserSettingsDto` property under its existing field name, and a backup round-trips the settings unchanged

### SET-008 — Android auto-save

Edits on the Settings screen are saved locally 500 ms after the last change.

- **Given** the Settings screen has loaded the stored settings
- **When** the user changes a field and pauses for 500 ms (or taps **Close**, which flushes pending changes)
- **Then** the form is written with `DiaryStore.updateSettings` unless it equals the last saved form; on success `Settings saved` shows for 2 seconds and the form is replaced by the stored result, unless the user edited it while the save was in flight, in which case another auto-save is scheduled instead; on failure the error message stays visible

### SET-009 — Saving reschedules the morning reminder

After every successful settings save, the next morning reminder is rescheduled from the saved notification time and working-days mask.

- **Given** the user changes the reminder time or working days
- **When** the save succeeds
- **Then** the next reminder is scheduled using the `notificationTime` and `notificationWorkingDaysMask` returned by `DiaryStore.updateSettings`, and the screen shows the next reminder time

### SET-010 — Working days selector

Reminder working days are chosen in a dialog of Monday–Sunday checkboxes and stored as a bitmask (Mon = bit 0 … Sun = bit 6).

- **Given** the Settings screen
- **When** the user taps **Set**, toggles days and saves
- **Then** the mask is updated (and auto-saved); the summary lists the selected day names, or a "none" label when no days are selected; Cancel discards the change

### SET-011 — Missed-reminder response selector

The missed-reminder section offers None, Home, Office, Leave and Geolocation chips, and only shows the apply-at time while a mode other than None is selected.

- **Given** the Settings screen
- **When** the user selects a chip
- **Then** the mode is updated; the apply-at time picker appears for any enabled mode; selecting Geolocation also shows a button that opens geolocation settings

### SET-012 — Customise Home or Office schedule

Each default schedule is edited in a bottom sheet that saves immediately on **Save**.

- **Given** the Settings screen
- **When** the user taps **Customize** for Home or Office, edits start, finish, break or description, and taps **Save**
- **Then** the sheet closes and the settings are saved straight away (bypassing the 500 ms delay); **Cancel** or dismissing discards the draft

### SET-013 — Home & office presets section

The Settings screen shows a **Home & office presets** section summarising the Home and Office default schedules, each with a **Customize** action.

- **Given** loaded user settings
- **When** the Settings screen is shown
- **Then** the section lists Home and Office with start–finish, break hours (non-numeric shown as `0`) and the description when non-blank; **Customize** opens the sheet described in SET-012

## Interfaces

| Kind | Location |
| --- | --- |
| Store & rules | `apps/android/app/src/main/java/dev/wfhdiary/app/data/store/DiaryStore.kt` (`getSettings`, `updateSettings`), `data/store/DiaryRules.kt` (`defaultSettings`, `applySettingsUpdate`) |
| Storage | `data/local/SettingsEntity.kt`, `data/local/SettingsDao.kt` (single row `id = 1`; settings JSON plus raw `legacyJson`) |
| Model | `data/model/UserSettingsDto.kt`, `data/model/UpdateUserSettingsRequest.kt`; backup schema: [BAK-001](../backup/spec.md#bak-001--backup-document-v1) |
| Android | `apps/android/app/src/main/java/dev/wfhdiary/app/ui/settings/` — `SettingsScreen.kt`, `SettingsViewModel.kt`, `SettingsFormState.kt`, `WorkPresetsSection.kt`, `WorkScheduleCustomizeSheet.kt`, `WorkScheduleSummaryRow.kt`, `WorkingDaysSelector.kt`, `MissedNotificationDefaultSelector.kt`, `SettingsInfoIcon.kt`, `SettingsSectionTitleRow.kt` |

## Test coverage

| Requirement | Tests |
| --- | --- |
| SET-001 | `DiaryRulesTest.default_settings_match_server_defaults`; `DiaryStoreTest.settings_default_then_update`; `WfhDiaryDatabaseTest.settings_single_row_upsert` |
| SET-002 | `DiaryStoreTest.settings_default_then_update`; `DiaryStoreTest.update_settings_preserves_legacy`; `DiaryRulesTest.settings_update_ignores_invalid_time_and_normalises_valid` |
| SET-003 | `DiaryRulesTest.settings_update_ignores_invalid_time_and_normalises_valid` (ported rule) |
| SET-004 | `DiaryRulesTest.settings_update_validates_enums` (ported rule) |
| SET-005 | `DiaryRulesTest.settings_update_trims_or_clears_descriptions` (ported rule) |
| SET-006 | `DiaryRulesTest.settings_geofence_requires_all_fields_and_clear_wins` (ported rule) |
| SET-007 | `BackupCodecTest.round_trip_preserves_document`; `DiaryStoreTest.export_document_and_replace_all_round_trip` |
| SET-008 | `untested` (the store write is covered by `DiaryStoreTest.settings_default_then_update`; the debounce is manual) |
| SET-009 | `untested` |
| SET-010 | `untested` |
| SET-011 | `untested` |
| SET-012 | `untested` |
| SET-013 | `untested` |

## Known gaps

- **Android cannot clear a default description (suspected bug).** The ViewModel sends a blank description as `null`, which `DiaryStore.updateSettings` treats as "unchanged", so emptying the field in the Customize sheet reverts to the old text after save. The blank-clears behaviour (SET-005) is never reached from the app. (Unlike day entries, this was not fixed by moving on-device.)
- **No input validation.** Invalid times are silently ignored (SET-003) rather than rejected; break hours, working-days mask range (0–127), display name and geofence coordinates/radius are not validated.
- **Inconsistent Office default.** `SettingsFormState` and the Customize sheet's time picker default Office start to `08:30`, while the stored default (`DiaryRules.defaultSettings`) is `08:00`. Only visible before settings load or when the picker has no value.
- Android ViewModel / repository tests are backlog ([TODO § B](../../TODO.md#b-strong-unit-testing--partial)).

## Revision log

- 2026-10-27: Initial spec backfilled from implementation ([change](../../changes/2026-10-27-spec-driven-setup/design.md)).
- 2026-10-27: Final-review consistency fixes ([change](../../changes/2026-10-27-spec-driven-setup/design.md)).
- 2026-10-27: On-device-only phase 1 — SET-001, SET-002, SET-007 and SET-008 rewritten for the single local settings row; coverage references updated ([change](../../changes/2026-10-27-on-device-only/design.md)).
- 2026-10-27: On-device-only phase 2 — SET-003..007 and SET-009 reworded for local saves (no server responses or contract fixture); DTOs live in `data/model/`; server interface row removed ([change](../../changes/2026-10-27-on-device-only/design.md)).
