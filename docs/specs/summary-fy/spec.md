# FY summary & working days

**Prefix:** `FY` · **Last updated:** 2026-10-27

## Purpose & scope

Defines the Australian financial year (1 July – 30 June) used throughout the app, the per-FY summary of logged hours (total, Home, Office, day counts, claimable WFH amount at the fixed rate, and a consistency check), how the Android app shows that summary under the month grid, and the working-days bitmask used to decide which weekdays count as working days. Out of scope: how individual day hours are entered or computed ([day-entries](../day-entries/spec.md)), how the mask drives notifications ([notifications](../notifications/spec.md)), and the XLSX export's own summary formulas ([export-xlsx](../export-xlsx/spec.md)).

## Requirements

### FY-001 — FY year from a date

A date belongs to the FY that starts in its calendar year if it falls in July–December, otherwise to the FY that started the previous year.

- **Given** the dates 2025-06-30, 2025-07-01 and 2026-01-15
- **When** the current FY year is computed (API or Android)
- **Then** the results are 2024, 2025 and 2025

### FY-002 — FY date range

FY `N` runs from 1 July `N` to 30 June `N+1` inclusive, and iterating its days includes both endpoints.

- **Given** FY 2025
- **When** its range is computed and iterated
- **Then** it starts 2025-07-01, ends 2026-06-30, and iteration from 1 to 3 July yields three days ending on 3 July

### FY-003 — Summary endpoint

`GET /v1/diaries/{fyYear}/summary` returns the summary computed over all of the user's day entries in that FY, creating the diary first if needed.

- **Given** a user who logged 08:00–17:00 with a 1-hour break at Home on the first day of the FY
- **When** they call `GET /v1/diaries/{fy}/summary`
- **Then** the response is 200 with `homeHours` ≥ 8 and `claimableWfhAmount` > 0, matching the `diary-summary.json` contract shape

### FY-004 — Hours by location

`totalHours` sums every day's computed hours; `homeHours` and `officeHours` sum only days whose location equals `Home` or `Office` (case-insensitive).

- **Given** one day of 8 hours with location `Home` and one of 8 hours with no location
- **When** the summary is computed
- **Then** `totalHours` is 16, `homeHours` is 8 and `officeHours` is 0

### FY-005 — Day counts

`daysAtHome` and `daysInOffice` count days with that location and more than 0 computed hours.

- **Given** a Home day with 8 hours and a Home day with no times
- **When** the summary is computed
- **Then** `daysAtHome` is 1

### FY-006 — Claimable WFH amount

`claimableWfhAmount` is `homeHours × 0.67`, rounded to 2 decimal places.

- **Given** 8 home hours
- **When** the summary is computed
- **Then** `claimableWfhAmount` is 5.36

### FY-007 — Logic check

`logicCheckPassed` is true only when `totalHours` equals `homeHours + officeHours`, i.e. no hours are logged without a Home/Office location.

- **Given** an 8-hour day with no location
- **When** the summary is computed
- **Then** `logicCheckPassed` is false (and true when the same day is at Home)

### FY-008 — Working-days mask

Working days are a 7-bit mask with bit `ISO day − 1` (Monday = bit 0 … Sunday = bit 6); the default is 31 (Monday–Friday), and individual days can be toggled.

- **Given** the default mask
- **When** Monday, Friday, Saturday and Sunday are checked, then Saturday is enabled and Monday disabled
- **Then** Monday and Friday are working days, Saturday and Sunday are not; after toggling, Saturday is and Monday is not, and the enabled-day labels list 5 short names including `Mon` but not `Sat` for the default

### FY-009 — FY summary tiles on Android

Below the month grid the app shows the displayed FY as "Financial year N–N+1 (Jul–Jun)" with tiles for WFH days, Office days, WFH hours (1 decimal) and Claimable (`$` with 2 decimals), showing `—` until a summary is loaded.

- **Given** a loaded summary for FY 2025 with 8 home hours
- **When** the month grid shows a month in FY 2025
- **Then** the tiles read WFH days 1, Office days 0, WFH hours `8.0 h`, Claimable `$5.36`

### FY-010 — Summary refresh on Android

The app reloads the summary after a refresh, a day save or clear, and a leave change, and keeps the last summary per FY in memory so navigating between FYs shows the right totals.

