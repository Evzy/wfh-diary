# API test plan

Companion: [`TEST_STRATEGY.md`](TEST_STRATEGY.md) · **Backlog:** [`../TODO.md`](../TODO.md) § End improvements B.

## Phase 1 — integration smoke

| Test | File | Status |
| --- | --- | --- |
| `GET /health` returns 200 | `HealthTests.cs` | Done |
| Upsert day + summary home hours | `DiaryFlowTests.cs` | Done |
| Export returns xlsx content-type | `DiaryFlowTests.cs` | Done |
| Settings GET/PUT | `SettingsFlowTests.cs` | Done |
| Leave create/list/delete | `LeaveFlowTests.cs` | Done |
| Work template CRUD | `WorkTemplateFlowTests.cs` | Done |

## Phase 2 — pure unit tests (no HTTP)

| Subject | File | Status |
| --- | --- | --- |
| `TimeHelpers` | `TimeHelpersTests.cs` | Done |
| `FinancialYearHelper` | `FinancialYearHelperTests.cs` | Done |
| `SummaryCalculator` | `SummaryCalculatorTests.cs` | Done |
| Contract JSON fixtures | `ApiContractTests.cs` | Done (incl. `work-template.json`) |

## Phase 3 — auth

| Subject | File | Status |
| --- | --- | --- |
| `/v1/settings` without auth → 401 (Firebase off, no debug header) | `AuthGateTests.cs` | Done |
| `X-Debug-User-Id` succeeds in test factory | `AuthGateTests.cs` | Done |
| `/health` no auth | `AuthGateTests.cs` | Done |
| Firebase configured → Bearer required | — | Manual / future factory variant |

## Phase 4 — deferred

| Subject | Cases |
| --- | --- |
| `DiaryService` in-memory | create diary rows, partial upsert, leave, settings PATCH |
| `XlsxExportService` golden-file | fixture user + days → assert key cells/formulas |
| Live response shape | extend contract assertions beyond settings/day |

Suggested files: `DiaryServiceTests.cs`, `XlsxExportServiceTests.cs`, `TestData/minimal-diary-fixture.json`.
