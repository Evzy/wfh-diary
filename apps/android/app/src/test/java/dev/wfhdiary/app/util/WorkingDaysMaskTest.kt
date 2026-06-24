package dev.wfhdiary.app.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class WorkingDaysMaskTest {
    @Test
    fun default_mask_is_monday_to_friday() {
        val mask = WorkingDaysMask.DEFAULT
        assertTrue(WorkingDaysMask.isWorkingDay(mask, LocalDate.of(2025, 7, 7))) // Monday
        assertTrue(WorkingDaysMask.isWorkingDay(mask, LocalDate.of(2025, 7, 11))) // Friday
        assertFalse(WorkingDaysMask.isWorkingDay(mask, LocalDate.of(2025, 7, 12))) // Saturday
        assertFalse(WorkingDaysMask.isWorkingDay(mask, LocalDate.of(2025, 7, 13))) // Sunday
    }

    @Test
    fun setDay_toggles_single_day() {
        var mask = WorkingDaysMask.DEFAULT
        mask = WorkingDaysMask.setDay(mask, DayOfWeek.SATURDAY, enabled = true)
        assertTrue(WorkingDaysMask.isWorkingDay(mask, DayOfWeek.SATURDAY))
        mask = WorkingDaysMask.setDay(mask, DayOfWeek.MONDAY, enabled = false)
        assertFalse(WorkingDaysMask.isWorkingDay(mask, DayOfWeek.MONDAY))
    }
}
