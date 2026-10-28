package dev.wfhdiary.app.notification

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ReminderAlarmSchedulerTest {
    private lateinit var context: Context
    private lateinit var alarmManager: AlarmManager
    private val at = LocalDateTime.of(2026, 10, 29, 10, 0)
    private val atMillis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        alarmManager = context.getSystemService(AlarmManager::class.java)
    }

    @Test
    fun set_uses_exact_allow_while_idle_alarm_when_permitted() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)

        ReminderAlarmScheduler.set(context, at)

        val alarm = shadowOf(alarmManager).peekNextScheduledAlarm()
        assertNotNull(alarm)
        assertEquals(atMillis, alarm!!.triggerAtMs)
        assertEquals(AlarmManager.RTC_WAKEUP, alarm.type)
        assertTrue(alarm.isAllowWhileIdle)
        assertNotNull(alarm.operation)
    }

    @Test
    fun set_falls_back_to_inexact_when_exact_not_permitted() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)

        ReminderAlarmScheduler.set(context, at)

        val alarm = shadowOf(alarmManager).peekNextScheduledAlarm()
        assertNotNull(alarm)
        assertEquals(atMillis, alarm!!.triggerAtMs)
        assertTrue(alarm.isAllowWhileIdle)
    }

    @Test
    fun set_replaces_previous_alarm() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)

        ReminderAlarmScheduler.set(context, at.minusDays(1))
        ReminderAlarmScheduler.set(context, at)

        val alarms = shadowOf(alarmManager).scheduledAlarms
        assertEquals(1, alarms.size)
        assertEquals(atMillis, alarms[0].triggerAtMs)
        assertFalse(alarms[0].operation == null)
    }
}
