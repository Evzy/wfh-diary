# Testing strategy

**Last updated:** 2026-10-27

## Approach

Testing is **docs-driven**: behaviour is described in the [capability specs](../specs/README.md) and [`ANDROID_TEST_PLAN.md`](ANDROID_TEST_PLAN.md) before or alongside implementation. Prefer **fast unit tests** over broad UI tests — most logic is time math, FY summaries, backup encoding and notification decisions.

Run from `apps/android/`: `./gradlew :app:testDebugUnitTest`. CI runs the same suite plus `assembleDebug` on every PR.

## Layers

| Layer | What runs | Examples |
| --- | --- | --- |
| JVM unit tests | Pure helpers and rules, table-driven with named cases | `TimeHelpersTest`, `FinancialYearHelperTest`, `DiaryRulesTest`, `GeofenceHelperTest`, `BackupCodecTest`, `BackupRetentionTest`, `XlsxWriterTest` |
| Robolectric tests | Room, `DiaryStore`, backup/restore services and notification updates against a real in-memory database and Android framework | `WfhDiaryDatabaseTest`, `DiaryStoreTest`, `BackupServiceTest`, `RestoreServiceTest`, `DayLogNotificationUpdaterTest` |
| Manual | Device checks from [`ANDROID_TEST_PLAN.md`](ANDROID_TEST_PLAN.md) before release (notifications, backups to a real folder, OTA install, map picker) | — |

Robolectric tests use `@Config(sdk = [34], application = Application::class)` and `runBlocking`.

## Principles

1. **Pure functions first** — time, FY and summary helpers get table-driven tests with named cases.
2. **One assertion theme per test** — e.g. `leave_day_has_zero_hours_and_grey_location`, not mega-tests.
3. **Backup format is the contract** — `BackupCodecTest` covers every `BackupDocument` version the app can read ([CONSTITUTION](../CONSTITUTION.md) principle 5).
4. **Fakes over mocks** — in-memory Room, `FakeBackupFolder` for backup storage.
5. **CI gate** — PR blocked if tests fail; no `--no-verify`.

## Revision log

- 2026-10-27: On-device only — removed API, contract and .NET test layers; documented JVM + Robolectric layers.
- 2026-06-24: Initial strategy; Phase 2 marked as post-ship improvement per product backlog.
