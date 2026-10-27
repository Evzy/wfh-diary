# Day entries & diaries

**Prefix:** `DAY` · **Last updated:** 2026-10-27

## Purpose & scope

The app keeps one diary per Australian financial year (FY), holding one entry per calendar day with start/finish times, break hours, description and location (Home/Office). All data lives on the phone: `DiaryStore` reads and writes the Room database (only days with data are stored; the grid synthesises empty days), and lets the user read, upsert and clear individual days. The Android app shows days on a month grid and edits them in a bottom sheet or the notification quick-add screen. Out of scope: FY summary totals ([summary-fy](../summary-fy/spec.md)), leave periods (`LEAVE`), notifications, export and backups ([backup](../backup/spec.md)) (see their own specs). Templates and authentication are retired.

## Requirements

### DAY-001 — Diary range available on demand

The diary for any FY is available without setup: `DiaryStore` derives its range (1 July to 30 June) on demand, stores only days that have data, and keeps no per-user data.

- **Given** no stored days for FY 2025
- **When** the app asks `DiaryStore` for diary 2025
- **Then** it gets `{ fyYear: 2025, fyStartDate: "2025-07-01", fyEndDate: "2026-06-30" }`, and no day rows are created

### DAY-002 — List days with optional date range

Listing days returns the stored entries for the FY ordered by date, filtered by optional inclusive `from`/`to` dates (clamped to the FY); an unparseable filter value is refused with an `IllegalArgumentException`.

- **Given** stored entries for FY 2025
- **When** the app lists days for 2025 with `from=2025-07-01` and `to=2025-07-03`
- **Then** exactly the stored entries for 1–3 July are returned in date order (days without data are not returned)

### DAY-003 — Partial upsert of a day

Upserting a day only changes the fields present (non-null) in the request; times accept `HH:mm` or `H:mm`, and a blank location is stored as null. The edit sheet saves with a full replace instead, which clears any field not supplied (null or blank), so clearing a field works.

- **Given** a day in the FY with existing values
- **When** the app upserts `{ startTime: "8:00", finishTime: "17:00", breakHours: 1, location: "Home" }` for that date
- **Then** the returned entry has `startTime: "08:00"`, fields omitted from the request keep their previous values, and a day whose fields all end up empty is deleted rather than stored

### DAY-004 — Date validation on day writes

Day upsert, replace and clear refuse an unparseable date or a date outside the requested FY with an `IllegalArgumentException`; the ViewModel shows its message and nothing is written.

- **Given** FY 2025
- **When** the app upserts, replaces or clears a day with `date` = `not-a-date` or `2026-07-01`
- **Then** `IllegalArgumentException` is thrown with the message `Invalid date: not-a-date` or `Date 2026-07-01 is outside financial year 2025.` respectively, and no row changes

### DAY-005 — Day entry response shape

Day responses contain `date` (yyyy-MM-dd), `dayName`, `startTime`/`finishTime` (`HH:mm` or null), `breakHours`, `totalHours`, `description` and `location`.

- **Given** a day with logged times
- **When** it is returned by list, upsert or clear
- **Then** the entry (`DayEntry`) has all eight properties; `dayName` is the English weekday name

### DAY-006 — Total hours computation

A day's `totalHours` is finish minus start minus break hours, floored at 0, and is 0 when start or finish is missing.

- **Given** start 08:00, finish 17:00, break 1
- **When** total hours are computed (`DiaryRules.totalHours` using `TimeHelpers`)
- **Then** the result is 8

### DAY-007 — Clear a day

Clearing a day deletes its stored row and returns an empty entry (start, finish, break, description and location all null); clearing a day with no row is a no-op that returns the same empty entry.

- **Given** a day logged at the Office 09:00–17:00
- **When** the app clears that date
- **Then** the stored row is gone and the returned entry has `startTime`, `finishTime` and `location` all null

### DAY-008 — Month grid display

The Android home screen shows a Monday-first calendar for the selected month, badging each day `L` (on a leave period or description `LEAVE`), `H` (Home) or `O` (Office), with the FY end year as the title.

- **Given** stored entries for July 2025 with one Home day and one leave day
- **When** the month grid renders
- **Then** those cells are coloured and badged `H` and `L`, other days are blank, and the title reads `2026`

### DAY-009 — Month and year navigation

The user moves between months with arrow buttons or a horizontal swipe, and by 12 months with the title-bar arrows; the FY follows the displayed month.

- **Given** the grid showing June 2025 (FY 2024)
- **When** the user taps the next-month arrow or swipes left
- **Then** July 2025 is shown and the FY switches to 2025

### DAY-010 — Local store is the source of truth

Room is the single source of truth for diary data: on app resume or pull-to-refresh the grid reloads the displayed FY from `DiaryStore`, there is no sync or cache staleness, and what was saved (including a cleared field) is what the grid and the reopened edit sheet show.

- **Given** days saved in an earlier session, with the description of one of them cleared in the edit sheet
- **When** the app starts (online or offline) or the user pulls to refresh
- **Then** the grid shows the stored entries, the cleared description stays cleared after reopening the sheet, and a failed read shows an error message instead of clearing the grid

### DAY-011 — Day edit sheet

Tapping a day opens a sheet with Home/Office chips, start/finish pickers (defaulting to 08:00/17:00), a 0.25-hour break stepper (minimum 0) and a description; Save writes to the FY containing that date, updates the grid and closes the sheet.

- **Given** the month grid
- **When** the user taps 15 July, picks Home, 08:00–16:30, break 0.5, and taps Save
- **Then** the day is written to FY 2025, the cell shows `H`, and the sheet closes; a Delete button (clear day) is only shown when the day has logged work and is not `LEAVE`

