package com.anqi.app.alarm

import android.content.Context
import androidx.core.content.edit

/**
 * The "a ring is in progress" mark on disk.
 *
 * A ringing alarm that dies with the process is the loudest possible failure: the
 * user is left with a notification that will not stop and no screen to dismiss it
 * from. [RingtoneService] returns START_STICKY so the system restarts it, but a
 * restarted service is handed a null intent — the mark is what tells it *which*
 * reminder was ringing.
 *
 * The timestamp is not decoration. Without it a mark left behind by a ring that
 * was killed and never restarted would make the phone start screaming at some
 * arbitrary later moment, which is worse than having stayed quiet. Past
 * [RESUME_WINDOW_MS] the mark is dead and gets swept.
 */
object RingResume {
    private const val PREFS = "anqi_ring"
    private const val KEY_ID = "ring_id"
    private const val KEY_AT = "ring_at"

    /**
     * How long after a ring started it may still be resumed, counted from the
     * start rather than from the kill: the ring is long over by then, and
     * re-ringing a dismissed-in-spirit alarm is worse than staying quiet.
     */
    const val RESUME_WINDOW_MS = 6 * 60_000L

    /** Records that [id] is ringing, now. Written before the service start is attempted. */
    fun mark(context: Context, id: Long) {
        prefs(context).edit {
            putLong(KEY_ID, id)
            putLong(KEY_AT, System.currentTimeMillis())
        }
    }

    /** The marked reminder, or -1 when nothing is ringing. */
    fun marked(context: Context): Long = prefs(context).getLong(KEY_ID, -1L)

    /** How long ago the mark was written, or -1 when there is no mark. */
    fun age(context: Context): Long {
        val prefs = prefs(context)
        if (prefs.getLong(KEY_ID, -1L) < 0L) return -1L
        val at = prefs.getLong(KEY_AT, 0L)
        if (at <= 0L) return -1L
        return System.currentTimeMillis() - at
    }

    /** True when a mark exists and is still young enough to resume. */
    fun resumable(context: Context): Boolean = age(context) in 0L..RESUME_WINDOW_MS

    fun clear(context: Context) {
        prefs(context).edit {
            remove(KEY_ID)
            remove(KEY_AT)
        }
    }

    /**
     * Sweeps a mark that is too old to resume, so the next restart cannot act on
     * it. Called from the heal pass.
     *
     * @return true when a stale mark was cleared.
     */
    fun clearStale(context: Context): Boolean {
        val id = marked(context)
        if (id < 0L) return false
        val age = age(context)
        if (age in 0L..RESUME_WINDOW_MS) return false
        clear(context)
        Diag.log(context, "RING 响铃被打断且已过补响窗口", "#$id")
        return true
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
