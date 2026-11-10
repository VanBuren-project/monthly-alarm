package com.anqi.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.anqi.app.data.AlarmStore
import com.anqi.app.model.Alarm
import com.anqi.app.model.RepeatType

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(AlarmScheduler.EXTRA_ID, -1L)
        if (id < 0) return
        val store = AlarmStore(context)
        val alarm = store.find(id) ?: return

        when (intent.action) {
            AlarmScheduler.ACTION_ALARM -> {
                if (!alarm.enabled) return
                val now = System.currentTimeMillis()
                val last = alarm.lastFiredAt
                if (last != null && now - last < 30_000L) {
                    Log.w("AnqiReceiver", "Ignoring duplicate delivery for ${alarm.id}")
                    return
                }
                val updated = advance(context, alarm, now, store)
                RingtoneService.start(context, updated)
            }

            AlarmScheduler.ACTION_SNOOZE_FIRE -> {
                RingtoneService.start(context, alarm)
            }

            AlarmScheduler.ACTION_SNOOZE -> {
                RingtoneService.stop(context)
                AlarmNotifier.cancel(context, alarm.id)
                AlarmScheduler(context).scheduleSnooze(alarm, store.snoozeMinutes)
            }

            AlarmScheduler.ACTION_DISMISS -> {
                RingtoneService.stop(context)
                AlarmNotifier.cancel(context, alarm.id)
                AlarmScheduler(context).cancelSnooze(alarm.id)
            }
        }
    }

    private fun advance(
        context: Context,
        alarm: Alarm,
        now: Long,
        store: AlarmStore
    ): Alarm {
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
        if (enabled) scheduler.schedule(updated) else scheduler.cancel(updated.id)
        return updated
    }
}
