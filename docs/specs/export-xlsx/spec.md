# XLSX export

**Prefix:** `XLSX` · **Last updated:** 2026-10-27

## Purpose & scope

Produces an MGIS-style "Working from Home Diary" spreadsheet for one Australian financial year (1 July – 30 June) from the caller's day entries, and lets the Android user pick a financial year in Settings, download the workbook, and share it via the system share sheet. The workbook contains one row per calendar day, an Excel formula per worked day, and a formula-driven summary block (total, home and office hours, claimable WFH amount at $0.67/hour, day counts). Out of scope: editing or importing spreadsheets, other file formats (CSV/PDF), server-side storage of exports, and the in-app FY summary (see the FY summary spec).

## Requirements

### XLSX-001 — Export endpoint returns an xlsx file

`GET /v1/diaries/{fyYear}/export.xlsx` returns the caller's diary for that financial year as an xlsx file attachment named `WFH-Diary-{fyYear}-{fyYear+1}.xlsx`.

- **Given** an authenticated caller (Firebase Bearer, or `X-Debug-User-Id` when Firebase is not configured)
- **When** they request `GET /v1/diaries/2025/export.xlsx`
- **Then** the response is 200 with content type `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`, download filename `WFH-Diary-2025-2026.xlsx`, and a non-trivial xlsx body

### XLSX-002 — Export is scoped to the caller and creates missing records

The export uses only the caller's settings, diary and day entries, and creates the settings and diary for that financial year if they do not yet exist; a request with no user id is rejected with 401.

- **Given** a caller with no diary for FY 2024 and another user who has entries for FY 2024
- **When** the caller requests `GET /v1/diaries/2024/export.xlsx`
- **Then** a diary for FY 2024 is created for the caller, the workbook contains none of the other user's entries, and a request with no user id returns 401

### XLSX-003 — Single "Diary" sheet with title and name

The workbook has one worksheet named `Diary` whose cell A1 is `"{start year}/{end year two digits} Working from Home Diary"` (bold, 16 pt) and whose row 2 is `Name:` followed by the caller's display name from settings.

- **Given** a diary for FY 2025 (1 Jul 2025 – 30 Jun 2026) and settings display name `Test User`
- **When** the workbook is generated
- **Then** sheet `Diary` has A1 = `2025/26 Working from Home Diary`, A2 = `Name:` and B2 = `Test User`

### XLSX-004 — Column headers and one row per financial-year day

Row 5 holds the headers `Day, Date, Started, Finished, Break (hrs), Hours, Description, Home/Office` (columns A–H), and rows from 6 onward hold exactly one row per calendar day from the diary's FY start to FY end inclusive, including days with no entry.

- **Given** a diary from 1 Jul 2025 to 30 Jun 2026 with a single day entry
- **When** the workbook is generated
- **Then** row 6 is Tuesday 1 Jul 2025, the last data row is `6 + 365 - 1` (30 Jun 2026), column A holds the weekday name and column B the date formatted `ddd d mmm yyyy`

### XLSX-005 — Day row values

For each day with an entry, Started and Finished are written as Excel time fractions (minutes ÷ 1440, formatted `h:mm AM/PM`), Break as decimal hours (`0.00`), and Description and Home/Office as the entry's text; missing values are left blank, and columns C–E are shaded as input cells.

- **Given** an entry on 1 Jul 2025 with start 08:00, finish 17:00, break 1, location `Home`, description `test work`
- **When** the workbook is generated
- **Then** C6 = 0.3333… (8:00 AM), D6 = 0.7083… (5:00 PM), E6 = 1, G6 = `test work`, H6 = `Home`

### XLSX-006 — Hours formula per day row

Column F holds the formula `(D{row}-C{row})*24-E{row}` when the entry has both start and finish times; otherwise it is `0` when the description is exactly `LEAVE`, and blank for all other days.

- **Given** an entry on row 6 with start and finish times, a leave day (description `LEAVE`, no times) and an empty day
- **When** the workbook is generated
- **Then** F6 is the formula `(D6-C6)*24-E6`, the leave row's F is `0`, and the empty day's F is blank

### XLSX-007 — Summary block formulas

Two rows below the last data row, column A/B hold a bold summary block of six formulas over the data rows: Total hours worked `SUM(F)`, Home hours `SUMIF(H,"Home",F)`, Claimable WFH `B{home row}*0.67` (currency `$#,##0.00`), Office hours `SUMIF(H,"Office",F)`, Days at home `COUNTIF(H,"Home")`, Days in office `COUNTIF(H,"Office")`.

- **Given** a full-year diary whose last data row is `L`
- **When** the workbook is generated
- **Then** B{L+2} = `SUM(F6:FL)`, B{L+3} = `SUMIF(H6:HL,"Home",F6:FL)`, B{L+4} = `B{L+3}*0.67`, B{L+5} = `SUMIF(H6:HL,"Office",F6:FL)`, B{L+6} = `COUNTIF(H6:HL,"Home")`, B{L+7} = `COUNTIF(H6:HL,"Office")`

### XLSX-008 — Location cell styling

The Home/Office cell is styled green (`#70AD47`, white bold text) for `Home`, light blue (`#81D4FA`, bold) for `Office` (both case-insensitive), and grey (`#E0E0E0`) when there is no Home/Office location and the description is `LEAVE`.

- **Given** rows with location `Home`, location `office`, and a leave row with no location
- **When** the workbook is generated
- **Then** their column H cells are filled green, light blue and grey respectively

