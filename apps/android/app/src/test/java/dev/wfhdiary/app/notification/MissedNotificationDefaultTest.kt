package dev.wfhdiary.app.notification

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MissedNotificationDefaultTest {
    @Test
    fun isEnabled_false_for_none() {
        assertFalse(MissedNotificationDefault.isEnabled(MissedNotificationDefault.NONE))
        assertFalse(MissedNotificationDefault.isEnabled("none"))
    }

    @Test
    fun isEnabled_true_for_home_office_leave() {
        assertTrue(MissedNotificationDefault.isEnabled(MissedNotificationDefault.HOME))
        assertTrue(MissedNotificationDefault.isEnabled(MissedNotificationDefault.OFFICE))
        assertTrue(MissedNotificationDefault.isEnabled(MissedNotificationDefault.LEAVE))
    }
}
