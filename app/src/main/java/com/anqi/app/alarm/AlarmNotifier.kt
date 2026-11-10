package com.anqi.app.alarm

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.anqi.app.AlarmActivity
import com.anqi.app.R
import com.anqi.app.model.Alarm
import java.util.Locale

object AlarmNotifier {
    /**
     * Bumped from `anqi_alarm` because channel settings are immutable once
     * created: the first build shipped a channel with vibration disabled, and only
     * a new id can pick up an alarm-grade configuration on installed devices.
     */
    const val CHANNEL_ID = "anqi_alarm_v2"

    /** Mirror of [CHANNEL_ID] for alarms whose 震动 switch is off. */
    private const val QUIET_CHANNEL_ID = "anqi_alarm_v2_quiet"

    /**
     * The last-resort channel, and the only one that carries a sound.
     *
     * It exists for exactly one situation: the ringing foreground service could
     * not be started (Android 12+ background-start rules, an OEM blocking it, or
     * no exact-alarm permission so no temporary allowlist was granted). In that
     * case the notification *is* the alarm, and a silent alarm channel — which
     * is what the normal path uses, because the service loops the tone itself —
     * would mean the phone stays completely quiet. That is the "nothing rings at
     * all" report, so this channel must not inherit that design.
     */
    private const val FALLBACK_CHANNEL_ID = "anqi_alarm_audible"
    private const val LEGACY_CHANNEL_ID = "anqi_alarm"
    const val NOTIFICATION_BASE = 7100

    /** Action + request-code offset that keep the full-screen intent its own PendingIntent. */
    private const val ACTION_FULLSCREEN = "com.anqi.app.action.FULLSCREEN_RING"
    private const val FULLSCREEN_REQUEST_OFFSET = 1_000_003

