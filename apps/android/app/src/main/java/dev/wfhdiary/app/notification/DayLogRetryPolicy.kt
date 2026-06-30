package dev.wfhdiary.app.notification

data class DayLogFailureRetry(
    val action: String,
    val retryMode: String,
)

object DayLogRetryPolicy {
    fun failureRetryForMissedDefault(missedDefault: String): DayLogFailureRetry =
        if (MissedNotificationDefault.isGeolocation(missedDefault)) {
            DayLogFailureRetry(
                action = MissedNotificationDefault.GEOLOCATION,
                retryMode = NotificationChannels.RETRY_MODE_MISSED,
            )
        } else {
            DayLogFailureRetry(
                action = missedDefault,
                retryMode = NotificationChannels.RETRY_MODE_APPLY,
            )
        }

    fun notificationTargetFromKey(key: String?): DayLogNotificationTarget =
        when (key) {
            NotificationChannels.TARGET_MORNING -> DayLogNotificationTarget.MORNING_REMINDER
            else -> DayLogNotificationTarget.DAY_LOG_RESULT
        }

    fun notificationTargetKey(target: DayLogNotificationTarget): String =
        when (target) {
            DayLogNotificationTarget.MORNING_REMINDER -> NotificationChannels.TARGET_MORNING
            DayLogNotificationTarget.DAY_LOG_RESULT -> NotificationChannels.TARGET_DAY_LOG
        }

    fun notificationTargetForWorker(storedTarget: String?, source: String): DayLogNotificationTarget {
        if (storedTarget != null) {
            return notificationTargetFromKey(storedTarget)
        }
        return if (source == NotificationActionWorker.SOURCE_MANUAL ||
            source == NotificationActionWorker.SOURCE_RETRY
        ) {
            DayLogNotificationTarget.MORNING_REMINDER
        } else {
            DayLogNotificationTarget.DAY_LOG_RESULT
        }
    }

    fun usesMissedWorkerRetry(retryMode: String): Boolean =
        retryMode == NotificationChannels.RETRY_MODE_MISSED
}
