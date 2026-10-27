package dev.wfhdiary.app.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

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

    @Test
    fun fyYearsCovering_june_and_july() {
        assertEquals(listOf(2024), FinancialYearHelper.fyYearsCovering(YearMonth.of(2025, 6)))
        assertEquals(listOf(2025), FinancialYearHelper.fyYearsCovering(YearMonth.of(2025, 7)))
    }

    @Test
    fun fy_day_iteration_includes_both_endpoints() {
        val days = FinancialYearHelper.eachDay(LocalDate.of(2025, 7, 1), LocalDate.of(2025, 7, 3)).toList()
        assertEquals(3, days.size)
        assertEquals(LocalDate.of(2025, 7, 3), days.last())

        val (start, end) = FinancialYearHelper.fyRange(2025)
        val fyDays = FinancialYearHelper.eachDay(start, end).toList()
        assertEquals(365, fyDays.size)
        assertEquals(LocalDate.of(2026, 6, 30), fyDays.last())
    }
}
