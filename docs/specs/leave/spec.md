# Leave periods

**Prefix:** `LEAVE` · **Last updated:** 2026-10-27

## Purpose & scope

Lets a user schedule leave as a date range (with an optional note), which marks the covered diary days as `LEAVE` so they are excluded from work hours, and later remove it again. Covers the leave API, the Android **Schedule leave** sheet, the month-grid leave badge and removing leave from the day edit sheet. Out of scope: the morning-notification **Leave** action and the missed-notification worker skipping leave days (see `notifications` spec), and how leave days are counted in the FY summary (see `summary-fy` spec).

## Requirements

### LEAVE-001 — Create leave period

`POST /v1/diaries/{fyYear}/leave` stores a leave period owned by the caller and returns it with `200`.

- **Given** an authenticated user
- **When** they post `{ startDate, endDate, note }` with `yyyy-MM-dd` dates
- **Then** the response is `200` with a `LeavePeriodDto` containing a new `id`, the start and end dates and the note as sent

### LEAVE-002 — Reject malformed dates

Creating leave with an unparseable start or end date is rejected with `400`.

- **Given** an authenticated user
- **When** they post leave where `startDate` or `endDate` is not a valid date
- **Then** the response is `400` with `{ error: "Invalid date format. Use yyyy-MM-dd." }` and nothing is stored

### LEAVE-003 — Reversed range is swapped

If the end date is before the start date, the API swaps them rather than rejecting the request.

- **Given** an authenticated user
- **When** they post leave with `startDate` later than `endDate`
- **Then** the stored period runs from the earlier date to the later date

### LEAVE-004 — Mark covered days as leave

Creating leave overwrites each covered day in the requested financial year's diary to a blank `LEAVE` day.

- **Given** a diary for `fyYear` with entries in the leave range
- **When** leave is created for that range
- **Then** each covered day inside that FY has start, finish, break and location cleared and description set to `LEAVE`; days outside the FY are not touched

### LEAVE-005 — List leave periods

`GET /v1/leave-periods` returns all of the caller's leave periods across every financial year, ordered by start date.

- **Given** a user with leave periods (and other users with their own)
- **When** they request `GET /v1/leave-periods`
- **Then** only their own periods are returned, sorted by `startDate` ascending

### LEAVE-006 — Delete leave clears marked days

Deleting a leave period removes it and clears the `LEAVE` days it covered in all of the caller's diaries.

- **Given** a leave period whose days are marked `LEAVE`
- **When** the user calls `DELETE /v1/leave-periods/{id}`
- **Then** the response is `204`, the period is no longer listed, and covered days whose description is `LEAVE` (case-insensitive) are cleared to empty

### LEAVE-007 — Delete preserves days covered by other leave

Days that are also covered by another of the caller's leave periods keep their `LEAVE` marking when one period is deleted.

- **Given** two overlapping leave periods
- **When** one of them is deleted
- **Then** days inside the remaining period still have description `LEAVE`

### LEAVE-008 — Delete unknown or foreign leave returns 404

Deleting a leave id that does not exist or belongs to another user returns `404` and changes nothing.

- **Given** a leave id that is unknown or owned by a different user
- **When** the caller sends `DELETE /v1/leave-periods/{id}`
- **Then** the response is `404`

### LEAVE-009 — Schedule leave sheet

The Android **Schedule leave** sheet lists existing periods and creates a new one from start/end date pickers and an optional note.

- **Given** the user opens the sheet from the month grid
- **When** it is shown
- **Then** existing periods are listed newest-first (single-day periods show one date) each with a delete button; both dates default to today; picking a start after the end moves the end (and vice versa); saving sends a blank note as `null`, closes the sheet and refreshes leave periods and the FY summary

### LEAVE-010 — Leave badge on month grid

The month grid shows the Leave badge for any date covered by a leave period or whose entry description is `LEAVE`.

- **Given** loaded leave periods and day entries
- **When** a date falls within a period (inclusive of start and end) or its entry description equals `LEAVE` (case-insensitive)
- **Then** the cell shows the Leave badge and colour, taking priority over Home/Office

### LEAVE-011 — Remove leave from a day removes the whole period

Removing leave from the day edit sheet deletes the entire leave period covering that date, not just the one day.

- **Given** a selected date covered by a leave period
- **When** the user taps remove leave in the day edit sheet
- **Then** the covering period is deleted (as in LEAVE-006), the day sheet closes and leave periods and summary are refreshed

## Interfaces

