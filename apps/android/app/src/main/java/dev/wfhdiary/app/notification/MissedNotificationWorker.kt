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

            val applyAt = settings.missedNotificationApplyAt
            if (MissedNotificationDefault.isGeolocation(default)) {
                val place = GeolocationNotificationHelper.matchingPlace(app, settings)
                if (place != null) {
                    GeolocationNotificationHelper.applyPlace(app, date, settings, place)
                    notifyResult(app, geolocationMatch = place, awayDefault = null, applyAt = applyAt)
                } else {
                    val away = settings.missedNotificationAwayDefault
                    GeolocationNotificationHelper.applyAwayFallback(app, date, settings)
                    notifyResult(app, geolocationMatch = null, awayDefault = away, applyAt = applyAt)
                }
            } else {
                NotificationDayActions.apply(app, date, default, settings)
                notifyResult(app, geolocationMatch = null, awayDefault = default, applyAt = applyAt, fixedDefault = true)
            }
            Result.success()
        }.getOrElse { Result.failure() }
    }

    private fun notifyResult(
        app: WfhDiaryApplication,
        geolocationMatch: dev.wfhdiary.app.util.GeofenceHelper.Place?,
        awayDefault: String?,
        applyAt: String,
        fixedDefault: Boolean = false,
    ) {
        if (!NotificationPermissions.canPost(app)) return
        when {
            geolocationMatch != null ->
                AutoLogResultNotifier.showGeolocationMatch(app, geolocationMatch, applyAt)
            fixedDefault && awayDefault != null ->
                AutoLogResultNotifier.showDefault(app, awayDefault, applyAt)
            awayDefault != null ->
                AutoLogResultNotifier.showGeolocationAway(app, awayDefault, applyAt)
        }
    }

    companion object {
        const val KEY_DATE = "date"
    }
}
