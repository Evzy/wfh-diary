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

        val settings =
            runCatching { app.diaryStore.getSettings() }
                .getOrElse {
                    showSettingsFailure(app, date, MissedNotificationDefault.HOME)
                    return Result.failure()
                }

        val default = settings.missedNotificationDefault
        if (!MissedNotificationDefault.isEnabled(default)) {
            return Result.success()
        }

        val localDate = LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE)
        val leavePeriods =
            runCatching { app.diaryStore.getLeavePeriods() }
                .getOrElse {
                    showSettingsFailure(app, date, default)
                    return Result.failure()
                }
        if (LeavePeriodHelper.isOnLeave(localDate, leavePeriods)) {
            return Result.success()
        }

        val fyYear = FinancialYearHelper.currentFyYear(localDate)
        val entry =
            runCatching { app.diaryStore.getDays(fyYear, date, date).firstOrNull() }
                .getOrElse {
                    showSettingsFailure(app, date, default)
                    return Result.failure()
                }
        if (!DayEntryHelper.needsDefault(entry)) {
            return Result.success()
        }

        val applyAt = settings.missedNotificationApplyAt
        return runCatching {
            if (MissedNotificationDefault.isGeolocation(default)) {
                applyGeolocationFallback(app, date, settings, applyAt)
            } else {
                NotificationDayActions.apply(app, date, default, settings)
                DayLogNotificationUpdater.showAutoSuccessDefault(app, default, applyAt)
            }
            Result.success()
        }.getOrElse {
            DayLogNotificationUpdater.showFailure(
                context = app,
                action = default,
                date = date,
                target = DayLogNotificationTarget.DAY_LOG_RESULT,
            )
            Result.failure()
        }
    }

    private suspend fun applyGeolocationFallback(
        app: WfhDiaryApplication,
        date: String,
        settings: dev.wfhdiary.app.data.model.UserSettings,
        applyAt: String,
    ) {
        val place = GeolocationNotificationHelper.matchingPlace(app, settings)
        if (place != null) {
            GeolocationNotificationHelper.applyPlace(app, date, settings, place)
            DayLogNotificationUpdater.showAutoSuccessGeolocationMatch(app, place, applyAt)
        } else {
            val away = settings.missedNotificationAwayDefault
            GeolocationNotificationHelper.applyAwayFallback(app, date, settings)
            DayLogNotificationUpdater.showAutoSuccessGeolocationAway(app, away, applyAt)
        }
    }

    private fun showSettingsFailure(
        app: WfhDiaryApplication,
        date: String,
        action: String,
    ) {
        if (!NotificationPermissions.canPost(app)) return
        DayLogNotificationUpdater.showFailure(
            context = app,
            action = action,
            date = date,
            target = DayLogNotificationTarget.DAY_LOG_RESULT,
        )
    }

    companion object {
        const val KEY_DATE = "date"
    }
}
