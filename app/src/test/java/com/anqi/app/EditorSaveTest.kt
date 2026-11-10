package com.anqi.app

import com.anqi.app.model.Alarm
import com.anqi.app.model.RepeatType
import com.anqi.app.ui.checkBeforeSave
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The save button used to validate the draft *as it was stored*, and
 * `NextOccurrence.next()` returns null for a disabled alarm. So editing a paused
 * reminder reported "这个时间已经过去" for a perfectly valid future time and
 * refused to save — which is the report that started this: "我闹钟在未启动的
 * 情况下设置闹钟时间，会提示闹钟设定失败".
 */
class EditorSaveTest {

    private fun alarm(
        label: String = "交电费",
        repeatType: RepeatType = RepeatType.MONTHLY_DAY,
        day: Int = 8,
        hour: Int = 8,
        minute: Int = 0,
        enabled: Boolean
    ) = Alarm(
        id = 1L,
        label = label,
        repeatType = repeatType,
        monthlyDay = day,
        hour = hour,
        minute = minute,
        date = LocalDate.now().plusDays(1),
        enabled = enabled
    )

    @Test
    fun aPausedReminderSavesAndComesBackEnabled() {
        val result = checkBeforeSave(alarm(enabled = false))

        assertNull("a paused reminder must not fail validation", result.error)
        assertTrue("saving a paused reminder has to arm it", result.alarm.enabled)
    }

    @Test
    fun thePausedReminderKeepsItsNewTime() {
        val result = checkBeforeSave(alarm(hour = 21, minute = 30, enabled = false))

        assertEquals(21, result.alarm.hour)
        assertEquals(30, result.alarm.minute)
        assertTrue(result.alarm.enabled)
    }

    @Test
    fun anAlreadyEnabledReminderKeepsWorking() {
        val result = checkBeforeSave(alarm(enabled = true))

        assertNull(result.error)
        assertTrue(result.alarm.enabled)
    }

    /**
     * Arming on save must not paper over a genuinely impossible rule: a yearly
     * reminder on 2/30 has no next occurrence and has to be caught.
     */
    @Test
    fun anImpossibleYearlyDateIsStillRejected() {
        val result = checkBeforeSave(
            alarm(repeatType = RepeatType.YEARLY, hour = 8, enabled = false)
                .copy(yearlyMonth = 2, yearlyDay = 30)
        )

        assertNotNull(result.error)
    }

    @Test
    fun aOnceReminderInThePastIsStillRejected() {
        val past = LocalDate.now().minusDays(3)
        val result = checkBeforeSave(
            alarm(repeatType = RepeatType.ONCE, hour = 0, minute = 1, enabled = false)
                .copy(date = past)
        )

        assertNotNull(result.error)
    }

    @Test
    fun savingTrimsTheLabel() {
        val result = checkBeforeSave(alarm(label = "  交电费  ", enabled = false))

        assertEquals("交电费", result.alarm.label)
    }
}
