package dev.wfhdiary.app.util

import dev.wfhdiary.app.data.remote.LeavePeriodDto
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LeavePeriodHelperTest {
    @Test
    fun isOnLeave_true_for_date_in_range() {
        val period = LeavePeriodDto("id", "2025-07-01", "2025-07-05")
        assertTrue(LeavePeriodHelper.isOnLeave(LocalDate.of(2025, 7, 3), period))
    }

    @Test
    fun isOnLeave_false_outside_range() {
        val period = LeavePeriodDto("id", "2025-07-01", "2025-07-05")
        assertFalse(LeavePeriodHelper.isOnLeave(LocalDate.of(2025, 7, 6), period))
    }

    @Test
    fun isOnLeave_includes_start_and_end_dates() {
        val period = LeavePeriodDto("id", "2025-07-01", "2025-07-01")
        assertTrue(LeavePeriodHelper.isOnLeave(LocalDate.of(2025, 7, 1), listOf(period)))
    }
}
