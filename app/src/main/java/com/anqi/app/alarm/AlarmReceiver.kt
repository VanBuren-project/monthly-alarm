package com.anqi.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.anqi.app.data.AlarmStore

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // The heartbeat carries no alarm id, so it has to be handled before the
        // "every alarm broadcast knows which alarm it is about" guard below.
        if (intent.action == AlarmScheduler.ACTION_HEARTBEAT) {
            val store = AlarmStore(context)
            AlarmNotifier.ensureChannel(context)
            val scheduler = AlarmScheduler(context)
            scheduler.rescheduleAll(store.all())
            // Re-arm the heartbeat too: if its own registration was the thing
            // that got cleared, this is the only chance to put it back.
            scheduler.scheduleHeartbeat()
            // And put the foreground guard back: reaching this code at all means
            // the process was gone, so the guard was torn down with it.
            GuardService.start(context)
            GuardJob.schedule(context)
            // Reaching this code proves the process was dead until now, so this
            // is exactly the right moment to notice and repair a silent skip.
            val late = CatchUp.run(context)
            Diag.log(
                context,
                "HEARTBEAT 心跳补排",
                "${store.all().size} 个提醒${if (late > 0) "，补响 $late 个" else ""}"
            )
            Log.i("AnqiReceiver", "Heartbeat re-armed ${store.all().size} alarm(s)")
            return
        }

        val id = intent.getLongExtra(AlarmScheduler.EXTRA_ID, -1L)
        if (id < 0) return
        val store = AlarmStore(context)
        val alarm = store.find(id) ?: return

        when (intent.action) {
            // The same moment is armed twice (see AlarmScheduler): this broadcast,
            // and a service PendingIntent the system starts on its own. Whichever
            // arrives second is folded into the first by the lastFiredAt guard
            // instead of ringing twice or advancing the schedule twice.
            AlarmScheduler.ACTION_ALARM -> {
                if (!alarm.enabled) return
                val now = System.currentTimeMillis()
                val last = alarm.lastFiredAt
                if (last != null && now - last < AlarmDelivery.SLOT_DEDUPE_MS) {
                    Log.w("AnqiReceiver", "Ignoring duplicate delivery for ${alarm.id}")
                    return
                }
                Diag.log(context, "FIRE 闹钟触发", "#${alarm.id}")
                AlarmDelivery.fire(context, alarm, store)
            }

            AlarmScheduler.ACTION_TEST_FIRE -> {
                // Same ring path as a real reminder, minus any state change: a
                // test must not move the schedule, consume a repeat count or
                // leave lastFiredAt behind.
                Diag.log(context, "TEST 试响", "#${alarm.id}")
                RingtoneService.start(context, alarm)
            }

            AlarmScheduler.ACTION_SNOOZE_FIRE -> {
                Diag.log(context, "FIRE 贪睡触发", "#${alarm.id}")
                RingtoneService.start(context, alarm)
            }

            AlarmScheduler.ACTION_SNOOZE -> {
                RingtoneService.stop(context)
                AlarmNotifier.cancel(context, alarm.id)
                AlarmScheduler(context).scheduleSnooze(alarm, store.snoozeMinutes)
                Diag.log(context, "SNOOZE 贪睡 ${store.snoozeMinutes} 分钟", "#${alarm.id}")
            }

            AlarmScheduler.ACTION_DISMISS -> {
                RingtoneService.stop(context)
                AlarmNotifier.cancel(context, alarm.id)
                AlarmScheduler(context).cancelSnooze(alarm.id)
                Diag.log(context, "DISMISS 已关闭", "#${alarm.id}")
            }
        }
    }
}
