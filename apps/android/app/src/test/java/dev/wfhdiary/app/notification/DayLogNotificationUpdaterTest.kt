package dev.wfhdiary.app.notification

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DayLogNotificationUpdaterTest {
    private lateinit var context: Context
    private lateinit var manager: NotificationManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        NotificationChannels.create(context)
        manager = context.getSystemService(NotificationManager::class.java)
    }

    @Test
    fun showFailure_posts_open_app_only_in_reminder_slot() {
        DayLogNotificationUpdater.showFailure(
            context = context,
            action = NotificationChannels.ACTION_HOME,
            date = "2026-10-28",
            target = DayLogNotificationTarget.MORNING_REMINDER,
        )

        val posted = shadowOf(manager).getNotification(DayLogNotificationUpdater.MORNING_REMINDER_ID)
        assertEquals(1, posted.actions.size)
        assertEquals("Open app", posted.actions[0].title.toString())
        assertEquals(
            "Couldn\u2019t log Home. Open the app to log it.",
            posted.extras.getCharSequence("android.text").toString(),
        )
    }

    @Test
    fun showFailure_auto_target_uses_auto_log_slot() {
        DayLogNotificationUpdater.showFailure(
            context = context,
            action = MissedNotificationDefault.OFFICE,
            date = "2026-10-28",
            target = DayLogNotificationTarget.DAY_LOG_RESULT,
        )

        val posted = shadowOf(manager).getNotification(DayLogNotificationUpdater.DAY_LOG_RESULT_ID)
        assertEquals(1, posted.actions.size)
    }
}
