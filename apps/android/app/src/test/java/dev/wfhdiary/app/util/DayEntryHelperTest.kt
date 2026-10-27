package dev.wfhdiary.app.util

import dev.wfhdiary.app.data.model.DayEntryDto
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DayEntryHelperTest {
    @Test
    fun needsDefault_true_when_entry_null() {
        assertTrue(DayEntryHelper.needsDefault(null))
    }

    @Test
    fun needsDefault_false_when_location_set() {
        val entry = DayEntryDto(date = "2025-07-01", dayName = "Tuesday", location = "Home")
        assertFalse(DayEntryHelper.needsDefault(entry))
    }

    @Test
    fun needsDefault_false_when_leave_description() {
        val entry = DayEntryDto(date = "2025-07-01", dayName = "Tuesday", description = "LEAVE")
        assertFalse(DayEntryHelper.needsDefault(entry))
    }

    @Test
    fun needsDefault_true_when_empty_day() {
        val entry = DayEntryDto(date = "2025-07-01", dayName = "Tuesday")
        assertTrue(DayEntryHelper.needsDefault(entry))
    }
}