- **Given** the grid showing FY 2025 with a loaded summary
- **When** the user saves a Home day in FY 2025
- **Then** the summary is re-fetched and the tiles update; navigating to FY 2024 and back shows each FY's last loaded summary

## Interfaces

| Kind | Location |
| --- | --- |
| API | `GET /v1/diaries/{fyYear}/summary` — `services/api/WfhDiary.Api/Endpoints/DiaryEndpoints.cs`; `services/api/WfhDiary.Api/Services/SummaryCalculator.cs`, `FinancialYearHelper.cs`, `WorkingDaysMask.cs` |
| DTO / contract | `packages/shared/WfhDiary.Shared/Dtos/DiarySummaryDto.cs`, `packages/shared/contracts/diary-summary.json` |
| Android | `apps/android/app/src/main/java/dev/wfhdiary/app/` — `ui/month/FySummaryGrid.kt`, `ui/month/MonthGridViewModel.kt`, `util/FinancialYearHelper.kt`, `util/WorkingDaysMask.kt` |

## Test coverage

| Requirement | Tests |
| --- | --- |
| FY-001 | `FinancialYearHelperTests.CurrentFyYear_july_start`; `FinancialYearHelperTest.currentFyYear_june_uses_prior_calendar_year`; `FinancialYearHelperTest.currentFyYear_july_uses_current_calendar_year` |
| FY-002 | `FinancialYearHelperTests.FyRange_spans_july_to_june`; `FinancialYearHelperTests.EachDay_includes_end_date`; `FinancialYearHelperTest.fyRange_spans_july_to_june` |
| FY-003 | `DiaryFlowTests.Upsert_day_and_summary_work`; `ApiContractTests.Fixture_deserializes_to_shared_dto`; `ContractDeserializationTest.diarySummary_fixture_deserializes` |
| FY-004 | `SummaryCalculatorTests.Compute_home_hours_and_claimable_amount`; `SummaryCalculatorTests.Compute_logic_check_fails_when_unlocated_hours` |
| FY-005 | `untested` |
| FY-006 | `SummaryCalculatorTests.Compute_home_hours_and_claimable_amount` |
| FY-007 | `SummaryCalculatorTests.Compute_logic_check_fails_when_unlocated_hours`; `SummaryCalculatorTests.Compute_home_hours_and_claimable_amount` |
| FY-008 | `WorkingDaysMaskTest.default_mask_is_monday_to_friday`; `WorkingDaysMaskTest.setDay_toggles_single_day`; `WorkingDaysMaskTest.selectedDayLabels_lists_enabled_days` |
| FY-009 | `untested` |
| FY-010 | `untested` |

## Known gaps

- **Fixed 67c/hour rate for every FY:** `SummaryCalculator` hard-codes `0.67` regardless of `fyYear`. The ATO fixed-rate method changed to 70c/hour from 1 July 2024 (to be confirmed against ATO guidance), so FY 2024+ claimable amounts are likely understated. Suspected unintended.
- **Banker's rounding:** `Math.Round(home * 0.67, 2)` uses .NET's default midpoint-to-even rounding, not away-from-zero.
- **API `WorkingDaysMask.IsWorkingDay` mis-maps Sunday:** .NET `DayOfWeek.Sunday` is 0, so `1 << -1` tests bit 31 instead of bit 6 (Android's Sunday bit). The API currently only uses `WorkingDaysMask.Default`, so there is no live impact. Suspected unintended; no API test exists for it.
- **Location matching is untrimmed:** matching is case-insensitive but does not trim, so `"Home "` with whitespace (or other labels, e.g. from quick add's free-text field) is excluded from Home/Office totals and fails the logic check — even though the Android grid trims and badges it `H`.
- **Android `eachDay`** has no unit test (API equivalent is tested).
- **No test** asserts `daysAtHome`/`daysInOffice` (FY-005) or Android summary display/refresh; Android ViewModel tests are backlog ([TODO § B](../../TODO.md#b-strong-unit-testing--partial)).

## Revision log

- 2026-10-27: Initial spec backfilled from implementation ([change](../../changes/2026-10-27-spec-driven-setup/design.md)).
- 2026-10-27: Final-review consistency fixes ([change](../../changes/2026-10-27-spec-driven-setup/design.md)).
