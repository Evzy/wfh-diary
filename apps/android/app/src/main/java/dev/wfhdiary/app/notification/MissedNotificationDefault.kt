package dev.wfhdiary.app.notification

object MissedNotificationDefault {
    const val NONE = "None"
    const val HOME = "Home"
    const val OFFICE = "Office"
    const val LEAVE = "Leave"
    const val GEOLOCATION = "Geolocation"

    val options = listOf(NONE, HOME, OFFICE, LEAVE, GEOLOCATION)

    val awayOptions = listOf(HOME, OFFICE, LEAVE)

    fun isEnabled(value: String?): Boolean =
        !value.isNullOrBlank() && !value.equals(NONE, ignoreCase = true)

    fun isGeolocation(value: String?): Boolean =
        value.equals(GEOLOCATION, ignoreCase = true)
}
