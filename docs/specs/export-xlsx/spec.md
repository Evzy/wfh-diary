# XLSX export

**Prefix:** `XLSX` · **Last updated:** 2026-10-27

## Purpose & scope

Produces an MGIS-style "Working from Home Diary" spreadsheet for one Australian financial year (1 July – 30 June) from the day entries stored on the phone, and lets the Android user pick a financial year in Settings, generate the workbook on-device (`XlsxWriter`) and share it via the system share sheet. The workbook contains one row per calendar day, an Excel formula per worked day, and a formula-driven summary block (total, home and office hours, claimable WFH amount at $0.67/hour, day counts). Out of scope: editing or importing spreadsheets, other file formats (CSV/PDF), persistent storage of exports beyond the share cache, and the in-app FY summary (see [summary-fy](../summary-fy/spec.md)).

## Requirements

### XLSX-001 — Workbook generated on-device

`DiaryStore.downloadExportXlsx(fyYear)` generates the diary for that financial year as an xlsx workbook with `XlsxWriter` (a hand-written OOXML zip, no third-party spreadsheet library) and returns its bytes with the file name `WFH-Diary-{fyYear}-{fyYear+1}.xlsx`.

- **Given** the app with stored days and settings
- **When** it exports FY 2025
- **Then** it gets a non-trivial xlsx byte array that is a valid package (workbook, sheet, styles parts) and the file name `WFH-Diary-2025-2026.xlsx`

### XLSX-002 — Export reads only local data and writes nothing

The export uses the local settings (display name, defaults if no row exists) and the stored day entries for that financial year only; it does not create or change any stored data.

- **Given** no stored days for FY 2024 and stored days for other FYs
- **When** the app exports FY 2024
- **Then** the workbook has one empty row per day of FY 2024 and contains none of the other FYs' entries, and no rows are created in the database

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

### XLSX-011 — Android generate and share

Tapping "Export financial year (XLSX)" generates the workbook on-device with `DiaryStore.downloadExportXlsx` for the selected FY (off the main thread), writes it to `cache/exports/WFH-Diary-{fy}-{fy+1}.xlsx`, and opens a system chooser with an `ACTION_SEND` intent carrying a FileProvider (`{package}.fileprovider`) URI with read permission; while running the button is disabled and "Downloading spreadsheet…" is shown, and on failure the error message (or "Export failed") is shown.

- **Given** the export picker shows FY 2025
- **When** the user taps "Export financial year (XLSX)"
- **Then** the button is disabled with "Downloading spreadsheet…" shown, then the share chooser opens for `WFH-Diary-2025-2026.xlsx` and the status text clears

## Interfaces

| Kind | Location |
| --- | --- |
| Workbook | `apps/android/app/src/main/java/dev/wfhdiary/app/export/XlsxWriter.kt` (port of the server `XlsxExportService`), `data/store/DiaryStore.kt` (`downloadExportXlsx`) |
| DTO / contract | None — binary xlsx; no shared DTO or contract fixture |
| Server (legacy, until phase 2) | `services/api/WfhDiary.Api/Endpoints/DiaryEndpoints.cs`, `Services/XlsxExportService.cs` (ClosedXML) — no longer used by the app |
| Android | `apps/android/app/src/main/java/dev/wfhdiary/app/export/XlsxExportHelper.kt` (cache file + share intent), `ui/settings/SettingsViewModel.kt` (`exportXlsx`, export FY state), `ui/settings/SettingsScreen.kt` (Export section), `MainActivity.kt` (drawer sync), `apps/android/app/src/main/res/xml/file_paths.xml` (`exports/` cache path) |

## Test coverage

| Requirement | Tests |
| --- | --- |
| XLSX-001 | `XlsxWriterTest.package_has_required_parts`, `XlsxWriterTest.filename_matches_previous_server_name`; `DiaryStoreTest.export_contains_xlsx_bytes_and_filename` |
| XLSX-002 | `DiaryStoreTest.export_contains_xlsx_bytes_and_filename`, `XlsxWriterTest.one_row_per_fy_day_and_summary_formulas` (that nothing is written to the database is not asserted) |
| XLSX-003 | `XlsxWriterTest.workbook_names_sheet_diary`, `XlsxWriterTest.title_and_name_are_written_and_escaped` |
| XLSX-004 | `XlsxWriterTest.headers_are_on_row_five`, `XlsxWriterTest.one_row_per_fy_day_and_summary_formulas` |
| XLSX-005 | `XlsxWriterTest.first_day_row_has_values_formula_and_styles`, `XlsxWriterTest.empty_day_keeps_input_styling` |
| XLSX-006 | `XlsxWriterTest.first_day_row_has_values_formula_and_styles`, `XlsxWriterTest.leave_day_has_zero_hours_and_grey_location`, `XlsxWriterTest.empty_day_keeps_input_styling` |
| XLSX-007 | `XlsxWriterTest.one_row_per_fy_day_and_summary_formulas` |
| XLSX-008 | `XlsxWriterTest.first_day_row_has_values_formula_and_styles`, `XlsxWriterTest.leave_day_has_zero_hours_and_grey_location` |
| XLSX-009 | `XlsxWriterTest.layout_freeze_widths_and_autofilter` |
| XLSX-010 | `untested` |
| XLSX-011 | `untested` |

## Known gaps

- No golden-file comparison against a workbook produced by the old server; `XlsxWriterTest` unzips the package and asserts the sheet name, headers, key cells, styles and formulas, but not that Excel renders it identically (covered by the manual device checklist).
- No Android ViewModel test for the export flow ([`TODO.md` § B](../../TODO.md#b-strong-unit-testing--partial)).
- Suspected inconsistency: the spreadsheet hours formula `(D-C)*24-E` is not clamped, so finish before start or a break longer than the span yields negative hours, whereas the in-app summary (`TimeHelpers.computeTotalHours`) clamps to 0; the two totals can disagree.
- Suspected inconsistency: the leave check for the Hours column (`Description == "LEAVE"`) is case-sensitive, while the grey leave styling is case-insensitive; an untimed entry described `leave` (lowercase) gets a blank Hours cell instead of 0 but is still shaded grey. (Separately, a timed entry described `LEAVE` gets the hours formula because times take precedence over the leave check.)
- The settings hint says the FY "defaults to the calendar year when you open settings", but the code resets it to the FY of the month currently shown in the month grid (XLSX-010); the copy looks out of date.
- The export status text (including "Downloading spreadsheet…") is always rendered in the error colour, and the label is now a misnomer because the workbook is generated locally.
- The $0.67/hour WFH rate is hard-coded separately in `XlsxWriter` and `DiaryRules`; a rate change must update both.

## Revision log

- 2026-10-27: Initial spec backfilled from implementation ([change](../../changes/2026-10-27-spec-driven-setup/design.md)).
- 2026-10-27: Final-review consistency fixes ([change](../../changes/2026-10-27-spec-driven-setup/design.md)).
- 2026-10-27: On-device-only phase 1 — XLSX-001, XLSX-002 and XLSX-011 now describe on-device generation by `XlsxWriter`; XLSX-003..009 content unchanged, coverage mapped to `XlsxWriterTest` ([change](../../changes/2026-10-27-on-device-only/design.md)).
