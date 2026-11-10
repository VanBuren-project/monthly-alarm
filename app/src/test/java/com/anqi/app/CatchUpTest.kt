package com.anqi.app

import android.app.AlarmManager
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.anqi.app.alarm.AlarmScheduler
import com.anqi.app.alarm.ArmLedger
import com.anqi.app.alarm.CatchUp
import com.anqi.app.alarm.Diag
import com.anqi.app.data.AlarmStore
import com.anqi.app.model.Alarm
import com.anqi.app.model.RepeatType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * The user's report was "nothing rings at all", not "it rings a bit late".
 * These tests pin down the difference between the two failures:
 *
 *  - a slot the app armed and the system then dropped is replayed, and
 *  - a reminder that was simply never armed for the past is never replayed,
 *    which is what stops a monthly reminder from ringing the moment it is
 *    created on a day past its monthly day.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CatchUpTest {
    private lateinit var app: Application
    private lateinit var store: AlarmStore

    private val alarmManager: AlarmManager
        get() = app.getSystemService(AlarmManager::class.java)

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        store = AlarmStore(app)
        store.saveAll(emptyList())
        ArmLedger.resetMissed(app)
        Diag.clear(app)
        shadowOf(alarmManager).scheduledAlarms.clear()
    }

    private fun monthly(id: Long = 1L) = Alarm(
        id = id,
        label = "交电费",
        repeatType = RepeatType.MONTHLY_DAY,
        monthlyDay = 8,
        hour = 8,
        minute = 0,
        date = LocalDate.now().plusDays(1),
        enabled = true
    )

    @Test
    fun aSlotTheSystemDroppedIsRungLate() {
        val alarm = monthly()
        store.saveAll(listOf(alarm))
        val expected = System.currentTimeMillis() - 30 * 60_000L
        ArmLedger.recordArmed(app, alarm.id, expected)

        val rung = CatchUp.run(app)

        assertEquals("the missed slot has to be rung, not skipped", 1, rung)
        assertEquals(1, ArmLedger.missedCount(app))
        val after = store.find(alarm.id)
        assertNotNull(after)
        // Firing advances the schedule exactly like a punctual ring does, so the
        // recovered reminder is already pointing at its next occurrence.
        assertTrue((after!!.lastFiredAt ?: 0L) >= expected)
    }

    @Test
    fun theSameMissIsNotRungTwice() {
        val alarm = monthly()
        store.saveAll(listOf(alarm))
        ArmLedger.recordArmed(app, alarm.id, System.currentTimeMillis() - 30 * 60_000L)

        assertEquals(1, CatchUp.run(app))
        assertEquals("a second pass must not ring the same slot again", 0, CatchUp.run(app))
    }

    @Test
    fun aSlotThatHasNotComeYetIsLeftAlone() {
        val alarm = monthly()
        store.saveAll(listOf(alarm))
        ArmLedger.recordArmed(app, alarm.id, System.currentTimeMillis() + 60 * 60_000L)

        assertEquals(0, CatchUp.run(app))
        assertEquals(0, ArmLedger.missedCount(app))
    }

    /**
     * The trap this test exists for: a reminder created today for "the 8th" which
     * has already passed this month. `NextOccurrence` arms it for next month, so
     * the ledger holds a future time — but even if it did not, silence is the
     * only honest answer, because nothing was ever promised to the user.
     */
    @Test
    fun aReminderThatWasNeverArmedNeverRingsOnItsOwn() {
        val alarm = monthly()
        store.saveAll(listOf(alarm))

        assertEquals(0, CatchUp.run(app))
        assertEquals(0, ArmLedger.missedCount(app))
        // Nothing is armed for the reminder itself. (The heartbeat, which the
        // process start armed by design, is a different action and is ignored.)
        val armedForReminders = shadowOf(alarmManager).scheduledAlarms.count {
            shadowOf(it.operation!!).savedIntent?.action == AlarmScheduler.ACTION_ALARM
        }
        assertEquals(0, armedForReminders)
    }

    @Test
    fun aDisabledReminderIsNotRungLate() {
        val alarm = monthly()
        store.saveAll(listOf(alarm.copy(enabled = false)))
        ArmLedger.recordArmed(app, alarm.id, System.currentTimeMillis() - 30 * 60_000L)

        assertEquals(0, CatchUp.run(app))
    }

    @Test
    fun aMissTooOldToBeUsefulIsSkipped() {
        val alarm = monthly()
        store.saveAll(listOf(alarm))
        // A day and a half late: ringing now would be a false alarm.
        ArmLedger.recordArmed(app, alarm.id, System.currentTimeMillis() - 36 * 60 * 60_000L)

        assertEquals(0, CatchUp.run(app))
    }

    @Test
    fun recoveringLeavesTheQueueArmedForTheNextOccurrence() {
        val alarm = monthly()
        store.saveAll(listOf(alarm))
        ArmLedger.recordArmed(app, alarm.id, System.currentTimeMillis() - 30 * 60_000L)

        CatchUp.run(app)

        val armed = shadowOf(alarmManager).scheduledAlarms
            .filter { shadowOf(it.operation!!).savedIntent?.action == AlarmScheduler.ACTION_ALARM }
        assertEquals(1, armed.size)
        @Suppress("DEPRECATION")
        assertTrue(
            "the recovered alarm must point at the future, not at the slot it just replayed",
            armed.single().triggerAtTime > System.currentTimeMillis()
        )
    }
}
