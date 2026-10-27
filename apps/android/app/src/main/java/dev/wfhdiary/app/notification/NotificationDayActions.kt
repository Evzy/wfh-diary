package dev.wfhdiary.app.notification

import dev.wfhdiary.app.WfhDiaryApplication
import dev.wfhdiary.app.data.model.DayEntryUpdate
import dev.wfhdiary.app.data.model.NewLeave
import dev.wfhdiary.app.data.model.UserSettings
import dev.wfhdiary.app.util.FinancialYearHelper
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object NotificationDayActions {
    private val iso = DateTimeFormatter.ISO_LOCAL_DATE

    suspend fun apply(
        app: WfhDiaryApplication,
        date: String,
        action: String,
        settings: UserSettings,
    ) {
        val localDate = LocalDate.parse(date, iso)
        val fyYear = FinancialYearHelper.currentFyYear(localDate)
        when (action) {
            NotificationChannels.ACTION_HOME, MissedNotificationDefault.HOME ->
                app.diaryStore.upsertDay(
                    fyYear,
                    date,
                    DayEntryUpdate(
                        startTime = settings.defaultHomeStart,
                        finishTime = settings.defaultHomeFinish,
                        breakHours = settings.defaultHomeBreakHours,
                        description = settings.defaultHomeDescription,
                        location = "Home",
                    ),
                )
            NotificationChannels.ACTION_OFFICE, MissedNotificationDefault.OFFICE ->
                app.diaryStore.upsertDay(
                    fyYear,
                    date,
                    DayEntryUpdate(
                        startTime = settings.defaultOfficeStart,
                        finishTime = settings.defaultOfficeFinish,
                        breakHours = settings.defaultOfficeBreakHours,
                        description = settings.defaultOfficeDescription,
                        location = "Office",
                    ),
                )
            NotificationChannels.ACTION_LEAVE, MissedNotificationDefault.LEAVE ->
                app.diaryStore.createLeave(
                    fyYear,
                    NewLeave(startDate = date, endDate = date),
                )
            else -> error("Unknown notification action: $action")
        }
    }
}
