package dev.wfhdiary.app.notification

import dev.wfhdiary.app.util.WorkingDaysMask
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationSchedulerTest {
    // 2026-10-28 is a Wednesday; 2026-10-30 a Friday.
    private val wedMorning = LocalDateTime.of(2026, 10, 28, 9, 0)
    private val wedNoon = LocalDateTime.of(2026, 10, 28, 12, 0)
    private val friEvening = LocalDateTime.of(2026, 10, 30, 18, 0)

    @Test
    fun nextReminderAt_later_today_when_time_not_passed() {
        assertEquals(
            LocalDateTime.of(2026, 10, 28, 10, 0),
            NotificationScheduler.nextReminderAt("10:00", WorkingDaysMask.DEFAULT, wedMorning),
        )
    }

    @Test
    fun nextReminderAt_tomorrow_when_time_passed() {
        assertEquals(
            LocalDateTime.of(2026, 10, 29, 10, 0),
            NotificationScheduler.nextReminderAt("10:00", WorkingDaysMask.DEFAULT, wedNoon),
        )
    }

    @Test
    fun nextReminderAt_skips_weekend() {
        assertEquals(
            LocalDateTime.of(2026, 11, 2, 10, 0),
            NotificationScheduler.nextReminderAt("10:00", WorkingDaysMask.DEFAULT, friEvening),
        )
    }

    @Test
    fun nextReminderAt_unparseable_time_uses_eight() {
        assertEquals(
            LocalDateTime.of(2026, 10, 29, 8, 0),
            NotificationScheduler.nextReminderAt("nope", WorkingDaysMask.DEFAULT, wedNoon),
        )
    }

    @Test
    fun catchUpDue_true_after_time_on_working_day_not_run() {
        assertTrue(NotificationScheduler.catchUpDue("10:00", WorkingDaysMask.DEFAULT, null, wedNoon))
        assertTrue(NotificationScheduler.catchUpDue("10:00", WorkingDaysMask.DEFAULT, "2026-10-27", wedNoon))
    }

    @Test
    fun catchUpDue_true_exactly_at_time() {
        val atTen = LocalDateTime.of(2026, 10, 28, 10, 0)
        assertTrue(NotificationScheduler.catchUpDue("10:00", WorkingDaysMask.DEFAULT, null, atTen))
    }

    @Test
    fun catchUpDue_false_before_time() {
        assertFalse(NotificationScheduler.catchUpDue("10:00", WorkingDaysMask.DEFAULT, null, wedMorning))
    }

    @Test
    fun catchUpDue_false_when_already_run_today() {
        assertFalse(NotificationScheduler.catchUpDue("10:00", WorkingDaysMask.DEFAULT, "2026-10-28", wedNoon))
    }

    @Test
    fun catchUpDue_false_on_non_working_day() {
        val saturday = LocalDateTime.of(2026, 10, 31, 12, 0)
        assertFalse(NotificationScheduler.catchUpDue("10:00", WorkingDaysMask.DEFAULT, null, saturday))
    }
}
