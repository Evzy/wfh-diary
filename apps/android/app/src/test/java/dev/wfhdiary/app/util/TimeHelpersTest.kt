package dev.wfhdiary.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class TimeHelpersTest {
    @Test
    fun parseTimeString_accepts_hh_mm() {
        assertEquals(8 * 60, TimeHelpers.parseTimeString("08:00"))
        assertEquals(8 * 60, TimeHelpers.parseTimeString("8:00"))
    }

    @Test
    fun parseTimeString_null_for_blank() {
        assertNull(TimeHelpers.parseTimeString(null))
        assertNull(TimeHelpers.parseTimeString(""))
    }

    @Test
    fun computeTotalHours_subtracts_break() {
        val total = TimeHelpers.computeTotalHours(8 * 60, 17 * 60, BigDecimal.ONE)
        assertEquals(BigDecimal("8.0000"), total)
    }
}
