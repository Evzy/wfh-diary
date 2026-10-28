package dev.wfhdiary.app.notification

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dev.wfhdiary.app.R
import dev.wfhdiary.app.WfhDiaryApplication
import dev.wfhdiary.app.data.model.UserSettings
import dev.wfhdiary.app.util.DayEntryHelper
import dev.wfhdiary.app.util.FinancialYearHelper
import dev.wfhdiary.app.util.WorkingDaysMask
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException

class MorningReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as WfhDiaryApplication
        val today = LocalDate.now()
        val iso = DateTimeFormatter.ISO_LOCAL_DATE
        val todayStr = today.format(iso)
        val catchUp = inputData.getBoolean(KEY_CATCH_UP, false)

        val settings =
            runCatching { app.diaryStore.getSettings() }
                .getOrNull()

        val notificationTime = settings?.notificationTime ?: "08:00"
        val workingDaysMask = settings?.notificationWorkingDaysMask ?: WorkingDaysMask.DEFAULT

        try {
            run(app, settings, today, todayStr, catchUp)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Recorded as run anyway so a failing day isn't retried on every app start.
        }
        ReminderRunState(applicationContext).lastRunDate = todayStr
        NotificationScheduler.scheduleNext(
            applicationContext,
            notificationTime,
            workingDaysMask,
            allowCatchUp = false,
        )
        return Result.success()
    }

    private suspend fun run(
        app: WfhDiaryApplication,
        settings: UserSettings?,
        today: LocalDate,
        todayStr: String,
        catchUp: Boolean,
    ) {
        val iso = DateTimeFormatter.ISO_LOCAL_DATE
        val workingDaysMask = settings?.notificationWorkingDaysMask ?: WorkingDaysMask.DEFAULT
        if (!WorkingDaysMask.isWorkingDay(workingDaysMask, today)) return

        val onLeave =
            runCatching {
                app.diaryStore.getLeavePeriods().any { period ->
                    val start = LocalDate.parse(period.startDate, iso)
                    val end = LocalDate.parse(period.endDate, iso)
                    !today.isBefore(start) && !today.isAfter(end)
                }
            }.getOrDefault(false)
        if (onLeave) return

        val geolocation =
            settings != null && MissedNotificationDefault.isGeolocation(settings.missedNotificationDefault)
        val entryNeeded =
            if (catchUp || geolocation) {
                val fyYear = FinancialYearHelper.currentFyYear(today)
                val entry =
                    runCatching { app.diaryStore.getDays(fyYear, todayStr, todayStr).firstOrNull() }
                        .getOrNull()
                DayEntryHelper.needsDefault(entry)
            } else {
                true
            }

        if (catchUp && !entryNeeded) return

        if (settings != null && geolocation && entryNeeded) {
            val place = GeolocationNotificationHelper.matchingPlace(app, settings)
            if (place != null &&
                GeolocationNotificationHelper.shouldSkipNotification(settings, place)
            ) {
                val applyResult =
                    runCatching {
                        GeolocationNotificationHelper.applyPlace(app, todayStr, settings, place)
                    }
                if (applyResult.isSuccess) {
                    if (NotificationPermissions.canPost(applicationContext)) {
                        DayLogNotificationUpdater.showAutoSuccessMorningGeo(applicationContext, place)
                    }
                } else {
                    if (NotificationPermissions.canPost(applicationContext)) {
                        DayLogNotificationUpdater.showFailure(
                            context = applicationContext,
                            action = DayLogNotificationUpdater.actionForPlace(place),
                            date = todayStr,
                            target = DayLogNotificationTarget.DAY_LOG_RESULT,
                        )
                    }
                }
                return
            }
        }

        if (NotificationPermissions.canPost(applicationContext)) {
            MorningReminderNotifier.show(applicationContext, todayStr)
            settings?.let { s ->
                MissedNotificationScheduler.schedule(
                    applicationContext,
                    todayStr,
                    s.missedNotificationApplyAt,
                    s.missedNotificationDefault,
                )
            }
        }
    }

    /** Only used before Android 12, where expedited work runs as a foreground service. */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification =
            NotificationCompat.Builder(applicationContext, NotificationChannels.MORNING_REMINDER)
                .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
                .setContentTitle(applicationContext.getString(R.string.notification_morning_title))
                .setContentText(applicationContext.getString(R.string.notification_reminder_checking))
                .setSilent(true)
                .build()
        return ForegroundInfo(FOREGROUND_NOTIFICATION_ID, notification)
    }

    companion object {
        const val KEY_CATCH_UP = "catch_up"
        private const val FOREGROUND_NOTIFICATION_ID = 1003
    }
}
