# Leave periods

**Prefix:** `LEAVE` · **Last updated:** 2026-10-27

## Purpose & scope

Lets a user schedule leave as a date range (with an optional note), which marks the covered diary days as `LEAVE` so they are excluded from work hours, and later remove it again. Leave periods are stored on the phone (Room table `leave_periods`) and handled by `DiaryStore`. Covers the leave rules, the Android **Schedule leave** sheet, the month-grid leave badge and removing leave from the day edit sheet. Out of scope: the morning-notification **Leave** action and the missed-notification worker skipping leave days (see [notifications](../notifications/spec.md)), and how leave days are counted in the FY summary (see [summary-fy](../summary-fy/spec.md)).

## Requirements

### LEAVE-001 — Create leave period

`DiaryStore.createLeave` stores a leave period with a new id and returns it.

- **Given** the app
- **When** it creates leave with `{ startDate, endDate, note }` using `yyyy-MM-dd` dates
- **Then** a `LeavePeriodDto` is returned containing a new `id`, the start and end dates and the note (trimmed; blank becomes null)

### LEAVE-002 — Reject malformed dates

Creating leave with an unparseable start or end date is refused with an `IllegalArgumentException`.

- **Given** the app
- **When** it creates leave where `startDate` or `endDate` is not a valid `yyyy-MM-dd` date
- **Then** `IllegalArgumentException("Invalid date: …")` is thrown and nothing is stored

### LEAVE-003 — Reversed range is swapped

If the end date is before the start date, the store swaps them rather than rejecting the request.

- **Given** the app
- **When** it creates leave with `startDate` later than `endDate`
- **Then** the stored period runs from the earlier date to the later date

### LEAVE-004 — Mark covered days as leave

Creating leave overwrites each covered day in the requested financial year to a blank `LEAVE` day.

- **Given** stored days with entries in the leave range
- **When** leave is created for that range and `fyYear`
- **Then** each covered day inside that FY has start, finish, break and location cleared and description set to `LEAVE`; days outside the FY are not touched

### LEAVE-005 — List leave periods

`DiaryStore.getLeavePeriods` returns all leave periods across every financial year, ordered by start date.

- **Given** stored leave periods
- **When** the app lists them
- **Then** all periods are returned, sorted by `startDate` ascending

### LEAVE-006 — Delete leave clears marked days

Deleting a leave period removes it and clears the `LEAVE` days it covered, in every financial year.

- **Given** a leave period whose days are marked `LEAVE`
- **When** the app deletes the period
- **Then** the period is no longer listed, and covered days whose description is `LEAVE` (case-insensitive) are cleared to empty (their rows are deleted)

### LEAVE-007 — Delete preserves days covered by other leave

Days that are also covered by another leave period keep their `LEAVE` marking when one period is deleted.

- **Given** two overlapping leave periods
- **When** one of them is deleted
- **Then** days inside the remaining period still have description `LEAVE`

### LEAVE-008 — Delete unknown leave is a no-op

Deleting a leave id that does not exist changes nothing and raises no error.

- **Given** a leave id that is unknown
- **When** the app deletes it
- **Then** nothing changes and no error is raised

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
| Store & rules | `apps/android/app/src/main/java/dev/wfhdiary/app/data/store/DiaryStore.kt` (`createLeave`, `getLeavePeriods`, `deleteLeave`), `data/store/DiaryRules.kt` (`leaveDatesInFy`, `leaveDay`, `datesToClearOnLeaveDelete`) |
| Storage | `data/local/LeavePeriodEntity.kt`, `data/local/LeavePeriodDao.kt` |
| DTO / contract | `packages/shared/contracts/leave-period.json` (shape reused by `data/remote/LeavePeriodDto.kt`, `CreateLeaveRequest.kt`) |
| Android | `apps/android/app/src/main/java/dev/wfhdiary/app/ui/leave/LeaveScheduleSheet.kt`, `util/LeavePeriodHelper.kt`, `ui/month/MonthGridScreen.kt`, `ui/month/MonthGridViewModel.kt` |
| Server (legacy, until phase 2) | `services/api/WfhDiary.Api/Services/DiaryService.cs`, `Data/LeavePeriodEntity.cs` — no longer used by the app |

## Test coverage

| Requirement | Tests |
| --- | --- |
| LEAVE-001 | `DiaryStoreTest.create_leave_swaps_dates_and_marks_fy_days_only`; contract: `ContractDeserializationTest.leavePeriod_fixture_deserializes` |
| LEAVE-002 | `untested` |
| LEAVE-003 | `DiaryStoreTest.create_leave_swaps_dates_and_marks_fy_days_only` |
| LEAVE-004 | `DiaryStoreTest.create_leave_swaps_dates_and_marks_fy_days_only`; `DiaryRulesTest.leave_dates_are_limited_to_fy`, `DiaryRulesTest.leave_day_has_only_leave_description` |
| LEAVE-005 | `WfhDiaryDatabaseTest.leave_ordered_by_start` |
| LEAVE-006 | `DiaryStoreTest.delete_leave_clears_only_uncovered_leave_days` |
| LEAVE-007 | `DiaryStoreTest.delete_leave_clears_only_uncovered_leave_days`; `DiaryRulesTest.dates_to_clear_skip_other_periods` |
| LEAVE-008 | `untested` |
| LEAVE-009 | `untested` |
| LEAVE-010 | `LeavePeriodHelperTest.isOnLeave_true_for_date_in_range`, `LeavePeriodHelperTest.isOnLeave_false_outside_range`, `LeavePeriodHelperTest.isOnLeave_includes_start_and_end_dates` (helper only; badge rendering untested) |
| LEAVE-011 | `LeavePeriodHelperTest.coveringPeriod_returns_matching_period`, `LeavePeriodHelperTest.coveringPeriod_null_when_not_on_leave` (helper only; ViewModel flow untested) |

## Known gaps

- **Cross-FY leave is only partly marked (suspected bug).** Creation only marks days inside the `fyYear` it is called with, and Android always passes the FY currently on screen, so a period spanning 30 June / 1 July leaves the other FY's days unmarked. Deletion, by contrast, clears days in all financial years.
- **Creating leave destroys existing day data.** Covered days are overwritten with no warning, and deleting the leave clears them to empty rather than restoring what was there before.
- **No validation of range against the FY.** A period entirely outside `fyYear` is still stored (and listed) while marking no days.
- **Overlapping and duplicate periods are allowed;** there is no update operation, so changing a period means delete and re-create.
- **Delete clears manual `LEAVE` text.** Any covered day whose description is `LEAVE` is cleared, even if the user typed it by hand.
- **Stale grid after delete (suspected bug).** After a leave change the ViewModel refreshes leave periods and summary but not the day list, so cells whose cached entry still says `LEAVE` keep the Leave badge until the next full refresh.
- **Removing leave from one day deletes the whole period** (LEAVE-011); there is no way to shorten a period.
- Android ViewModel tests are backlog ([TODO § B](../../TODO.md#b-strong-unit-testing--partial)).

## Revision log

- 2026-10-27: Initial spec backfilled from implementation ([change](../../changes/2026-10-27-spec-driven-setup/design.md)).
- 2026-10-27: Final-review consistency fixes ([change](../../changes/2026-10-27-spec-driven-setup/design.md)).
- 2026-10-27: On-device-only phase 1 — LEAVE-001..008 rewritten as local `DiaryStore` behaviour; "404" cases became no-ops ([change](../../changes/2026-10-27-on-device-only/design.md)).