### XLSX-009 — Sheet layout

Columns A–H have fixed widths, rows 1–5 are frozen, and an auto-filter covers the header row through the last data row (A5:H{last}).

- **Given** any generated workbook
- **When** it is opened in Excel
- **Then** scrolling keeps the title and header rows visible and the header row has filter drop-downs over the day rows only

### XLSX-010 — Android export financial-year picker

Settings has an Export section showing "Financial year {fy}–{fy+1}" with previous/next arrows; the selection starts at the current financial year and is reset to the financial year of the month shown in the month grid each time the settings drawer opens.

- **Given** the month grid is showing March 2026 (FY 2025)
- **When** the user opens the settings drawer and taps the previous arrow once
- **Then** the picker first shows "Financial year 2025–2026" and then "Financial year 2024–2025"

### XLSX-011 — Android download and share

Tapping "Export financial year (XLSX)" downloads `GET /v1/diaries/{fy}/export.xlsx` for the selected FY, writes it to `cache/exports/WFH-Diary-{fy}-{fy+1}.xlsx`, and opens a system chooser with an `ACTION_SEND` intent carrying a FileProvider (`{package}.fileprovider`) URI with read permission; while running the button is disabled and "Downloading spreadsheet…" is shown, and on failure the error message (or "Export failed") is shown.

- **Given** the export picker shows FY 2025 and the API is reachable
- **When** the user taps "Export financial year (XLSX)"
- **Then** the button is disabled with "Downloading spreadsheet…" shown, then the share chooser opens for `WFH-Diary-2025-2026.xlsx` and the status text clears

## Interfaces

| Kind | Location |
| --- | --- |
| API | `GET /v1/diaries/{fyYear}/export.xlsx` — `services/api/WfhDiary.Api/Endpoints/DiaryEndpoints.cs` |
| API service | `services/api/WfhDiary.Api/Services/XlsxExportService.cs` (ClosedXML; registered as singleton in `Program.cs`) |
| DTO / contract | None — binary xlsx response; no shared DTO or contract fixture |
| Android | `apps/android/app/src/main/java/dev/wfhdiary/app/export/XlsxExportHelper.kt`, `data/remote/DiaryRepository.kt` (`downloadExportXlsx`), `ui/settings/SettingsViewModel.kt` (`exportXlsx`, export FY state), `ui/settings/SettingsScreen.kt` (Export section), `MainActivity.kt` (drawer sync), `apps/android/app/src/main/res/xml/file_paths.xml` (`exports/` cache path) |

## Test coverage

| Requirement | Tests |
| --- | --- |
| XLSX-001 | `DiaryFlowTests.Export_returns_xlsx` (status, content type, size; filename not asserted) |
| XLSX-002 | `untested` |
| XLSX-003 | `XlsxExportServiceTests.Export_summary_formulas_use_valid_column_ranges` (sheet name only; title and name rows untested) |
| XLSX-004 | `XlsxExportServiceTests.Export_summary_formulas_use_valid_column_ranges` (row count implied via last data row; headers untested) |
| XLSX-005 | `untested` |
| XLSX-006 | `XlsxExportServiceTests.Export_day_row_hours_formula_uses_start_finish_and_break` (timed row only; leave and blank rows untested) |
| XLSX-007 | `XlsxExportServiceTests.Export_summary_formulas_use_valid_column_ranges` (claimable WFH row not asserted) |
| XLSX-008 | `untested` |
| XLSX-009 | `untested` |
| XLSX-010 | `untested` |
| XLSX-011 | `untested` |

## Known gaps

- No golden-file xlsx export test; workbook content is only spot-checked by two formula tests ([`TODO.md` § B](../../TODO.md#b-strong-unit-testing--partial)).
- No Android ViewModel/repository tests (MockEngine) for the export flow ([`TODO.md` § B](../../TODO.md#b-strong-unit-testing--partial)).
- Suspected bug: the Android `HttpClient` does not set `expectSuccess` and `downloadExportXlsx` does not check the status code, so a non-2xx response (e.g. a 401 JSON error) is saved and shared as an `.xlsx` file instead of showing "Export failed".
- Suspected inconsistency: the spreadsheet hours formula `(D-C)*24-E` is not clamped, so finish before start or a break longer than the span yields negative hours, whereas the in-app summary (`TimeHelpers.ComputeTotalHours`) clamps to 0; the two totals can disagree.
- Suspected inconsistency: the leave check for the Hours column (`Description == "LEAVE"`) is case-sensitive, while the grey leave styling is case-insensitive; an untimed entry described `leave` (lowercase) gets a blank Hours cell instead of 0 but is still shaded grey. (Separately, a timed entry described `LEAVE` gets the hours formula because times take precedence over the leave check.)
- The settings hint says the FY "defaults to the calendar year when you open settings", but the code resets it to the FY of the month currently shown in the month grid (XLSX-010); the copy looks out of date.
- The export status text (including "Downloading spreadsheet…") is always rendered in the error colour.
- `fyYear` is not range-validated: exporting any in-range year creates an empty diary for it as a side effect (XLSX-002), and years outside the `DateOnly` range (e.g. 0 or 9999) throw in `FinancialYearHelper.FyRange`, likely surfacing as a 500.
- The $0.67/hour WFH rate is hard-coded separately in `XlsxExportService` and `SummaryCalculator`; a rate change must update both.

## Revision log

- 2026-10-27: Initial spec backfilled from implementation ([change](../../changes/2026-10-27-spec-driven-setup/design.md)).
