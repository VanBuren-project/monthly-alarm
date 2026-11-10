package com.anqi.app.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.anqi.app.MainActivity
import com.anqi.app.R
import com.anqi.app.data.AlarmStore
import com.anqi.app.data.NextOccurrence
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Keeps the process (and therefore a live AlarmManager registration) alive.
 *
 * This is the part 1.3.3 got wrong. Re-arming on process start and a six-hour
 * heartbeat both assume the process will run again *on its own* — but the thing
 * that breaks these installs is the process being killed and never coming back,
 * and the heartbeat lives in AlarmManager, so it gets cleared together with the
 * alarms it was supposed to resurrect. A watchdog that is itself an alarm cannot
 * rescue anything.
 *
 * A foreground service with an ongoing notification is the only mechanism
 * Android lets an app use to *stay* running: the system keeps the process in a
 * high-priority bucket, and the user can see it is alive. On top of that:
 *
 *  - [START_STICKY] brings the service back when the system reclaims memory;
 *  - a persisted flag re-arms it after a reboot / app update;
 *  - the notification always shows the next reminder, so "is it working?" is
 *    answerable by glance instead of by waiting a month.
 */
class GuardService : Service() {

    override fun onCreate() {
        super.onCreate()
        AlarmNotifier.ensureChannel(this)
        ensureGuardChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_GUARD) {
            setWanted(this, false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val persisted = AlarmStore(this)
        try {
            startForeground(NOTIFICATION_ID, buildNotification(nextOccurrence(persisted)))
        } catch (t: Throwable) {
            Log.e(TAG, "Unable to enter the foreground", t)
            return START_STICKY
        }

        // Being awake is the perfect moment to notice that a promise was broken
        // while we were gone, and to put the queue back in place.
        val late = runCatching { SelfHeal.heal(this) }.getOrDefault(0)
        // Refresh the notification afterwards: a guard that sits there showing a
        // stale "next reminder" is worse than no guard at all.
        val next = nextOccurrence(persisted)
        runCatching {
            getSystemService(NotificationManager::class.java)
                ?.notify(NOTIFICATION_ID, buildNotification(next))
        }
        Diag.log(
            this,
            "GUARD 守护服务在线",
            (next?.let { "下次 ${format(it)}" } ?: "暂无已启用的提醒") +
                if (late > 0) "，补响 $late 个" else ""
        )
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiping the app out of recents is the exact gesture that used to break
        // the alarms. Re-arm and keep the guard alive instead of letting the
        // process go down with the task.
        Diag.log(this, "GUARD 应用被划出最近任务")
        runCatching { SelfHeal.heal(this) }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        Diag.log(this, "GUARD 守护服务被销毁")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun nextOccurrence(store: AlarmStore): ZonedDateTime? =
        store.all()
            .asSequence()
            .filter { it.enabled }
            .mapNotNull { NextOccurrence.next(it) }
            .minOrNull()

    private fun buildNotification(next: ZonedDateTime?): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = next?.let { "下次提醒 ${format(it)}" } ?: "暂无已启用的提醒"
        return NotificationCompat.Builder(this, GUARD_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_guard)
            .setContentTitle(getString(R.string.guard_title))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun format(value: ZonedDateTime): String =
        value.format(
            DateTimeFormatter.ofPattern(
                if (value.toLocalDate() == java.time.LocalDate.now()) "今天 HH:mm" else "M月d日 HH:mm",
                Locale.CHINA
            )
        )

    companion object {
        private const val TAG = "AnqiGuard"
        const val GUARD_CHANNEL_ID = "anqi_guard"
        private const val NOTIFICATION_ID = 7001
        const val ACTION_STOP_GUARD = "com.anqi.app.action.STOP_GUARD"
        private const val PREFS = "anqi_guard"
        private const val KEY_WANTED = "wanted"

        /**
         * Whether the user wants the guard running. Persisted so the app can put
         * it back after a reboot: a reboot clears the process but not the intent.
         */
        fun isWanted(context: Context): Boolean =
            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_WANTED, true)

        private fun setWanted(context: Context, wanted: Boolean) {
            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit { putBoolean(KEY_WANTED, wanted) }
        }

        fun ensureGuardChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(GUARD_CHANNEL_ID) != null) return
            manager.createNotificationChannel(
                NotificationChannel(
                    GUARD_CHANNEL_ID,
                    context.getString(R.string.guard_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = context.getString(R.string.guard_channel_desc)
                    setShowBadge(false)
                    lockscreenVisibility = Notification.VISIBILITY_SECRET
                }
            )
        }

        /** Starts the guard unless the user turned it off in settings. */
        fun start(context: Context) {
            if (!isWanted(context)) return
            ensureGuardChannel(context)
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, GuardService::class.java)
                )
            } catch (t: Throwable) {
                Log.e(TAG, "Unable to start the guard", t)
                Diag.log(context, "GUARD 启动失败", t.message ?: t.javaClass.simpleName)
            }
        }

        /** Stops the guard and remembers that the user asked for this. */
        fun shutdown(context: Context) {
            setWanted(context, false)
            context.stopService(Intent(context, GuardService::class.java))
        }

        /** Turns the guard back on after an explicit opt-in in settings. */
        fun enable(context: Context) {
            setWanted(context, true)
            start(context)
        }
    }
}
