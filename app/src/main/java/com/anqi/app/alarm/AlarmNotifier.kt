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
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.anqi.app.AlarmActivity
import com.anqi.app.R
import com.anqi.app.model.Alarm
import java.util.Locale

object AlarmNotifier {
    const val CHANNEL_ID = "anqi_alarm"
    const val NOTIFICATION_BASE = 7100

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_alarm_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_alarm_desc)
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setBypassDnd(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun build(context: Context, alarm: Alarm, includeFullScreen: Boolean = true): Notification {
        ensureChannel(context)
        val notificationId = NOTIFICATION_BASE + alarm.id.toInt()
        val time = String.format(Locale.US, "%02d:%02d", alarm.hour, alarm.minute)
        val label = alarm.label.ifBlank { context.getString(R.string.alarm_default_label) }

        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, AlarmActivity::class.java)
                .putExtra(AlarmScheduler.EXTRA_ID, alarm.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

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

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
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
            .addAction(R.drawable.ic_notification, context.getString(R.string.alarm_snooze), snoozeIntent)
            .addAction(R.drawable.ic_notification, context.getString(R.string.alarm_dismiss), dismissIntent)
            .setSound(null)
            .setVibrate(null)

        if (includeFullScreen) {
            builder.setFullScreenIntent(contentIntent, true)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        }
        return builder.build()
    }

    /**
     * Full-screen notifications are the sanctioned path, but several OEM builds
     * silently drop them. Opening the ringing screen from the foreground service
     * as well gives the user a reliable way to dismiss the alarm.
     */
    fun launch(context: Context, alarm: Alarm) {
        val intent = Intent(context, AlarmActivity::class.java)
            .putExtra(AlarmScheduler.EXTRA_ID, alarm.id)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        runCatching { context.startActivity(intent) }
    }

    fun notify(context: Context, alarm: Alarm) {
        ensureChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        runCatching {
            NotificationManagerCompat.from(context)
                .notify(NOTIFICATION_BASE + alarm.id.toInt(), build(context, alarm))
        }
    }

    fun cancel(context: Context, alarmId: Long) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_BASE + alarmId.toInt())
    }

    fun alarmSoundUri(): android.net.Uri =
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

    fun audioAttributes(): AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
}
