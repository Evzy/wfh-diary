package dev.wfhdiary.app.util

import java.time.LocalDate

object FinancialYearHelper {
    fun currentFyYear(today: LocalDate): Int =
        if (today.monthValue >= 7) today.year else today.year - 1

    fun fyRange(fyYear: Int): Pair<LocalDate, LocalDate> =
        LocalDate.of(fyYear, 7, 1) to LocalDate.of(fyYear + 1, 6, 30)

    fun eachDay(start: LocalDate, end: LocalDate): Sequence<LocalDate> =
        generateSequence(start) { d -> if (d < end) d.plusDays(1) else null }

    fun fyYearsCovering(yearMonth: java.time.YearMonth): List<Int> =
        listOf(currentFyYear(yearMonth.atDay(1)), currentFyYear(yearMonth.atEndOfMonth())).distinct()
}
