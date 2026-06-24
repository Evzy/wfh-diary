package dev.wfhdiary.app.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.wfhdiary.app.WfhDiaryApplication
import dev.wfhdiary.app.data.remote.CreateLeaveRequest
import dev.wfhdiary.app.data.remote.UpsertDayEntryRequest
import dev.wfhdiary.app.util.FinancialYearHelper
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class NotificationActionWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val action = inputData.getString(KEY_ACTION) ?: return Result.failure()
        val date =
            inputData.getString(KEY_DATE)
                ?: LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val app = applicationContext as WfhDiaryApplication

        return runCatching {
            val localDate = LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE)
            val fyYear = FinancialYearHelper.currentFyYear(localDate)
            val settings = app.diaryRepository.getSettings()
            when (action) {
                NotificationChannels.ACTION_HOME ->
                    app.diaryRepository.upsertDay(
                        fyYear,
                        date,
                        UpsertDayEntryRequest(
                            startTime = settings.defaultHomeStart,
                            finishTime = settings.defaultHomeFinish,
                            breakHours = settings.defaultHomeBreakHours,
                            location = "Home",
                        ),
                    )
                NotificationChannels.ACTION_OFFICE ->
                    app.diaryRepository.upsertDay(
                        fyYear,
                        date,
                        UpsertDayEntryRequest(
                            startTime = settings.defaultOfficeStart,
                            finishTime = settings.defaultOfficeFinish,
                            breakHours = settings.defaultOfficeBreakHours,
                            location = "Office",
                        ),
                    )
                NotificationChannels.ACTION_LEAVE ->
                    app.diaryRepository.createLeave(
                        fyYear,
                        CreateLeaveRequest(startDate = date, endDate = date),
                    )
                else -> error("Unknown notification action: $action")
            }
            Result.success()
        }.getOrElse { Result.failure() }
    }

    companion object {
        const val KEY_ACTION = "action"
        const val KEY_DATE = "date"
    }
}
