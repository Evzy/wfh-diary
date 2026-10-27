package dev.wfhdiary.app.util

import dev.wfhdiary.app.data.model.DayEntry

object DayEntryHelper {
    fun needsDefault(entry: DayEntry?): Boolean {
        if (entry == null) return true
        if (!entry.location.isNullOrBlank()) return false
        if (!entry.startTime.isNullOrBlank()) return false
        if (entry.description.equals("LEAVE", ignoreCase = true)) return false
        return true
    }
}
