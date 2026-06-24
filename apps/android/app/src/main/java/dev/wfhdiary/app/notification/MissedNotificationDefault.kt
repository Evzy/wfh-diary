package dev.wfhdiary.app.notification

object MissedNotificationDefault {
    const val NONE = "None"
    const val HOME = "Home"
    const val OFFICE = "Office"
    const val LEAVE = "Leave"

    val options = listOf(NONE, HOME, OFFICE, LEAVE)

    fun isEnabled(value: String?): Boolean =
        !value.isNullOrBlank() && !value.equals(NONE, ignoreCase = true)
}
