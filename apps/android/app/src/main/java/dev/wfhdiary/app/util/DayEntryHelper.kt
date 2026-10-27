package dev.wfhdiary.app.util

import dev.wfhdiary.app.data.model.DayEntryDto

object DayEntryHelper {
    fun needsDefault(entry: DayEntryDto?): Boolean {
        if (entry == null) return true
        if (!entry.location.isNullOrBlank()) return false
        if (!entry.startTime.isNullOrBlank()) return false
        if (entry.description.equals("LEAVE", ignoreCase = true)) return false
        return true
    }
}
