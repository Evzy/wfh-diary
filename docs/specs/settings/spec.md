# User settings

**Prefix:** `SET` · **Last updated:** 2026-10-27

## Purpose & scope

Per-user preferences stored by the API and edited on the Android Settings screen: display name, morning reminder time and working days, missed-reminder response mode and apply-at time, and the default Home and Office schedules (start, finish, break, description) used by quick actions. Out of scope: the geolocation settings sheet, map picker and geofence capture UI (see `geolocation` spec), the work-template API and the Home & office presets summary (see [work-templates](../work-templates/spec.md)), notification scheduling and delivery (see `notifications` spec), XLSX export and OTA updates, which share the Settings screen but have their own specs.

## Requirements

### SET-001 — Lazily created defaults

`GET /v1/settings` returns the caller's settings, creating a row with defaults on first access.

- **Given** a user with no stored settings
- **When** they call `GET /v1/settings`
- **Then** the response is `200` with display name `YOUR NAME HERE`, notification time `08:00`, working-days mask `31` (Mon–Fri), missed default `None`, apply-at `12:00`, away default `Office`, both skip flags `false`, Home and Office `08:00`–`17:00` with a 1-hour break, null descriptions and null geofences

### SET-002 — Partial update

`PUT /v1/settings` updates only the fields that are non-null in the request and returns the full updated settings.

- **Given** existing settings
- **When** the user sends a request with some fields set and the rest `null`
- **Then** the response is `200`, the set fields are changed, `null` fields keep their previous values, and a later `GET` returns the same values

### SET-003 — Unparseable times are ignored

Time fields (`notificationTime`, `missedNotificationApplyAt`, Home/Office start and finish) that are not `HH:mm` / `H:mm` are silently ignored.

- **Given** existing settings
- **When** the user sends a time field such as `"25:99"` or `"soon"`
- **Then** the response is still `200` and that field keeps its previous value

### SET-004 — Mode fields fall back on unknown values

`missedNotificationDefault` accepts only `Home`, `Office`, `Leave`, `Geolocation` or `None` (falling back to `None`); `missedNotificationAwayDefault` accepts only `Home`, `Office` or `Leave` (falling back to `Office`).

- **Given** existing settings
- **When** the user sends an unrecognised value (after trimming) for either field
- **Then** the stored value becomes `None` or `Office` respectively and the response is `200`

### SET-005 — Default descriptions are trimmed or cleared

Home and Office default descriptions are trimmed, and a whitespace-only value clears the description.

- **Given** existing settings
- **When** the user sends `defaultHomeDescription` or `defaultOfficeDescription`
- **Then** a non-blank value is stored trimmed and a blank value (e.g. `" "`) is stored as `null`

### SET-006 — Geofence set requires all three fields; clear wins

A home or office geofence is only updated when latitude, longitude and radius are all supplied, and `clearHomeGeofence` / `clearOfficeGeofence` set to `true` nulls all three, taking precedence over supplied values.

- **Given** existing settings
- **When** the user sends all three geofence fields, or sends the clear flag
- **Then** the geofence is stored as sent, or all three fields become `null`; a partial set (e.g. latitude only) changes nothing

### SET-007 — Response matches the shared contract

The settings response exposes every property of the shared `user-settings.json` contract.

- **Given** an authenticated user
- **When** they call `GET /v1/settings`
- **Then** the JSON includes every contract property, and the fixture deserializes into both the API and Android DTOs

### SET-008 — Android auto-save

Edits on the Settings screen are saved automatically 500 ms after the last change.

- **Given** the Settings screen has loaded the user's settings
- **When** the user changes a field and pauses for 500 ms (or taps **Close**, which flushes pending changes)
- **Then** the form is sent with `PUT /v1/settings` unless it equals the last saved form; on success the form is replaced by the server response and `Settings saved` shows for 2 seconds; on failure the error message stays visible

### SET-009 — Saving reschedules the morning reminder

After every successful settings save, the next morning reminder is rescheduled from the saved notification time and working-days mask.

- **Given** the user changes the reminder time or working days
- **When** the save succeeds
- **Then** the next reminder is scheduled using the server's returned `notificationTime` and `notificationWorkingDaysMask`, and the screen shows the next reminder time

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

## Interfaces

| Kind | Location |
| --- | --- |
| API | `GET /v1/settings`, `PUT /v1/settings` — `services/api/WfhDiary.Api/Endpoints/SettingsEndpoints.cs`, `services/api/WfhDiary.Api/Services/DiaryService.cs` |
| Data | `services/api/WfhDiary.Api/Data/UserSettingsEntity.cs` |
| DTO / contract | `packages/shared/WfhDiary.Shared/Dtos/UserSettingsDto.cs`, `packages/shared/WfhDiary.Shared/Dtos/UpdateUserSettingsRequest.cs`, `packages/shared/contracts/user-settings.json` |
| Android | `apps/android/app/src/main/java/dev/wfhdiary/app/ui/settings/` — `SettingsScreen.kt`, `SettingsViewModel.kt`, `SettingsFormState.kt`, `WorkScheduleCustomizeSheet.kt`, `WorkScheduleSummaryRow.kt`, `WorkingDaysSelector.kt`, `MissedNotificationDefaultSelector.kt`, `SettingsInfoIcon.kt`, `SettingsSectionTitleRow.kt` |

## Test coverage

| Requirement | Tests |
| --- | --- |
| SET-001 | `SettingsFlowTests.Settings_default_missed_notification_values` (checks missed default, apply-at and mask only) |
| SET-002 | `SettingsFlowTests.Settings_update_missed_notification_persists` |
| SET-003 | untested |
| SET-004 | `SettingsFlowTests.Settings_invalid_missed_notification_default_falls_back_to_none` (away-default fallback untested) |
| SET-005 | untested |
| SET-006 | `SettingsFlowTests.Settings_home_geofence_set_and_clear` (partial-set and office cases untested) |
| SET-007 | `ApiContractTests.Live_settings_response_matches_contract_properties`, `ApiContractTests.Fixture_deserializes_to_shared_dto`, `ContractDeserializationTest.userSettings_fixture_deserializes` |
| SET-008 | untested |
| SET-009 | untested |
| SET-010 | untested |
| SET-011 | untested |
| SET-012 | untested |

## Known gaps

- **Android cannot clear a default description (suspected bug).** The ViewModel sends a blank description as `null`, which the API treats as "unchanged", so emptying the field in the Customize sheet reverts to the old text after save. The API's blank-clears behaviour (SET-005) is never reached from the app.
- **No input validation.** Invalid times are silently ignored (SET-003) rather than rejected with `400`; break hours, working-days mask range (0–127), display name and geofence coordinates/radius are not validated.
- **Inconsistent Office default.** `SettingsFormState` and the Customize sheet's time picker default Office start to `08:30`, while the API default is `08:00`. Only visible before settings load or when the picker has no value.
- Android ViewModel / repository tests are backlog ([TODO § B](../../TODO.md#b-strong-unit-testing--partial)).

## Revision log

- 2026-10-27: Initial spec backfilled from implementation ([change](../../changes/2026-10-27-spec-driven-setup/design.md)).
