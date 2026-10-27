# API test plan

Requirement IDs refer to [capability specs](../specs/README.md).

Companion: [`TEST_STRATEGY.md`](TEST_STRATEGY.md) · **Backlog:** [`../TODO.md`](../TODO.md) § End improvements B.

## Phase 1 — integration smoke

| Test | File | Status |
| --- | --- | --- |
| `GET /health` returns 200 | `HealthTests.cs` | Done [OTA-001] |
| Upsert day + summary home hours | `DiaryFlowTests.cs` | Done [DAY-003, FY-003, FY-004] |
| Export returns xlsx content-type | `DiaryFlowTests.cs` | Done [XLSX-001] |
| Settings GET/PUT | `SettingsFlowTests.cs` | Done [SET-001, SET-002] |
| Leave create/list/delete | `LeaveFlowTests.cs` | Done [LEAVE-001, LEAVE-004, LEAVE-005, LEAVE-006] |
| Work template CRUD | `WorkTemplateFlowTests.cs` | Done [TPL-001, TPL-003, TPL-007] |

## Phase 2 — pure unit tests (no HTTP)

| Subject | File | Status |
| --- | --- | --- |
| `TimeHelpers` | `TimeHelpersTests.cs` | Done [DAY-003, DAY-006] |
| `FinancialYearHelper` | `FinancialYearHelperTests.cs` | Done [FY-001, FY-002] |
| `SummaryCalculator` | `SummaryCalculatorTests.cs` | Done [FY-004, FY-006, FY-007] |
| Contract JSON fixtures | `ApiContractTests.cs` | Done (incl. `work-template.json`) [DAY-001, DAY-005, FY-003, LEAVE-001, SET-007, TPL-008] |

## Phase 3 — auth

| Subject | File | Status |
| --- | --- | --- |
| `/v1/settings` without auth → 401 (Firebase off, no debug header) | `AuthGateTests.cs` | Done [AUTH-002] |
| `X-Debug-User-Id` succeeds in test factory | `AuthGateTests.cs` | Done [AUTH-001] |
| `/health` no auth | `AuthGateTests.cs` | Done [AUTH-005, OTA-001] |
| Firebase configured → Bearer required | — | Manual / future factory variant [AUTH-003] |

## Phase 4 — deferred

| Subject | Cases |
| --- | --- |
| `DiaryService` in-memory | create diary rows, partial upsert, leave, settings PATCH [DAY-001, DAY-003, LEAVE-004, SET-002] |
| `XlsxExportService` golden-file | fixture user + days → assert key cells/formulas [XLSX-005, XLSX-006, XLSX-007] |
| Live response shape | extend contract assertions beyond settings/day |

Suggested files: `DiaryServiceTests.cs`, `XlsxExportServiceTests.cs`, `TestData/minimal-diary-fixture.json`.
