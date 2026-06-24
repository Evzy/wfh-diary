package dev.wfhdiary.app.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class FinancialYearHelperTest {
    @Test
    fun currentFyYear_june_uses_prior_calendar_year() {
        assertEquals(2024, FinancialYearHelper.currentFyYear(LocalDate.of(2025, 6, 30)))
    }

    @Test
    fun currentFyYear_july_uses_current_calendar_year() {
        assertEquals(2025, FinancialYearHelper.currentFyYear(LocalDate.of(2025, 7, 1)))
    }

    @Test
    fun fyRange_spans_july_to_june() {
        val (start, end) = FinancialYearHelper.fyRange(2025)
        assertEquals(LocalDate.of(2025, 7, 1), start)
        assertEquals(LocalDate.of(2026, 6, 30), end)
    }
}
