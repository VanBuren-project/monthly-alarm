package com.anqi.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.anqi.app.MainActivity
import com.anqi.app.data.AlarmStore
import com.anqi.app.data.NextOccurrence
import com.anqi.app.model.Alarm
import java.time.ZonedDateTime

class AlarmScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)

    fun scheduleAll(alarms: List<Alarm>) {
        alarms.forEach(::schedule)
    }

    fun schedule(alarm: Alarm) {
        val pending = alarmPendingIntent(alarm.id)
        if (!alarm.enabled) {
            alarmManager.cancel(pending)
            return
        }

        val next = NextOccurrence.next(alarm, ZonedDateTime.now()) ?: run {
            alarmManager.cancel(pending)
            return
        }
        val triggerAt = next.toInstant().toEpochMilli()
        val showIntent = PendingIntent.getActivity(
            appContext,
            requestCode(alarm.id, ROLE_SHOW),
            Intent(appContext, MainActivity::class.java)
                .putExtra(EXTRA_ID, alarm.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()

        if (exactAllowed) {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerAt, showIntent),
                pending
            )
        } else {
            alarmManager.setWindow(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                60_000L,
                pending
            )
        }
    }

    fun scheduleSnooze(alarm: Alarm, minutes: Int) {
        val triggerAt = System.currentTimeMillis() + minutes.coerceIn(1, 120) * 60_000L
        val pending = snoozePendingIntent(alarm.id)
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()
        if (exactAllowed) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    fun cancel(id: Long) {
        alarmManager.cancel(alarmPendingIntent(id))
        alarmManager.cancel(snoozePendingIntent(id))
    }

    fun cancelSnooze(id: Long) {
        alarmManager.cancel(snoozePendingIntent(id))
    }

    fun cancelAll() {
        AlarmStore(appContext).all().forEach { cancel(it.id) }
    }

    fun rescheduleAll(alarms: List<Alarm>) {
        alarms.forEach { alarm ->
            alarmManager.cancel(alarmPendingIntent(alarm.id))
            alarmManager.cancel(snoozePendingIntent(alarm.id))
            if (alarm.enabled) schedule(alarm)
        }
    }

    private fun alarmPendingIntent(id: Long): PendingIntent =
        PendingIntent.getBroadcast(
            appContext,
            requestCode(id, ROLE_ALARM),
            Intent(appContext, AlarmReceiver::class.java)
                .setAction(ACTION_ALARM)
                .putExtra(EXTRA_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun snoozePendingIntent(id: Long): PendingIntent =
        PendingIntent.getBroadcast(
            appContext,
            requestCode(id, ROLE_SNOOZE),
            Intent(appContext, AlarmReceiver::class.java)
                .setAction(ACTION_SNOOZE_FIRE)
                .putExtra(EXTRA_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun requestCode(id: Long, role: Int): Int {
        val mixed = (id xor (id ushr 32)).toInt()
        return mixed * 7 + role
    }

    companion object {
        const val ACTION_ALARM = "com.anqi.app.action.ALARM"
        const val ACTION_SNOOZE_FIRE = "com.anqi.app.action.SNOOZE_FIRE"
        const val ACTION_SNOOZE = "com.anqi.app.action.SNOOZE"
        const val ACTION_DISMISS = "com.anqi.app.action.DISMISS"
        const val EXTRA_ID = "alarm_id"

        private const val ROLE_ALARM = 1
        private const val ROLE_SNOOZE = 2
        private const val ROLE_SHOW = 3
    }
}
