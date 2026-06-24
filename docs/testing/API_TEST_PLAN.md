# API test plan

Companion: [`TEST_STRATEGY.md`](TEST_STRATEGY.md) · **Backlog:** [`../TODO.md`](../TODO.md) § End improvements B.

## Phase 1 (current)

| Test | File | Status |
| --- | --- | --- |
| `GET /health` returns 200 | `HealthTests.cs` | Done |
| Upsert day + summary home hours | `DiaryFlowTests.cs` | Done |
| Export returns xlsx content-type | `DiaryFlowTests.cs` | Done |

## Phase 2 — strong unit testing (deferred)

### Pure unit tests (no HTTP)

| Subject | Cases |
| --- | --- |
| `TimeHelpers.ParseTimeString` | valid `08:00`, `8:00`, null, invalid |
| `TimeHelpers.ComputeTotalHours` | normal day, no break, missing start/finish |
| `TimeHelpers.MinutesToExcelDecimal` | 8:30 → ~0.354 |
| `FinancialYearHelper.CurrentFyYear` | June → prior year; July → current year |
| `FinancialYearHelper.EachDay` | leap year FY length |
| `SummaryCalculator.Compute` | home only, office only, mixed, leave rows excluded from location counts, logic check fail |

Suggested files (one test class per file):

- `TimeHelpersTests.cs`
- `FinancialYearHelperTests.cs`
- `SummaryCalculatorTests.cs`

### Service tests (in-memory DB)

| Subject | Cases |
| --- | --- |
| `DiaryService.GetOrCreateDiaryAsync` | creates 365/366 rows for FY |
| `DiaryService.UpsertDayAsync` | partial update preserves other fields |
| `DiaryService.CreateLeaveAsync` | range marks LEAVE, clears location |
| `DiaryService.UpdateSettingsAsync` | partial PATCH semantics |

Suggested file: `DiaryServiceTests.cs` (+ `WfhDiaryApiFactory` or dedicated in-memory factory).

### Export golden-file

| Subject | Cases |
| --- | --- |
| `XlsxExportService.Export` | fixture user + 3 days → assert B3, row 24 columns, N14/O14 formulas |

Suggested files:

- `TestData/minimal-diary-fixture.json`
- `XlsxExportServiceTests.cs`
- `TestData/expected-export-cells.json` (cell → expected value/formula)

### Auth

| Subject | Cases |
| --- | --- |
| Firebase configured | `/v1/settings` without Bearer → 401 |
| Dev open mode | `X-Debug-User-Id` or no auth when Firebase off |

Suggested file: `AuthGateTests.cs`
