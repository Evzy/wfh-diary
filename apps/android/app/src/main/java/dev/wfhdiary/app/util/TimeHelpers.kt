package dev.wfhdiary.app.util

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.math.BigDecimal
import java.math.RoundingMode

object TimeHelpers {
    private val hm = DateTimeFormatter.ofPattern("HH:mm")

    fun minutesToTimeString(minutes: Int): String =
        LocalTime.of(minutes / 60, minutes % 60).format(hm)

    fun parseTimeString(value: String?): Int? {
        if (value.isNullOrBlank()) return null
        val trimmed = value.trim()
        val patterns = listOf(DateTimeFormatter.ofPattern("H:mm"), hm)
        for (fmt in patterns) {
            try {
                val t = LocalTime.parse(trimmed, fmt)
                return t.hour * 60 + t.minute
            } catch (_: DateTimeParseException) {
            }
        }
        return null
    }

    fun computeTotalHours(startMinutes: Int?, finishMinutes: Int?, breakHours: BigDecimal?): BigDecimal {
        if (startMinutes == null || finishMinutes == null) return BigDecimal.ZERO
        val spanHours =
            BigDecimal(finishMinutes - startMinutes)
                .divide(BigDecimal(60), 4, RoundingMode.HALF_UP)
        val breaks = breakHours ?: BigDecimal.ZERO
        val total = spanHours.subtract(breaks)
        return if (total.signum() < 0) BigDecimal.ZERO else total
    }
}
