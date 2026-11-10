package com.anqi.app

import android.app.AlarmManager
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.anqi.app.alarm.AlarmScheduler
import com.anqi.app.alarm.ArmLedger
import com.anqi.app.alarm.Diag
import com.anqi.app.alarm.GuardJob
import com.anqi.app.alarm.SelfHeal
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
 * [SelfHeal] is the single repair routine shared by process start, the
 * heartbeat, the guard service, the periodic job, boot and "user opened the app".
 *
 * Its *order* is the load-bearing detail: the ledger has to be audited before the
 * queue is re-armed, because re-arming overwrites each expected trigger time.
 * Healing in the other order erases the evidence of a skipped slot and silently
 * defers the reminder to next month — the exact bug these tests exist to prevent.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SelfHealTest {
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
        AlarmScheduler(app).cancelHeartbeat()
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

    private fun armedForReminders(): Int = shadowOf(alarmManager).scheduledAlarms.count {
        shadowOf(it.operation!!).savedIntent?.action == AlarmScheduler.ACTION_ALARM
    }

    @Test
    fun healingAWipedQueuePutsEveryEnabledReminderBack() {
        store.saveAll(listOf(monthly(1L), monthly(2L), monthly(3L).copy(enabled = false)))
        assertEquals(0, armedForReminders())

        SelfHeal.heal(app)

        assertEquals(2, armedForReminders())
    }

    @Test
    fun healingReArmsTheHeartbeatToo() {
        SelfHeal.heal(app)

        val heartbeats = shadowOf(alarmManager).scheduledAlarms.count {
            shadowOf(it.operation!!).savedIntent?.action == AlarmScheduler.ACTION_HEARTBEAT
        }
        assertEquals(1, heartbeats)
    }

    /**
     * A single pass must both replay the skipped slot and leave the reminder
     * armed for its next occurrence — not one at the cost of the other.
     */
    @Test
    fun asinglePassReplaysTheSkippedSlotAndRearmsTheNextOne() {
        val alarm = monthly()
        store.saveAll(listOf(alarm))
        ArmLedger.recordArmed(app, alarm.id, System.currentTimeMillis() - 20 * 60_000L)

        val late = SelfHeal.heal(app)

        assertEquals(1, late)
        assertEquals(1, armedForReminders())
        @Suppress("DEPRECATION")
        val armedAt = shadowOf(alarmManager).scheduledAlarms
            .single { shadowOf(it.operation!!).savedIntent?.action == AlarmScheduler.ACTION_ALARM }
            .triggerAtTime
        assertTrue("must point at the future, not the replayed slot", armedAt > System.currentTimeMillis())
    }

    @Test
    fun healingTwiceIsHarmless() {
        store.saveAll(listOf(monthly()))
        SelfHeal.heal(app)
        SelfHeal.heal(app)

        // Re-arming replaces the registration instead of stacking a second one.
        assertEquals(1, armedForReminders())
    }

    @Test
    fun theGuardJobCanBeScheduledRepeatedlyWithoutPilingUp() {
        GuardJob.schedule(app)
        GuardJob.schedule(app)
        GuardJob.schedule(app)

        // `getAllPendingJobs` is the real framework API, not a shadow accessor.
        val jobs = app.getSystemService(android.app.job.JobScheduler::class.java).allPendingJobs
        assertEquals(1, jobs.size)
    }

    @Test
    fun theGuardJobIsPersistedSoItSurvivesAReboot() {
        GuardJob.schedule(app)

        val job = app.getSystemService(android.app.job.JobScheduler::class.java)
            .allPendingJobs
            .single()
        assertTrue(
            "a job that dies with the process would be no better than the heartbeat",
            job.isPersisted
        )
    }
}
