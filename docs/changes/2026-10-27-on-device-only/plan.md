# On-device only (phase 1) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Android app use a local Room database as its only source of truth, with automatic JSON backups to a user-chosen folder, manual backup/share/restore, an on-device XLSX export, and a one-time "Import from old server".

**Architecture:** A new `DiaryDataSource` interface carries the existing `DiaryRepository` method signatures. The remote repository and a new local `DiaryStore` (Room) both implement it. `WfhDiaryApplication.diaryRepository` switches to `DiaryStore`, so call sites barely change. Server business rules are ported into a pure `DiaryRules` object. Backups use `BackupCodec` (kotlinx.serialization) and a Storage Access Framework (SAF) folder driven by WorkManager.

**Tech Stack:** Kotlin 2.0.21, Compose, Room 2.6.1 (KSP), WorkManager 2.10, kotlinx.serialization 1.7.3, Ktor 3.0.3 (importer only), androidx.documentfile, JUnit 4, Robolectric 4.14.1, ktor-client-mock.

**Spec:** [`design.md`](design.md). Phase 2 (removing the backend, Firebase and Azure) gets its own `plan-phase-2.md`, written only after the phase-1 gate in Task 11 passes.

---

## Global constraints

- All Gradle commands run from `apps/android` on Windows PowerShell:
  - Unit tests: `.\gradlew.bat :app:testDebugUnitTest --no-daemon`
  - Single class: `.\gradlew.bat :app:testDebugUnitTest --no-daemon --tests "dev.wfhdiary.app.backup.BackupCodecTest"`
  - Build: `.\gradlew.bat :app:assembleDebug --no-daemon`
- There is no bash and no .NET SDK. Do not try to run `dotnet`.
- Do not edit anything under `services/`, `packages/` or `.github/` in phase 1. The one exception is the version sync that `scripts/bump-version.ps1` writes to `services/api/WfhDiary.Api/AppVersion.cs` and `WfhDiary.Api.csproj` in Task 11.
- Robolectric tests must use `@Config(sdk = [34], application = Application::class)` (plain `android.app.Application`). Otherwise `WfhDiaryApplication.onCreate` runs Firebase setup.
- Robolectric tests that touch Room use `runBlocking`, not `runTest`.
- Commit messages: conventional style (`feat(android): …`, `test(android): …`, `docs: …`). Set `SKIP_VERSION_BUMP=1` for every commit except the final version bump in Task 11. If the pre-commit hook is not installed it does nothing.
- Follow the existing code style: 4-space indent, trailing commas, one public top-level type per file where practical, test method names like `upsert_keeps_unset_fields`.
- Dates are ISO `yyyy-MM-dd` strings (`LocalDate.toString()`). Times are `HH:mm` strings.

## File map

New (main, under `apps/android/app/src/main/java/dev/wfhdiary/app/`):

| File | Responsibility |
|---|---|
| `backup/BackupDay.kt`, `backup/BackupLeavePeriod.kt`, `backup/BackupDocument.kt` | Backup v1 model (also the stored day shape) |
| `backup/BackupParseResult.kt`, `backup/BackupCodec.kt` | Encode, decode and validate backups |
| `data/store/DiaryRules.kt` | Pure ports of server rules (upsert, replace, leave, summary, settings) |
| `data/local/LeavePeriodEntity.kt`, `LeavePeriodDao.kt`, `SettingsEntity.kt`, `SettingsDao.kt` | Room v2 tables |
| `export/XlsxWriter.kt` | Hand-written OOXML workbook |
| `data/DiaryDataSource.kt` | Interface shared by remote and local |
| `data/store/DiaryStore.kt` | Local implementation plus export/replace-all |
| `backup/BackupPreferences.kt`, `BackupFolder.kt`, `SafBackupFolder.kt`, `BackupRetention.kt`, `BackupOutcome.kt`, `BackupService.kt`, `BackupWorker.kt`, `BackupScheduler.kt`, `BackupShareHelper.kt`, `BackupHealth.kt` | Backup infrastructure |
| `backup/RestoreOutcome.kt`, `backup/RestoreService.kt` | Restore flow |
| `backup/LegacyServerImporter.kt` | Phase-1-only server import |
| `ui/backup/BackupUiState.kt`, `BackupViewModel.kt`, `BackupSection.kt` | Backup UI |

Modified: `data/local/DayEntryEntity.kt`, `DayEntryDao.kt`, `WfhDiaryDatabase.kt`, `data/remote/DiaryRepository.kt`, `WfhDiaryApplication.kt`, `ui/month/MonthGridViewModel.kt`, `ui/month/MonthGridScreen.kt`, `ui/settings/SettingsScreen.kt`, `MainActivity.kt`, `res/values/strings.xml`, `app/build.gradle.kts`, `gradle/libs.versions.toml`.

---

### Task 1: Backup document model and codec

**Requirements:** BAK-001, BAK-008, BAK-010.

**Files:**
- Create: `app/src/main/java/dev/wfhdiary/app/backup/BackupDay.kt`
- Create: `app/src/main/java/dev/wfhdiary/app/backup/BackupLeavePeriod.kt`
- Create: `app/src/main/java/dev/wfhdiary/app/backup/BackupDocument.kt`
- Create: `app/src/main/java/dev/wfhdiary/app/backup/BackupParseResult.kt`
- Create: `app/src/main/java/dev/wfhdiary/app/backup/BackupCodec.kt`
- Test: `app/src/test/java/dev/wfhdiary/app/backup/BackupCodecTest.kt`

- [ ] **Step 1: Create the model files**

`BackupDay.kt`:

```kotlin
package dev.wfhdiary.app.backup

import kotlinx.serialization.Serializable

@Serializable
data class BackupDay(
    val date: String,
    val startTime: String? = null,
    val finishTime: String? = null,
    val breakHours: Double? = null,
    val description: String? = null,
    val location: String? = null,
)
```

`BackupLeavePeriod.kt`:

```kotlin
package dev.wfhdiary.app.backup

import kotlinx.serialization.Serializable

@Serializable
data class BackupLeavePeriod(
    val id: String,
    val startDate: String,
    val endDate: String,
    val note: String? = null,
)
```

`BackupDocument.kt`:

```kotlin
package dev.wfhdiary.app.backup

import dev.wfhdiary.app.data.remote.UserSettingsDto
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class BackupDocument(
    val format: String = FORMAT,
    val version: Int = CURRENT_VERSION,
    val exportedAt: String,
    val appVersion: String,
    val settings: UserSettingsDto,
    val days: List<BackupDay> = emptyList(),
    val leavePeriods: List<BackupLeavePeriod> = emptyList(),
    val legacy: JsonObject? = null,
) {
    companion object {
        const val FORMAT = "wfh-diary-backup"
        const val CURRENT_VERSION = 1
    }
}
```

`BackupParseResult.kt`:

```kotlin
package dev.wfhdiary.app.backup

sealed interface BackupParseResult {
    data class Success(val document: BackupDocument) : BackupParseResult

    data class Failure(val message: String) : BackupParseResult
}
```

- [ ] **Step 2: Write the failing tests**

`BackupCodecTest.kt`:

```kotlin
package dev.wfhdiary.app.backup

import dev.wfhdiary.app.data.remote.UserSettingsDto
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {
    private fun settings() =
        UserSettingsDto(
            displayName = "Test User",
            notificationTime = "08:00",
            defaultHomeStart = "08:00",
            defaultHomeFinish = "17:00",
            defaultHomeBreakHours = 1.0,
            defaultOfficeStart = "08:30",
            defaultOfficeFinish = "17:00",
            defaultOfficeBreakHours = 0.5,
        )

    private fun document(
        days: List<BackupDay> = listOf(BackupDay("2025-07-01", "08:00", "17:00", 1.0, null, "Home")),
        leave: List<BackupLeavePeriod> = listOf(BackupLeavePeriod("a", "2025-12-22", "2026-01-02", "Xmas")),
        legacy: JsonObject? = null,
    ) = BackupDocument(
        exportedAt = "2026-10-27T09:00:00+11:00",
        appVersion = "1.0.0",
        settings = settings(),
        days = days,
        leavePeriods = leave,
        legacy = legacy,
    )

    private fun decodeOk(text: String): BackupDocument {
        val result = BackupCodec.decode(text)
        assertTrue("expected success but was $result", result is BackupParseResult.Success)
        return (result as BackupParseResult.Success).document
    }

    private fun decodeFails(text: String): String {
        val result = BackupCodec.decode(text)
        assertTrue("expected failure but was $result", result is BackupParseResult.Failure)
        return (result as BackupParseResult.Failure).message
    }

    @Test
    fun round_trip_preserves_document() {
        val doc = document()
        assertEquals(doc, decodeOk(BackupCodec.encode(doc)))
    }

    @Test
    fun encode_includes_format_and_version() {
        val text = BackupCodec.encode(document())
        assertTrue(text.contains("\"format\": \"wfh-diary-backup\""))
        assertTrue(text.contains("\"version\": 1"))
    }

    @Test
    fun legacy_section_is_preserved() {
        val legacy = JsonObject(mapOf("workTemplates" to JsonArray(listOf(JsonPrimitive("t1")))))
        val decoded = decodeOk(BackupCodec.encode(document(legacy = legacy)))
        assertEquals(legacy, decoded.legacy)
    }

    @Test
    fun unknown_fields_are_ignored() {
        val text = BackupCodec.encode(document()).replaceFirst("{", "{\"futureField\": 42,")
        decodeOk(text)
    }

    @Test
    fun malformed_json_is_refused() {
        decodeFails("{ not json")
    }

    @Test
    fun wrong_format_is_refused() {
        val text = BackupCodec.encode(document()).replace("wfh-diary-backup", "something-else")
        assertTrue(decodeFails(text).contains("Not a WFH Diary backup"))
    }

    @Test
    fun newer_version_is_refused() {
        val text = BackupCodec.encode(document()).replace("\"version\": 1", "\"version\": 2")
        assertTrue(decodeFails(text).contains("newer"))
    }

    @Test
    fun invalid_day_date_is_refused() {
        decodeFails(BackupCodec.encode(document(days = listOf(BackupDay("2025-13-01")))))
    }

    @Test
    fun duplicate_day_is_refused() {
        decodeFails(BackupCodec.encode(document(days = listOf(BackupDay("2025-07-01"), BackupDay("2025-07-01")))))
    }

    @Test
    fun invalid_time_is_refused() {
        decodeFails(BackupCodec.encode(document(days = listOf(BackupDay("2025-07-01", startTime = "25:99")))))
    }

    @Test
    fun negative_break_is_refused() {
        decodeFails(BackupCodec.encode(document(days = listOf(BackupDay("2025-07-01", breakHours = -1.0)))))
    }

    @Test
    fun invalid_settings_time_is_refused() {
        val doc = document().copy(settings = settings().copy(notificationTime = "nope"))
        decodeFails(BackupCodec.encode(doc))
    }

    @Test
    fun reversed_leave_period_is_swapped() {
        val doc = document(leave = listOf(BackupLeavePeriod("a", "2026-01-02", "2025-12-22")))
        val decoded = decodeOk(BackupCodec.encode(doc))
        assertEquals("2025-12-22", decoded.leavePeriods.single().startDate)
        assertEquals("2026-01-02", decoded.leavePeriods.single().endDate)
    }

    @Test
    fun blank_leave_id_is_refused() {
        decodeFails(BackupCodec.encode(document(leave = listOf(BackupLeavePeriod(" ", "2025-12-22", "2025-12-23")))))
    }
}
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `.\gradlew.bat :app:testDebugUnitTest --no-daemon --tests "dev.wfhdiary.app.backup.BackupCodecTest"`
Expected: compilation failure, because `BackupCodec` is unresolved.

- [ ] **Step 4: Implement `BackupCodec.kt`**

```kotlin
package dev.wfhdiary.app.backup

import dev.wfhdiary.app.util.TimeHelpers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.time.format.DateTimeParseException

object BackupCodec {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
            prettyPrint = true
        }

    fun encode(document: BackupDocument): String =
        json.encodeToString(BackupDocument.serializer(), document)

    fun decode(text: String): BackupParseResult {
        val root =
            try {
                json.parseToJsonElement(text).jsonObject
            } catch (e: Exception) {
                return BackupParseResult.Failure("Not a valid backup file (unreadable JSON).")
            }
        val format = root["format"]?.jsonPrimitive?.contentOrNull
        if (format != BackupDocument.FORMAT) {
            return BackupParseResult.Failure("Not a WFH Diary backup file.")
        }
        val version =
            root["version"]?.jsonPrimitive?.intOrNull
                ?: return BackupParseResult.Failure("Backup file has no version.")
        if (version > BackupDocument.CURRENT_VERSION) {
            return BackupParseResult.Failure(
                "Backup was made by a newer app (format version $version). Update the app first.",
            )
        }
        val document =
            try {
                json.decodeFromJsonElement(BackupDocument.serializer(), root)
            } catch (e: Exception) {
                return BackupParseResult.Failure("Backup file is damaged: ${e.message}")
            }
        return validate(document)
    }

    fun validate(document: BackupDocument): BackupParseResult {
        val s = document.settings
        val settingsTimes =
            listOf(
                s.notificationTime,
                s.missedNotificationApplyAt,
                s.defaultHomeStart,
                s.defaultHomeFinish,
                s.defaultOfficeStart,
                s.defaultOfficeFinish,
            )
        if (settingsTimes.any { TimeHelpers.parseTimeString(it) == null }) {
            return BackupParseResult.Failure("Backup settings contain an invalid time.")
        }

        val seen = HashSet<String>()
        for (day in document.days) {
            if (parseDate(day.date) == null) {
                return BackupParseResult.Failure("Invalid day date: ${day.date}")
            }
            if (!seen.add(day.date)) {
                return BackupParseResult.Failure("Day appears twice: ${day.date}")
            }
            if (!isValidOptionalTime(day.startTime) || !isValidOptionalTime(day.finishTime)) {
                return BackupParseResult.Failure("Invalid time on ${day.date}")
            }
            val breakHours = day.breakHours
            if (breakHours != null && (breakHours < 0 || breakHours.isNaN())) {
                return BackupParseResult.Failure("Invalid break hours on ${day.date}")
            }
        }

        val leave = ArrayList<BackupLeavePeriod>(document.leavePeriods.size)
        for (period in document.leavePeriods) {
            if (period.id.isBlank()) {
                return BackupParseResult.Failure("Leave period without an id.")
            }
            val start = parseDate(period.startDate)
            val end = parseDate(period.endDate)
            if (start == null || end == null) {
                return BackupParseResult.Failure("Invalid leave dates: ${period.startDate}..${period.endDate}")
            }
            leave +=
                if (end < start) {
                    period.copy(startDate = end.toString(), endDate = start.toString())
                } else {
                    period
                }
        }
        return BackupParseResult.Success(document.copy(leavePeriods = leave))
    }

    private fun parseDate(value: String): LocalDate? =
        try {
            LocalDate.parse(value)
        } catch (_: DateTimeParseException) {
            null
        }

    private fun isValidOptionalTime(value: String?): Boolean =
        value == null || TimeHelpers.parseTimeString(value) != null
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `.\gradlew.bat :app:testDebugUnitTest --no-daemon --tests "dev.wfhdiary.app.backup.BackupCodecTest"`
Expected: all 14 tests PASS.

- [ ] **Step 6: Commit**

```powershell
$env:SKIP_VERSION_BUMP = "1"
git add apps/android/app/src/main/java/dev/wfhdiary/app/backup apps/android/app/src/test/java/dev/wfhdiary/app/backup
git commit -m "feat(android): add backup document v1 model and codec"
```

---

### Task 2: Port server rules into `DiaryRules`

**Requirements:** DAY (upsert/replace semantics), LEAVE (mark/clear), FY-003 (summary), SET (update rules).

**Files:**
- Create: `app/src/main/java/dev/wfhdiary/app/data/store/DiaryRules.kt`
- Test: `app/src/test/java/dev/wfhdiary/app/data/store/DiaryRulesTest.kt`

