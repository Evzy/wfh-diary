package dev.wfhdiary.app.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.wfhdiary.app.WfhDiaryApplication
import dev.wfhdiary.app.util.DayEntryHelper
import dev.wfhdiary.app.util.FinancialYearHelper
import dev.wfhdiary.app.util.LeavePeriodHelper
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class MissedNotificationWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val date =
            inputData.getString(KEY_DATE)
                ?: LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val app = applicationContext as WfhDiaryApplication

        return runCatching {
            val settings = app.diaryRepository.getSettings()
            val default = settings.missedNotificationDefault
            if (!MissedNotificationDefault.isEnabled(default)) {
                return Result.success()
            }

            val localDate = LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE)
            val leavePeriods = app.diaryRepository.getLeavePeriods()
            if (LeavePeriodHelper.isOnLeave(localDate, leavePeriods)) {
                return Result.success()
            }

            val fyYear = FinancialYearHelper.currentFyYear(localDate)
            val entry = app.diaryRepository.getDays(fyYear, date, date).firstOrNull()
            if (!DayEntryHelper.needsDefault(entry)) {
                return Result.success()
            }

            NotificationDayActions.apply(app, date, default, settings)
            Result.success()
        }.getOrElse { Result.failure() }
    }

    companion object {
        const val KEY_DATE = "date"
    }
}
