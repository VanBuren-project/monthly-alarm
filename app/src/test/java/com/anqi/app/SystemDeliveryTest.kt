package com.anqi.app

import android.app.AlarmManager
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.anqi.app.alarm.AlarmDelivery
import com.anqi.app.alarm.AlarmScheduler
import com.anqi.app.alarm.ArmLedger
import com.anqi.app.alarm.Diag
import com.anqi.app.data.AlarmStore
import com.anqi.app.model.Alarm
import com.anqi.app.model.RepeatType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * The path where the system starts the ringing service itself from an exact
 * alarm, with no broadcast of ours in between. This is the delivery model that
 * survives an OEM cleaning the app out of the background, so it has to advance
 * the schedule on its own — and it has to leave the *other* registration's
 * arrival alone instead of ringing the same slot twice.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SystemDeliveryTest {
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

    private fun armedForReminders(): Int = shadowOf(alarmManager).scheduledAlarms.count {
        shadowOf(it.operation!!).savedIntent?.action == AlarmScheduler.ACTION_ALARM
    }

    @Test
    fun anUnknownIdRingsNothing() {
        assertNull(AlarmDelivery.advanceForSystemDelivery(app, 404L))
    }

    @Test
    fun aDisabledReminderIsNotRung() {
        val alarm = monthly(enabled = false)
        store.saveAll(listOf(alarm))

        assertNull(AlarmDelivery.advanceForSystemDelivery(app, alarm.id))
    }

    /**
     * The whole point of the second registration: no broadcast ran, yet the
     * service still has a reminder to ring and the next month is already armed.
     */
    @Test
    fun theSystemStartedServiceAdvancesTheScheduleOnItsOwn() {
        val alarm = monthly()
        store.saveAll(listOf(alarm))
        assertEquals(0, armedForReminders())

        val rung = AlarmDelivery.advanceForSystemDelivery(app, alarm.id)

        assertNotNull(rung)
        assertEquals(alarm.id, rung!!.id)
        assertNotNull("the slot must count as delivered", rung.lastFiredAt)
        assertEquals(
            "the next occurrence has to be armed even though no broadcast ran",
            1,
            armedForReminders()
        )
    }

    /**
     * Both registrations fire in the same minute. The second arrival must be
     * folded into the first: ringing again is acceptable, advancing the schedule
     * a second time would silently skip a month.
     */
    @Test
    fun theSecondArrivalOfTheSameSlotDoesNotAdvanceAgain() {
        val alarm = monthly()
        store.saveAll(listOf(alarm))
        val first = AlarmDelivery.advanceForSystemDelivery(app, alarm.id)
        assertNotNull(first)

        val second = AlarmDelivery.advanceForSystemDelivery(app, alarm.id)

        assertNotNull("the duplicate still has to ring", second)
        assertEquals(
            "the duplicate must carry the same lastFiredAt, not a newer one",
            first!!.lastFiredAt,
            second!!.lastFiredAt
        )
        assertEquals(1, armedForReminders())
    }

    @Test
    fun aSlotDeliveredLongAgoIsNotTreatedAsADuplicate() {
        val stale = System.currentTimeMillis() - AlarmDelivery.SLOT_DEDUPE_MS - 1_000L
        val alarm = monthly().copy(lastFiredAt = stale)
        store.saveAll(listOf(alarm))

        val rung = AlarmDelivery.advanceForSystemDelivery(app, alarm.id)

        assertNotNull(rung)
        assertTrue(
            "a genuinely new slot has to advance past the old timestamp",
            (rung!!.lastFiredAt ?: 0L) > stale
        )
    }

    @Test
    fun aOneShotReminderTurnsItselfOffAfterTheSystemDelivery() {
        val tomorrow = LocalDate.now().plusDays(1)
        val alarm = Alarm(
            id = 2L,
            label = "取快递",
            repeatType = RepeatType.ONCE,
            date = tomorrow,
            hour = 8,
            minute = 0
        )
        store.saveAll(listOf(alarm))

        val rung = AlarmDelivery.advanceForSystemDelivery(app, alarm.id)

        assertNotNull(rung)
        assertEquals(false, rung!!.enabled)
        assertEquals(0, armedForReminders())
    }
}
