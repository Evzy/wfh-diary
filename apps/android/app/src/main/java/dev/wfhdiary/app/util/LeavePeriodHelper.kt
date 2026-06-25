package dev.wfhdiary.app.util

import dev.wfhdiary.app.data.remote.LeavePeriodDto
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object LeavePeriodHelper {
    private val iso = DateTimeFormatter.ISO_LOCAL_DATE

    fun isOnLeave(date: LocalDate, periods: List<LeavePeriodDto>): Boolean =
        periods.any { period -> isOnLeave(date, period) }

    fun isOnLeave(date: LocalDate, period: LeavePeriodDto): Boolean {
        val start = LocalDate.parse(period.startDate, iso)
        val end = LocalDate.parse(period.endDate, iso)
        return !date.isBefore(start) && !date.isAfter(end)
    }

    fun coveringPeriod(date: LocalDate, periods: List<LeavePeriodDto>): LeavePeriodDto? =
        periods.firstOrNull { isOnLeave(date, it) }
}
