package dev.wfhdiary.app.ui.settings

data class SettingsFormState(
    val displayName: String = "",
    val notificationTime: String = "08:00",
    val defaultHomeStart: String = "08:00",
    val defaultHomeFinish: String = "17:00",
    val defaultHomeBreakHours: String = "1",
    val defaultOfficeStart: String = "08:00",
    val defaultOfficeFinish: String = "17:00",
    val defaultOfficeBreakHours: String = "1",
)
