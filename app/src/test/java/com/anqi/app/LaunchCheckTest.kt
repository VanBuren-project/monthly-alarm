package com.anqi.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.anqi.app.alarm.LaunchCheck
import com.anqi.app.alarm.SwipeTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The startup check is the one place this app is allowed to make a claim about
 * the three OEM switches, so these tests pin exactly what it may and may not
 * say.
 *
 * The rules under test:
 *
 *  - the switches are never "已开启"; they are 已确认 only after a ring that
 *    happened while they were claimed on;
 *  - a verdict measured *before* the confirmation proves nothing about it, so an
 *    old pass must not mask a freshly broken phone;
 *  - a late ring still confirms delivery — it proves the process was woken,
 *    which is precisely what the switches govern;
 *  - "待确认" alone never prompts (the user has nothing to do but wait), and a
 *    failed measurement always prompts (it is the only real evidence).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class LaunchCheckTest {

    private lateinit var app: android.app.Application

    private fun prefs() = app.getSharedPreferences("anqi_launch", Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        prefs().edit().clear().commit()
        app.getSharedPreferences("anqi_swipe", Context.MODE_PRIVATE).edit().clear().commit()
    }

    /** A phone where all four system rows are granted, so only manual matters. */
    private fun good() = LaunchCheck.Snapshot(
        notifications = true,
        exactAlarms = true,
        fullScreen = true,
        batteryUnrestricted = true
    )

    private fun verdict(armedAt: Long, rang: Boolean, late: Boolean = false) =
        SwipeTest.Verdict(
            armedAt = armedAt,
            rang = rang,
            swiped = true,
            path = "service",
            late = late
        )

    // ------------------------------------------------------------ manual state

    @Test
    fun neverClaimedCountsAsNeedingAction() {
        assertEquals(
            LaunchCheck.State.NEEDS_ACTION,
            LaunchCheck.manualState(confirmedAt = 0L, verdict = null)
        )
    }

    @Test
    fun claimedButUnmeasuredIsPending() {
        assertEquals(
            LaunchCheck.State.PENDING,
            LaunchCheck.manualState(confirmedAt = 1_000L, verdict = null)
        )
    }

    @Test
    fun anOldPassDoesNotConfirmAFreshSetup() {
        // The pass was measured before these switches were claimed on, so it says
        // nothing about them. This is the bug that would otherwise make a broken
        // phone look fine forever.
        val old = verdict(armedAt = 500L, rang = true)
        assertEquals(
            LaunchCheck.State.PENDING,
            LaunchCheck.manualState(confirmedAt = 1_000L, verdict = old)
        )
    }

    @Test
    fun aRingAfterTheClaimConfirms() {
        assertEquals(
            LaunchCheck.State.CONFIRMED,
            LaunchCheck.manualState(confirmedAt = 1_000L, verdict = verdict(1_000L, rang = true))
        )
    }

    @Test
    fun aLateRingStillConfirms() {
        // Lateness proves the process was woken and the alarm delivered, which is
        // exactly what 自启动/锁屏显示 govern. Lateness has its own banner.
        assertEquals(
            LaunchCheck.State.CONFIRMED,
            LaunchCheck.manualState(
                confirmedAt = 1_000L,
                verdict = verdict(1_000L, rang = true, late = true)
            )
        )
    }

    @Test
    fun aMeasuredMissNeedsAction() {
        assertEquals(
            LaunchCheck.State.NEEDS_ACTION,
            LaunchCheck.manualState(confirmedAt = 1_000L, verdict = verdict(1_000L, rang = false))
        )
    }

    // ---------------------------------------------------------------- reasons

    @Test
    fun aFullyConfirmedPhoneHasNothingToReport() {
        val s = good().copy(confirmedAt = 1_000L, verdict = verdict(1_000L, rang = true))
        assertTrue(LaunchCheck.reasons(s).isEmpty())
    }

    @Test
    fun aMissingSystemPermissionIsReportedEvenWhenTheSwitchesAreFine() {
        val s = good().copy(
            fullScreen = false,
            confirmedAt = 1_000L,
            verdict = verdict(1_000L, rang = true)
        )
        assertEquals(listOf(LaunchCheck.Reason.MISSING_SYSTEM), LaunchCheck.reasons(s))
    }

    @Test
    fun aPhoneWithNoClaimIsNotAccusedOfAFailedMeasurement() {
        val s = good()
        assertEquals(listOf(LaunchCheck.Reason.NEVER_CONFIRMED), LaunchCheck.reasons(s))
    }

    @Test
    fun aClaimWithoutAVerdictIsAwaitingMeasurement() {
        val s = good().copy(confirmedAt = 1_000L)
        assertEquals(listOf(LaunchCheck.Reason.AWAITING_MEASUREMENT), LaunchCheck.reasons(s))
    }

    @Test
    fun aMeasuredMissIsAFailedMeasurement() {
        val s = good().copy(confirmedAt = 1_000L, verdict = verdict(1_000L, rang = false))
        assertEquals(listOf(LaunchCheck.Reason.MEASUREMENT_FAILED), LaunchCheck.reasons(s))
    }

    // ----------------------------------------------------------- shouldPrompt

    @Test
    fun aConfirmedPhoneIsNeverPrompted() {
        val s = good().copy(confirmedAt = 1_000L, verdict = verdict(1_000L, rang = true))
        assertFalse(LaunchCheck.shouldPrompt(s))
    }

    @Test
    fun waitingForTheTestDoesNotPrompt() {
        // Nothing the user can do but wait, so a dialog would just be noise; the
        // countdown lives on the settings card instead.
        val s = good().copy(confirmedAt = 1_000L, now = 2_000L)
        assertFalse(LaunchCheck.shouldPrompt(s))
    }

    @Test
    fun aFailedMeasurementPromptsEvenInsideTheSnoozeWindow() {
        val now = 10_000_000L
        val s = good().copy(
            confirmedAt = 1_000L,
            verdict = verdict(1_000L, rang = false),
            snoozedUntil = now + LaunchCheck.SNOOZE_MS,
            now = now
        )
        assertTrue(
            "the one piece of evidence that the switches do not work must not be hidden",
            LaunchCheck.shouldPrompt(s)
        )
    }

    @Test
    fun snoozeSuppressesAnUnaddressedClaim() {
        val now = 10_000_000L
        val snoozed = good().copy(snoozedUntil = now + 1_000L, now = now)
        assertFalse(LaunchCheck.shouldPrompt(snoozed))
    }

    @Test
    fun snoozeExpires() {
        val now = 10_000_000L
        val expired = good().copy(snoozedUntil = now - 1L, now = now)
        assertTrue(LaunchCheck.shouldPrompt(expired))
    }

    @Test
    fun aMissingSystemPermissionPrompts() {
        val s = good().copy(notifications = false, now = 5_000L)
        assertTrue(LaunchCheck.shouldPrompt(s))
    }

    // --------------------------------------------------------------- storage

    @Test
    fun snoozeLastsExactlyOneDay() {
        val at = 1_700_000_000_000L
        LaunchCheck.snooze(app, at)
        assertEquals(at + LaunchCheck.SNOOZE_MS, LaunchCheck.snoozedUntil(app))
    }

    @Test
    fun confirmingClearsTheSnooze() {
        LaunchCheck.snooze(app, 1_700_000_000_000L)
        LaunchCheck.clearSnooze(app)
        assertEquals(0L, LaunchCheck.snoozedUntil(app))
    }

    // ------------------------------------------------------------------ copy

    @Test
    fun theSummaryNeverClaimsASwitchIsOn() {
        val claimed = good().copy(confirmedAt = 1_000L)
        assertEquals("三个开关待确认 · 系统权限都开了", LaunchCheck.summary(claimed))

        val never = good()
        assertEquals("三个开关还没设置 · 系统权限都开了", LaunchCheck.summary(never))

        val broken = good().copy(
            confirmedAt = 1_000L,
            verdict = verdict(1_000L, rang = false),
            notifications = false
        )
        assertEquals("三个开关没生效 · 系统权限还差 1 项", LaunchCheck.summary(broken))
    }

    @Test
    fun systemRowsReportTheirOwnTruth() {
        val s = good().copy(batteryUnrestricted = false)
        val states = LaunchCheck.systemItems(s).associate { it.item to it.state }
        assertEquals(LaunchCheck.State.CONFIRMED, states[LaunchCheck.Item.NOTIFICATIONS])
        assertEquals(LaunchCheck.State.CONFIRMED, states[LaunchCheck.Item.EXACT_ALARM])
        assertEquals(LaunchCheck.State.CONFIRMED, states[LaunchCheck.Item.FULL_SCREEN])
        assertEquals(LaunchCheck.State.NEEDS_ACTION, states[LaunchCheck.Item.BATTERY])
    }

    @Test
    fun theThreeManualRowsShareOneState() {
        val s = good().copy(confirmedAt = 1_000L)
        val items = LaunchCheck.manualItems(s)
        assertEquals(LaunchCheck.MANUAL, items.map { it.item })
        assertTrue(items.all { it.state == LaunchCheck.State.PENDING })
    }

    @Test
    fun everyStateHasPlainWording() {
        assertEquals("已确认", LaunchCheck.stateText(LaunchCheck.State.CONFIRMED))
        assertEquals("待确认", LaunchCheck.stateText(LaunchCheck.State.PENDING))
        assertEquals("需要你处理", LaunchCheck.stateText(LaunchCheck.State.NEEDS_ACTION))
    }
}