Rules being ported, from `services/api/WfhDiary.Api/Services/DiaryService.cs` and `SummaryCalculator.cs`:
- **Upsert (partial):** a field is applied only when it is non-null. Times go through `parseTimeString` and become null when blank or invalid. Description is set as given. Location becomes null when blank.
- **Replace (full):** used by the edit sheet. All fields are taken from the request. Blank strings become null.
- **Leave mark:** every date in the FY that falls inside the period becomes `description = "LEAVE"` with all other fields null.
- **Leave clear:** for dates of the deleted period not covered by another period, entries whose description equals `LEAVE` (case-insensitive) are cleared.
- **Summary:** Home/Office matched case-insensitively. Days are counted only when hours > 0. Claimable = home × 0.67, rounded to 2 dp with HALF_EVEN (.NET banker's rounding). Logic check is total == home + office.
- **Settings update:**
  - Partial update; unparseable times are ignored.
  - Descriptions are trimmed, and blank becomes null.
  - Missed default must be one of Home/Office/Leave/Geolocation/None, otherwise None.
  - Away default must be one of Home/Office/Leave, otherwise Office.
  - A geofence is set only when all three fields are present, and clear wins.

- [ ] **Step 1: Write the failing tests**

`DiaryRulesTest.kt`:

```kotlin
package dev.wfhdiary.app.data.store

import dev.wfhdiary.app.backup.BackupDay
import dev.wfhdiary.app.data.remote.UpdateUserSettingsRequest
import dev.wfhdiary.app.data.remote.UpsertDayEntryRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DiaryRulesTest {
    private val full = BackupDay("2025-07-01", "08:00", "17:00", 1.0, "Work", "Home")

    @Test
    fun upsert_keeps_unset_fields() {
        val result = DiaryRules.applyUpsert(full, "2025-07-01", UpsertDayEntryRequest(location = "Office"))
        assertEquals(full.copy(location = "Office"), result)
    }

    @Test
    fun upsert_on_missing_day_creates_it() {
        val result = DiaryRules.applyUpsert(null, "2025-07-02", UpsertDayEntryRequest(startTime = "9:05"))
        assertEquals(BackupDay("2025-07-02", startTime = "09:05"), result)
    }

    @Test
    fun upsert_blank_time_and_location_clear_them() {
        val result = DiaryRules.applyUpsert(full, full.date, UpsertDayEntryRequest(startTime = "", location = " "))
        assertNull(result.startTime)
        assertNull(result.location)
        assertEquals("17:00", result.finishTime)
    }

    @Test
    fun upsert_invalid_time_becomes_null() {
        val result = DiaryRules.applyUpsert(full, full.date, UpsertDayEntryRequest(finishTime = "late"))
        assertNull(result.finishTime)
    }

    @Test
    fun replace_clears_fields_not_supplied() {
        val result = DiaryRules.replace(full.date, UpsertDayEntryRequest(location = "Office"))
        assertEquals(BackupDay(full.date, location = "Office"), result)
    }

    @Test
    fun replace_blank_description_becomes_null() {
        assertNull(DiaryRules.replace(full.date, UpsertDayEntryRequest(description = "  ")).description)
    }

    @Test
    fun isEmpty_detects_blank_days() {
        assertTrue(DiaryRules.isEmpty(BackupDay("2025-07-01", description = "")))
        assertFalse(DiaryRules.isEmpty(BackupDay("2025-07-01", breakHours = 0.0)))
    }

    @Test
    fun toDto_fills_day_name_and_total() {
        val dto = DiaryRules.toDto(full)
        assertEquals("Tuesday", dto.dayName)
        assertEquals(8.0, dto.totalHours, 0.0001)
    }

    @Test
    fun leave_dates_are_limited_to_fy() {
        val dates = DiaryRules.leaveDatesInFy(2025, LocalDate.parse("2026-06-29"), LocalDate.parse("2026-07-02"))
        assertEquals(listOf(LocalDate.parse("2026-06-29"), LocalDate.parse("2026-06-30")), dates)
    }

    @Test
    fun leave_day_has_only_leave_description() {
        assertEquals(BackupDay("2025-12-24", description = "LEAVE"), DiaryRules.leaveDay("2025-12-24"))
    }

    @Test
    fun dates_to_clear_skip_other_periods() {
        val removed = LocalDate.parse("2025-12-22")..LocalDate.parse("2025-12-26")
        val other = LocalDate.parse("2025-12-25")..LocalDate.parse("2025-12-31")
        val dates = DiaryRules.datesToClearOnLeaveDelete(removed, listOf(other))
        assertEquals(
            listOf("2025-12-22", "2025-12-23", "2025-12-24").map(LocalDate::parse),
            dates,
        )
    }

    @Test
    fun summary_matches_server_rules() {
        val days =
            listOf(
                BackupDay("2025-07-01", "08:00", "17:00", 1.0, null, "Home"),
                BackupDay("2025-07-02", "08:00", "16:00", 0.5, null, "office"),
                BackupDay("2025-07-03", null, null, null, "LEAVE", "Home"),
            )
        val summary = DiaryRules.summarize(days)
        assertEquals(15.5, summary.totalHours, 0.0001)
        assertEquals(8.0, summary.homeHours, 0.0001)
        assertEquals(7.5, summary.officeHours, 0.0001)
        assertEquals(5.36, summary.claimableWfhAmount, 0.0001)
        assertEquals(1, summary.daysAtHome)
        assertEquals(1, summary.daysInOffice)
        assertTrue(summary.logicCheckPassed)
    }

    @Test
    fun summary_logic_check_fails_for_untagged_hours() {
        val summary = DiaryRules.summarize(listOf(BackupDay("2025-07-01", "08:00", "17:00", 1.0)))
        assertFalse(summary.logicCheckPassed)
    }

    @Test
    fun default_settings_match_server_defaults() {
        val s = DiaryRules.defaultSettings()
        assertEquals("YOUR NAME HERE", s.displayName)
        assertEquals("08:00", s.notificationTime)
        assertEquals(31, s.notificationWorkingDaysMask)
        assertEquals("17:00", s.defaultOfficeFinish)
        assertEquals(1.0, s.defaultHomeBreakHours, 0.0)
    }

    @Test
    fun settings_update_ignores_invalid_time_and_normalises_valid() {
        val s = DiaryRules.applySettingsUpdate(
            DiaryRules.defaultSettings(),
            UpdateUserSettingsRequest(notificationTime = "bad", defaultHomeStart = "7:30"),
        )
        assertEquals("08:00", s.notificationTime)
        assertEquals("07:30", s.defaultHomeStart)
    }

    @Test
    fun settings_update_trims_or_clears_descriptions() {
        var s = DiaryRules.applySettingsUpdate(
            DiaryRules.defaultSettings(),
            UpdateUserSettingsRequest(defaultHomeDescription = "  WFH  "),
        )
        assertEquals("WFH", s.defaultHomeDescription)
        s = DiaryRules.applySettingsUpdate(s, UpdateUserSettingsRequest(defaultHomeDescription = " "))
        assertNull(s.defaultHomeDescription)
    }

    @Test
    fun settings_update_validates_enums() {
        val s = DiaryRules.applySettingsUpdate(
            DiaryRules.defaultSettings(),
            UpdateUserSettingsRequest(missedNotificationDefault = "Moon", missedNotificationAwayDefault = "Geolocation"),
        )
        assertEquals("None", s.missedNotificationDefault)
        assertEquals("Office", s.missedNotificationAwayDefault)
    }

    @Test
    fun settings_geofence_requires_all_fields_and_clear_wins() {
        val base = DiaryRules.defaultSettings()
        val partial = DiaryRules.applySettingsUpdate(base, UpdateUserSettingsRequest(homeGeofenceLatitude = 1.0))
        assertNull(partial.homeGeofenceLatitude)
        val set = DiaryRules.applySettingsUpdate(
            base,
            UpdateUserSettingsRequest(homeGeofenceLatitude = 1.0, homeGeofenceLongitude = 2.0, homeGeofenceRadiusMeters = 150),
        )
        assertEquals(150, set.homeGeofenceRadiusMeters)
        val cleared = DiaryRules.applySettingsUpdate(
            set,
            UpdateUserSettingsRequest(
                clearHomeGeofence = true,
                homeGeofenceLatitude = 5.0,
                homeGeofenceLongitude = 6.0,
                homeGeofenceRadiusMeters = 100,
            ),
        )
        assertNull(cleared.homeGeofenceLatitude)
        assertNull(cleared.homeGeofenceRadiusMeters)
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `.\gradlew.bat :app:testDebugUnitTest --no-daemon --tests "dev.wfhdiary.app.data.store.DiaryRulesTest"`
Expected: compilation failure, because `DiaryRules` is unresolved.

- [ ] **Step 3: Implement `DiaryRules.kt`**

```kotlin
package dev.wfhdiary.app.data.store

import dev.wfhdiary.app.backup.BackupDay
import dev.wfhdiary.app.data.remote.DayEntryDto
import dev.wfhdiary.app.data.remote.DiarySummaryDto
import dev.wfhdiary.app.data.remote.UpdateUserSettingsRequest
import dev.wfhdiary.app.data.remote.UpsertDayEntryRequest
import dev.wfhdiary.app.data.remote.UserSettingsDto
import dev.wfhdiary.app.util.FinancialYearHelper
import dev.wfhdiary.app.util.TimeHelpers
import dev.wfhdiary.app.util.WorkingDaysMask
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

object DiaryRules {
    const val LEAVE = "LEAVE"
    private val WFH_RATE = BigDecimal("0.67")
    private val MISSED_DEFAULTS = setOf("Home", "Office", "Leave", "Geolocation", "None")
    private val AWAY_DEFAULTS = setOf("Home", "Office", "Leave")

    fun normalizeTime(value: String?): String? =
        TimeHelpers.parseTimeString(value)?.let(TimeHelpers::minutesToTimeString)

    fun applyUpsert(existing: BackupDay?, date: String, request: UpsertDayEntryRequest): BackupDay {
        var day = existing ?: BackupDay(date)
        if (request.startTime != null) day = day.copy(startTime = normalizeTime(request.startTime))
        if (request.finishTime != null) day = day.copy(finishTime = normalizeTime(request.finishTime))
        if (request.breakHours != null) day = day.copy(breakHours = request.breakHours)
        if (request.description != null) day = day.copy(description = request.description)
        if (request.location != null) day = day.copy(location = request.location.takeIf { it.isNotBlank() })
        return day
    }

    fun replace(date: String, request: UpsertDayEntryRequest): BackupDay =
        BackupDay(
            date = date,
            startTime = normalizeTime(request.startTime),
            finishTime = normalizeTime(request.finishTime),
            breakHours = request.breakHours,
            description = request.description?.takeIf { it.isNotBlank() },
            location = request.location?.takeIf { it.isNotBlank() },
        )

    fun isEmpty(day: BackupDay): Boolean =
        day.startTime == null &&
            day.finishTime == null &&
            day.breakHours == null &&
            day.description.isNullOrBlank() &&
            day.location.isNullOrBlank()

    fun totalHours(day: BackupDay): BigDecimal =
        TimeHelpers.computeTotalHours(
            TimeHelpers.parseTimeString(day.startTime),
            TimeHelpers.parseTimeString(day.finishTime),
            day.breakHours?.let { BigDecimal.valueOf(it) },
        )

    fun dayName(date: String): String =
        LocalDate.parse(date).dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)

    fun toDto(day: BackupDay): DayEntryDto =
        DayEntryDto(
            date = day.date,
            dayName = dayName(day.date),
            startTime = day.startTime,
            finishTime = day.finishTime,
            breakHours = day.breakHours,
            totalHours = totalHours(day).toDouble(),
            description = day.description,
            location = day.location,
        )

    fun leaveDatesInFy(fyYear: Int, start: LocalDate, end: LocalDate): List<LocalDate> {
        val (fyStart, fyEnd) = FinancialYearHelper.fyRange(fyYear)
        val from = maxOf(start, fyStart)
        val to = minOf(end, fyEnd)
        if (from > to) return emptyList()
        return FinancialYearHelper.eachDay(from, to).toList()
    }

    fun leaveDay(date: String): BackupDay = BackupDay(date = date, description = LEAVE)

    fun datesToClearOnLeaveDelete(
        removed: ClosedRange<LocalDate>,
        others: List<ClosedRange<LocalDate>>,
    ): List<LocalDate> =
        FinancialYearHelper.eachDay(removed.start, removed.endInclusive)
            .filter { date -> others.none { date in it } }
            .toList()

    fun summarize(days: Collection<BackupDay>): DiarySummaryDto {
        var total = BigDecimal.ZERO
        var home = BigDecimal.ZERO
        var office = BigDecimal.ZERO
        var daysAtHome = 0
        var daysInOffice = 0
        for (day in days) {
            val hours = totalHours(day)
            total += hours
            when {
                day.location.equals("Home", ignoreCase = true) -> {
                    home += hours
                    if (hours.signum() > 0) daysAtHome++
                }
                day.location.equals("Office", ignoreCase = true) -> {
                    office += hours
                    if (hours.signum() > 0) daysInOffice++
                }
            }
        }
        val claimable = home.multiply(WFH_RATE).setScale(2, RoundingMode.HALF_EVEN)
        return DiarySummaryDto(
            totalHours = total.toDouble(),
            homeHours = home.toDouble(),
            officeHours = office.toDouble(),
            claimableWfhAmount = claimable.toDouble(),
            daysAtHome = daysAtHome,
            daysInOffice = daysInOffice,
            logicCheckPassed = total.compareTo(home + office) == 0,
        )
    }

    fun defaultSettings(): UserSettingsDto =
        UserSettingsDto(
            displayName = "YOUR NAME HERE",
            notificationTime = "08:00",
            notificationWorkingDaysMask = WorkingDaysMask.DEFAULT,
            missedNotificationDefault = "None",
            missedNotificationApplyAt = "12:00",
            missedNotificationAwayDefault = "Office",
            defaultHomeStart = "08:00",
            defaultHomeFinish = "17:00",
            defaultHomeBreakHours = 1.0,
            defaultOfficeStart = "08:00",
            defaultOfficeFinish = "17:00",
            defaultOfficeBreakHours = 1.0,
        )

    fun applySettingsUpdate(current: UserSettingsDto, r: UpdateUserSettingsRequest): UserSettingsDto {
        var s = current
        r.displayName?.let { s = s.copy(displayName = it) }
        normalizeTime(r.notificationTime)?.let { s = s.copy(notificationTime = it) }
        normalizeTime(r.defaultHomeStart)?.let { s = s.copy(defaultHomeStart = it) }
        normalizeTime(r.defaultHomeFinish)?.let { s = s.copy(defaultHomeFinish = it) }
        r.defaultHomeBreakHours?.let { s = s.copy(defaultHomeBreakHours = it) }
        r.defaultHomeDescription?.let { s = s.copy(defaultHomeDescription = it.trim().ifEmpty { null }) }
        normalizeTime(r.defaultOfficeStart)?.let { s = s.copy(defaultOfficeStart = it) }
        normalizeTime(r.defaultOfficeFinish)?.let { s = s.copy(defaultOfficeFinish = it) }
        r.defaultOfficeBreakHours?.let { s = s.copy(defaultOfficeBreakHours = it) }
        r.defaultOfficeDescription?.let { s = s.copy(defaultOfficeDescription = it.trim().ifEmpty { null }) }
        r.notificationWorkingDaysMask?.let { s = s.copy(notificationWorkingDaysMask = it) }
        r.missedNotificationDefault?.let {
            val d = it.trim()
            s = s.copy(missedNotificationDefault = if (d in MISSED_DEFAULTS) d else "None")
        }
        r.missedNotificationAwayDefault?.let {
            val away = it.trim()
            s = s.copy(missedNotificationAwayDefault = if (away in AWAY_DEFAULTS) away else "Office")
        }
        r.geolocationSkipNotificationAtHome?.let { s = s.copy(geolocationSkipNotificationAtHome = it) }
        r.geolocationSkipNotificationAtOffice?.let { s = s.copy(geolocationSkipNotificationAtOffice = it) }
        normalizeTime(r.missedNotificationApplyAt)?.let { s = s.copy(missedNotificationApplyAt = it) }

        if (r.clearHomeGeofence == true) {
            s = s.copy(homeGeofenceLatitude = null, homeGeofenceLongitude = null, homeGeofenceRadiusMeters = null)
        } else if (r.homeGeofenceLatitude != null && r.homeGeofenceLongitude != null && r.homeGeofenceRadiusMeters != null) {
            s = s.copy(
                homeGeofenceLatitude = r.homeGeofenceLatitude,
                homeGeofenceLongitude = r.homeGeofenceLongitude,
                homeGeofenceRadiusMeters = r.homeGeofenceRadiusMeters,
            )
        }
        if (r.clearOfficeGeofence == true) {
            s = s.copy(officeGeofenceLatitude = null, officeGeofenceLongitude = null, officeGeofenceRadiusMeters = null)
        } else if (r.officeGeofenceLatitude != null && r.officeGeofenceLongitude != null && r.officeGeofenceRadiusMeters != null) {
            s = s.copy(
                officeGeofenceLatitude = r.officeGeofenceLatitude,
                officeGeofenceLongitude = r.officeGeofenceLongitude,
                officeGeofenceRadiusMeters = r.officeGeofenceRadiusMeters,
            )
        }
        return s
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `.\gradlew.bat :app:testDebugUnitTest --no-daemon --tests "dev.wfhdiary.app.data.store.DiaryRulesTest"`
Expected: all 18 tests PASS.

- [ ] **Step 5: Commit**

```powershell
$env:SKIP_VERSION_BUMP = "1"
git add apps/android/app/src/main/java/dev/wfhdiary/app/data/store apps/android/app/src/test/java/dev/wfhdiary/app/data/store
git commit -m "feat(android): port diary, leave, summary and settings rules on-device"
```

---

### Task 3: Room v2 schema and test dependencies

**Requirements:** design "Data model". This task removes the remote-response cache, which made `MonthGridViewModel` depend on the old entity shape.

**Files:**
- Modify: `apps/android/gradle/libs.versions.toml`
- Modify: `apps/android/app/build.gradle.kts`
- Modify: `app/src/main/java/dev/wfhdiary/app/data/local/DayEntryEntity.kt`
- Modify: `app/src/main/java/dev/wfhdiary/app/data/local/DayEntryDao.kt`
- Create: `app/src/main/java/dev/wfhdiary/app/data/local/LeavePeriodEntity.kt`
- Create: `app/src/main/java/dev/wfhdiary/app/data/local/LeavePeriodDao.kt`
- Create: `app/src/main/java/dev/wfhdiary/app/data/local/SettingsEntity.kt`
- Create: `app/src/main/java/dev/wfhdiary/app/data/local/SettingsDao.kt`
- Modify: `app/src/main/java/dev/wfhdiary/app/data/local/WfhDiaryDatabase.kt`
- Modify: `app/src/main/java/dev/wfhdiary/app/ui/month/MonthGridViewModel.kt`
- Test: `app/src/test/java/dev/wfhdiary/app/data/local/WfhDiaryDatabaseTest.kt`

- [ ] **Step 1: Add dependencies**

In `libs.versions.toml`, under `[versions]` add:

```toml
robolectric = "4.14.1"
androidxTestCore = "1.6.1"
documentfile = "1.0.1"
```

Under `[libraries]` add:

```toml
robolectric = { group = "org.robolectric", name = "robolectric", version.ref = "robolectric" }
androidx-test-core = { group = "androidx.test", name = "core-ktx", version.ref = "androidxTestCore" }
androidx-documentfile = { group = "androidx.documentfile", name = "documentfile", version.ref = "documentfile" }
ktor-client-mock = { group = "io.ktor", name = "ktor-client-mock", version.ref = "ktor" }
```

In `app/build.gradle.kts`, inside `android { … }` after `buildFeatures { … }` add:

```kotlin
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
```

In `dependencies { … }` add, keeping the groups in the existing order:

```kotlin
    implementation(libs.androidx.documentfile)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.ktor.client.mock)
```

- [ ] **Step 2: Replace the entity and DAO, add the new tables**

`DayEntryEntity.kt` (full replacement):

```kotlin
package dev.wfhdiary.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "day_entries")
data class DayEntryEntity(
    @PrimaryKey val date: String,
    val startTime: String?,
    val finishTime: String?,
    val breakHours: Double?,
    val description: String?,
    val location: String?,
    val updatedAtMillis: Long,
)
```

`DayEntryDao.kt` (full replacement):

```kotlin
package dev.wfhdiary.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DayEntryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: DayEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<DayEntryEntity>)

    @Query("SELECT * FROM day_entries WHERE date = :date")
    suspend fun get(date: String): DayEntryEntity?

    @Query(
        """
        SELECT * FROM day_entries
        WHERE date >= :fromDate AND date <= :toDate
        ORDER BY date
        """,
    )
    suspend fun getBetween(fromDate: String, toDate: String): List<DayEntryEntity>

    @Query("SELECT * FROM day_entries ORDER BY date")
    suspend fun getAll(): List<DayEntryEntity>

    @Query("DELETE FROM day_entries WHERE date = :date")
    suspend fun delete(date: String)

    @Query("DELETE FROM day_entries")
    suspend fun deleteAll()
}
```

`LeavePeriodEntity.kt`:

```kotlin
package dev.wfhdiary.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "leave_periods")
data class LeavePeriodEntity(
    @PrimaryKey val id: String,
    val startDate: String,
    val endDate: String,
    val note: String?,
    val createdAtMillis: Long,
)
```

`LeavePeriodDao.kt`:

```kotlin
package dev.wfhdiary.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LeavePeriodDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(period: LeavePeriodEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(periods: List<LeavePeriodEntity>)

    @Query("SELECT * FROM leave_periods WHERE id = :id")
    suspend fun get(id: String): LeavePeriodEntity?

    @Query("SELECT * FROM leave_periods ORDER BY startDate, endDate")
    suspend fun getAll(): List<LeavePeriodEntity>

    @Query("DELETE FROM leave_periods WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM leave_periods")
    suspend fun deleteAll()
}
```

`SettingsEntity.kt`. Settings are stored as a JSON blob of `UserSettingsDto` in a single row, so new settings fields don't need a Room migration.

```kotlin
package dev.wfhdiary.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = SINGLE_ROW_ID,
    val settingsJson: String,
    val legacyJson: String?,
) {
    companion object {
        const val SINGLE_ROW_ID = 1
    }
}
```

`SettingsDao.kt`:

```kotlin
package dev.wfhdiary.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE id = 1")
    suspend fun get(): SettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SettingsEntity)

    @Query("DELETE FROM settings")
    suspend fun deleteAll()
}
```

`WfhDiaryDatabase.kt` (full replacement):

```kotlin
package dev.wfhdiary.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [DayEntryEntity::class, LeavePeriodEntity::class, SettingsEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class WfhDiaryDatabase : RoomDatabase() {
    abstract fun dayEntryDao(): DayEntryDao

    abstract fun leavePeriodDao(): LeavePeriodDao

    abstract fun settingsDao(): SettingsDao

    companion object {
        @Volatile
        private var instance: WfhDiaryDatabase? = null

        fun getInstance(context: Context): WfhDiaryDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    WfhDiaryDatabase::class.java,
                    "wfh_diary.db",
                )
                    // v1 was only a cache of server responses; nothing in it needs keeping.
                    .fallbackToDestructiveMigrationFrom(1)
                    .build()
                    .also { instance = it }
            }
    }
}
```

- [ ] **Step 3: Remove the response cache from `MonthGridViewModel`**

In `ui/month/MonthGridViewModel.kt`:

1. Delete `import dev.wfhdiary.app.data.local.DayEntryEntity`.
2. Delete `private val dao = app.database.dayEntryDao()`.
3. In `init`, delete `mergeRoomIntoCache(dao.getAll())`. Keep `applyMonth(now)` and the leave-period load.
4. In `refreshFromRemote`, delete `cacheDays(days, fyYear)`.
5. In `refreshDayInState`, delete `cacheDays(listOf(saved), fyYear)`.
6. In `applyMonth`, delete the whole trailing `viewModelScope.launch { … loadCached … }` block. Task 6 replaces it.
7. Delete the private functions `mergeRoomIntoCache`, `cacheDays`, `loadCached` and `DayEntryEntity.toDto()`.

- [ ] **Step 4: Write the DAO test**

`WfhDiaryDatabaseTest.kt`:

```kotlin
package dev.wfhdiary.app.data.local

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WfhDiaryDatabaseTest {
    private lateinit var db: WfhDiaryDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            WfhDiaryDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private fun day(date: String) = DayEntryEntity(date, "08:00", "17:00", 1.0, null, "Home", 0L)

    @Test
    fun days_between_and_delete() = runBlocking {
        val dao = db.dayEntryDao()
        dao.upsertAll(listOf(day("2025-07-01"), day("2025-07-02"), day("2025-08-01")))
        assertEquals(2, dao.getBetween("2025-07-01", "2025-07-31").size)
        dao.delete("2025-07-01")
        assertNull(dao.get("2025-07-01"))
        assertEquals(2, dao.getAll().size)
    }

    @Test
    fun leave_ordered_by_start() = runBlocking {
        val dao = db.leavePeriodDao()
        dao.insert(LeavePeriodEntity("b", "2025-12-22", "2025-12-30", null, 0L))
        dao.insert(LeavePeriodEntity("a", "2025-09-01", "2025-09-02", "x", 0L))
        assertEquals(listOf("a", "b"), dao.getAll().map { it.id })
    }

    @Test
    fun settings_single_row_upsert() = runBlocking {
        val dao = db.settingsDao()
        assertNull(dao.get())
        dao.upsert(SettingsEntity(settingsJson = "{}", legacyJson = null))
        dao.upsert(SettingsEntity(settingsJson = "{\"a\":1}", legacyJson = "{}"))
        assertEquals("{\"a\":1}", dao.get()!!.settingsJson)
    }
}
```

- [ ] **Step 5: Run all unit tests**

Run: `.\gradlew.bat :app:testDebugUnitTest --no-daemon`
Expected: BUILD SUCCESSFUL. The new DAO tests pass and the existing tests still pass. The first Robolectric run downloads the android-all jar, so it can take several minutes.

- [ ] **Step 6: Commit**

```powershell
$env:SKIP_VERSION_BUMP = "1"
git add apps/android/gradle/libs.versions.toml apps/android/app/build.gradle.kts apps/android/app/src/main/java/dev/wfhdiary/app/data/local apps/android/app/src/main/java/dev/wfhdiary/app/ui/month/MonthGridViewModel.kt apps/android/app/src/test/java/dev/wfhdiary/app/data/local
git commit -m "feat(android): room v2 schema for days, leave and settings"
```

---

### Task 4: On-device XLSX writer

**Requirements:** XLSX-003..009 (layout identical to `services/api/WfhDiary.Api/Services/XlsxExportService.cs`).

**Files:**
- Create: `app/src/main/java/dev/wfhdiary/app/export/XlsxWriter.kt`
- Test: `app/src/test/java/dev/wfhdiary/app/export/XlsxWriterTest.kt`

Layout recap:
- Sheet "Diary".
- A1: "{yyyy}/{yy} Working from Home Diary", bold 16pt.
- A2: "Name:" bold; B2: the display name.
- Row 5 headers, dark fill #12262F, white bold text, centred.
- One row per FY day from row 6:
  - A: day name. B: date serial with format `ddd d mmm yyyy`.
  - C–E: input fill #DEEAF6. C and D are times as fractions of a day with format `h:mm AM/PM`; E is break hours with format `0.00`.
  - F: formula `(D-C)*24-E` when both times exist; `0` when description == "LEAVE" (case-sensitive, as on the server); format `0.00`.
  - G: description. H: location, styled Home green / Office blue / LEAVE grey.
- Six summary rows at lastRow+2: bold label in A, bold formula in B.
- Column widths, freeze below row 5, autofilter A5:H{last}.

- [ ] **Step 1: Write the failing tests**

`XlsxWriterTest.kt`:

```kotlin
package dev.wfhdiary.app.export

import dev.wfhdiary.app.backup.BackupDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class XlsxWriterTest {
    private val days =
        listOf(
            BackupDay("2025-07-01", "08:00", "17:00", 1.0, "Work & stuff", "Home"),
            BackupDay("2025-07-02", "08:30", "16:30", 0.5, null, "Office"),
            BackupDay("2025-07-03", description = "LEAVE"),
        )

    private fun unzip(bytes: ByteArray): Map<String, String> {
        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        return entries
    }

    private val sheet by lazy { unzip(XlsxWriter.write(2025, "Jane <Doe>", days))["xl/worksheets/sheet1.xml"]!! }

    private fun cell(ref: String): String {
        val match = Regex("<c r=\"$ref\"[^>]*?(/>|>.*?</c>)").find(sheet)
        return match?.value ?: error("cell $ref not found")
    }

    @Test
    fun package_has_required_parts() {
        val parts = unzip(XlsxWriter.write(2025, "x", days)).keys
        assertTrue(
            parts.containsAll(
                listOf(
                    "[Content_Types].xml",
                    "_rels/.rels",
                    "xl/workbook.xml",
                    "xl/_rels/workbook.xml.rels",
                    "xl/styles.xml",
                    "xl/worksheets/sheet1.xml",
                ),
            ),
        )
    }

    @Test
    fun workbook_names_sheet_diary() {
        val workbook = unzip(XlsxWriter.write(2025, "x", days))["xl/workbook.xml"]!!
        assertTrue(workbook.contains("<sheet name=\"Diary\" sheetId=\"1\" r:id=\"rId1\"/>"))
    }

    @Test
    fun title_and_name_are_written_and_escaped() {
        assertTrue(cell("A1").contains("2025/26 Working from Home Diary"))
        assertTrue(cell("A1").contains("s=\"1\""))
        assertTrue(cell("A2").contains("Name:"))
        assertTrue(cell("B2").contains("Jane &lt;Doe&gt;"))
    }

    @Test
    fun headers_are_on_row_five() {
        val expected = listOf("Day", "Date", "Started", "Finished", "Break (hrs)", "Hours", "Description", "Home/Office")
        "ABCDEFGH".forEachIndexed { i, col ->
            val c = cell("${col}5")
            assertTrue(c.contains(expected[i]))
            assertTrue(c.contains("s=\"3\""))
        }
    }

    @Test
    fun first_day_row_has_values_formula_and_styles() {
        assertTrue(cell("A6").contains("Tuesday"))
        assertTrue(cell("B6").contains("<v>45839</v>"))
        assertTrue(cell("C6").contains("<v>0.3333333333333333</v>"))
        assertTrue(cell("E6").contains("<v>1.0</v>"))
        assertTrue(cell("F6").contains("<f>(D6-C6)*24-E6</f>"))
        assertTrue(cell("G6").contains("Work &amp; stuff"))
        assertTrue(cell("H6").contains("s=\"8\""))
        assertTrue(cell("H7").contains("s=\"9\""))
    }

    @Test
    fun leave_day_has_zero_hours_and_grey_location() {
        assertTrue(cell("F8").contains("<v>0</v>"))
        assertEquals("<c r=\"H8\" s=\"10\"/>", cell("H8"))
    }

    @Test
    fun empty_day_keeps_input_styling() {
        assertEquals("<c r=\"C9\" s=\"5\"/>", cell("C9"))
        assertEquals("<c r=\"F9\" s=\"7\"/>", cell("F9"))
    }

    @Test
    fun one_row_per_fy_day_and_summary_formulas() {
        // FY2025 has 365 days: rows 6..370.
        assertTrue(cell("A370").contains("Tuesday"))
        assertTrue(cell("B372").contains("<f>SUM(F6:F370)</f>"))
        assertTrue(cell("B373").contains("<f>SUMIF(H6:H370,\"Home\",F6:F370)</f>"))
        assertTrue(cell("A374").contains("Claimable WFH (Home hours × \$0.67)"))
        assertTrue(cell("B374").contains("<f>B373*0.67</f>"))
        assertTrue(cell("B374").contains("s=\"12\""))
        assertTrue(cell("B376").contains("<f>COUNTIF(H6:H370,\"Home\")</f>"))
        assertTrue(cell("B377").contains("s=\"13\""))
    }

    @Test
    fun layout_freeze_widths_and_autofilter() {
        assertTrue(sheet.contains("<pane ySplit=\"5\" topLeftCell=\"A6\" activePane=\"bottomLeft\" state=\"frozen\"/>"))
        assertTrue(sheet.contains("<col min=\"7\" max=\"7\" width=\"36\" customWidth=\"1\"/>"))
        assertTrue(sheet.contains("<autoFilter ref=\"A5:H370\"/>"))
    }

    @Test
    fun filename_matches_previous_server_name() {
        assertEquals("WFH-Diary-2025-2026.xlsx", XlsxWriter.filename(2025))
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `.\gradlew.bat :app:testDebugUnitTest --no-daemon --tests "dev.wfhdiary.app.export.XlsxWriterTest"`
Expected: compilation failure, because `XlsxWriter` is unresolved.

- [ ] **Step 3: Implement `XlsxWriter.kt`**

```kotlin
package dev.wfhdiary.app.export

import dev.wfhdiary.app.backup.BackupDay
import dev.wfhdiary.app.data.store.DiaryRules
import dev.wfhdiary.app.util.FinancialYearHelper
import dev.wfhdiary.app.util.TimeHelpers
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object XlsxWriter {
    private const val HEADER_ROW = 5
    private const val FIRST_DATA_ROW = 6
    private val EXCEL_EPOCH: LocalDate = LocalDate.of(1899, 12, 30)
    private val COLUMN_WIDTHS = listOf(12, 18, 11, 11, 11, 9, 36, 12)
    private val HEADERS = listOf("Day", "Date", "Started", "Finished", "Break (hrs)", "Hours", "Description", "Home/Office")

    private const val S_TITLE = 1
    private const val S_BOLD = 2
    private const val S_HEADER = 3
    private const val S_DATE = 4
    private const val S_INPUT_TIME = 5
    private const val S_INPUT_BREAK = 6
    private const val S_HOURS = 7
    private const val S_HOME = 8
    private const val S_OFFICE = 9
    private const val S_LEAVE = 10
    private const val S_SUM_DECIMAL = 11
    private const val S_SUM_CURRENCY = 12
    private const val S_SUM_INTEGER = 13

    fun filename(fyYear: Int): String = "WFH-Diary-$fyYear-${fyYear + 1}.xlsx"

    fun write(fyYear: Int, displayName: String, days: List<BackupDay>): ByteArray {
        val (fyStart, fyEnd) = FinancialYearHelper.fyRange(fyYear)
        val lastDataRow = FIRST_DATA_ROW + ChronoUnit.DAYS.between(fyStart, fyEnd).toInt()
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.put("[Content_Types].xml", CONTENT_TYPES)
            zip.put("_rels/.rels", ROOT_RELS)
            zip.put("xl/workbook.xml", workbookXml(lastDataRow))
            zip.put("xl/_rels/workbook.xml.rels", WORKBOOK_RELS)
            zip.put("xl/styles.xml", STYLES)
            zip.put("xl/worksheets/sheet1.xml", sheetXml(fyYear, displayName, days, fyStart, fyEnd, lastDataRow))
        }
        return out.toByteArray()
    }

    private fun ZipOutputStream.put(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun sheetXml(
        fyYear: Int,
        displayName: String,
        days: List<BackupDay>,
        fyStart: LocalDate,
        fyEnd: LocalDate,
        lastDataRow: Int,
    ): String {
        val byDate = days.associateBy { it.date }
        val rows = StringBuilder()
        val fyLabel = "$fyYear/${"%02d".format((fyYear + 1) % 100)}"

        rows.row(1) { text("A", "$fyLabel Working from Home Diary", S_TITLE) }
        rows.row(2) {
            text("A", "Name:", S_BOLD)
            text("B", displayName, 0)
        }
        rows.row(HEADER_ROW) {
            HEADERS.forEachIndexed { i, header -> text(column(i), header, S_HEADER) }
        }

        var r = FIRST_DATA_ROW
        for (date in FinancialYearHelper.eachDay(fyStart, fyEnd)) {
            val entry = byDate[date.toString()]
            rows.row(r) { dayRow(this, r, date, entry) }
            r++
        }

        val summaryRow = lastDataRow + 2
        val hours = "F$FIRST_DATA_ROW:F$lastDataRow"
        val locations = "H$FIRST_DATA_ROW:H$lastDataRow"
        val summary =
            listOf(
                Triple("Total hours worked", "SUM($hours)", S_SUM_DECIMAL),
                Triple("Home hours", "SUMIF($locations,\"Home\",$hours)", S_SUM_DECIMAL),
                Triple("Claimable WFH (Home hours × \$0.67)", "B${summaryRow + 1}*0.67", S_SUM_CURRENCY),
                Triple("Office hours", "SUMIF($locations,\"Office\",$hours)", S_SUM_DECIMAL),
                Triple("Days at home", "COUNTIF($locations,\"Home\")", S_SUM_INTEGER),
                Triple("Days in office", "COUNTIF($locations,\"Office\")", S_SUM_INTEGER),
            )
        summary.forEachIndexed { i, (label, formula, style) ->
            rows.row(summaryRow + i) {
                text("A", label, S_BOLD)
                formula("B", formula, style)
            }
        }

        val cols =
            COLUMN_WIDTHS.mapIndexed { i, w ->
                "<col min=\"${i + 1}\" max=\"${i + 1}\" width=\"$w\" customWidth=\"1\"/>"
            }.joinToString("")

        return XML_HEADER +
            "<worksheet xmlns=\"$MAIN_NS\">" +
            "<sheetViews><sheetView workbookViewId=\"0\">" +
            "<pane ySplit=\"5\" topLeftCell=\"A6\" activePane=\"bottomLeft\" state=\"frozen\"/>" +
            "</sheetView></sheetViews>" +
            "<sheetFormatPr defaultRowHeight=\"15\"/>" +
            "<cols>$cols</cols>" +
            "<sheetData>$rows</sheetData>" +
            "<autoFilter ref=\"A$HEADER_ROW:H$lastDataRow\"/>" +
            "</worksheet>"
    }

    private fun dayRow(row: RowBuilder, r: Int, date: LocalDate, entry: BackupDay?) {
        row.text("A", DiaryRules.dayName(date.toString()), 0)
        row.number("B", ChronoUnit.DAYS.between(EXCEL_EPOCH, date).toString(), S_DATE)

        val start = TimeHelpers.parseTimeString(entry?.startTime)
        val finish = TimeHelpers.parseTimeString(entry?.finishTime)
        row.numberOrEmpty("C", start?.let { (it / 1440.0).toString() }, S_INPUT_TIME)
        row.numberOrEmpty("D", finish?.let { (it / 1440.0).toString() }, S_INPUT_TIME)
        row.numberOrEmpty("E", entry?.breakHours?.let { BigDecimal.valueOf(it).toString() }, S_INPUT_BREAK)

        when {
            start != null && finish != null -> row.formula("F", "(D$r-C$r)*24-E$r", S_HOURS)
            entry?.description == DiaryRules.LEAVE -> row.number("F", "0", S_HOURS)
            else -> row.empty("F", S_HOURS)
        }

        row.text("G", entry?.description.orEmpty(), 0)
        val locationStyle =
            when {
                entry?.location.equals("Home", ignoreCase = true) -> S_HOME
                entry?.location.equals("Office", ignoreCase = true) -> S_OFFICE
                entry?.description.equals(DiaryRules.LEAVE, ignoreCase = true) -> S_LEAVE
                else -> 0
            }
        row.text("H", entry?.location.orEmpty(), locationStyle)
    }

    private fun column(index: Int): String = ('A' + index).toString()

    private inline fun StringBuilder.row(r: Int, build: RowBuilder.() -> Unit) {
        val builder = RowBuilder(r)
        builder.build()
        append("<row r=\"").append(r).append("\">").append(builder.cells).append("</row>")
    }

    private class RowBuilder(private val r: Int) {
        val cells = StringBuilder()

        private fun open(col: String, style: Int, type: String? = null): StringBuilder {
            cells.append("<c r=\"").append(col).append(r).append('"')
            if (style != 0) cells.append(" s=\"").append(style).append('"')
            if (type != null) cells.append(" t=\"").append(type).append('"')
            return cells
        }

        fun text(col: String, value: String, style: Int) {
            if (value.isEmpty()) {
                if (style != 0) empty(col, style)
                return
            }
            open(col, style, "inlineStr")
                .append("><is><t xml:space=\"preserve\">").append(escape(value)).append("</t></is></c>")
        }

        fun number(col: String, value: String, style: Int) {
            open(col, style).append("><v>").append(value).append("</v></c>")
        }

        fun numberOrEmpty(col: String, value: String?, style: Int) {
            if (value == null) empty(col, style) else number(col, value, style)
        }

        fun formula(col: String, formula: String, style: Int) {
            open(col, style).append("><f>").append(escape(formula)).append("</f></c>")
        }

        fun empty(col: String, style: Int) {
            open(col, style).append("/>")
        }
    }

    private fun escape(value: String): String =
        buildString(value.length) {
            for (c in value) {
                when (c) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    else -> if (c >= ' ' || c == '\n' || c == '\t') append(c)
                }
            }
        }

    private const val XML_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
    private const val MAIN_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

    private const val CONTENT_TYPES =
        XML_HEADER +
            "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
            "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
            "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
            "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
            "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
            "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>" +
            "</Types>"

    private const val ROOT_RELS =
        XML_HEADER +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"$REL_NS/officeDocument\" Target=\"xl/workbook.xml\"/>" +
            "</Relationships>"

    private const val WORKBOOK_RELS =
        XML_HEADER +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"$REL_NS/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
            "<Relationship Id=\"rId2\" Type=\"$REL_NS/styles\" Target=\"styles.xml\"/>" +
            "</Relationships>"

    private fun workbookXml(lastDataRow: Int): String =
        XML_HEADER +
            "<workbook xmlns=\"$MAIN_NS\" xmlns:r=\"$REL_NS\">" +
            "<sheets><sheet name=\"Diary\" sheetId=\"1\" r:id=\"rId1\"/></sheets>" +
            "<definedNames><definedName name=\"_xlnm._FilterDatabase\" localSheetId=\"0\" hidden=\"1\">" +
            "Diary!\$A\$$HEADER_ROW:\$H\$$lastDataRow</definedName></definedNames>" +
            "<calcPr calcId=\"0\" fullCalcOnLoad=\"1\"/>" +
            "</workbook>"

    private fun xf(numFmt: Int = 0, font: Int = 0, fill: Int = 0, center: Boolean = false): String {
        val attrs = StringBuilder("numFmtId=\"$numFmt\" fontId=\"$font\" fillId=\"$fill\" borderId=\"0\" xfId=\"0\"")
        if (numFmt != 0) attrs.append(" applyNumberFormat=\"1\"")
        if (font != 0) attrs.append(" applyFont=\"1\"")
        if (fill != 0) attrs.append(" applyFill=\"1\"")
        return if (center) {
            "<xf $attrs applyAlignment=\"1\"><alignment horizontal=\"center\"/></xf>"
        } else {
            "<xf $attrs/>"
        }
    }

    private fun solidFill(rgb: String) =
        "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF$rgb\"/><bgColor indexed=\"64\"/></patternFill></fill>"

    // cellXfs order must match the S_* constants above.
    private val STYLES: String =
        XML_HEADER +
            "<styleSheet xmlns=\"$MAIN_NS\">" +
            "<numFmts count=\"3\">" +
            "<numFmt numFmtId=\"164\" formatCode=\"ddd d mmm yyyy\"/>" +
            "<numFmt numFmtId=\"165\" formatCode=\"h:mm AM/PM\"/>" +
            "<numFmt numFmtId=\"166\" formatCode=\"\$#,##0.00\"/>" +
            "</numFmts>" +
            "<fonts count=\"4\">" +
            "<font><sz val=\"11\"/><name val=\"Calibri\"/></font>" +
            "<font><b/><sz val=\"16\"/><name val=\"Calibri\"/></font>" +
            "<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font>" +
            "<font><b/><sz val=\"11\"/><color rgb=\"FFFFFFFF\"/><name val=\"Calibri\"/></font>" +
            "</fonts>" +
            "<fills count=\"7\">" +
            "<fill><patternFill patternType=\"none\"/></fill>" +
            "<fill><patternFill patternType=\"gray125\"/></fill>" +
            solidFill("12262F") +
            solidFill("DEEAF6") +
            solidFill("70AD47") +
            solidFill("81D4FA") +
            solidFill("E0E0E0") +
            "</fills>" +
            "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>" +
            "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
            "<cellXfs count=\"14\">" +
            xf() +
            xf(font = 1) +
            xf(font = 2) +
            xf(font = 3, fill = 2, center = true) +
            xf(numFmt = 164) +
            xf(numFmt = 165, fill = 3) +
            xf(numFmt = 2, fill = 3) +
            xf(numFmt = 2) +
            xf(font = 3, fill = 4) +
            xf(font = 2, fill = 5) +
            xf(fill = 6) +
            xf(numFmt = 2, font = 2) +
            xf(numFmt = 166, font = 2) +
            xf(numFmt = 1, font = 2) +
            "</cellXfs>" +
            "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>" +
            "</styleSheet>"
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `.\gradlew.bat :app:testDebugUnitTest --no-daemon --tests "dev.wfhdiary.app.export.XlsxWriterTest"`
Expected: all 10 tests PASS. If `C6` fails because of floating-point text, check that the expected value equals `(480 / 1440.0).toString()` and fix the test literal, not the writer.

- [ ] **Step 5: Commit**

```powershell
$env:SKIP_VERSION_BUMP = "1"
git add apps/android/app/src/main/java/dev/wfhdiary/app/export/XlsxWriter.kt apps/android/app/src/test/java/dev/wfhdiary/app/export
git commit -m "feat(android): generate diary xlsx on-device"
```

---

### Task 5: `DiaryDataSource` interface and local `DiaryStore`

**Requirements:** DAY, LEAVE, FY-003, SET, XLSX-001, BAK-001, BAK-009 (transactional replace), BAK-010.

**Files:**
- Create: `app/src/main/java/dev/wfhdiary/app/data/DiaryDataSource.kt`
- Modify: `app/src/main/java/dev/wfhdiary/app/data/remote/DiaryRepository.kt`
- Create: `app/src/main/java/dev/wfhdiary/app/data/store/DiaryStore.kt`
- Test: `app/src/test/java/dev/wfhdiary/app/data/store/DiaryStoreTest.kt`

- [ ] **Step 1: Create the interface**

`DiaryDataSource.kt`:

```kotlin
package dev.wfhdiary.app.data

import dev.wfhdiary.app.data.remote.CreateLeaveRequest
import dev.wfhdiary.app.data.remote.DayEntryDto
import dev.wfhdiary.app.data.remote.DiaryDto
import dev.wfhdiary.app.data.remote.DiarySummaryDto
import dev.wfhdiary.app.data.remote.LeavePeriodDto
import dev.wfhdiary.app.data.remote.UpdateUserSettingsRequest
import dev.wfhdiary.app.data.remote.UpsertDayEntryRequest
import dev.wfhdiary.app.data.remote.UserSettingsDto

interface DiaryDataSource {
    suspend fun getDiary(fyYear: Int): DiaryDto

    suspend fun getDays(fyYear: Int, from: String?, to: String?): List<DayEntryDto>

    /** Partial update: null fields are left unchanged. */
    suspend fun upsertDay(fyYear: Int, date: String, request: UpsertDayEntryRequest): DayEntryDto

    /** Full replace: null or blank fields are cleared. */
    suspend fun replaceDay(fyYear: Int, date: String, request: UpsertDayEntryRequest): DayEntryDto

    suspend fun deleteDay(fyYear: Int, date: String): DayEntryDto

    suspend fun createLeave(fyYear: Int, request: CreateLeaveRequest): LeavePeriodDto

    suspend fun getSummary(fyYear: Int): DiarySummaryDto

    suspend fun downloadExportXlsx(fyYear: Int): Pair<ByteArray, String>

    suspend fun getLeavePeriods(): List<LeavePeriodDto>

    suspend fun deleteLeave(id: String)

    suspend fun getSettings(): UserSettingsDto

    suspend fun updateSettings(request: UpdateUserSettingsRequest): UserSettingsDto
}
```

- [ ] **Step 2: Make `DiaryRepository` implement it**

In `data/remote/DiaryRepository.kt`:
- Add `import dev.wfhdiary.app.data.DiaryDataSource`.
- Change the class header to `) : DiaryDataSource {`.
- Add `override` to `getDiary`, `getDays`, `upsertDay`, `deleteDay`, `createLeave`, `getSummary`, `downloadExportXlsx`, `getLeavePeriods`, `deleteLeave`, `getSettings` and `updateSettings`.
- Add after `upsertDay`:

```kotlin
    override suspend fun replaceDay(fyYear: Int, date: String, request: UpsertDayEntryRequest): DayEntryDto =
        upsertDay(fyYear, date, request)
```

The work-template methods stay as they are (not part of the interface; the importer uses them).

- [ ] **Step 3: Write the failing tests**

`DiaryStoreTest.kt`:

```kotlin
package dev.wfhdiary.app.data.store

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.wfhdiary.app.backup.BackupDay
import dev.wfhdiary.app.backup.BackupDocument
import dev.wfhdiary.app.backup.BackupLeavePeriod
import dev.wfhdiary.app.data.local.WfhDiaryDatabase
import dev.wfhdiary.app.data.remote.CreateLeaveRequest
import dev.wfhdiary.app.data.remote.UpdateUserSettingsRequest
import dev.wfhdiary.app.data.remote.UpsertDayEntryRequest
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DiaryStoreTest {
    private lateinit var db: WfhDiaryDatabase
    private lateinit var store: DiaryStore

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            WfhDiaryDatabase::class.java,
        ).allowMainThreadQueries().build()
        store = DiaryStore(db) { 1_000L }
    }

    @After
    fun tearDown() = db.close()

    private val homeDay = UpsertDayEntryRequest("08:00", "17:00", 1.0, null, "Home")

    @Test
    fun upsert_then_partial_upsert_keeps_fields() = runBlocking {
        store.upsertDay(2025, "2025-07-01", homeDay)
        val saved = store.upsertDay(2025, "2025-07-01", UpsertDayEntryRequest(description = "Notes"))
        assertEquals("08:00", saved.startTime)
        assertEquals("Notes", saved.description)
        assertEquals(8.0, saved.totalHours, 0.0001)
        assertEquals("Tuesday", saved.dayName)
    }

    @Test
    fun replace_clears_fields() = runBlocking {
        store.upsertDay(2025, "2025-07-01", homeDay)
        val saved = store.replaceDay(2025, "2025-07-01", UpsertDayEntryRequest(location = "Office"))
        assertNull(saved.startTime)
        assertEquals("Office", saved.location)
    }

    @Test(expected = IllegalArgumentException::class)
    fun upsert_outside_fy_throws() {
        runBlocking { store.upsertDay(2025, "2026-07-01", homeDay) }
    }

    @Test
    fun get_days_filters_by_fy_and_range() = runBlocking {
        store.upsertDay(2025, "2025-07-01", homeDay)
        store.upsertDay(2025, "2025-08-01", homeDay)
        store.upsertDay(2024, "2025-06-30", homeDay)
        assertEquals(2, store.getDays(2025, null, null).size)
        assertEquals(listOf("2025-08-01"), store.getDays(2025, "2025-08-01", "2025-08-31").map { it.date })
    }

    @Test
    fun delete_day_removes_row_and_returns_empty_dto() = runBlocking {
        store.upsertDay(2025, "2025-07-01", homeDay)
        val cleared = store.deleteDay(2025, "2025-07-01")
        assertNull(cleared.location)
        assertTrue(store.getDays(2025, null, null).isEmpty())
    }

    @Test
    fun create_leave_swaps_dates_and_marks_fy_days_only() = runBlocking {
        val period = store.createLeave(2025, CreateLeaveRequest("2026-07-01", "2026-06-29", "Trip"))
        assertEquals("2026-06-29", period.startDate)
        assertEquals("2026-07-01", period.endDate)
        val days = store.getDays(2025, null, null)
        assertEquals(listOf("2026-06-29", "2026-06-30"), days.map { it.date })
        assertTrue(days.all { it.description == "LEAVE" })
        assertEquals(1, store.getLeavePeriods().size)
    }

    @Test
    fun delete_leave_clears_only_uncovered_leave_days() = runBlocking {
        val a = store.createLeave(2025, CreateLeaveRequest("2025-12-22", "2025-12-26"))
        store.createLeave(2025, CreateLeaveRequest("2025-12-25", "2025-12-31"))
        store.replaceDay(2025, "2025-12-22", homeDay)
        store.deleteLeave(a.id)
        val dates = store.getDays(2025, "2025-12-01", "2025-12-31").map { it.date }
        assertTrue("worked day kept", "2025-12-22" in dates)
        assertTrue("leave day cleared", "2025-12-23" !in dates)
        assertTrue("covered by other period", "2025-12-25" in dates)
    }

    @Test
    fun summary_uses_fy_days() = runBlocking {
        store.upsertDay(2025, "2025-07-01", homeDay)
        store.upsertDay(2024, "2025-06-30", homeDay)
        assertEquals(8.0, store.getSummary(2025).homeHours, 0.0001)
    }

    @Test
    fun settings_default_then_update() = runBlocking {
        assertEquals("YOUR NAME HERE", store.getSettings().displayName)
        store.updateSettings(UpdateUserSettingsRequest(displayName = "Me"))
        assertEquals("Me", store.getSettings().displayName)
    }

    @Test
    fun export_contains_xlsx_bytes_and_filename() = runBlocking {
        val (bytes, name) = store.downloadExportXlsx(2025)
        assertEquals("WFH-Diary-2025-2026.xlsx", name)
        assertEquals('P'.code.toByte(), bytes[0])
        assertEquals('K'.code.toByte(), bytes[1])
    }

    @Test
    fun export_document_and_replace_all_round_trip() = runBlocking {
        val legacy = JsonObject(mapOf("workTemplates" to JsonPrimitive("x")))
        val doc =
            BackupDocument(
                exportedAt = "2026-10-27T09:00:00Z",
                appVersion = "test",
                settings = DiaryRules.defaultSettings().copy(displayName = "Restored"),
                days = listOf(BackupDay("2025-07-01", "08:00", "17:00", 1.0, null, "Home")),
                leavePeriods = listOf(BackupLeavePeriod("id-1", "2025-12-22", "2025-12-23", null)),
                legacy = legacy,
            )
        store.upsertDay(2025, "2025-08-01", homeDay)
        store.replaceAll(doc)
        val exported = store.exportDocument("test", "2026-10-27T09:00:00Z")
        assertEquals(doc, exported)
    }

    @Test
    fun writes_emit_change_events() = runBlocking {
        val first = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(5_000) { store.changes.first() } }
        store.upsertDay(2025, "2025-07-01", homeDay)
        first.await()
    }
}
```

- [ ] **Step 4: Run the tests to verify they fail**

Run: `.\gradlew.bat :app:testDebugUnitTest --no-daemon --tests "dev.wfhdiary.app.data.store.DiaryStoreTest"`
Expected: compilation failure, because `DiaryStore` is unresolved.

- [ ] **Step 5: Implement `DiaryStore.kt`**

```kotlin
package dev.wfhdiary.app.data.store

import androidx.room.withTransaction
import dev.wfhdiary.app.backup.BackupDay
import dev.wfhdiary.app.backup.BackupDocument
import dev.wfhdiary.app.backup.BackupLeavePeriod
import dev.wfhdiary.app.data.DiaryDataSource
import dev.wfhdiary.app.data.local.DayEntryEntity
import dev.wfhdiary.app.data.local.LeavePeriodEntity
import dev.wfhdiary.app.data.local.SettingsEntity
import dev.wfhdiary.app.data.local.WfhDiaryDatabase
import dev.wfhdiary.app.data.remote.CreateLeaveRequest
import dev.wfhdiary.app.data.remote.DayEntryDto
import dev.wfhdiary.app.data.remote.DiaryDto
import dev.wfhdiary.app.data.remote.DiarySummaryDto
import dev.wfhdiary.app.data.remote.LeavePeriodDto
import dev.wfhdiary.app.data.remote.UpdateUserSettingsRequest
import dev.wfhdiary.app.data.remote.UpsertDayEntryRequest
import dev.wfhdiary.app.data.remote.UserSettingsDto
import dev.wfhdiary.app.export.XlsxWriter
import dev.wfhdiary.app.util.FinancialYearHelper
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.UUID

class DiaryStore(
    private val db: WfhDiaryDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) : DiaryDataSource {
    private val dayDao = db.dayEntryDao()
    private val leaveDao = db.leavePeriodDao()
    private val settingsDao = db.settingsDao()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _changes =
        MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Emits after every committed write; drives automatic backups. */
    val changes: SharedFlow<Unit> = _changes.asSharedFlow()

    private suspend fun <T> write(block: suspend () -> T): T {
        val result = db.withTransaction { block() }
        _changes.tryEmit(Unit)
        return result
    }

    override suspend fun getDiary(fyYear: Int): DiaryDto {
        val (start, end) = FinancialYearHelper.fyRange(fyYear)
        return DiaryDto(fyYear, start.toString(), end.toString())
    }

    override suspend fun getDays(fyYear: Int, from: String?, to: String?): List<DayEntryDto> {
        val (fyStart, fyEnd) = FinancialYearHelper.fyRange(fyYear)
        val lower = maxOf(fyStart, from?.let(::parseDate) ?: fyStart)
        val upper = minOf(fyEnd, to?.let(::parseDate) ?: fyEnd)
        if (lower > upper) return emptyList()
        return dayDao.getBetween(lower.toString(), upper.toString()).map { DiaryRules.toDto(it.toBackupDay()) }
    }

    override suspend fun upsertDay(fyYear: Int, date: String, request: UpsertDayEntryRequest): DayEntryDto =
        write {
            requireInFy(fyYear, date)
            save(DiaryRules.applyUpsert(dayDao.get(date)?.toBackupDay(), date, request))
        }

    override suspend fun replaceDay(fyYear: Int, date: String, request: UpsertDayEntryRequest): DayEntryDto =
        write {
            requireInFy(fyYear, date)
            save(DiaryRules.replace(date, request))
        }

    override suspend fun deleteDay(fyYear: Int, date: String): DayEntryDto =
        write {
            requireInFy(fyYear, date)
            dayDao.delete(date)
            DiaryRules.toDto(BackupDay(date))
        }

    override suspend fun createLeave(fyYear: Int, request: CreateLeaveRequest): LeavePeriodDto =
        write {
            var start = parseDate(request.startDate)
            var end = parseDate(request.endDate)
            if (end < start) start = end.also { end = start }
            val period =
                LeavePeriodEntity(
                    id = UUID.randomUUID().toString(),
                    startDate = start.toString(),
                    endDate = end.toString(),
                    note = request.note?.trim()?.ifEmpty { null },
                    createdAtMillis = clock(),
                )
            leaveDao.insert(period)
            DiaryRules.leaveDatesInFy(fyYear, start, end).forEach { save(DiaryRules.leaveDay(it.toString())) }
            period.toDto()
        }

    override suspend fun getSummary(fyYear: Int): DiarySummaryDto =
        DiaryRules.summarize(daysInFy(fyYear))

    override suspend fun downloadExportXlsx(fyYear: Int): Pair<ByteArray, String> {
        val bytes = XlsxWriter.write(fyYear, getSettings().displayName, daysInFy(fyYear))
        return bytes to XlsxWriter.filename(fyYear)
    }

    override suspend fun getLeavePeriods(): List<LeavePeriodDto> = leaveDao.getAll().map { it.toDto() }

    override suspend fun deleteLeave(id: String) {
        write {
            val period = leaveDao.get(id) ?: return@write
            leaveDao.delete(id)
            val others = leaveDao.getAll().map { parseDate(it.startDate)..parseDate(it.endDate) }
            val removed = parseDate(period.startDate)..parseDate(period.endDate)
            for (date in DiaryRules.datesToClearOnLeaveDelete(removed, others)) {
                val existing = dayDao.get(date.toString()) ?: continue
                if (existing.description.equals(DiaryRules.LEAVE, ignoreCase = true)) {
                    dayDao.delete(existing.date)
                }
            }
        }
    }

    override suspend fun getSettings(): UserSettingsDto =
        settingsDao.get()?.let { json.decodeFromString(UserSettingsDto.serializer(), it.settingsJson) }
            ?: DiaryRules.defaultSettings()

    override suspend fun updateSettings(request: UpdateUserSettingsRequest): UserSettingsDto =
        write {
            val updated = DiaryRules.applySettingsUpdate(getSettings(), request)
            settingsDao.upsert(
                SettingsEntity(
                    settingsJson = json.encodeToString(UserSettingsDto.serializer(), updated),
                    legacyJson = settingsDao.get()?.legacyJson,
                ),
            )
            updated
        }

    suspend fun exportDocument(appVersion: String, exportedAt: String): BackupDocument =
        db.withTransaction {
            BackupDocument(
                exportedAt = exportedAt,
                appVersion = appVersion,
                settings = getSettings(),
                days = dayDao.getAll().map { it.toBackupDay() },
                leavePeriods = leaveDao.getAll().map { BackupLeavePeriod(it.id, it.startDate, it.endDate, it.note) },
                legacy = settingsDao.get()?.legacyJson?.let { json.parseToJsonElement(it).jsonObject },
            )
        }

    /** Replaces every table with [document] in one transaction. Callers validate first. */
    suspend fun replaceAll(document: BackupDocument) {
        write {
            val now = clock()
            dayDao.deleteAll()
            leaveDao.deleteAll()
            settingsDao.deleteAll()
            dayDao.upsertAll(document.days.filterNot(DiaryRules::isEmpty).map { it.toEntity(now) })
            leaveDao.insertAll(
                document.leavePeriods.map { LeavePeriodEntity(it.id, it.startDate, it.endDate, it.note, now) },
            )
            settingsDao.upsert(
                SettingsEntity(
                    settingsJson = json.encodeToString(UserSettingsDto.serializer(), document.settings),
                    legacyJson = document.legacy?.toString(),
                ),
            )
        }
    }

    private suspend fun daysInFy(fyYear: Int): List<BackupDay> {
        val (start, end) = FinancialYearHelper.fyRange(fyYear)
        return dayDao.getBetween(start.toString(), end.toString()).map { it.toBackupDay() }
    }

    private suspend fun save(day: BackupDay): DayEntryDto {
        if (DiaryRules.isEmpty(day)) dayDao.delete(day.date) else dayDao.upsert(day.toEntity(clock()))
        return DiaryRules.toDto(day)
    }

    private fun requireInFy(fyYear: Int, date: String) {
        val (start, end) = FinancialYearHelper.fyRange(fyYear)
        val d = parseDate(date)
        require(d in start..end) { "Date $date is outside financial year $fyYear." }
    }

    private fun parseDate(value: String): LocalDate =
        try {
            LocalDate.parse(value)
        } catch (e: DateTimeParseException) {
            throw IllegalArgumentException("Invalid date: $value", e)
        }

    private fun DayEntryEntity.toBackupDay() =
        BackupDay(date, startTime, finishTime, breakHours, description, location)

    private fun BackupDay.toEntity(now: Long) =
        DayEntryEntity(date, startTime, finishTime, breakHours, description, location, now)

    private fun LeavePeriodEntity.toDto() = LeavePeriodDto(id, startDate, endDate, note)
}
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `.\gradlew.bat :app:testDebugUnitTest --no-daemon --tests "dev.wfhdiary.app.data.store.DiaryStoreTest"`
Expected: all 12 tests PASS.

- [ ] **Step 7: Run the full suite**

Run: `.\gradlew.bat :app:testDebugUnitTest --no-daemon`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```powershell
$env:SKIP_VERSION_BUMP = "1"
git add apps/android/app/src/main/java/dev/wfhdiary/app/data apps/android/app/src/test/java/dev/wfhdiary/app/data/store/DiaryStoreTest.kt
git commit -m "feat(android): local DiaryStore behind a DiaryDataSource interface"
```

---

### Task 6: Wire the app to the local store

**Requirements:** DAY-010 (edit sheet clears fields), FY-003, XLSX-001.

**Files:**
- Modify: `app/src/main/java/dev/wfhdiary/app/WfhDiaryApplication.kt`
- Modify: `app/src/main/java/dev/wfhdiary/app/ui/month/MonthGridViewModel.kt`

- [ ] **Step 1: Switch the application's data source**

In `WfhDiaryApplication.kt`, replace the `diaryRepository` property with:

```kotlin
    val diaryStore: DiaryStore by lazy { DiaryStore(database) }

    val diaryRepository: DiaryDataSource
        get() = diaryStore

    /** Old server API; used only by the one-time importer in phase 1. */
    val remoteDiaryRepository: DiaryRepository by lazy {
        DiaryRepository(httpClient) { resolveApiBaseUrl() }
    }
```

Add the imports `dev.wfhdiary.app.data.DiaryDataSource` and `dev.wfhdiary.app.data.store.DiaryStore`. The `onCreate` scheduling block keeps calling `diaryRepository.getSettings()`, which is now local.

- [ ] **Step 2: Load month days from the store and make refresh authoritative**

In `MonthGridViewModel.kt`:

1. In `saveDay`, change `repo.upsertDay(fyYear, date, request)` to `repo.replaceDay(fyYear, date, request)`.
2. In `refreshFromRemote`, directly after `val days = repo.getDays(…)`, insert this line before the `days.forEach` line so deleted or restored days don't linger:

```kotlin
                daysCache.keys.removeAll { it >= fyStart.format(isoDate) && it <= fyEnd.format(isoDate) }
```

3. At the end of `applyMonth` (after the `_uiState.update { … }` block), add:

```kotlin
        viewModelScope.launch {
            val from = yearMonth.atDay(1).format(isoDate)
            val to = yearMonth.atEndOfMonth().format(isoDate)
            val monthDays =
                runCatching {
                    FinancialYearHelper.fyYearsCovering(yearMonth).flatMap { fy -> repo.getDays(fy, from, to) }
                }.getOrNull() ?: return@launch
            daysCache.keys.removeAll { it in from..to }
            monthDays.forEach { daysCache[it.date] = it }
            val summary = runCatching { repo.getSummary(fyYear) }.getOrNull()
            if (summary != null) summaryByFy[fyYear] = summary
            _uiState.update { state ->
                if (state.yearMonth == yearMonth) {
                    state.copy(days = daysForMonth(yearMonth), summary = summaryByFy[fyYear] ?: state.summary)
                } else {
                    state
                }
            }
        }
```

4. In `util/FinancialYearHelper.kt`, add this function (a calendar month never spans two FYs, but this keeps the call site honest):

```kotlin
    fun fyYearsCovering(yearMonth: java.time.YearMonth): List<Int> =
        listOf(currentFyYear(yearMonth.atDay(1)), currentFyYear(yearMonth.atEndOfMonth())).distinct()
```

Then add a test to the existing `FinancialYearHelperTest.kt` if it exists. If it doesn't, create `app/src/test/java/dev/wfhdiary/app/util/FinancialYearHelperTest.kt` containing:

```kotlin
package dev.wfhdiary.app.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth

class FinancialYearHelperTest {
    @Test
    fun fyYearsCovering_june_and_july() {
        assertEquals(listOf(2024), FinancialYearHelper.fyYearsCovering(YearMonth.of(2025, 6)))
        assertEquals(listOf(2025), FinancialYearHelper.fyYearsCovering(YearMonth.of(2025, 7)))
    }
}
```

If the file already exists, add only the test method and the `YearMonth` import.

- [ ] **Step 3: Check that no other code uses the remote repository**

Run (from repo root): `rg "remoteDiaryRepository|DiaryRepository\(" apps/android/app/src/main`
Expected: only `WfhDiaryApplication.kt` matches.

- [ ] **Step 4: Build and test**

Run: `.\gradlew.bat :app:assembleDebug --no-daemon`, then `.\gradlew.bat :app:testDebugUnitTest --no-daemon`
Expected: both BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```powershell
$env:SKIP_VERSION_BUMP = "1"
git add apps/android/app/src/main/java/dev/wfhdiary/app apps/android/app/src/test/java/dev/wfhdiary/app/util
git commit -m "feat(android): use the on-device store as the app's data source"
```

---

### Task 7: Backup infrastructure (folder, retention, service, scheduling)

**Requirements:** BAK-002, BAK-003, BAK-004, BAK-005, BAK-006 (status data), BAK-007 (service side).

**Files (all under `app/src/main/java/dev/wfhdiary/app/backup/`):**
- Create: `BackupPreferences.kt`, `BackupFolder.kt`, `SafBackupFolder.kt`, `BackupRetention.kt`, `BackupOutcome.kt`, `BackupService.kt`, `BackupWorker.kt`, `BackupScheduler.kt`, `BackupShareHelper.kt`, `BackupHealth.kt`
- Modify: `app/src/main/java/dev/wfhdiary/app/WfhDiaryApplication.kt`
- Test: `app/src/test/java/dev/wfhdiary/app/backup/BackupRetentionTest.kt`, `BackupHealthTest.kt`, `BackupServiceTest.kt`, `FakeBackupFolder.kt`

- [ ] **Step 1: Write the failing pure tests**

`BackupRetentionTest.kt`:

```kotlin
package dev.wfhdiary.app.backup

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class BackupRetentionTest {
    @Test
    fun file_name_uses_iso_date() {
        assertEquals("wfh-diary-backup-2026-10-27.json", BackupRetention.fileNameFor(LocalDate.of(2026, 10, 27)))
    }

    @Test
    fun keeps_newest_thirty_and_ignores_other_files() {
        val names =
            (1..35).map { BackupRetention.fileNameFor(LocalDate.of(2026, 1, 1).plusDays(it.toLong())) } +
                listOf("wfh-diary-pre-restore-20260101-120000.json", "notes.txt")
        val toDelete = BackupRetention.filesToDelete(names.shuffled())
        assertEquals(5, toDelete.size)
        assertEquals(
            (1..5).map { BackupRetention.fileNameFor(LocalDate.of(2026, 1, 1).plusDays(it.toLong())) }.toSet(),
            toDelete.toSet(),
        )
    }
}
```

`BackupHealthTest.kt`:

```kotlin
package dev.wfhdiary.app.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupHealthTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 100 * day

    @Test
    fun no_folder_warns() {
        assertEquals(BackupWarning.NO_FOLDER, BackupHealth.warning(null, now, null, now))
    }

    @Test
    fun last_error_warns() {
        assertEquals(BackupWarning.FAILED, BackupHealth.warning("uri", now, "boom", now))
    }

    @Test
    fun stale_after_three_days() {
        assertNull(BackupHealth.warning("uri", now - 3 * day, null, now))
        assertEquals(BackupWarning.STALE, BackupHealth.warning("uri", now - 3 * day - 1, null, now))
        assertEquals(BackupWarning.STALE, BackupHealth.warning("uri", null, null, now))
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `.\gradlew.bat :app:testDebugUnitTest --no-daemon --tests "dev.wfhdiary.app.backup.BackupRetentionTest" --tests "dev.wfhdiary.app.backup.BackupHealthTest"`
Expected: compilation failure.

- [ ] **Step 3: Implement the pure pieces**

`BackupRetention.kt`:

```kotlin
package dev.wfhdiary.app.backup

import java.time.LocalDate

object BackupRetention {
    const val KEEP = 30
    private const val PREFIX = "wfh-diary-backup-"
    private val AUTO_NAME = Regex("""^wfh-diary-backup-\d{4}-\d{2}-\d{2}\.json$""")

    fun fileNameFor(date: LocalDate): String = "$PREFIX$date.json"

    /** Only automatic daily files are pruned; anything else in the folder is left alone. */
    fun filesToDelete(names: List<String>, keep: Int = KEEP): List<String> =
        names.filter { AUTO_NAME.matches(it) }.sortedDescending().drop(keep)
}
```

`BackupHealth.kt`:

```kotlin
package dev.wfhdiary.app.backup

enum class BackupWarning { NO_FOLDER, FAILED, STALE }

object BackupHealth {
    const val STALE_AFTER_MILLIS = 3L * 24 * 60 * 60 * 1000

    fun warning(folderUri: String?, lastSuccessMillis: Long?, lastError: String?, nowMillis: Long): BackupWarning? =
        when {
            folderUri == null -> BackupWarning.NO_FOLDER
            lastError != null -> BackupWarning.FAILED
            lastSuccessMillis == null || nowMillis - lastSuccessMillis > STALE_AFTER_MILLIS -> BackupWarning.STALE
            else -> null
        }
}
```

Run the step-2 command again. Expected: 5 tests PASS.

- [ ] **Step 4: Implement preferences, folder abstraction and outcome**

`BackupPreferences.kt`:

```kotlin
package dev.wfhdiary.app.backup

import android.content.Context

/** Device-local backup state. Deliberately not part of the backup file. */
class BackupPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("backup", Context.MODE_PRIVATE)

    var folderUri: String?
        get() = prefs.getString(KEY_FOLDER, null)
        set(value) = prefs.edit().putString(KEY_FOLDER, value).apply()

    var folderLabel: String?
        get() = prefs.getString(KEY_FOLDER_LABEL, null)
        set(value) = prefs.edit().putString(KEY_FOLDER_LABEL, value).apply()

    val lastSuccessMillis: Long?
        get() = prefs.getLong(KEY_SUCCESS, 0L).takeIf { it > 0L }

    val lastError: String?
        get() = prefs.getString(KEY_ERROR, null)

    fun recordSuccess(millis: Long) {
        prefs.edit().putLong(KEY_SUCCESS, millis).remove(KEY_ERROR).apply()
    }

    fun recordFailure(message: String) {
        prefs.edit().putString(KEY_ERROR, message).apply()
    }

    private companion object {
        const val KEY_FOLDER = "folder_uri"
        const val KEY_FOLDER_LABEL = "folder_label"
        const val KEY_SUCCESS = "last_success_millis"
        const val KEY_ERROR = "last_error"
    }
}
```

`BackupFolder.kt`:

```kotlin
package dev.wfhdiary.app.backup

interface BackupFolder {
    fun listNames(): List<String>

    /** Creates or overwrites [name]. */
    fun write(name: String, text: String)

    fun delete(name: String)
}
```

`SafBackupFolder.kt`:

```kotlin
package dev.wfhdiary.app.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.IOException

class SafBackupFolder(
    private val context: Context,
    private val treeUri: Uri,
) : BackupFolder {
    private fun root(): DocumentFile =
        DocumentFile.fromTreeUri(context, treeUri)?.takeIf { it.canWrite() }
            ?: throw IOException("Backup folder is not accessible. Choose it again in Settings.")

    override fun listNames(): List<String> = root().listFiles().mapNotNull { it.name }

    override fun write(name: String, text: String) {
        val dir = root()
        val file =
            dir.findFile(name)
                ?: dir.createFile("application/json", name)
                ?: throw IOException("Could not create $name in the backup folder.")
        val stream =
            context.contentResolver.openOutputStream(file.uri, "wt")
                ?: throw IOException("Could not open $name for writing.")
        stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    override fun delete(name: String) {
        root().findFile(name)?.delete()
    }
}
```

`BackupOutcome.kt`:

```kotlin
package dev.wfhdiary.app.backup

sealed interface BackupOutcome {
    data object NoFolder : BackupOutcome

    data class Success(val fileName: String) : BackupOutcome

    data class Failed(val message: String) : BackupOutcome
}
```

- [ ] **Step 5: Write the failing `BackupServiceTest`**

`FakeBackupFolder.kt` (test source set):

```kotlin
package dev.wfhdiary.app.backup

import java.io.IOException

class FakeBackupFolder : BackupFolder {
    val files = linkedMapOf<String, String>()
    var failWrites = false

    override fun listNames(): List<String> = files.keys.toList()

    override fun write(name: String, text: String) {
        if (failWrites) throw IOException("disk full")
        files[name] = text
    }

    override fun delete(name: String) {
        files.remove(name)
    }
}
```

`BackupServiceTest.kt`:

```kotlin
package dev.wfhdiary.app.backup

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.wfhdiary.app.data.local.WfhDiaryDatabase
import dev.wfhdiary.app.data.remote.UpsertDayEntryRequest
import dev.wfhdiary.app.data.store.DiaryStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BackupServiceTest {
    private lateinit var db: WfhDiaryDatabase
    private lateinit var store: DiaryStore
    private lateinit var prefs: BackupPreferences
    private val folder = FakeBackupFolder()
    private val clock = Clock.fixed(Instant.parse("2026-10-27T01:00:00Z"), ZoneId.of("Australia/Sydney"))
    private lateinit var service: BackupService

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, WfhDiaryDatabase::class.java).allowMainThreadQueries().build()
        store = DiaryStore(db)
        prefs = BackupPreferences(context)
        service = BackupService(store, prefs, "test", { folder }, clock)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun no_folder_returns_no_folder() = runBlocking {
        prefs.folderUri = null
        assertEquals(BackupOutcome.NoFolder, service.backupToFolder())
    }

    @Test
    fun writes_todays_file_and_records_success() = runBlocking {
        prefs.folderUri = "content://tree"
        store.upsertDay(2025, "2025-07-01", UpsertDayEntryRequest("08:00", "17:00", 1.0, null, "Home"))
        val outcome = service.backupToFolder()
        assertEquals(BackupOutcome.Success("wfh-diary-backup-2026-10-27.json"), outcome)
        val decoded = BackupCodec.decode(folder.files.getValue("wfh-diary-backup-2026-10-27.json"))
        assertTrue(decoded is BackupParseResult.Success)
        assertEquals(1, (decoded as BackupParseResult.Success).document.days.size)
        assertEquals(clock.millis(), prefs.lastSuccessMillis)
        assertNull(prefs.lastError)
    }

    @Test
    fun prunes_old_files() = runBlocking {
        prefs.folderUri = "content://tree"
        repeat(31) { i -> folder.files["wfh-diary-backup-2026-09-%02d.json".format(i % 30 + 1)] = "{}" }
        folder.files["wfh-diary-backup-2026-08-31.json"] = "{}"
        service.backupToFolder()
        assertEquals(30, folder.files.keys.count { it.startsWith("wfh-diary-backup-") })
        assertTrue("wfh-diary-backup-2026-10-27.json" in folder.files)
    }

    @Test
    fun failure_is_recorded() = runBlocking {
        prefs.folderUri = "content://tree"
        folder.failWrites = true
        val outcome = service.backupToFolder()
        assertTrue(outcome is BackupOutcome.Failed)
        assertEquals("disk full", prefs.lastError)
    }

    @Test
    fun safety_backup_goes_to_folder_or_fallback() = runBlocking {
        prefs.folderUri = "content://tree"
        val name = service.writeSafetyBackup(File("unused"))
        assertTrue(name.startsWith("wfh-diary-pre-restore-"))
        assertTrue(name in folder.files)

        prefs.folderUri = null
        val dir = createTempDirectory()
        val path = service.writeSafetyBackup(dir)
        assertTrue(File(path).exists())
    }

    private fun createTempDirectory(): File =
        kotlin.io.path.createTempDirectory("wfh-backup").toFile()
}
```

Run: `.\gradlew.bat :app:testDebugUnitTest --no-daemon --tests "dev.wfhdiary.app.backup.BackupServiceTest"`
Expected: compilation failure, because `BackupService` is unresolved.

- [ ] **Step 6: Implement `BackupService.kt`**

```kotlin
package dev.wfhdiary.app.backup

import dev.wfhdiary.app.data.store.DiaryStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

class BackupService(
    private val store: DiaryStore,
    private val prefs: BackupPreferences,
    private val appVersion: String,
    private val openFolder: (String) -> BackupFolder,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    private val mutex = Mutex()

    suspend fun buildDocument(): BackupDocument =
        store.exportDocument(appVersion, OffsetDateTime.now(clock).toString())

    suspend fun backupToFolder(): BackupOutcome =
        mutex.withLock {
            val uri = prefs.folderUri ?: return BackupOutcome.NoFolder
            try {
                val text = BackupCodec.encode(buildDocument())
                val name = BackupRetention.fileNameFor(LocalDate.now(clock))
                withContext(Dispatchers.IO) {
                    val folder = openFolder(uri)
                    folder.write(name, text)
                    BackupRetention.filesToDelete(folder.listNames()).forEach(folder::delete)
                }
                prefs.recordSuccess(clock.millis())
                BackupOutcome.Success(name)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = e.message ?: e.javaClass.simpleName
                prefs.recordFailure(message)
                BackupOutcome.Failed(message)
            }
        }

    /**
     * Writes a backup of the current data before a restore. Uses the backup folder when possible,
     * otherwise [fallbackDir]/backups. Returns the file name or path written. Throws if both fail.
     */
    suspend fun writeSafetyBackup(fallbackDir: File): String {
        val text = BackupCodec.encode(buildDocument())
        val stamp = LocalDateTime.now(clock).format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val name = "wfh-diary-pre-restore-$stamp.json"
        return withContext(Dispatchers.IO) {
            val uri = prefs.folderUri
            val wroteToFolder = uri != null && runCatching { openFolder(uri).write(name, text) }.isSuccess
            if (wroteToFolder) {
                name
            } else {
                val dir = File(fallbackDir, "backups").apply { mkdirs() }
                File(dir, name).apply { writeText(text) }.absolutePath
            }
        }
    }

    /** Writes today's backup into [dir] for sharing. */
    suspend fun writeShareFile(dir: File): File {
        val text = BackupCodec.encode(buildDocument())
        return withContext(Dispatchers.IO) {
            dir.mkdirs()
            File(dir, BackupRetention.fileNameFor(LocalDate.now(clock))).apply { writeText(text) }
        }
    }
}
```

Run the step-5 command again. Expected: 5 tests PASS.

- [ ] **Step 7: Implement the worker, scheduler and share helper**

`BackupWorker.kt`:

```kotlin
package dev.wfhdiary.app.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.wfhdiary.app.WfhDiaryApplication

class BackupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // Failures are recorded in BackupPreferences and surfaced in the UI; retrying blindly won't fix a revoked folder.
        (applicationContext as WfhDiaryApplication).backupService.backupToFolder()
        return Result.success()
    }
}
```

`BackupScheduler.kt`:

```kotlin
package dev.wfhdiary.app.backup

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object BackupScheduler {
    private const val AFTER_CHANGE = "backup-after-change"
    private const val DAILY = "backup-daily"
    const val AFTER_CHANGE_DELAY_MINUTES = 10L

    fun scheduleAfterChange(context: Context) {
        val request =
            OneTimeWorkRequestBuilder<BackupWorker>()
                .setInitialDelay(AFTER_CHANGE_DELAY_MINUTES, TimeUnit.MINUTES)
                .build()
        WorkManager.getInstance(context).enqueueUniqueWork(AFTER_CHANGE, ExistingWorkPolicy.REPLACE, request)
    }

    fun ensureDaily(context: Context) {
        val request = PeriodicWorkRequestBuilder<BackupWorker>(24, TimeUnit.HOURS).build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(DAILY, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
```

`BackupShareHelper.kt`. It reuses the existing `exports/` FileProvider path:

```kotlin
package dev.wfhdiary.app.backup

import android.app.Activity
import android.content.Intent
import androidx.core.content.FileProvider
import dev.wfhdiary.app.R
import java.io.File

object BackupShareHelper {
    fun share(activity: Activity, file: File) {
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.backup_share)))
    }
}
```

Add to `res/values/strings.xml` now, because the share helper needs it:

```xml
    <string name="backup_share">Share backup…</string>
```

- [ ] **Step 8: Wire into the application**

In `WfhDiaryApplication.kt` add these properties:

```kotlin
    val backupPreferences: BackupPreferences by lazy { BackupPreferences(this) }

    val backupService: BackupService by lazy {
        BackupService(
            store = diaryStore,
            prefs = backupPreferences,
            appVersion = BuildConfig.VERSION_NAME,
            openFolder = { uri -> SafBackupFolder(this, Uri.parse(uri)) },
        )
    }
```

In `onCreate`, after `NotificationChannels.create(this)`:

```kotlin
        BackupScheduler.ensureDaily(this)
        authSyncScope.launch {
            diaryStore.changes.collect { BackupScheduler.scheduleAfterChange(this@WfhDiaryApplication) }
        }
```

Add imports for `android.net.Uri`, `BackupPreferences`, `BackupService`, `BackupScheduler` and `SafBackupFolder`.

- [ ] **Step 9: Build and run the full suite**

Run: `.\gradlew.bat :app:assembleDebug --no-daemon`, then `.\gradlew.bat :app:testDebugUnitTest --no-daemon`
Expected: both BUILD SUCCESSFUL.

- [ ] **Step 10: Commit**

```powershell
$env:SKIP_VERSION_BUMP = "1"
git add apps/android/app/src/main apps/android/app/src/test/java/dev/wfhdiary/app/backup
git commit -m "feat(android): automatic json backups to a chosen folder"
```

---

### Task 8: Restore service

**Requirements:** BAK-008, BAK-009, BAK-010.

**Files:**
- Create: `app/src/main/java/dev/wfhdiary/app/backup/RestoreOutcome.kt`
- Create: `app/src/main/java/dev/wfhdiary/app/backup/RestoreService.kt`
- Modify: `app/src/main/java/dev/wfhdiary/app/WfhDiaryApplication.kt`
- Test: `app/src/test/java/dev/wfhdiary/app/backup/RestoreServiceTest.kt`

- [ ] **Step 1: Write the failing test**

`RestoreServiceTest.kt`:

```kotlin
package dev.wfhdiary.app.backup

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.wfhdiary.app.data.local.WfhDiaryDatabase
import dev.wfhdiary.app.data.remote.UpsertDayEntryRequest
import dev.wfhdiary.app.data.remote.UserSettingsDto
import dev.wfhdiary.app.data.store.DiaryRules
import dev.wfhdiary.app.data.store.DiaryStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RestoreServiceTest {
    private lateinit var db: WfhDiaryDatabase
    private lateinit var store: DiaryStore
    private val folder = FakeBackupFolder()
    private var rescheduledWith: UserSettingsDto? = null
    private lateinit var restore: RestoreService

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, WfhDiaryDatabase::class.java).allowMainThreadQueries().build()
        store = DiaryStore(db)
        val prefs = BackupPreferences(context).apply { folderUri = "content://tree" }
        val backup = BackupService(store, prefs, "test", { folder })
        restore = RestoreService(store, backup, File("unused")) { rescheduledWith = it }
    }

    @After
    fun tearDown() = db.close()

    private fun document(days: List<BackupDay>) =
        BackupDocument(
            exportedAt = "2026-10-27T09:00:00Z",
            appVersion = "test",
            settings = DiaryRules.defaultSettings().copy(displayName = "From backup"),
            days = days,
        )

    @Test
    fun restore_writes_safety_backup_replaces_data_and_reschedules() = runBlocking {
        store.upsertDay(2025, "2025-08-01", UpsertDayEntryRequest(location = "Office"))
        val outcome = restore.restore(document(listOf(BackupDay("2025-07-01", location = "Home"))))

        assertTrue(outcome is RestoreOutcome.Success)
        val safety = folder.files.entries.single { it.key.startsWith("wfh-diary-pre-restore-") }
        assertTrue("safety backup holds old data", safety.value.contains("2025-08-01"))
        assertEquals(listOf("2025-07-01"), store.getDays(2025, null, null).map { it.date })
        assertEquals("From backup", rescheduledWith?.displayName)
    }

    @Test
    fun invalid_document_is_refused_without_changes() = runBlocking {
        store.upsertDay(2025, "2025-08-01", UpsertDayEntryRequest(location = "Office"))
        val outcome = restore.restore(document(listOf(BackupDay("2025-02-30"))))
        assertTrue(outcome is RestoreOutcome.Failed)
        assertEquals(listOf("2025-08-01"), store.getDays(2025, null, null).map { it.date })
        assertNull(rescheduledWith)
        assertTrue(folder.files.isEmpty())
    }

    @Test
    fun safety_backup_failure_aborts_restore() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val prefs = BackupPreferences(context).apply { folderUri = null }
        val brokenDir = File.createTempFile("not-a-dir", ".tmp")
        val service = RestoreService(store, BackupService(store, prefs, "test", { folder }), brokenDir) {}
        store.upsertDay(2025, "2025-08-01", UpsertDayEntryRequest(location = "Office"))
        val outcome = service.restore(document(emptyList()))
        assertTrue(outcome is RestoreOutcome.Failed)
        assertEquals(1, store.getDays(2025, null, null).size)
    }
}
```

The last test points `fallbackDir` at a regular file, so `File(file, "backups").mkdirs()` fails, and the subsequent write throws.

Run: `.\gradlew.bat :app:testDebugUnitTest --no-daemon --tests "dev.wfhdiary.app.backup.RestoreServiceTest"`
Expected: compilation failure.

- [ ] **Step 2: Implement**

`RestoreOutcome.kt`:

```kotlin
package dev.wfhdiary.app.backup

sealed interface RestoreOutcome {
    data class Success(val safetyBackup: String, val days: Int, val leavePeriods: Int) : RestoreOutcome

    data class Failed(val message: String) : RestoreOutcome
}
```

`RestoreService.kt`:

```kotlin
package dev.wfhdiary.app.backup

import dev.wfhdiary.app.data.remote.UserSettingsDto
import dev.wfhdiary.app.data.store.DiaryStore
import kotlinx.coroutines.CancellationException
import java.io.File

class RestoreService(
    private val store: DiaryStore,
    private val backupService: BackupService,
    private val safetyFallbackDir: File,
    private val onRestored: suspend (UserSettingsDto) -> Unit,
) {
    fun preview(text: String): BackupParseResult = BackupCodec.decode(text)

    suspend fun restore(document: BackupDocument): RestoreOutcome {
        val valid =
            when (val result = BackupCodec.validate(document)) {
                is BackupParseResult.Failure -> return RestoreOutcome.Failed(result.message)
                is BackupParseResult.Success -> result.document
            }
        val safety =
            try {
                backupService.writeSafetyBackup(safetyFallbackDir)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return RestoreOutcome.Failed("Could not save a safety backup first, so nothing was changed: ${e.message}")
            }
        try {
            store.replaceAll(valid)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return RestoreOutcome.Failed("Restore failed and your data was not changed: ${e.message}")
        }
        runCatching { onRestored(valid.settings) }
        return RestoreOutcome.Success(safety, valid.days.size, valid.leavePeriods.size)
    }
}
```

- [ ] **Step 3: Wire into the application**

In `WfhDiaryApplication.kt`:

```kotlin
    val restoreService: RestoreService by lazy {
        RestoreService(diaryStore, backupService, cacheDir) { settings ->
            NotificationScheduler.scheduleNext(this, settings.notificationTime, settings.notificationWorkingDaysMask)
        }
    }
```

- [ ] **Step 4: Run tests and build**

Run: `.\gradlew.bat :app:testDebugUnitTest --no-daemon`, then `.\gradlew.bat :app:assembleDebug --no-daemon`
Expected: both BUILD SUCCESSFUL. The 3 new tests pass.

- [ ] **Step 5: Commit**

```powershell
$env:SKIP_VERSION_BUMP = "1"
git add apps/android/app/src/main apps/android/app/src/test/java/dev/wfhdiary/app/backup/RestoreServiceTest.kt
git commit -m "feat(android): restore from backup with safety copy"
```

---

### Task 9: One-time importer from the old server

**Requirements:** BAK-011 (phase 1 only).

**Files:**
- Create: `app/src/main/java/dev/wfhdiary/app/backup/LegacyServerImporter.kt`
- Modify: `app/src/main/java/dev/wfhdiary/app/WfhDiaryApplication.kt`
- Test: `app/src/test/java/dev/wfhdiary/app/backup/LegacyServerImporterTest.kt`

- [ ] **Step 1: Write the failing test**

`LegacyServerImporterTest.kt`:

```kotlin
package dev.wfhdiary.app.backup

import dev.wfhdiary.app.data.remote.DiaryRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class LegacyServerImporterTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-27T00:00:00Z"), ZoneId.of("UTC"))

    private val settingsJson =
        """{"displayName":"Me","notificationTime":"08:00","defaultHomeStart":"08:00","defaultHomeFinish":"17:00",
           "defaultHomeBreakHours":1.0,"defaultOfficeStart":"08:00","defaultOfficeFinish":"17:00","defaultOfficeBreakHours":1.0}"""

    private fun daysJson(fy: Int) =
        when (fy) {
            2024 -> """[{"date":"2024-07-01","dayName":"Monday","startTime":"08:00","finishTime":"17:00","breakHours":1.0,"totalHours":8.0,"location":"Home"},
                       {"date":"2024-07-02","dayName":"Tuesday","totalHours":0.0}]"""
            2026 -> """[{"date":"2026-07-01","dayName":"Wednesday","description":"LEAVE","totalHours":0.0}]"""
            else -> "[]"
        }

    private fun importer(failFy: Int? = null): Pair<LegacyServerImporter, MutableList<String>> {
        val requested = mutableListOf<String>()
        val engine =
            MockEngine { request ->
                val path = request.url.encodedPath
                requested += path
                val json = "application/json"
                val fy = Regex("""/v1/diaries/(\d+)/days""").find(path)?.groupValues?.get(1)?.toInt()
                when {
                    fy != null && fy == failFy -> respond("boom", HttpStatusCode.InternalServerError)
                    fy != null -> respond(daysJson(fy), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, json))
                    path.endsWith("/v1/settings") -> respond(settingsJson, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, json))
                    path.endsWith("/v1/leave-periods") ->
                        respond("""[{"id":"L1","startDate":"2026-07-01","endDate":"2026-07-01"}]""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, json))
                    path.endsWith("/v1/work-templates") ->
                        respond("""[{"id":"T1","label":"Home day"}]""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, json))
                    else -> respond("not found", HttpStatusCode.NotFound)
                }
            }
        val client =
            HttpClient(engine) {
                expectSuccess = true
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
        val repo = DiaryRepository(client) { "https://example.test/" }
        return LegacyServerImporter(repo, "test", clock) to requested
    }

    @Test
    fun imports_all_fys_skipping_empty_days() = runBlocking {
        val (importer, requested) = importer()
        val doc = importer.fetch()
        assertEquals("Me", doc.settings.displayName)
        assertEquals(listOf("2024-07-01", "2026-07-01"), doc.days.map { it.date })
        assertEquals("L1", doc.leavePeriods.single().id)
        assertEquals(1, doc.legacy!!["workTemplates"]!!.jsonArray.size)
        assertTrue("/v1/diaries/2020/days" in requested)
        assertTrue("/v1/diaries/2026/days" in requested)
        assertTrue(BackupCodec.validate(doc) is BackupParseResult.Success)
    }

    @Test(expected = Exception::class)
    fun any_failure_throws() {
        runBlocking { importer(failFy = 2023).first.fetch() }
    }
}
```

Run: `.\gradlew.bat :app:testDebugUnitTest --no-daemon --tests "dev.wfhdiary.app.backup.LegacyServerImporterTest"`
Expected: compilation failure.

- [ ] **Step 2: Implement `LegacyServerImporter.kt`**

```kotlin
package dev.wfhdiary.app.backup

import dev.wfhdiary.app.data.remote.DiaryRepository
import dev.wfhdiary.app.data.remote.WorkTemplateDto
import dev.wfhdiary.app.data.store.DiaryRules
import dev.wfhdiary.app.util.FinancialYearHelper
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import java.time.Clock
import java.time.LocalDate
import java.time.OffsetDateTime

/** Phase-1-only bridge from the old API. Fetches everything first; callers restore only on full success. */
class LegacyServerImporter(
    private val remote: DiaryRepository,
    private val appVersion: String,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val firstFyYear: Int = FIRST_FY_YEAR,
) {
    private val json = Json { encodeDefaults = true }

    suspend fun fetch(): BackupDocument {
        val settings = remote.getSettings()
        val leave = remote.getLeavePeriods()
        val templates = remote.getWorkTemplates()
        val currentFy = FinancialYearHelper.currentFyYear(LocalDate.now(clock))
        val days =
            (firstFyYear..currentFy)
                .flatMap { fy -> remote.getDays(fy, null, null) }
                .map { BackupDay(it.date, it.startTime, it.finishTime, it.breakHours, it.description, it.location) }
                .filterNot(DiaryRules::isEmpty)
                .distinctBy { it.date }
                .sortedBy { it.date }
        return BackupDocument(
            exportedAt = OffsetDateTime.now(clock).toString(),
            appVersion = appVersion,
            settings = settings,
            days = days,
            leavePeriods = leave.map { BackupLeavePeriod(it.id, it.startDate, it.endDate, it.note) },
            legacy =
                buildJsonObject {
                    put("workTemplates", json.encodeToJsonElement(ListSerializer(WorkTemplateDto.serializer()), templates))
                },
        )
    }

    companion object {
        const val FIRST_FY_YEAR = 2020
    }
}
```

- [ ] **Step 3: Wire it up and make HTTP errors throw**

In `WfhDiaryApplication.kt`:
- Inside `HttpClient(Android) { … }`, add `expectSuccess = true` as the first line. Only the importer uses this client now, and a non-2xx response must fail the import instead of being decoded.
- Add:

```kotlin
    val legacyServerImporter: LegacyServerImporter by lazy {
        LegacyServerImporter(remoteDiaryRepository, BuildConfig.VERSION_NAME)
    }
```

- [ ] **Step 4: Run tests and build**

Run: `.\gradlew.bat :app:testDebugUnitTest --no-daemon`, then `.\gradlew.bat :app:assembleDebug --no-daemon`
Expected: both BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```powershell
$env:SKIP_VERSION_BUMP = "1"
git add apps/android/app/src/main apps/android/app/src/test/java/dev/wfhdiary/app/backup/LegacyServerImporterTest.kt
git commit -m "feat(android): one-time import from the old server"
```

---

### Task 10: Backup UI (settings section, warning banner)

**Requirements:** BAK-002, BAK-006, BAK-007, BAK-009 (confirmation), BAK-011 (button).

**Files:**
- Create: `app/src/main/java/dev/wfhdiary/app/ui/backup/BackupUiState.kt`
- Create: `app/src/main/java/dev/wfhdiary/app/ui/backup/BackupViewModel.kt`
- Create: `app/src/main/java/dev/wfhdiary/app/ui/backup/BackupSection.kt`
- Modify: `app/src/main/java/dev/wfhdiary/app/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/dev/wfhdiary/app/ui/month/MonthGridScreen.kt`
- Modify: `app/src/main/java/dev/wfhdiary/app/MainActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`

Compose UI isn't unit-tested in this repo. The logic is covered by Tasks 1–9 and `BackupHealthTest`; this task is verified by building and by the manual checklist in Task 11.

- [ ] **Step 1: Add strings**

Add to `res/values/strings.xml`, after the existing `backup_share` entry:

```xml
    <string name="backup_section_title">Backup &amp; restore</string>
    <string name="backup_folder_none">No backup folder chosen. Automatic backups are off.</string>
    <string name="backup_folder_set">Backup folder: %1$s</string>
    <string name="backup_folder_choose">Choose backup folder</string>
    <string name="backup_folder_change">Change folder</string>
    <string name="backup_last_success">Last backup: %1$s</string>
    <string name="backup_last_never">No backup yet</string>
    <string name="backup_last_error">Last backup failed: %1$s</string>
    <string name="backup_now">Back up now</string>
    <string name="backup_done">Backed up to %1$s</string>
    <string name="backup_failed">Backup failed: %1$s</string>
    <string name="backup_no_folder">Choose a backup folder first.</string>
    <string name="restore_from_file">Restore from file…</string>
    <string name="restore_unreadable">Could not read that file.</string>
    <string name="restore_confirm_title">Replace all data?</string>
    <string name="restore_confirm_body">This backup (%1$s) has %2$d days and %3$d leave periods. Everything currently in the app will be replaced. A safety backup of the current data is saved first.</string>
    <string name="restore_confirm_action">Replace</string>
    <string name="restore_done">Restored %1$d days and %2$d leave periods.</string>
    <string name="import_from_server">Import from old server</string>
    <string name="import_confirm_title">Import from the old server?</string>
    <string name="import_confirm_body">This downloads everything from the old server and then replaces the data on this phone. You must be signed in.</string>
    <string name="import_confirm_action">Import</string>
    <string name="import_failed">Import failed, nothing was changed: %1$s</string>
    <string name="common_cancel">Cancel</string>
    <string name="backup_warning_no_folder">Backups are off. Tap to choose a backup folder.</string>
    <string name="backup_warning_failed">The last backup failed. Tap to check.</string>
    <string name="backup_warning_stale">No backup in the last 3 days. Tap to check.</string>
```

If `common_cancel` (or an equivalent "Cancel" string) already exists, reuse it and don't add a duplicate.

- [ ] **Step 2: Create the state and view model**

`BackupUiState.kt`:

```kotlin
package dev.wfhdiary.app.ui.backup

import dev.wfhdiary.app.backup.BackupDocument
import dev.wfhdiary.app.backup.BackupWarning

enum class RestoreSource { FILE, SERVER }

data class PendingRestore(
    val document: BackupDocument,
    val source: RestoreSource,
)

data class BackupUiState(
    val folderChosen: Boolean = false,
    val folderLabel: String? = null,
    val lastSuccessMillis: Long? = null,
    val lastError: String? = null,
    val warning: BackupWarning? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val pendingRestore: PendingRestore? = null,
    /** Incremented after each successful restore so screens can reload. */
    val restoreGeneration: Int = 0,
)
```

`BackupViewModel.kt`:

```kotlin
package dev.wfhdiary.app.ui.backup

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.wfhdiary.app.R
import dev.wfhdiary.app.WfhDiaryApplication
import dev.wfhdiary.app.backup.BackupCodec
import dev.wfhdiary.app.backup.BackupHealth
import dev.wfhdiary.app.backup.BackupOutcome
import dev.wfhdiary.app.backup.BackupParseResult
import dev.wfhdiary.app.backup.BackupShareHelper
import dev.wfhdiary.app.backup.RestoreOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class BackupViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as WfhDiaryApplication
    private val prefs = app.backupPreferences

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.update {
            it.copy(
                folderChosen = prefs.folderUri != null,
                folderLabel = prefs.folderLabel,
                lastSuccessMillis = prefs.lastSuccessMillis,
                lastError = prefs.lastError,
                warning = BackupHealth.warning(prefs.folderUri, prefs.lastSuccessMillis, prefs.lastError, System.currentTimeMillis()),
            )
        }
    }

    fun onFolderChosen(uri: Uri?) {
        if (uri == null) return
        app.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        prefs.folderUri = uri.toString()
        prefs.folderLabel = DocumentFile.fromTreeUri(app, uri)?.name
        backupNow()
    }

    fun backupNow() =
        runBusy {
            when (val outcome = app.backupService.backupToFolder()) {
                is BackupOutcome.Success -> app.getString(R.string.backup_done, outcome.fileName)
                is BackupOutcome.Failed -> app.getString(R.string.backup_failed, outcome.message)
                BackupOutcome.NoFolder -> app.getString(R.string.backup_no_folder)
            }
        }

    fun share(activity: Activity) =
        runBusy {
            val file = app.backupService.writeShareFile(File(app.cacheDir, "exports"))
            BackupShareHelper.share(activity, file)
            null
        }

    fun onRestoreFileChosen(uri: Uri?) {
        if (uri == null) return
        runBusy {
            val text =
                withContext(Dispatchers.IO) {
                    app.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                } ?: return@runBusy app.getString(R.string.restore_unreadable)
            when (val result = BackupCodec.decode(text)) {
                is BackupParseResult.Failure -> result.message
                is BackupParseResult.Success -> {
                    _uiState.update { it.copy(pendingRestore = PendingRestore(result.document, RestoreSource.FILE)) }
                    null
                }
            }
        }
    }

    fun importFromServer() =
        runBusy {
            val document =
                try {
                    app.legacyServerImporter.fetch()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    return@runBusy app.getString(R.string.import_failed, e.message ?: e.javaClass.simpleName)
                }
            when (val result = BackupCodec.validate(document)) {
                is BackupParseResult.Failure -> app.getString(R.string.import_failed, result.message)
                is BackupParseResult.Success -> {
                    _uiState.update { it.copy(pendingRestore = PendingRestore(result.document, RestoreSource.SERVER)) }
                    null
                }
            }
        }

    fun cancelRestore() {
        _uiState.update { it.copy(pendingRestore = null) }
    }

    fun confirmRestore() {
        val pending = _uiState.value.pendingRestore ?: return
        _uiState.update { it.copy(pendingRestore = null) }
        runBusy {
            when (val outcome = app.restoreService.restore(pending.document)) {
                is RestoreOutcome.Failed -> outcome.message
                is RestoreOutcome.Success -> {
                    if (pending.source == RestoreSource.SERVER) app.backupService.backupToFolder()
                    _uiState.update { it.copy(restoreGeneration = it.restoreGeneration + 1) }
                    app.getString(R.string.restore_done, outcome.days, outcome.leavePeriods)
                }
            }
        }
    }

    private fun runBusy(block: suspend () -> String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, message = null) }
            val message =
                try {
                    block()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    e.message ?: e.javaClass.simpleName
                }
            _uiState.update { it.copy(busy = false, message = message) }
            refresh()
        }
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = BackupViewModel(application) as T
            }
    }
}
```

- [ ] **Step 3: Create the composable**

`BackupSection.kt`:

```kotlin
package dev.wfhdiary.app.ui.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.wfhdiary.app.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun BackupSection(
    state: BackupUiState,
    onFolderChosen: (Uri?) -> Unit,
    onBackupNow: () -> Unit,
    onShare: () -> Unit,
    onRestoreFileChosen: (Uri?) -> Unit,
    onImportFromServer: () -> Unit,
    onConfirmRestore: () -> Unit,
    onCancelRestore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree(), onFolderChosen)
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), onRestoreFileChosen)
    var confirmImport by remember { mutableStateOf(false) }
    val formatter = remember { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM) }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.backup_section_title), style = MaterialTheme.typography.titleSmall)
        Text(
            if (state.folderChosen) {
                stringResource(R.string.backup_folder_set, state.folderLabel ?: "…")
            } else {
                stringResource(R.string.backup_folder_none)
            },
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            state.lastSuccessMillis?.let {
                stringResource(
                    R.string.backup_last_success,
                    formatter.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())),
                )
            } ?: stringResource(R.string.backup_last_never),
            style = MaterialTheme.typography.bodySmall,
        )
        state.lastError?.let {
            Text(
                stringResource(R.string.backup_last_error, it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        OutlinedButton(onClick = { folderLauncher.launch(null) }, enabled = !state.busy) {
            Text(stringResource(if (state.folderChosen) R.string.backup_folder_change else R.string.backup_folder_choose))
        }
        Button(onClick = onBackupNow, enabled = !state.busy && state.folderChosen) {
            Text(stringResource(R.string.backup_now))
        }
        OutlinedButton(onClick = onShare, enabled = !state.busy) {
            Text(stringResource(R.string.backup_share))
        }
        OutlinedButton(
            onClick = { restoreLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/*")) },
            enabled = !state.busy,
        ) {
            Text(stringResource(R.string.restore_from_file))
        }
        OutlinedButton(onClick = { confirmImport = true }, enabled = !state.busy) {
            Text(stringResource(R.string.import_from_server))
        }
        state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
    }

    if (confirmImport) {
        AlertDialog(
            onDismissRequest = { confirmImport = false },
            title = { Text(stringResource(R.string.import_confirm_title)) },
            text = { Text(stringResource(R.string.import_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmImport = false
                    onImportFromServer()
                }) { Text(stringResource(R.string.import_confirm_action)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmImport = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }

    state.pendingRestore?.let { pending ->
        AlertDialog(
            onDismissRequest = onCancelRestore,
            title = { Text(stringResource(R.string.restore_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.restore_confirm_body,
                        pending.document.exportedAt,
                        pending.document.days.size,
                        pending.document.leavePeriods.size,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirmRestore) { Text(stringResource(R.string.restore_confirm_action)) }
            },
            dismissButton = {
                TextButton(onClick = onCancelRestore) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}
```

- [ ] **Step 4: Add the slot to `SettingsScreen`**

In `ui/settings/SettingsScreen.kt`:
- Add the parameter `backupSection: @Composable () -> Unit = {},` immediately before `onClose: () -> Unit,`.
- Directly before the `Text(stringResource(R.string.settings_export_section_title), …)` block (around line 330), insert:

```kotlin
        backupSection()
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
```

- [ ] **Step 5: Add the banner to `MonthGridScreen`**

In `ui/month/MonthGridScreen.kt`:
- Add the parameters `backupWarning: String? = null,` and `onBackupWarningClick: () -> Unit = {},` immediately before `modifier: Modifier = Modifier,`.
- Inside the main `Column(…) {` in the Scaffold content, as its first child (before the month navigation `Row`), insert:

```kotlin
            if (backupWarning != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .clickable(onClick = onBackupWarningClick),
                ) {
                    Text(
                        backupWarning,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
```

- Add the imports `androidx.compose.foundation.clickable` and `androidx.compose.material3.Surface` if they're not already present.

- [ ] **Step 6: Wire `MainActivity`**

In `MainActivity.kt`:
1. Add the property:

```kotlin
    private val backupViewModel: BackupViewModel by viewModels {
        BackupViewModel.factory(application)
    }
```

2. Pass `backupViewModel = backupViewModel,` to `MainAppContent(…)` and add the `backupViewModel: BackupViewModel,` parameter to `MainAppContent`.
3. In `MainAppContent`, after the other `collectAsStateWithLifecycle` lines:

```kotlin
    val backupState by backupViewModel.uiState.collectAsStateWithLifecycle()
```

4. In the existing `ON_RESUME` observer, add `backupViewModel.refresh()` next to `monthViewModel.refreshFromRemote(showProgress = false)`.
5. After the drawer `LaunchedEffect`, add:

```kotlin
    LaunchedEffect(backupState.restoreGeneration) {
        if (backupState.restoreGeneration > 0) {
            monthViewModel.refreshFromRemote(showProgress = true)
            settingsViewModel.refreshSettings()
        }
    }
```

6. In the `SettingsScreen(…)` call, add before `onClose = …`:

```kotlin
                    backupSection = {
                        BackupSection(
                            state = backupState,
                            onFolderChosen = backupViewModel::onFolderChosen,
                            onBackupNow = backupViewModel::backupNow,
                            onShare = { backupViewModel.share(activity) },
                            onRestoreFileChosen = backupViewModel::onRestoreFileChosen,
                            onImportFromServer = backupViewModel::importFromServer,
                            onConfirmRestore = backupViewModel::confirmRestore,
                            onCancelRestore = backupViewModel::cancelRestore,
                        )
                    },
```

7. In the `MonthGridScreen(…)` call, add after `onRefresh = …`:

```kotlin
                backupWarning = when (backupState.warning) {
                    BackupWarning.NO_FOLDER -> stringResource(R.string.backup_warning_no_folder)
                    BackupWarning.FAILED -> stringResource(R.string.backup_warning_failed)
                    BackupWarning.STALE -> stringResource(R.string.backup_warning_stale)
                    null -> null
                },
                onBackupWarningClick = { scope.launch { drawerState.open() } },
```

8. Add the imports `dev.wfhdiary.app.ui.backup.BackupSection`, `dev.wfhdiary.app.ui.backup.BackupViewModel`, `dev.wfhdiary.app.backup.BackupWarning`, `androidx.compose.ui.res.stringResource` and `dev.wfhdiary.app.R` (if missing).

`backupNow` and `share` return `Unit` via `runBusy`, so the method references compile as `() -> Unit`. If the compiler complains that `backupNow` has the wrong type for the method reference, wrap it in a lambda: `{ backupViewModel.backupNow() }`.

- [ ] **Step 7: Build and test**

Run: `.\gradlew.bat :app:assembleDebug --no-daemon`, then `.\gradlew.bat :app:testDebugUnitTest --no-daemon`
Expected: both BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```powershell
$env:SKIP_VERSION_BUMP = "1"
git add apps/android/app/src/main
git commit -m "feat(android): backup and restore settings section with warning banner"
```

---

### Task 11: Specs, change docs, version bump and the phase-1 gate

**Requirements:** spec-driven repo rules (`AGENTS.md`, `docs/CONSTITUTION.md`).

**Files:**
- Create: `docs/specs/backup/spec.md`
- Modify: `docs/specs/README.md`
- Modify: `docs/specs/{day-entries,summary-fy,leave,settings,export-xlsx,notifications,ota-updates,geolocation}/spec.md`
- Create: `docs/changes/2026-10-27-on-device-only/tasks.md`
- Modify: `docs/changes/2026-10-27-on-device-only/design.md` (status → `in-progress`)
- Modify: `docs/testing/ANDROID_TEST_PLAN.md`
- Modify: `version.json` and synced version files (via script)

- [ ] **Step 1: Write `docs/specs/backup/spec.md`**

Follow `docs/specs/_template.md`. Use the prefix `BAK` and copy BAK-001..BAK-011 verbatim from the "Requirement delta" section of `design.md`. Mark BAK-011 "phase 1 only". In the coverage/test section, map:
- BAK-001 → `BackupCodecTest`, `DiaryStoreTest.export_document_and_replace_all_round_trip`
- BAK-002, BAK-003, BAK-004, BAK-007 → manual checklist (Step 5)
- BAK-005 → `BackupRetentionTest`, `BackupServiceTest`
- BAK-006 → `BackupHealthTest` plus manual
- BAK-008 → `BackupCodecTest`, `RestoreServiceTest.invalid_document_is_refused_without_changes`
- BAK-009 → `RestoreServiceTest`
- BAK-010 → `BackupCodecTest.legacy_section_is_preserved`, `DiaryStoreTest`
- BAK-011 → `LegacyServerImporterTest`

Code locations: `apps/android/app/src/main/java/dev/wfhdiary/app/backup/` and `ui/backup/`.

- [ ] **Step 2: Apply the MODIFIED requirement deltas from `design.md` to the existing specs**

For each spec listed under **MODIFIED** in `design.md`, change only those requirements:
- `day-entries`, `summary-fy`, `leave`, `settings`, `export-xlsx`, plus NOTIF-005/008, OTA-009 and GEO-006.
- Update the requirement text so it describes the on-device behaviour (data in Room via `DiaryStore`, export generated by `XlsxWriter`, and so on).
- Update code-location/coverage references to the new tests.
- Add a revision-log line: `2026-10-27 — on-device-only phase 1 (docs/changes/2026-10-27-on-device-only)`.

Do **not** apply the REMOVED items. Those land in phase 2, while the API still exists. Add BAK to the index table in `docs/specs/README.md`.

- [ ] **Step 3: Write `tasks.md` and update status**

Create `docs/changes/2026-10-27-on-device-only/tasks.md` from `docs/changes/_template/tasks.md`. List Tasks 1–11 of this plan as checked items, each with its commit hash (`git log --oneline` gives them). Add an unchecked "Phase 2" section pointing at the future `plan-phase-2.md`. Change `Status:` in `design.md` to `in-progress`.

- [ ] **Step 4: Add the manual checklist to the Android test plan**

Append a "Backup & restore (on-device)" section to `docs/testing/ANDROID_TEST_PLAN.md` with the checks from Step 5, each tagged with its BAK ID.

- [ ] **Step 5: Phase-1 gate (manual, on the phone). Record the results in `tasks.md`.**

1. Install the new build over the old one, using GitHub Releases OTA or `adb install -r`. The Room v1 cache is dropped on first launch, so the grid is empty. That's expected.
2. Settings → Backup & restore → **Choose backup folder**. Pick a folder that syncs off the phone, such as a Google Drive–synced or Documents folder. A `wfh-diary-backup-YYYY-MM-DD.json` file appears immediately. (BAK-002, BAK-005)
3. **Import from old server** → confirm → confirm the replace summary. The month grid now shows your historic days for several FYs. Settings show your name and geofences. A backup file is updated. (BAK-011, BAK-009)
4. Check that the FY summary in the app matches an XLSX export from the old server for the same FY. Then export XLSX in the app and open it in Excel or Google Sheets. Layout, colours and totals should match. (XLSX-003..009)
5. Edit a day; within about 10–15 minutes the backup file's modified time changes. (BAK-003)
6. Clear a field in the edit sheet (for example, remove the description) and save. It stays cleared after reopening. (DAY-010)
7. **Share backup…** opens the share sheet with a `.json` file. (BAK-007)
8. **Restore from file…** with the backup → confirm. Data is unchanged, and a `wfh-diary-pre-restore-*.json` file exists. (BAK-009)
9. Restore a non-backup file (any other JSON): it's refused with a message. (BAK-008)
10. Morning notification and "Home"/"Office" actions still log days. (NOTIF)
11. Revoke the folder (move or delete it) and tap **Back up now**. A failure message shows and the month-grid banner appears. (BAK-006)

Phase 2 may only be planned once steps 2–4 have passed.

- [ ] **Step 6: Bump the version so the OTA updater offers the new build**

Run from repo root: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/bump-version.ps1`
Expected: `version.json` `versionCode` +1 and `versionName` patch +1, and synced files updated.

- [ ] **Step 7: Run all tests one final time**

Run (in `apps/android`): `.\gradlew.bat :app:testDebugUnitTest --no-daemon`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```powershell
git add docs version.json apps/android services/api/WfhDiary.Api/AppVersion.cs services/api/WfhDiary.Api/WfhDiary.Api.csproj
git commit -m "docs: backup spec and on-device phase-1 spec updates; bump version"
```

Before committing, check that `git status` shows no other changes under `services/`.

---

## Self-review notes

- **Spec coverage:** BAK-001..011 map to Tasks 1, 5, 7, 8, 9 and 10, plus the manual gate. Every MODIFIED requirement in `design.md` is handled by Task 11 Step 2. The REMOVED items are deferred to phase 2, as the design says.
- **Type consistency:** `BackupDay` is both the backup shape and the rules/store domain type. `DiaryDataSource` signatures match `DiaryRepository` plus `replaceDay`. `BackupService(store, prefs, appVersion, openFolder, clock)` is used identically in Tasks 7, 8 and the app wiring. `RestoreService(store, backupService, safetyFallbackDir, onRestored)` is used identically in Task 8 tests and the app.
- **Known behaviour differences from the server, accepted:**
  - `totalHours` uses the Android `TimeHelpers` (4 dp HALF_UP span) rather than .NET decimal.
  - `getDays` returns only stored days instead of every FY day; callers already handle missing days via `DayEntryHelper.needsDefault(null)`.
