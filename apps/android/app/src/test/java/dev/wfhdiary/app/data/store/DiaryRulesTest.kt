package dev.wfhdiary.app.data.store

import dev.wfhdiary.app.backup.BackupDay
import dev.wfhdiary.app.data.model.UpdateUserSettingsRequest
import dev.wfhdiary.app.data.model.UpsertDayEntryRequest
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