### DAY-012 — Quick add from notification

The quick-add screen, opened from the morning reminder with a date (default today), dismisses the reminder and saves location (default "Home") plus start/finish times for that date.

- **Given** the morning reminder for 2025-07-15
- **When** the user opens quick add, enters 08:00–16:00 and taps Save
- **Then** the day is upserted with location Home and the screen closes; on failure the error message is shown and the screen stays open

## Interfaces

| Kind | Location |
| --- | --- |
| Data source | `apps/android/app/src/main/java/dev/wfhdiary/app/data/store/DiaryStore.kt` (local store, `WfhDiaryApplication.diaryStore`), `data/store/DiaryRules.kt` (pure rules: upsert, replace, total hours, day entry) |
| Storage | `data/local/` — Room v2 `DayEntryEntity`, `DayEntryDao`, `WfhDiaryDatabase` (`date` TEXT primary key; only days with data are stored) |
| Model | `data/model/DayEntry.kt`, `DiaryRange.kt`, `DayEntryUpdate.kt` |
| Android | `ui/month/MonthGridScreen.kt`, `ui/month/MonthGridViewModel.kt`, `ui/month/MonthGridUiState.kt`, `ui/day/DayEditSheet.kt`, `ui/BreakHoursField.kt`, `ui/quick/QuickAddActivity.kt`, `util/DayEntryHelper.kt`, `util/DayBadge.kt`, `util/TimeHelpers.kt` |

## Test coverage

| Requirement | Tests |
| --- | --- |
| DAY-001 | `DiaryStoreTest.diary_range_derived_without_storing_days` |
| DAY-002 | `DiaryStoreTest.get_days_filters_by_fy_and_range` (unparseable filter untested) |
| DAY-003 | `DiaryRulesTest.upsert_keeps_unset_fields`, `DiaryRulesTest.upsert_on_missing_day_creates_it`, `DiaryRulesTest.upsert_blank_time_and_location_clear_them`, `DiaryRulesTest.replace_clears_fields_not_supplied`, `DiaryRulesTest.replace_blank_description_becomes_null`, `DiaryStoreTest.upsert_then_partial_upsert_keeps_fields`, `DiaryStoreTest.replace_clears_fields`; `TimeHelpersTest.parseTimeString_accepts_hh_mm` |
| DAY-004 | `DiaryStoreTest.upsert_outside_fy_throws` (outside-FY case only; unparseable date untested) |
| DAY-005 | `DiaryRulesTest.toDayEntry_fills_day_name_and_total` |
| DAY-006 | `TimeHelpersTest.computeTotalHours_subtracts_break`; `DiaryRulesTest.toDayEntry_fills_day_name_and_total` |
| DAY-007 | `DiaryStoreTest.delete_day_removes_row_and_returns_empty_entry`; `DiaryRulesTest.isEmpty_detects_blank_days` |
| DAY-008 | `untested` |
| DAY-009 | `untested` |
| DAY-010 | `DiaryStoreTest.replace_clears_fields` (store half); manual — [Android test plan](../../testing/ANDROID_TEST_PLAN.md#backup--restore-on-device) (step "clear a field") |
| DAY-011 | `untested` |
| DAY-012 | `untested` |

## Known gaps

- **Invalid times silently clear a field:** a non-null but unparseable `startTime`/`finishTime` (e.g. `"8am"`) is stored as null instead of being refused. Suspected unintended.
- **Location is free text:** `DiaryStore` accepts any location string, and quick add uses a free-text field. Values like `"WFH"` are stored but never count as Home/Office. Untrimmed values like `"Home "` are inconsistent: the grid trims and shows `H`, but the summary does not trim, so they are excluded from WFH totals.
- **Overnight shifts unsupported:** finish earlier than start yields 0 hours.
- **Write semantics differ by caller:** the edit sheet saves with a full replace (`replaceDay`, clears unsupplied fields); quick add and the notification actions use a partial `upsertDay`, so they keep existing fields they do not supply.
- **No negative-path tests** for unparseable dates (DAY-004) or filter values (DAY-002); ViewModel tests are backlog ([TODO § B](../../TODO.md#b-strong-unit-testing--partial)).
- **Template picker:** "Apply template…" in the day edit sheet and quick-add screen was never implemented ([TODO § C](../../TODO.md#c-work-scenario-templates--dropped), items 1 and 3); templates were dropped.
- `DayEntryHelper.needsDefault` (tested in `DayEntryHelperTest`) is only used by the morning-reminder and missed-notification workers, so it belongs to the notifications capability and is not specified here.
## Revision log

- 2026-10-27: Initial spec backfilled from implementation ([change](../../changes/2026-10-27-spec-driven-setup/design.md)).
- 2026-10-27: On-device-only phase 1 — DAY-001..007 and DAY-010 rewritten as local `DiaryStore` behaviour; the clear-a-field gap is closed ([change](../../changes/2026-10-27-on-device-only/design.md)).
- 2026-10-27: On-device-only phase 2 — server row and contract-fixture references removed (DAY-005 no longer cites a fixture; DAY-001 now untested); DTOs live in `data/model/`; grid reload renamed to `MonthGridViewModel.reload` ([change](../../changes/2026-10-27-on-device-only/design.md)).
- 2026-10-27: DAY-001 coverage added (`DiaryStoreTest.diary_range_derived_without_storing_days`). Internal model classes renamed (`DayEntryDto` → `DayEntry`, `DiaryDto` → `DiaryRange`, `UpsertDayEntryRequest` → `DayEntryUpdate`; `DiaryRules.toDto` → `toDayEntry`; `DiaryDataSource` interface removed), no behaviour change.
