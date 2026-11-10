package com.anqi.app

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.anqi.app.alarm.AlarmDelivery
import com.anqi.app.alarm.Diag
import com.anqi.app.alarm.RingResume
import com.anqi.app.alarm.RingtoneService
import com.anqi.app.alarm.SelfHeal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The mark that lets a killed ring come back.
 *
 * Two failures have to be told apart, and they pull in opposite directions:
 * a ring that is still wanted must survive the process being reclaimed (the
 * "cleared the background and the alarm died" report), while a mark left over
 * from long ago must never make the phone scream at a random moment. The age
 * window is the whole distinction, so both sides are pinned here.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RingResumeTest {
    private lateinit var app: Application

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        RingResume.clear(app)
        Diag.clear(app)
    }

    private fun writeMark(id: Long, at: Long) {
        app.getSharedPreferences("anqi_ring", Context.MODE_PRIVATE)
            .edit()
            .putLong("ring_id", id)
            .putLong("ring_at", at)
            .commit()
    }

    @Test
    fun nothingIsMarkedBeforeTheFirstRing() {
        assertEquals(-1L, RingResume.marked(app))
        assertEquals(-1L, RingResume.age(app))
        assertFalse(RingResume.resumable(app))
    }

    @Test
    fun aRingThatJustStartedIsResumable() {
        RingResume.mark(app, 7L)
        assertEquals(7L, RingResume.marked(app))
        assertTrue(RingResume.resumable(app))
    }

    @Test
    fun aRingOlderThanTheWindowIsNotResumed() {
        writeMark(7L, System.currentTimeMillis() - RingResume.RESUME_WINDOW_MS - 60_000L)
        assertFalse(RingResume.resumable(app))
        // Still marked: sweeping is the heal pass's job, not a read.
        assertEquals(7L, RingResume.marked(app))
    }

    @Test
    fun aMarkJustInsideTheWindowIsStillResumed() {
        writeMark(7L, System.currentTimeMillis() - RingResume.RESUME_WINDOW_MS + 60_000L)
        assertTrue(RingResume.resumable(app))
    }

    @Test
    fun aFutureTimestampIsNotTreatedAsFresh() {
        // A clock that was wrong, or a device that was restored from a backup,
        // must not park the phone in "resume forever".
        writeMark(7L, System.currentTimeMillis() + 60 * 60_000L)
        assertFalse(RingResume.resumable(app))
    }

    @Test
    fun dismissingClearsTheMark() {
        RingResume.mark(app, 7L)
        RingtoneService.stop(app)
        assertEquals(-1L, RingResume.marked(app))
        assertFalse(RingResume.resumable(app))
    }

    @Test
    fun theHealPassSweepsAStaleMarkAndSaysSo() {
        writeMark(7L, System.currentTimeMillis() - RingResume.RESUME_WINDOW_MS - 60_000L)
        SelfHeal.heal(app)
        assertEquals(-1L, RingResume.marked(app))
        assertTrue(Diag.read(app).any { it.contains("已过补响窗口") })
    }

    @Test
    fun theHealPassLeavesALiveMarkAlone() {
        RingResume.mark(app, 7L)
        SelfHeal.heal(app)
        assertEquals(7L, RingResume.marked(app))
    }

    @Test
    fun aSweepWithNothingMarkedIsSilent() {
        assertFalse(RingResume.clearStale(app))
        assertTrue(Diag.read(app).isEmpty())
    }

    @Test
    fun deliveringAReminderMarksItAsRinging() {
        val alarm = com.anqi.app.model.Alarm(
            label = "交电费",
            repeatType = com.anqi.app.model.RepeatType.MONTHLY_DAY,
            monthlyDay = 8,
            hour = 8,
            minute = 0,
            date = java.time.LocalDate.now(),
            enabled = true
        )
        val store = com.anqi.app.data.AlarmStore(app)
        store.upsert(alarm)
        val saved = store.all().first()
        AlarmDelivery.fire(app, saved, store)
        assertEquals(saved.id, RingResume.marked(app))
    }
}
