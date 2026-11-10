package com.anqi.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.anqi.app.MainActivity
import com.anqi.app.data.AlarmStore
import com.anqi.app.data.NextOccurrence
import com.anqi.app.model.Alarm
import java.time.ZonedDateTime
import kotlin.random.Random

class AlarmScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)

    fun scheduleAll(alarms: List<Alarm>) {
        alarms.forEach { schedule(it) }
    }

    /**
     * @param ensureGuard false when the caller *is* the guard service: starting
     *   it again from inside its own `onStartCommand` would make it re-enter
     *   itself forever.
     */
    fun schedule(alarm: Alarm, ensureGuard: Boolean = true) {
        val pending = alarmPendingIntent(alarm.id)
        if (!alarm.enabled) {
            alarmManager.cancel(pending)
            ArmLedger.clearArmed(appContext, alarm.id)
            return
        }

        val next = NextOccurrence.next(alarm, ZonedDateTime.now()) ?: run {
            alarmManager.cancel(pending)
            ArmLedger.clearArmed(appContext, alarm.id)
            return
        }
        val triggerAt = next.toInstant().toEpochMilli()
        // Deliberately the list, not the ring screen: this is what the system
        // shows when the user taps the "next alarm" chip in the status bar, and
        // that can happen hours before the alarm fires. Sending it to the ring
        // screen made a future alarm look like it was ringing right now.
        // "Unlock and land on the ringing page" is handled by MainActivity
        // instead, which only routes when a ring is actually in progress.
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

        val armed = runCatching {
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

        armed.onSuccess {
            // The ledger is what lets the app notice afterwards that the system
            // did not keep this promise. Writing it only after the call returns
            // keeps a failed registration from looking like a missed alarm.
            ArmLedger.recordArmed(appContext, alarm.id, triggerAt)
            Diag.log(
                appContext,
                "ARM",
                "#${alarm.id} ${Diag.time(triggerAt)}${if (exactAllowed) "" else "（窗口）"}"
            )
        }.onFailure { t ->
            Log.e("AnqiScheduler", "Unable to arm alarm ${alarm.id}", t)
            Diag.log(appContext, "ARM 失败", "#${alarm.id} ${t.javaClass.simpleName}")
        }

        // A live foreground guard is what actually keeps the registration alive
        // on ROMs that clear the background; make sure it is up whenever a
        // reminder is armed.
        if (ensureGuard) GuardService.start(appContext)
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

    /**
     * Re-arms every enabled alarm from scratch.
     *
     * Callable from any process start: the app is redundant about scheduling on
     * purpose. `setAlarmClock` registrations normally survive a plain process
     * kill, but mainland ROMs "clean the background" by force-stopping the
     * package, which silently drops the whole alarm queue. Re-arming on every
     * start is what makes the failure self-healing instead of permanent.
     *
     * A pending snooze is deliberately left alone: a snoozed alarm is still
     * enabled, so re-arming the base alarm is harmless, while cancelling it
     * would eat the snooze the user is waiting for.
     */
    fun rescheduleAll(alarms: List<Alarm>, ensureGuard: Boolean = true) {
        alarms.forEach { alarm ->
            alarmManager.cancel(alarmPendingIntent(alarm.id))
            if (alarm.enabled) schedule(alarm, ensureGuard) else ArmLedger.clearArmed(appContext, alarm.id)
        }
    }

    /**
     * Wakes the process up every few hours so [AlarmReceiver] can re-arm the
     * queue. `setAlarmClock` itself is not something the OS normally drops, but
     * a force-stop clears our alarms and the app then has no reason to ever run
     * again — this trigger is the safety net that turns a dead install back into
     * a working one.
     *
     * A one-shot `setAndAllowWhileIdle` re-armed on every fire, rather than
     * `setInexactRepeating`: repeating alarms are batched into Doze maintenance
     * windows and can slip by many hours, while `allowWhileIdle` is explicitly
     * delivered during Doze. Slack is deliberately generous (+/- 10 min) — the
     * exact minute of a book-keeping wake-up does not matter, and exact alarms
     * are a scarce resource that belongs to the user's reminders.
     */
    fun scheduleHeartbeat() {
        val triggerAt = System.currentTimeMillis() + HEARTBEAT_INTERVAL_MS +
            Random.nextLong(0L, HEARTBEAT_SLACK_MS)
        try {
            // minSdk is 26, so `setAndAllowWhileIdle` is always available.
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                heartbeatPendingIntent()
            )
        } catch (t: Throwable) {
            Log.e("AnqiScheduler", "Unable to schedule heartbeat", t)
        }
    }

    fun cancelHeartbeat() {
        alarmManager.cancel(heartbeatPendingIntent())
    }

    /**
     * Rings [alarm] a few seconds from now, through the exact same pipeline a
     * real reminder uses. It touches no alarm state (no `lastFiredAt`, no
     * countdown) — it exists so the user can clear the app from the recents
     * list and still see, seconds later, whether the ring survived. Waiting a
     * month to find out is not a test.
     */
    fun scheduleTestRing(alarm: Alarm, delayMs: Long = 10_000L) {
        val triggerAt = System.currentTimeMillis() + delayMs
        val pending = testPendingIntent(alarm.id)
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()
        if (exactAllowed) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    fun cancelTestRing(id: Long) {
        alarmManager.cancel(testPendingIntent(id))
    }

    private fun testPendingIntent(id: Long): PendingIntent =
        PendingIntent.getBroadcast(
            appContext,
            requestCode(id, ROLE_TEST),
            Intent(appContext, AlarmReceiver::class.java)
                .setAction(ACTION_TEST_FIRE)
                .putExtra(EXTRA_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun heartbeatPendingIntent(): PendingIntent =
        PendingIntent.getBroadcast(
            appContext,
            HEARTBEAT_REQUEST_CODE,
            Intent(appContext, AlarmReceiver::class.java).setAction(ACTION_HEARTBEAT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

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
        const val ACTION_HEARTBEAT = "com.anqi.app.action.HEARTBEAT"
        const val ACTION_TEST_FIRE = "com.anqi.app.action.TEST_FIRE"
        const val EXTRA_ID = "alarm_id"

        private const val ROLE_ALARM = 1
        private const val ROLE_SNOOZE = 2
        private const val ROLE_SHOW = 3
        private const val ROLE_TEST = 4
        private const val HEARTBEAT_REQUEST_CODE = 907_113
        private const val HEARTBEAT_INTERVAL_MS = 6 * 60 * 60 * 1000L
        private const val HEARTBEAT_SLACK_MS = 10 * 60 * 1000L
    }
}
