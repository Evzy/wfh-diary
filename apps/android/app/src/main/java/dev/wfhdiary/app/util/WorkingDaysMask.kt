package dev.wfhdiary.app.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

object WorkingDaysMask {
    /** Mon–Fri (ISO day 1–5). */
    const val DEFAULT = 31

    fun isWorkingDay(mask: Int, date: LocalDate): Boolean = isWorkingDay(mask, date.dayOfWeek)

    fun isWorkingDay(mask: Int, dayOfWeek: DayOfWeek): Boolean {
        val bit = 1 shl (dayOfWeek.value - 1)
        return mask and bit != 0
    }

    fun setDay(mask: Int, dayOfWeek: DayOfWeek, enabled: Boolean): Int {
        val bit = 1 shl (dayOfWeek.value - 1)
        return if (enabled) mask or bit else mask and bit.inv()
    }

    fun allDays(): List<DayOfWeek> =
        listOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
            DayOfWeek.SATURDAY,
            DayOfWeek.SUNDAY,
        )

    fun selectedDayLabels(mask: Int, style: TextStyle = TextStyle.FULL): List<String> =
        allDays()
            .filter { isWorkingDay(mask, it) }
            .map { it.getDisplayName(style, Locale.getDefault()) }
}
