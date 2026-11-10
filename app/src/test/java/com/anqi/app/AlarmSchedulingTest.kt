package com.anqi.app

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.anqi.app.alarm.AlarmReceiver
import com.anqi.app.alarm.AlarmScheduler
import com.anqi.app.data.AlarmStore
import com.anqi.app.model.Alarm
import com.anqi.app.model.RepeatType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Guards the "the alarm stopped firing after I cleared the app from recents"
 * regression: whatever the ROM did to our process, reopening the app or waking
 * up for a heartbeat has to leave a live registration behind for every enabled
 * reminder.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AlarmSchedulingTest {
    private lateinit var app: Application
    private lateinit var store: AlarmStore
    private lateinit var scheduler: AlarmScheduler

    private val alarmManager: AlarmManager
        get() = app.getSystemService(AlarmManager::class.java)

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        store = AlarmStore(app)
        store.saveAll(emptyList())
        scheduler = AlarmScheduler(app)
        // AnqiApp.onCreate already armed a heartbeat when the process started;
        // every test below wants to observe only what it triggers itself.
        // Cancelling through the real API (rather than poking the shadow's
        // internal list) keeps the shadow's own bookkeeping consistent.
        scheduler.cancelHeartbeat()
        shadowOf(alarmManager).scheduledAlarms.clear()
    }

    private fun monthly(id: Long = 1L, enabled: Boolean = true) = Alarm(
        id = id,
        label = "交电费",
        repeatType = RepeatType.MONTHLY_DAY,
        monthlyDay = 8,
        hour = 8,
        minute = 0,
        date = LocalDate.now().plusDays(1),
        enabled = enabled
    )

    /** Only counts registrations whose broadcast carries [action]. */
    private fun countOf(action: String): Int =
        shadowOf(alarmManager).scheduledAlarms.count { it.operation?.action() == action }

    private fun PendingIntent.action(): String? = shadowOf(this).savedIntent?.action

    private fun armedAlarms(): Int = countOf(AlarmScheduler.ACTION_ALARM)

    private fun snoozes(): Int = countOf(AlarmScheduler.ACTION_SNOOZE_FIRE)

    private fun totalCount(): Int = shadowOf(alarmManager).scheduledAlarms.size

    @Test
    fun rescheduleAllArmsEveryEnabledAlarm() {
        store.saveAll(
            listOf(monthly(id = 1L), monthly(id = 2L), monthly(id = 3L, enabled = false))
        )

        scheduler.rescheduleAll(store.all())

        assertEquals(2, armedAlarms())
    }

    /**
     * The exact scenario the user hit: the ROM wiped our registrations, so the
     * queue is empty even though the reminder is still enabled. Starting the
     * process again must heal it without any user interaction beyond opening
     * the app.
     */
    @Test
    fun rescheduleAllHealsAWipedQueue() {
        store.saveAll(listOf(monthly()))
        assertEquals(0, armedAlarms())

        scheduler.rescheduleAll(store.all())

        assertEquals(1, armedAlarms())
    }

    @Test
    fun heartbeatRearmsTheQueue() {
        store.saveAll(listOf(monthly()))
        assertEquals(0, armedAlarms())

        AlarmReceiver().onReceive(
            app,
            Intent(app, AlarmReceiver::class.java).setAction(AlarmScheduler.ACTION_HEARTBEAT)
        )

        assertEquals(1, armedAlarms())
    }

    @Test
    fun heartbeatPutItselfBack() {
        // Nothing armed at all: the ROM cleaned everything, heartbeat included.
        assertEquals(
            shadowOf(alarmManager).scheduledAlarms.map { it.operation?.action() }.toString(),
            0,
            totalCount()
        )
        store.saveAll(listOf(monthly()))

        AlarmReceiver().onReceive(
            app,
            Intent(app, AlarmReceiver::class.java).setAction(AlarmScheduler.ACTION_HEARTBEAT)
        )

        assertEquals(1, countOf(AlarmScheduler.ACTION_HEARTBEAT))
    }

    @Test
    fun heartbeatIsScheduledAheadOfTime() {
        scheduler.scheduleHeartbeat()

        val heartbeat = shadowOf(alarmManager).scheduledAlarms
            .single { it.operation?.action() == AlarmScheduler.ACTION_HEARTBEAT }
        @Suppress("DEPRECATION")
        val delay = heartbeat.triggerAtTime - System.currentTimeMillis()
        // Somewhere between 6h and 6h10m from now: a one-shot that re-arms
        // itself, so it still fires during Doze instead of being batched.
        assertTrue(
            "unexpected heartbeat delay: $delay",
            delay in (6 * 60 * 60 * 1000L)..(6 * 60 * 60 * 1000L + 11 * 60 * 1000L)
        )
    }

    @Test
    fun reschedulingKeepsAPendingSnooze() {
        val alarm = monthly()
        store.saveAll(listOf(alarm))
        scheduler.schedule(alarm)
        scheduler.scheduleSnooze(alarm, 10)
        assertEquals(1, armedAlarms())
        assertEquals(1, snoozes())

        scheduler.rescheduleAll(store.all())

        // One base alarm + the snooze the user is still waiting for.
        assertEquals(1, armedAlarms())
        assertEquals(1, snoozes())
    }

    @Test
    fun testRingDoesNotDisturbTheRealSchedule() {
        val alarm = monthly()
        store.saveAll(listOf(alarm))
        scheduler.schedule(alarm)
        assertEquals(1, armedAlarms())

        scheduler.scheduleTestRing(alarm, delayMs = 10_000L)

        assertEquals(1, armedAlarms())
        assertEquals(1, countOf(AlarmScheduler.ACTION_TEST_FIRE))
    }
}