    private val VIBRATION_PATTERN = longArrayOf(0L, 800L, 600L, 800L, 600L)

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        // The 1.3.x channel was created silent; it can never be changed in place.
        runCatching { manager.deleteNotificationChannel(LEGACY_CHANNEL_ID) }

        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.channel_alarm_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.channel_alarm_desc)
                    // Sound stays off: RingtoneService loops the alarm tone and the
                    // channel could only play it once. Vibration stays on as a
                    // safety net for ROMs that drop the service's waveform.
                    setSound(null, null)
                    enableVibration(true)
                    vibrationPattern = VIBRATION_PATTERN
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    setShowBadge(false)
                }
            )
        }
        if (manager.getNotificationChannel(QUIET_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    QUIET_CHANNEL_ID,
                    context.getString(R.string.channel_alarm_quiet_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.channel_alarm_desc)
                    setSound(null, null)
                    enableVibration(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    setShowBadge(false)
                }
            )
        }
        if (manager.getNotificationChannel(FALLBACK_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    FALLBACK_CHANNEL_ID,
                    context.getString(R.string.channel_alarm_fallback_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.channel_alarm_fallback_desc)
                    setSound(
                        alarmSoundUri(),
                        audioAttributes()
                    )
                    enableVibration(true)
                    vibrationPattern = VIBRATION_PATTERN
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    setShowBadge(false)
                    // An alarm must be heard through Do Not Disturb, exactly like
                    // the platform clock app.
                    setBypassDnd(true)
                }
            )
        }
    }

    /**
     * @param audible use the channel that plays a sound itself. Only for the
     *   fallback path where no ringing service is running to loop the tone.
     */
    fun build(
        context: Context,
        alarm: Alarm,
        includeFullScreen: Boolean = true,
        audible: Boolean = false
    ): Notification {
        ensureChannel(context)
        val notificationId = notificationId(alarm.id)
        val time = String.format(Locale.US, "%02d:%02d", alarm.hour, alarm.minute)
        val label = alarm.label.ifBlank { context.getString(R.string.alarm_default_label) }

        val contentIntent = ringIntent(context, notificationId, alarm.id)

        val snoozeIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 1,
            Intent(context, AlarmReceiver::class.java)
                .setAction(AlarmScheduler.ACTION_SNOOZE)
                .putExtra(AlarmScheduler.EXTRA_ID, alarm.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 2,
            Intent(context, AlarmReceiver::class.java)
                .setAction(AlarmScheduler.ACTION_DISMISS)
                .putExtra(AlarmScheduler.EXTRA_ID, alarm.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channel = when {
            audible -> FALLBACK_CHANNEL_ID
            alarm.vibrate -> CHANNEL_ID
            else -> QUIET_CHANNEL_ID
        }
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$time  $label")
            .setContentText(context.getString(R.string.alarm_notification_title))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setShowWhen(false)
            .setContentIntent(contentIntent)
            .setDeleteIntent(dismissIntent)
            .setTimeoutAfter(6 * 60_000L)
            .addAction(
                R.drawable.ic_notification,
                context.getString(R.string.alarm_snooze),
                snoozeIntent
            )
            .addAction(
                R.drawable.ic_notification,
                context.getString(R.string.alarm_dismiss),
                dismissIntent
            )
            .setSound(null)
            // null means "inherit the channel": buzz for the alarm channel, stay
            // still for the quiet one.
            .setVibrate(null)

        if (includeFullScreen) {
            // A *separate* PendingIntent from the content one, with its own action
            // string. Reusing the content intent's identity means the system has
            // one PendingIntent playing two roles: it dedupes the two launches,
            // and on several ROMs the full-screen launch ends up treated as the
            // ordinary content launch (no keyguard bypass, no screen wake) — the
            // "it rings but the lockscreen shows nothing to press" report. The
            // stock clock keeps them apart the same way.
            builder.setFullScreenIntent(fullScreenIntent(context, notificationId, alarm.id), true)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        }
        return builder.build()
    }

    private fun ringIntent(context: Context, requestCode: Int, alarmId: Long): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, AlarmActivity::class.java)
                .putExtra(AlarmScheduler.EXTRA_ID, alarmId)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /**
     * The "wake the phone and take over the screen" intent, kept distinct from
     * [ringIntent] on purpose: different action, different request code. That is
     * how the AOSP clock does it, and it is what keeps the system from collapsing
     * the two into one launch.
     */
    private fun fullScreenIntent(context: Context, requestCode: Int, alarmId: Long): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode + FULLSCREEN_REQUEST_OFFSET,
            Intent(context, AlarmActivity::class.java)
                .setAction(ACTION_FULLSCREEN)
                .putExtra(AlarmScheduler.EXTRA_ID, alarmId)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_USER_ACTION or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /**
     * Full-screen notifications are the sanctioned path, but they are also the
     * first thing OEM ROMs disable. Starting the activity from the ringing
     * foreground service is the fallback; when the ROM blocks it, the exception is
     * logged to 运行记录 as well as logcat, because that on-device log is the only
     * evidence available when the user reports "sound but no screen".
     */
    fun launch(context: Context, alarm: Alarm) {
        val intent = Intent(context, AlarmActivity::class.java)
            .putExtra(AlarmScheduler.EXTRA_ID, alarm.id)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION
            )
        runCatching { context.startActivity(intent) }
            .onFailure { error ->
                android.util.Log.w(
                    "AnqiNotifier",
                    "startActivity blocked (fullScreen=${canUseFullScreenIntent(context)}, " +
                        "overlay=${canDrawOverlays(context)})",
                    error
                )
                Diag.log(
                    context,
                    "RING 响铃页被系统拒绝",
                    "全屏意图=${canUseFullScreenIntent(context).let { if (it) "已授权" else "未授权" }}" +
                        "，悬浮窗=${canDrawOverlays(context).let { if (it) "已授权" else "未授权" }}"
                )
            }
    }

    // The POST_NOTIFICATIONS guard is inlined instead of delegated to
    // canPostNotifications(): lint only trusts a checkSelfPermission call that is
    // visible in this method, and the annotation documents that trade-off.
    /**
     * Falls back to a *sounding* notification, because reaching this method means
     * no service is looping the tone. A silent notification here is the
     * "I got nothing at all" failure.
     */
    @android.annotation.SuppressLint("MissingPermission")
    fun notify(context: Context, alarm: Alarm, audible: Boolean = true) {
        ensureChannel(context)
        if (!canPostNotifications(context)) return
        runCatching {
            NotificationManagerCompat.from(context)
                .notify(notificationId(alarm.id), build(context, alarm, audible = audible))
        }
    }

    fun cancel(context: Context, alarmId: Long) {
        NotificationManagerCompat.from(context).cancel(notificationId(alarmId))
    }

    fun canPostNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    fun canUseFullScreenIntent(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun notificationId(alarmId: Long): Int = NOTIFICATION_BASE + alarmId.toInt()

    fun alarmSoundUri(): android.net.Uri =
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

    fun audioAttributes(): AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
}
