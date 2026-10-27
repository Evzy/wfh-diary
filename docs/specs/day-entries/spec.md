# Day entries & diaries

**Prefix:** `DAY` · **Last updated:** 2026-10-27

## Purpose & scope

Each user has one diary per Australian financial year (FY), holding one entry per calendar day with start/finish times, break hours, description and location (Home/Office). The API creates diaries on demand and lets the user read, upsert and clear individual days; the Android app shows them on a month grid, edits them in a bottom sheet or the notification quick-add screen, and caches them locally. Out of scope: FY summary totals ([summary-fy](../summary-fy/spec.md)), leave periods (`LEAVE`), templates, notifications, export and authentication (see their own specs).

## Requirements

### DAY-001 — Diary created on first access, per user

A user's diary for an FY is created on first access, pre-populated with one empty day entry for every date from 1 July to 30 June, and is only visible to that user.

- **Given** an authenticated user with no diary for FY 2025
- **When** they call `GET /v1/diaries/2025`
- **Then** the response is 200 with `{ fyYear: 2025, fyStartDate: "2025-07-01", fyEndDate: "2026-06-30" }`, 365 empty day rows exist for that user, and other users' diaries are unaffected

### DAY-002 — List days with optional date range

Listing days returns the user's entries for the FY ordered by date, filtered by optional inclusive `from`/`to` dates; unparseable filter values are ignored.

- **Given** a diary for FY 2025
- **When** the user calls `GET /v1/diaries/2025/days?from=2025-07-01&to=2025-07-03`
- **Then** the response is 200 with exactly the three entries for 1–3 July in date order

### DAY-003 — Partial upsert of a day

Upserting a day only changes the fields present (non-null) in the request; times accept `HH:mm` or `H:mm`, and a blank location is stored as null.

- **Given** a day in the FY with existing values
- **When** the user calls `PUT /v1/diaries/{fy}/days/{date}` with `{ startTime: "8:00", finishTime: "17:00", breakHours: 1, location: "Home" }`
- **Then** the response is 200 with the updated entry (`startTime: "08:00"`), and fields omitted from the request keep their previous values

### DAY-004 — Date validation on day writes

Day upsert and clear reject an unparseable date or a date outside the requested FY with 400.

- **Given** a diary for FY 2025
- **When** the user calls `PUT` or `DELETE /v1/diaries/2025/days/{date}` with `date` = `not-a-date` or `2026-07-01`
- **Then** the response is 400 with `{ error: "Invalid date. Use yyyy-MM-dd." }` or `{ error: "Date outside financial year." }` respectively

### DAY-005 — Day entry response shape

Day responses contain `date` (yyyy-MM-dd), `dayName`, `startTime`/`finishTime` (`HH:mm` or null), `breakHours`, `totalHours`, `description` and `location`.

- **Given** a day with logged times
- **When** it is returned by list, upsert or clear
- **Then** the JSON has all eight properties and matches the `day-entry.json` contract fixture

### DAY-006 — Total hours computation

A day's `totalHours` is finish minus start minus break hours, floored at 0, and is 0 when start or finish is missing.

- **Given** start 08:00, finish 17:00, break 1
- **When** total hours are computed (API or Android helper)
- **Then** the result is 8

### DAY-007 — Clear a day

Deleting a day nulls its start, finish, break, description and location and returns the cleared entry; a missing row returns 404.

- **Given** a day logged at the Office 09:00–17:00
- **When** the user calls `DELETE /v1/diaries/{fy}/days/{date}`
- **Then** the response is 200 with `startTime`, `finishTime` and `location` all null

### DAY-008 — Month grid display

The Android home screen shows a Monday-first calendar for the selected month, badging each day `L` (on a leave period or description `LEAVE`), `H` (Home) or `O` (Office), with the FY end year as the title.

- **Given** cached entries for July 2025 with one Home day and one leave day
- **When** the month grid renders
- **Then** those cells are coloured and badged `H` and `L`, other days are blank, and the title reads `2026`

### DAY-009 — Month and year navigation

The user moves between months with arrow buttons or a horizontal swipe, and by 12 months with the title-bar arrows; the FY follows the displayed month.

- **Given** the grid showing June 2025 (FY 2024)
- **When** the user taps the next-month arrow or swipes left
- **Then** July 2025 is shown and the FY switches to 2025

### DAY-010 — Sync and local cache

On app resume or pull-to-refresh the app fetches the whole displayed FY from the API, stores it in the local Room cache, and shows cached entries when a month is opened before any refresh completes.

- **Given** entries cached from a previous session
- **When** the app starts offline
- **Then** the grid shows the cached entries, and a failed refresh shows an error message instead of clearing them

### DAY-011 — Day edit sheet

Tapping a day opens a sheet with Home/Office chips, start/finish pickers (defaulting to 08:00/17:00), a 0.25-hour break stepper (minimum 0) and a description; Save upserts to the FY containing that date, updates the grid and closes the sheet.

- **Given** the month grid
- **When** the user taps 15 July, picks Home, 08:00–16:30, break 0.5, and taps Save
- **Then** the day is upserted to FY 2025, the cell shows `H`, and the sheet closes; a Delete button (clear day) is only shown when the day has logged work and is not `LEAVE`

