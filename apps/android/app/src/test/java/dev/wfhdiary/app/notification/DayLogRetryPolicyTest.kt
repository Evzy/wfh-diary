package dev.wfhdiary.app.notification

import dev.wfhdiary.app.util.GeofenceHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DayLogRetryPolicyTest {
    @Test
    fun failureRetryForMissedDefault_geolocation_uses_missed_worker() {
        val retry = DayLogRetryPolicy.failureRetryForMissedDefault(MissedNotificationDefault.GEOLOCATION)
        assertEquals(MissedNotificationDefault.GEOLOCATION, retry.action)
        assertEquals(NotificationChannels.RETRY_MODE_MISSED, retry.retryMode)
    }

    @Test
    fun failureRetryForMissedDefault_home_uses_apply_action() {
        val retry = DayLogRetryPolicy.failureRetryForMissedDefault(MissedNotificationDefault.HOME)
        assertEquals(MissedNotificationDefault.HOME, retry.action)
        assertEquals(NotificationChannels.RETRY_MODE_APPLY, retry.retryMode)
    }

    @Test
    fun failureRetryForMissedDefault_office_uses_apply_action() {
        val retry = DayLogRetryPolicy.failureRetryForMissedDefault(MissedNotificationDefault.OFFICE)
        assertEquals(MissedNotificationDefault.OFFICE, retry.action)
        assertEquals(NotificationChannels.RETRY_MODE_APPLY, retry.retryMode)
    }

    @Test
    fun notificationTargetFromKey_morning() {
        assertEquals(
            DayLogNotificationTarget.MORNING_REMINDER,
            DayLogRetryPolicy.notificationTargetFromKey(NotificationChannels.TARGET_MORNING),
        )
    }

    @Test
    fun notificationTargetFromKey_day_log_or_null() {
        assertEquals(
            DayLogNotificationTarget.DAY_LOG_RESULT,
            DayLogRetryPolicy.notificationTargetFromKey(NotificationChannels.TARGET_DAY_LOG),
        )
        assertEquals(
            DayLogNotificationTarget.DAY_LOG_RESULT,
            DayLogRetryPolicy.notificationTargetFromKey(null),
        )
    }

    @Test
    fun notificationTargetKey_round_trips() {
        assertEquals(
            NotificationChannels.TARGET_MORNING,
            DayLogRetryPolicy.notificationTargetKey(DayLogNotificationTarget.MORNING_REMINDER),
        )
        assertEquals(
            NotificationChannels.TARGET_DAY_LOG,
            DayLogRetryPolicy.notificationTargetKey(DayLogNotificationTarget.DAY_LOG_RESULT),
        )
    }

    @Test
    fun notificationTargetForWorker_manual_defaults_to_morning() {
        assertEquals(
            DayLogNotificationTarget.MORNING_REMINDER,
            DayLogRetryPolicy.notificationTargetForWorker(
                storedTarget = null,
                source = NotificationActionWorker.SOURCE_MANUAL,
            ),
        )
    }

    @Test
    fun notificationTargetForWorker_retry_defaults_to_morning() {
        assertEquals(
            DayLogNotificationTarget.MORNING_REMINDER,
            DayLogRetryPolicy.notificationTargetForWorker(
                storedTarget = null,
                source = NotificationActionWorker.SOURCE_RETRY,
            ),
        )
    }

    @Test
    fun notificationTargetForWorker_auto_apply_defaults_to_day_log() {
        assertEquals(
            DayLogNotificationTarget.DAY_LOG_RESULT,
            DayLogRetryPolicy.notificationTargetForWorker(
                storedTarget = null,
                source = NotificationActionWorker.SOURCE_AUTO_APPLY,
            ),
        )
    }

    @Test
    fun notificationTargetForWorker_stored_target_overrides_source() {
        assertEquals(
            DayLogNotificationTarget.DAY_LOG_RESULT,
            DayLogRetryPolicy.notificationTargetForWorker(
                storedTarget = NotificationChannels.TARGET_DAY_LOG,
                source = NotificationActionWorker.SOURCE_MANUAL,
            ),
        )
    }

    @Test
    fun usesMissedWorkerRetry_only_for_missed_mode() {
        assertTrue(DayLogRetryPolicy.usesMissedWorkerRetry(NotificationChannels.RETRY_MODE_MISSED))
        assertFalse(DayLogRetryPolicy.usesMissedWorkerRetry(NotificationChannels.RETRY_MODE_APPLY))
    }
}

class DayLogNotificationUpdaterActionTest {
    @Test
    fun actionForPlace_maps_home_and_office() {
        assertEquals(
            MissedNotificationDefault.HOME,
            DayLogNotificationUpdater.actionForPlace(GeofenceHelper.Place.HOME),
        )
        assertEquals(
            MissedNotificationDefault.OFFICE,
            DayLogNotificationUpdater.actionForPlace(GeofenceHelper.Place.OFFICE),
        )
    }
}
