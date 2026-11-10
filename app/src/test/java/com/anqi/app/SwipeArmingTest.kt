package com.anqi.app

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.anqi.app.alarm.AlarmScheduler
import com.anqi.app.alarm.RingtoneService
import com.anqi.app.alarm.SwipeTest
import com.anqi.app.data.AlarmStore
import com.anqi.app.model.Alarm
import com.anqi.app.model.RepeatType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * The 10-second "试一次" ring and the swipe test both go through
 * [AlarmScheduler.ACTION_TEST_FIRE], so they are one careless edit away from
 * sharing a PendingIntent. These tests pin them apart: arming one must not
 * overwrite the other, cancelling one must not disarm the other, and only the
 * swipe test's own registrations may be measured.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SwipeArmingTest {
    private lateinit var app: Application
    private lateinit var scheduler: AlarmScheduler

    private val alarmManager: AlarmManager
        get() = app.getSystemService(AlarmManager::class.java)

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        app.getSharedPreferences("anqi_swipe", Application.MODE_PRIVATE).edit().clear().commit()
        AlarmStore(app).saveAll(emptyList())
        scheduler = AlarmScheduler(app)
        scheduler.cancelHeartbeat()
        shadowOf(alarmManager).scheduledAlarms.clear()
    }

    private fun reminder(id: Long = 1L) = Alarm(
        id = id,
        label = "交电费",
        repeatType = RepeatType.MONTHLY_DAY,
        monthlyDay = 8,
        hour = 8,
        minute = 0,
        date = LocalDate.now().plusDays(1)
    )

    private fun save(alarm: Alarm = reminder()): Alarm {
        AlarmStore(app).saveAll(listOf(alarm))
        return alarm
    }

    private fun PendingIntent.saved() = shadowOf(this).savedIntent

    private fun scheduledPsi(): List<PendingIntent> =
        shadowOf(alarmManager).scheduledAlarms.mapNotNull { it.operation }

    private fun swipePaths(): List<String?> = scheduledPsi()
        .map { it.saved()?.getStringExtra(RingtoneService.EXTRA_TEST_PATH) }
        .filter { it != null }

    /** Both halves of the swipe test must stamp the path extra. */
    @Test
    fun theSwipeTestArmsTwoRegistrationsThatBothCarryThePathExtra() {
        val alarm = save()
        val at = System.currentTimeMillis() + 60_000L

        scheduler.scheduleSwipeTest(alarm, at)

        assertEquals(2, swipePaths().size)
        assertEquals(
            setOf(AlarmScheduler.PATH_BROADCAST, AlarmScheduler.PATH_SERVICE),
            swipePaths().toSet()
        )
    }

    /**
     * The regression this class exists for: the two tests used to share a request
     * code, so arming the swipe test silently replaced the 10-second ring.
     */
    @Test
    fun armingTheSwipeTestDoesNotReplaceTheTenSecondRing() {
        val alarm = save()
        scheduler.scheduleTestRing(alarm, 10_000L)
        val before = scheduledPsi().size

        SwipeTest.start(app, alarm.id, 60_000L)

        assertTrue(
            "the 10-second ring must still be armed alongside the swipe test",
            scheduledPsi().size > before
        )
        assertTrue(
            "the 10-second ring must not be measured as a swipe test",
            scheduledPsi().count { it.saved()?.getStringExtra(RingtoneService.EXTRA_TEST_PATH) == null } > 0
        )
    }

    /** And the reverse: the 10-second ring must not disarm a pending swipe test. */
    @Test
    fun cancellingTheTenSecondRingLeavesTheSwipeTestAlone() {
        val alarm = save()
        SwipeTest.start(app, alarm.id, 60_000L)
        scheduler.scheduleTestRing(alarm, 10_000L)

        scheduler.cancelTestRing(alarm.id)

        assertEquals("both halves of the swipe test must survive", 2, swipePaths().size)
    }

    @Test
    fun cancellingTheSwipeTestLeavesTheTenSecondRingAlone() {
        val alarm = save()
        scheduler.scheduleTestRing(alarm, 10_000L)
        SwipeTest.start(app, alarm.id, 60_000L)

        SwipeTest.cancel(app)

        assertEquals("the swipe test is gone", 0, swipePaths().size)
        assertEquals(
            "the 10-second ring is not the swipe test's to cancel",
            1,
            scheduledPsi().count { it.saved()?.getStringExtra(RingtoneService.EXTRA_TEST_PATH) == null }
        )
    }

    @Test
    fun theTwoTestsDoNotShareAPendingIntentIdentity() {
        val alarm = save()
        scheduler.scheduleTestRing(alarm, 10_000L)
        val tenSecond = scheduledPsi().first { it.saved()?.getStringExtra(RingtoneService.EXTRA_TEST_PATH) == null }

        SwipeTest.start(app, alarm.id, 60_000L)

        // PendingIntent equality is identity by (request code, intent filter),
        // which is exactly what would collide if the roles were shared.
        val swipeBroadcast = scheduledPsi().first {
            it.saved()?.getStringExtra(RingtoneService.EXTRA_TEST_PATH) == AlarmScheduler.PATH_BROADCAST
        }
        assertNotEquals(tenSecond, swipeBroadcast)
    }

    @Test
    fun armingASwipeTestWithoutAReminderFails() {
        assertTrue(!SwipeTest.start(app, 99L, 60_000L))
        assertEquals(0, swipePaths().size)
    }

    @Test
    fun aRealRemindersRegistrationsAreNeverMarkedAsATest() {
        val alarm = save()
        scheduler.schedule(alarm)

        assertNotNull(scheduledPsi())
        assertEquals(
            "no part of a real reminder may claim to be a measurement",
            0,
            swipePaths().size
        )
    }
}
