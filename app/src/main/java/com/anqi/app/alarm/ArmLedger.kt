package com.anqi.app.alarm

import android.content.Context
import androidx.core.content.edit
import com.anqi.app.data.AlarmStore
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Records *what we promised the system to do* ("this reminder is armed for
 * 08:00 on the 8th") and, later, checks whether that promise was kept.
 *
 * This is the only way to tell the difference between two failures that look
 * identical from the outside:
 *
 *  - the alarm was never armed (scheduling is broken), or
 *  - the alarm was armed and the ROM swallowed it (the process was frozen or
 *    force-stopped before the broadcast could be delivered).
 *
 * Without it every report is just "it didn't ring", which cannot be fixed.
 */
object ArmLedger {
    private const val PREFS = "anqi_ledger"
    private const val KEY_MISSED = "missed"
    private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun key(id: Long) = "expect_$id"

    /** Called every time [AlarmScheduler] hands a trigger time to AlarmManager. */
    fun recordArmed(context: Context, alarmId: Long, triggerAt: Long) {
        prefs(context).edit { putLong(key(alarmId), triggerAt) }
    }

    fun clearArmed(context: Context, alarmId: Long) {
        prefs(context).edit { remove(key(alarmId)) }
    }

    fun expectedFor(context: Context, alarmId: Long): Long =
        prefs(context).getLong(key(alarmId), -1L)

    /**
     * Compares every recorded expectation with what actually happened.
     *
     * @return the enabled reminders whose trigger time has come and gone without
     *   the receiver recording a fire. Each one is logged once, so the count in
     *   the diagnostics screen is "how many times we were silently skipped".
     */
    fun audit(context: Context, graceMs: Long = 2 * 60_000L): List<Missed> {
        val now = System.currentTimeMillis()
        val store = AlarmStore(context)
        val missed = mutableListOf<Missed>()
        store.all().forEach { alarm ->
            val expected = expectedFor(context, alarm.id)
            if (expected <= 0L) return@forEach
            if (!alarm.enabled) {
                // Turned off or already consumed: nothing was promised any more.
                clearArmed(context, alarm.id)
                return@forEach
            }
            if (now - expected < graceMs) return@forEach
            val firedAt = alarm.lastFiredAt ?: 0L
            if (firedAt >= expected) {
                // It did ring; only the bookkeeping entry was left behind.
                clearArmed(context, alarm.id)
                return@forEach
            }
            missed += Missed(
                alarmId = alarm.id,
                label = alarm.label.ifBlank { "提醒" },
                expectedAt = expected
            )
            clearArmed(context, alarm.id)
        }
        if (missed.isNotEmpty()) {
            val total = missedCount(context) + missed.size
            prefs(context).edit { putInt(KEY_MISSED, total) }
            missed.forEach {
                Diag.log(
                    context,
                    "MISSED 到点没响",
                    "${it.label} 应在 ${format(it.expectedAt)} 触发"
                )
            }
        }
        return missed
    }

    fun missedCount(context: Context): Int = prefs(context).getInt(KEY_MISSED, 0)

    fun resetMissed(context: Context) {
        prefs(context).edit { putInt(KEY_MISSED, 0) }
        AlarmStore(context).all().forEach { clearArmed(context, it.id) }
    }

    fun format(millis: Long): String =
        Instant.ofEpochMilli(millis)
            .atZone(ZoneId.systemDefault())
            .format(STAMP)

    data class Missed(val alarmId: Long, val label: String, val expectedAt: Long)
}
