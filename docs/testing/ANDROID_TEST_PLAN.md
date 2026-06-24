# Android test plan

Companion: [`TEST_STRATEGY.md`](TEST_STRATEGY.md) · **Backlog:** [`../TODO.md`](../TODO.md) § End improvements B.

## Phase 1 (current)

No JVM tests yet. Manual smoke: sign-in, month grid, edit day, export download.

## Phase 2 — strong unit testing (deferred)

Implement after Compose UI + sync ship. Use `./gradlew :app:testDebugUnitTest`.

### Pure utilities (one test class per file)

| Subject | Cases |
| --- | --- |
| FY helpers | current FY from today's date, month list for FY |
| Time parsing/formatting | match API `HH:mm` contract |
| Total hours | `(finish - start) - break`, clamp at zero |
| Day badge | Home / Office / Leave / empty from entry fields |

### ViewModels

| Subject | Cases |
| --- | --- |
| `MonthGridViewModel` | load month, tap day emits edit state, summary from API |
| `DayEditViewModel` | save builds correct `UpsertDayEntryRequest` |
| `SettingsViewModel` | defaults load/save |

### Notifications

| Subject | Cases |
| --- | --- |
| Home action | applies default home times + location Home |
| Office action | applies default office times |
| Leave range | suppresses WorkManager until end date |
| Skip when on leave | no notification fired |

### Repository (fake Ktor)

| Subject | Cases |
| --- | --- |
| `DiaryRepository` | GET days deserializes; PUT sends camelCase JSON; Bearer header attached |

### Location auto-log (when Phase A ships)

| Subject | Cases |
| --- | --- |
| Geofence check | inside radius → Office; outside → Home defaults |

Suggested package: `dev.wfhdiary.app` under `app/src/test/java/...` mirroring production package layout.
