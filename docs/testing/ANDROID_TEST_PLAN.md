# Android test plan

Requirement IDs refer to [capability specs](../specs/README.md).

Companion: [`TEST_STRATEGY.md`](TEST_STRATEGY.md) · **Backlog:** [`../TODO.md`](../TODO.md) § End improvements B.

## Phase 1 (current)

No JVM tests yet. Manual smoke: sign-in, month grid, edit day, export download. [AUTH-010, DAY-008, DAY-011, XLSX-011]

## Phase 2 — strong unit testing (deferred)

Implement after Compose UI + sync ship. Use `./gradlew :app:testDebugUnitTest`.

### Pure utilities (one test class per file)

| Subject | Cases |
| --- | --- |
| FY helpers | current FY from today's date, month list for FY [FY-001, FY-002] |
| Time parsing/formatting | match API `HH:mm` contract [DAY-003, DAY-005] |
| Total hours | `(finish - start) - break`, clamp at zero [DAY-006] |
| Day badge | Home / Office / Leave / empty from entry fields [DAY-008] |

### ViewModels

| Subject | Cases |
| --- | --- |
| `MonthGridViewModel` | load month, tap day emits edit state, summary from API [DAY-010, DAY-011, FY-010] |
| `DayEditViewModel` | save builds correct `UpsertDayEntryRequest` [DAY-011] |
| `SettingsViewModel` | defaults load/save [SET-008] |

### Notifications

| Subject | Cases |
| --- | --- |
| Home action | applies default home times + location Home [NOTIF-005] |
| Office action | applies default office times [NOTIF-005] |
| Leave range | suppresses WorkManager until end date [NOTIF-003] |
| Skip when on leave | no notification fired [NOTIF-003, NOTIF-007] |

### Repository (fake Ktor)

| Subject | Cases |
| --- | --- |
| `DiaryRepository` | GET days deserializes; PUT sends camelCase JSON; Bearer header attached [AUTH-007] |

### Location auto-log (when Phase A ships)

| Subject | Cases |
| --- | --- |
| Geofence check | inside radius → Office; outside → Home defaults [GEO-001, GEO-002, GEO-008] |

Suggested package: `dev.wfhdiary.app` under `app/src/test/java/...` mirroring production package layout.