### DAY-012 — Quick add from notification

The quick-add screen, opened from the morning reminder with a date (default today), dismisses the reminder and saves location (default "Home") plus start/finish times for that date.

- **Given** the morning reminder for 2025-07-15
- **When** the user opens quick add, enters 08:00–16:00 and taps Save
- **Then** the day is upserted with location Home and the screen closes; on failure the error message is shown and the screen stays open

## Interfaces

| Kind | Location |
| --- | --- |
| API | `GET /v1/diaries/{fyYear}`, `GET /v1/diaries/{fyYear}/days?from&to`, `PUT` / `DELETE /v1/diaries/{fyYear}/days/{date}` — `services/api/WfhDiary.Api/Endpoints/DiaryEndpoints.cs`, `services/api/WfhDiary.Api/Services/DiaryService.cs`, `services/api/WfhDiary.Api/Services/TimeHelpers.cs` |
| Data | `services/api/WfhDiary.Api/Data/DiaryEntity.cs`, `services/api/WfhDiary.Api/Data/DayEntryEntity.cs` |
| DTO / contract | `packages/shared/WfhDiary.Shared/Dtos/DiaryDto.cs`, `DayEntryDto.cs`, `UpsertDayEntryRequest.cs`; `packages/shared/contracts/diary.json`, `day-entry.json` |
| Android | `apps/android/app/src/main/java/dev/wfhdiary/app/` — `ui/month/MonthGridScreen.kt`, `ui/month/MonthGridViewModel.kt`, `ui/month/MonthGridUiState.kt`, `ui/day/DayEditSheet.kt`, `ui/BreakHoursField.kt`, `ui/quick/QuickAddActivity.kt`, `data/local/` (Room cache), `data/remote/DiaryRepository.kt`, `util/DayEntryHelper.kt`, `util/DayBadge.kt`, `util/TimeHelpers.kt` |

## Test coverage

| Requirement | Tests |
| --- | --- |
| DAY-001 | `DiaryFlowTests.Upsert_day_and_summary_work` (indirect: uses returned `FyStartDate`); `ApiContractTests.Fixture_deserializes_to_shared_dto`; `ContractDeserializationTest.diary_fixture_deserializes` |
| DAY-002 | `LeaveFlowTests.Create_leave_marks_days_and_delete_clears_them` (indirect: asserts 3 days for a 3-day `from`/`to` range) |
| DAY-003 | `DiaryFlowTests.Upsert_day_and_summary_work`; `TimeHelpersTests.ParseTimeString_valid`; `TimeHelpersTest.parseTimeString_accepts_hh_mm` |
| DAY-004 | `untested` |
| DAY-005 | `ApiContractTests.Live_day_upsert_response_matches_contract_properties`; `ApiContractTests.Fixture_deserializes_to_shared_dto`; `ContractDeserializationTest.dayEntry_fixture_deserializes` |
| DAY-006 | `TimeHelpersTests.ComputeTotalHours_subtracts_break`; `TimeHelpersTest.computeTotalHours_subtracts_break` |
| DAY-007 | `DiaryFlowTests.Clear_day_removes_times_and_location` |
| DAY-008 | `untested` |
| DAY-009 | `untested` |
| DAY-010 | `untested` |
| DAY-011 | `untested` |
| DAY-012 | `untested` |

## Known gaps

- **Blank fields can't be cleared from Android:** the app's JSON client omits null fields (`encodeDefaults = false`) and the API treats a missing field as "leave unchanged", so clearing start/finish/description in the edit sheet (or quick add) does not clear it on the server. Only Delete clears a day. Suspected unintended.
- **Invalid times silently clear a field:** a non-null but unparseable `startTime`/`finishTime` (e.g. `"8am"`) is stored as null instead of returning 400. Suspected unintended.
- **Location is free text:** the API accepts any location string, and quick add uses a free-text field; values like `"Home "` or `"WFH"` are stored but never count as Home/Office in the summary or grid badge.
- **Overnight shifts unsupported:** finish earlier than start yields 0 hours.
- **`dayName` depends on server culture:** `ToDto` formats `dddd` with the current culture rather than invariant culture.
- **No negative-path tests** for DAY-004 or 404 on clear; `DiaryService` in-memory tests and Android ViewModel/repository tests are backlog ([TODO § B](../../TODO.md#b-strong-unit-testing--partial)).
- **No offline writes:** saves and deletes require the network; the Room cache is read-only between refreshes.
- **Template picker:** "Apply template…" in the day edit sheet and quick-add screen is backlog ([TODO § C](../../TODO.md#c-work-scenario-templates--partial), items 1 and 3).
- `DayEntryHelper.needsDefault` (tested in `DayEntryHelperTest`) is only used by the morning-reminder and missed-notification workers, so it belongs to the notifications capability and is not specified here.

## Revision log

- 2026-10-27: Initial spec backfilled from implementation ([change](../../changes/2026-10-27-spec-driven-setup/design.md)).