| Kind | Location |
| --- | --- |
| API | `POST /v1/diaries/{fyYear}/leave`, `GET /v1/leave-periods`, `DELETE /v1/leave-periods/{id}` — `services/api/WfhDiary.Api/Endpoints/DiaryEndpoints.cs`, `services/api/WfhDiary.Api/Services/DiaryService.cs` |
| Data | `services/api/WfhDiary.Api/Data/LeavePeriodEntity.cs` |
| DTO / contract | `packages/shared/WfhDiary.Shared/Dtos/CreateLeaveRequest.cs`, `packages/shared/WfhDiary.Shared/Dtos/LeavePeriodDto.cs`, `packages/shared/contracts/leave-period.json` |
| Android | `apps/android/app/src/main/java/dev/wfhdiary/app/ui/leave/LeaveScheduleSheet.kt`, `apps/android/app/src/main/java/dev/wfhdiary/app/util/LeavePeriodHelper.kt`, `apps/android/app/src/main/java/dev/wfhdiary/app/ui/month/MonthGridScreen.kt`, `apps/android/app/src/main/java/dev/wfhdiary/app/ui/month/MonthGridViewModel.kt` |

## Test coverage

| Requirement | Tests |
| --- | --- |
| LEAVE-001 | `LeaveFlowTests.Create_leave_marks_days_and_delete_clears_them`; contract: `ApiContractTests.Fixture_deserializes_to_shared_dto`, `ContractDeserializationTest.leavePeriod_fixture_deserializes` |
| LEAVE-002 | untested |
| LEAVE-003 | untested |
| LEAVE-004 | `LeaveFlowTests.Create_leave_marks_days_and_delete_clears_them` (FY-boundary skip untested) |
| LEAVE-005 | `LeaveFlowTests.Create_leave_marks_days_and_delete_clears_them` (ordering and per-user scoping untested) |
| LEAVE-006 | `LeaveFlowTests.Create_leave_marks_days_and_delete_clears_them` |
| LEAVE-007 | `LeaveFlowTests.Delete_leave_preserves_days_covered_by_another_period` |
| LEAVE-008 | `LeaveFlowTests.Delete_leave_unknown_id_returns_not_found` (foreign-owner case untested) |
| LEAVE-009 | untested |
| LEAVE-010 | `LeavePeriodHelperTest.isOnLeave_true_for_date_in_range`, `LeavePeriodHelperTest.isOnLeave_false_outside_range`, `LeavePeriodHelperTest.isOnLeave_includes_start_and_end_dates` (helper only; badge rendering untested) |
| LEAVE-011 | `LeavePeriodHelperTest.coveringPeriod_returns_matching_period`, `LeavePeriodHelperTest.coveringPeriod_null_when_not_on_leave` (helper only; ViewModel flow untested) |

## Known gaps

- **Cross-FY leave is only partly marked (suspected bug).** Creation only marks days inside the `fyYear` in the URL, and Android always passes the FY currently on screen, so a period spanning 30 June / 1 July leaves the other FY's days unmarked. Deletion, by contrast, clears days in all of the user's diaries.
- **Creating leave destroys existing day data.** Covered days are overwritten with no warning, and deleting the leave clears them to empty rather than restoring what was there before.
- **Date format not enforced.** `DateOnly.TryParse` in `DiaryService.CreateLeaveAsync` is culture-sensitive, so `yyyy-MM-dd` is requested but other formats the server culture accepts (e.g. `1/7/2025`) also succeed, and may be read differently on servers with other cultures.
- **No validation of range against the FY.** A period entirely outside `fyYear` is still stored (and listed) while marking no days.
- **Overlapping and duplicate periods are allowed;** there is no update endpoint, so changing a period means delete and re-create.
- **Delete clears manual `LEAVE` text.** Any covered day whose description is `LEAVE` is cleared, even if the user typed it by hand.
- **Stale grid after delete (suspected bug).** After a leave change the ViewModel refreshes leave periods and summary but not the day list, so cells whose cached entry still says `LEAVE` keep the Leave badge until the next full refresh.
- **Removing leave from one day deletes the whole period** (LEAVE-011); there is no way to shorten a period.
- Android `DiaryRepository.deleteLeave` does not inspect the response, so a `404` is not surfaced to the user.
- `DiaryService` in-memory service tests and Android ViewModel tests are backlog ([TODO § B](../../TODO.md#b-strong-unit-testing--partial)).

## Revision log

- 2026-10-27: Initial spec backfilled from implementation ([change](../../changes/2026-10-27-spec-driven-setup/design.md)).
