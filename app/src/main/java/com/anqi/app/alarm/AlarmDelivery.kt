package com.anqi.app.alarm

import android.content.Context
import android.util.Log
import com.anqi.app.data.AlarmStore
import com.anqi.app.model.Alarm
import com.anqi.app.model.RepeatType

/**
 * The single "an alarm goes off now" path.
 *
 * Both a normal delivery ([AlarmReceiver]) and a late catch-up ([CatchUp]) go
 * through here, so a recovered reminder advances its schedule exactly like a
 * punctual one. Having two copies of this logic is how a "late ring" ends up
 * repeating every minute.
 */
object AlarmDelivery {

    /** Advances the schedule, persists it, and starts the ring. Returns the updated alarm. */
    fun fire(context: Context, alarm: Alarm, store: AlarmStore = AlarmStore(context)): Alarm {
        val now = System.currentTimeMillis()
        val updated = advance(context, alarm, now, store)
        ArmLedger.clearArmed(context, updated.id)
        GuardService.start(context)
        RingtoneService.start(context, updated)
        return updated
    }

    fun advance(context: Context, alarm: Alarm, now: Long, store: AlarmStore): Alarm {
        val scheduler = AlarmScheduler(context)
        if (alarm.repeatType == RepeatType.ONCE) {
            val updated = alarm.copy(enabled = false, lastFiredAt = now)
            store.upsert(updated)
            scheduler.cancel(updated.id)
            return updated
        }

        val remaining = alarm.remainingCount?.let { (it - 1).coerceAtLeast(0) }
        val enabled = remaining == null || remaining > 0
        val updated = alarm.copy(
            remainingCount = remaining,
            enabled = enabled,
            lastFiredAt = now
        )
        store.upsert(updated)
        if (enabled) {
            scheduler.schedule(updated)
        } else {
            scheduler.cancel(updated.id)
        }
        return updated
    }
}

/**
 * What turns "it never rang" into "it rang a bit late".
 *
 * The ledger knows the exact trigger time the app handed to AlarmManager. If that
 * moment has passed and nothing ever fired, the registration was dropped —
 * either by an OEM "clean the background", or because the process was frozen
 * through the delivery window. Ringing now is strictly better than staying
 * silent until next month, which is the failure the user actually reported.
 *
 * Only slots the app itself armed are recovered, so a reminder created *after*
 * its monthly day has already passed never triggers a phantom ring.
 */
object CatchUp {
    private const val TAG = "AnqiCatchUp"

    /** Beyond this, a late ring is more confusing than useful. */
    private const val WINDOW_MS = 12 * 60 * 60 * 1000L

    /**
     * @return how many reminders were rung late by this call.
     */
    fun run(context: Context): Int {
        val now = System.currentTimeMillis()
        val missed = runCatching { ArmLedger.audit(context) }
            .onFailure { Log.w(TAG, "Unable to audit the ledger", it) }
            .getOrDefault(emptyList())
        if (missed.isEmpty()) return 0

        var fired = 0
        val store = AlarmStore(context)
        missed.forEach { entry ->
            val late = now - entry.expectedAt
            if (late > WINDOW_MS) {
                Diag.log(context, "SKIP 太迟了不再补", "${entry.label} 迟 ${late / 60_000} 分钟")
                return@forEach
            }
            val alarm = store.find(entry.alarmId) ?: return@forEach
            if (!alarm.enabled) return@forEach
            Diag.log(
                context,
                "CATCHUP 补响",
                "${entry.label} 迟到 ${late / 60_000} 分钟（${ArmLedger.format(entry.expectedAt)} 未触发）"
            )
            runCatching { AlarmDelivery.fire(context, alarm, store) }
                .onFailure { Log.e(TAG, "Unable to ring the missed alarm ${alarm.id}", it) }
            fired += 1
        }
        return fired
    }
}

/**
 * The one repair routine every "we are awake again" entry point calls.
 *
 * Order matters and is easy to get wrong: the ledger is audited *before* the
 * queue is re-armed, because re-arming overwrites each expected trigger time
 * with the next one. Re-arming first would erase the evidence of the slot that
 * was silently dropped, and the reminder would stay silent until next month —
 * which is precisely the bug being fixed.
 */
object SelfHeal {
    fun heal(context: Context, ensureGuard: Boolean = false): Int {
        val store = AlarmStore(context)
        val scheduler = AlarmScheduler(context)
        val late = runCatching { CatchUp.run(context) }.getOrDefault(0)
        runCatching { RingResume.clearStale(context) }
        runCatching { scheduler.rescheduleAll(store.all(), ensureGuard = ensureGuard) }
        runCatching { scheduler.scheduleHeartbeat() }
        runCatching { GuardJob.schedule(context) }
        if (ensureGuard) runCatching { GuardService.start(context) }
        return late
    }
}
