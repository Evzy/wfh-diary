# Testing strategy

**Last updated:** 2026-06-24

## Approach

Testing is **docs-driven**: behavior is described in [`API_TEST_PLAN.md`](API_TEST_PLAN.md) and [`ANDROID_TEST_PLAN.md`](ANDROID_TEST_PLAN.md) before or alongside implementation. Prefer **fast unit tests** over broad UI tests for this app — most logic is time math, summary aggregation, and sync payloads.

## Current coverage

| Layer | What runs today | Target |
| --- | --- | --- |
| API | 3 integration tests via `WebApplicationFactory` | Full plan below |
| Android | None | JVM unit tests per plan |
| Export | Smoke: response is non-empty xlsx | Golden-file cell assertions |

## Phases

### Phase 1 — Ship (minimum)

Keep existing smoke tests green in CI. Manual operability checklist before release (see [`OPERABILITY_CHECKLIST.md`](OPERABILITY_CHECKLIST.md)).

### Phase 2 — Strong unit testing (end improvement)

Implement after core Android + CI ship. Goal: **change time/summary/export logic with confidence** without manual spreadsheet checks every time.

**Principles:**

1. **Pure functions first** — `TimeHelpers`, `SummaryCalculator`, Android time utilities get table-driven tests with named cases.
2. **One assertion theme per test** — e.g. `Leave_day_has_zero_hours_and_leave_description`, not mega-tests.
3. **Golden file for xlsx only** — binary export compared at known cells (B3 name, F24 formula, N14, O14), not full file diff.
4. **Fakes over mocks** — in-memory SQLite for API service tests; fake HTTP client for Android repository tests.
5. **CI gate** — PR blocked if tests fail; no `--no-verify`.

## Revision log

- 2026-06-24: Initial strategy; Phase 2 marked as post-ship improvement per product backlog.
