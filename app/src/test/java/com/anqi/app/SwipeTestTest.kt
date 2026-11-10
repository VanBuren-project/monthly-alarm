package com.anqi.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.anqi.app.alarm.AlarmScheduler
import com.anqi.app.alarm.OemAutostart
import com.anqi.app.alarm.SwipeTest
import com.anqi.app.data.AlarmStore
import com.anqi.app.model.Alarm
import com.anqi.app.model.RepeatType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * The swipe test is the first thing in this app that reports a *measured*
 * delivery result instead of an intention, so its honesty is the feature. These
 * tests pin the three ways it could lie:
 *
 *  - calling a ring that never happened a pass;
 *  - calling a ring that happened *without* the process dying a pass;
 *  - letting an unrelated reminder in the same minute launder itself into a pass.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SwipeTestTest {
    private lateinit var app: android.app.Application

    private fun prefs() = app.getSharedPreferences("anqi_swipe", Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        prefs().edit().clear().commit()
        AlarmStore(app).saveAll(emptyList())
        SwipeTest.noteProcessStart(app)
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

    private fun armWith(alarm: Alarm = reminder()) {
        AlarmStore(app).saveAll(listOf(alarm))
        assertTrue(SwipeTest.start(app, alarm.id, 60_000L))
    }

    @Test
    fun anUnmeasuredPhoneHasNoVerdict() {
        assertNull("never measured is not the same as failed", SwipeTest.verdict(app))
        assertFalse(SwipeTest.isPending(app))
    }

    @Test
    fun armingClearsAPreviousVerdict() {
        armWith()
        SwipeTest.noteDelivery(app, AlarmScheduler.PATH_SERVICE)
        assertNotNull(SwipeTest.verdict(app))

        SwipeTest.start(app, 1L, 60_000L)

        assertNull("a new measurement must not show the old result", SwipeTest.verdict(app))
        assertTrue(SwipeTest.isPending(app))
    }

    @Test
    fun aRingAfterTheProcessDiedIsAPass() {
        armWith()
        // The process was torn down and came back — that is what a swipe does.
        SwipeTest.noteProcessStart(app)

        SwipeTest.noteDelivery(app, AlarmScheduler.PATH_SERVICE)

        val verdict = SwipeTest.verdict(app)!!
        assertTrue(verdict.rang)
        assertTrue("the process restarted, so the swipe really was survived", verdict.swiped)
        assertEquals(AlarmScheduler.PATH_SERVICE, verdict.path)
    }

    @Test
    fun aRingWithoutAProcessRestartIsNotAPass() {
        armWith()

        SwipeTest.noteDelivery(app, AlarmScheduler.PATH_BROADCAST)

        val verdict = SwipeTest.verdict(app)!!
        assertTrue("it did ring, and that is worth recording", verdict.rang)
        assertFalse("nothing was swiped, so nothing was proven", verdict.swiped)
    }

    @Test
    fun anUnrelatedRingInTheSameMinuteIsIgnored() {
        armWith()
        val armedAt = SwipeTest.pendingAt(app)
        // Pretend the slot is an hour away; the ring that just happened cannot
        // have been the test.
        prefs().edit().putLong("pending_at", armedAt + 60 * 60_000L).commit()

        SwipeTest.noteDelivery(app, AlarmScheduler.PATH_SERVICE)

        assertNull(SwipeTest.verdict(app))
        assertTrue("the real test is still waiting", SwipeTest.isPending(app))
    }

    @Test
    fun anOverdueTestIsSettledAsAFailureOnceTheGraceHasPassed() {
        armWith()
        // Push the slot into the past, well beyond the settle grace.
        prefs().edit().putLong("pending_at", System.currentTimeMillis() - 10 * 60_000L).commit()

        assertTrue(SwipeTest.settle(app))

        val verdict = SwipeTest.verdict(app)!!
        assertFalse(verdict.rang)
        assertFalse(SwipeTest.isPending(app))
        assertFalse("settling twice must not double-report", SwipeTest.settle(app))
    }

    @Test
    fun anOverdueTestInsideTheGraceIsLeftAlone() {
        armWith()
        prefs().edit().putLong("pending_at", System.currentTimeMillis() - 5_000L).commit()

        assertFalse(SwipeTest.settle(app))
        assertNull(SwipeTest.verdict(app))
        assertTrue(SwipeTest.isPending(app))
    }

    /**
     * Doze can hold a non-exact alarm for minutes. A phone that rang late did
     * not stay silent, so the provisional miss has to be overturned — otherwise
     * the app tells the user to go fix a switch that was already fine.
     */
    @Test
    fun aRingThatArrivesLateOverturnsTheProvisionalMiss() {
        armWith()
        prefs().edit().putLong("pending_at", System.currentTimeMillis() - 4 * 60_000L).commit()
        assertTrue(SwipeTest.settle(app))
        assertFalse(SwipeTest.verdict(app)!!.rang)

        SwipeTest.noteDelivery(app, AlarmScheduler.PATH_SERVICE)

        val verdict = SwipeTest.verdict(app)!!
        assertTrue("a late ring is still a ring", verdict.rang)
        assertTrue("and it must be reported as late, not as a clean pass", verdict.late)
    }

    @Test
    fun aRingFarTooLateIsNotCreditedToTheTest() {
        armWith()
        prefs().edit().putLong("pending_at", System.currentTimeMillis() - 3 * 60 * 60_000L).commit()
        SwipeTest.settle(app)
        // Three hours later this is some other reminder's business, not the test's.
        prefs().edit().putLong("verdict_at", 0L).commit()

        SwipeTest.noteDelivery(app, AlarmScheduler.PATH_SERVICE)

        assertNull(SwipeTest.verdict(app))
    }

    /**
     * A slot the user walked away from must not become a permanent accusation.
     * If the phone was never opened again until days later, nothing about that
     * window is measurable, so the honest answer is to forget it.
     */
    @Test
    fun aTestAbandonedForHoursIsForgottenRatherThanBlamed() {
        armWith()
        prefs().edit().putLong("pending_at", System.currentTimeMillis() - 6 * 60 * 60_000L).commit()

        assertFalse("an expired measurement produces no verdict", SwipeTest.settle(app))

        assertNull("forgetting is not the same as failing", SwipeTest.verdict(app))
        assertNull(SwipeTest.pendingAlarmId(app))
        assertFalse(SwipeTest.isPending(app))
    }

    /**
     * Forgetting an abandoned slot must only forget the slot. A verdict that
     * already exists describes something that really happened, so a later sweep
     * must not touch it or pretend it was withdrawn.
     */
    @Test
    fun anExpirySweepLeavesAnExistingVerdictAlone() {
        armWith()
        // A miss that was judged while the window was still open, then left alone
        // for hours: the slot is now far past the upgrade window too.
        val at = System.currentTimeMillis() - 40 * 60_000L
        prefs().edit()
            .putLong("pending_at", at)
            .putLong("verdict_at", at)
            .putBoolean("verdict_rang", false)
            .commit()

        assertFalse("an expired slot produces no new verdict", SwipeTest.settle(app))
        assertFalse("and the old verdict still stands", SwipeTest.verdict(app)!!.rang)
        assertEquals(at, SwipeTest.verdict(app)!!.armedAt)
    }

    @Test
    fun aJudgedTestIsNoLongerPending() {
        armWith()
        prefs().edit().putLong("pending_at", System.currentTimeMillis() - 10 * 60_000L).commit()
        SwipeTest.settle(app)

        assertFalse(
            "the slot stays on disk only to let a late ring overturn it",
            SwipeTest.isPending(app)
        )
    }

    @Test
    fun cancellingForgetsTheArmedSlot() {
        armWith()

        SwipeTest.cancel(app)

        assertFalse(SwipeTest.isPending(app))
        assertNull(SwipeTest.pendingAlarmId(app))
        assertNull(SwipeTest.verdict(app))
    }

    @Test
    fun startingWithoutAnEnabledReminderDoesNothing() {
        AlarmStore(app).saveAll(listOf(reminder().copy(enabled = false)))

        assertFalse(SwipeTest.start(app, 1L, 60_000L))
        assertFalse(SwipeTest.isPending(app))
    }

    @Test
    fun theSetupWalkthroughIsRemembered() {
        assertFalse(SwipeTest.setupDone(app))
        SwipeTest.markSetupDone(app)
        assertTrue(SwipeTest.setupDone(app))
    }
}
