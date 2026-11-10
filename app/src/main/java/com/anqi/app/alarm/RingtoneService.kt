package com.anqi.app.alarm

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.anqi.app.data.AlarmStore
import com.anqi.app.model.Alarm

class RingtoneService : Service() {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var screenLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private var currentAlarmId: Long = -1L
    private var currentAlarm: Alarm? = null
    private var unlockReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        AlarmNotifier.ensureChannel(this)
        Diag.log(this, "RING 响铃服务启动")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            RingResume.clear(this)
            stopSelf()
            return START_NOT_STICKY
        }

        val requestedId = intent?.getLongExtra(AlarmScheduler.EXTRA_ID, -1L) ?: -1L
        // A null intent means the system restarted us after reclaiming the
        // process, which is exactly the case where the alarm was still ringing
        // and the user never dismissed it. The age check matters just as much:
        // resuming a stale mark would make the phone scream at some arbitrary
        // later moment, which is worse than having stayed quiet.
        val resuming = requestedId < 0L
        val alarmId = when {
            !resuming -> requestedId
            RingResume.resumable(this) -> RingResume.marked(this)
            else -> -1L
        }
        val alarm = if (alarmId >= 0L) AlarmStore(this).find(alarmId) else null
        if (alarm == null) {
            RingResume.clear(this)
            stopSelf()
            return START_NOT_STICKY
        }
        if (resuming) {
            Diag.log(this, "RING 响铃被打断后自动恢复", "#${alarm.id} · ${RingResume.age(this) / 1000} 秒")
        }

        currentAlarmId = alarm.id
        currentAlarm = alarm
        RingResume.mark(this, alarm.id)
        activeAlarmId = alarm.id
        nextRingToken()
        val notification = AlarmNotifier.build(this, alarm)
        try {
            startForeground(AlarmNotifier.NOTIFICATION_BASE + alarm.id.toInt(), notification)
        } catch (t: Throwable) {
            // Android 12+ refuses a foreground start from the background and many
            // OEM ROMs refuse it outright. The notification then has to be the
            // alarm itself, so it goes out on the channel that carries a sound.
            Log.e(TAG, "startForeground failed", t)
            Diag.log(this, "RING 前台启动被拒，改用有声通知")
            AlarmNotifier.notify(this, alarm, audible = true)
        }

        if (player == null) startSound(alarm)
        if (alarm.vibrate) startVibration()
        acquireWakeLock()
        // A partial wake lock keeps the CPU alive but never lights the panel. The
        // ringing screen normally does that through setTurnScreenOn(); this short
        // full wake lock covers the case where the OEM refuses to start it.
        wakeScreen()
        // Some OEMs ignore full-screen notifications. Open the ringing screen
        // directly from the foreground service so the alarm is always dismissible.
        AlarmNotifier.launch(this, alarm)
        watchForUnlock()
        handler.removeCallbacks(stopRunnable)
        handler.postDelayed(stopRunnable, AUTO_STOP_MS)
        // Not START_NOT_STICKY: a ringing alarm that gets killed with the app is
        // the loudest possible bug. The system restarts the service with a null
        // intent, and the persisted id above re-enters the ring.
        return START_STICKY
    }

    /**
     * The one case the full-screen intent cannot cover: the ROM refuses to put
     * the ring screen above the lock screen, so the user has to unlock before
     * they can reach it. Watching for the unlock and re-opening the screen at
     * that exact moment is the difference between "it is right there" and
     * "unlock, then go hunting in the launcher" — which is what the user
     * actually complained about.
     *
     * Registered at runtime on purpose: a manifest receiver for
     * ACTION_USER_PRESENT would wake us up for every unlock of the day.
     */
    private fun watchForUnlock() {
        if (unlockReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != Intent.ACTION_USER_PRESENT) return
                val alarm = currentAlarm ?: return
                Log.i(TAG, "Unlocked while ringing; bringing the alarm forward")
                AlarmNotifier.launch(this@RingtoneService, alarm)
            }
        }
        unlockReceiver = receiver
        runCatching {
            ContextCompat.registerReceiver(
                this,
                receiver,
                IntentFilter(Intent.ACTION_USER_PRESENT),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }.onFailure { Log.w(TAG, "Unable to watch for unlock", it) }
    }

    private fun stopWatchingForUnlock() {
        val receiver = unlockReceiver ?: return
        unlockReceiver = null
        runCatching { unregisterReceiver(receiver) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        stopSound()
        stopVibration()
        releaseWakeLock()
        stopWatchingForUnlock()
        currentAlarm = null
        activeAlarmId = -1L
        Diag.log(this, "RING 响铃服务结束")
        super.onDestroy()
    }

    private fun startSound(alarm: Alarm) {
        val uri: Uri = alarm.soundUri?.let(Uri::parse) ?: AlarmNotifier.alarmSoundUri()
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(AlarmNotifier.audioAttributes())
                setDataSource(this@RingtoneService, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Unable to play alarm sound", t)
            player?.release()
            player = null
        }
    }

    private fun stopSound() {
        player?.runCatching {
            if (isPlaying) stop()
            release()
        }
        player = null
    }

    /**
     * Vibration is tagged with [android.os.VibrationAttributes.USAGE_ALARM]. Without
     * that tag the platform routes the pattern through the "touch" channel, which
     * stays silent when the phone is in vibrate/silent mode. The channel-level
     * vibration is deliberately off, because that one only fires once.
     */
    private fun startVibration() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        this.vibrator = vibrator
        if (vibrator == null || !vibrator.hasVibrator()) {
            Log.w(TAG, "Device reports no vibrator")
            return
        }

        // Repeat from index 0, so the alarm keeps buzzing until it is stopped.
        val pattern = longArrayOf(0L, 800L, 600L, 800L, 600L)
        val effect = VibrationEffect.createWaveform(pattern, 0)

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                vibrator.vibrate(
                    effect,
                    VibrationAttributes.Builder()
                        .setUsage(VibrationAttributes.USAGE_ALARM)
                        .build()
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(effect, AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
            }
        }.onFailure { Log.e(TAG, "Unable to start vibration", it) }
    }

    private fun stopVibration() {
        runCatching { vibrator?.cancel() }
        vibrator = null
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val power = getSystemService(PowerManager::class.java)
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "anqi:ringing").apply {
            setReferenceCounted(false)
            acquire(10 * 60_000L)
        }
    }

    /**
     * Briefly holds a full wake lock so the display lights up even when the
     * ringing activity never becomes visible. It is released as soon as the
     * activity has had time to attach its own FLAG_KEEP_SCREEN_ON.
     */
    private fun wakeScreen() {
        if (screenLock?.isHeld == true) return
        val power = getSystemService(PowerManager::class.java)
        @Suppress("DEPRECATION")
        val level = PowerManager.FULL_WAKE_LOCK
        runCatching {
            @Suppress("DEPRECATION")
            screenLock = power.newWakeLock(
                level or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "anqi:ringing-screen"
            ).apply {
                setReferenceCounted(false)
                acquire(SCREEN_WAKE_MS)
            }
        }.onFailure { Log.e(TAG, "Unable to wake the screen", it) }
    }

    private fun releaseWakeLock() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
        runCatching { if (screenLock?.isHeld == true) screenLock?.release() }
        screenLock = null
    }

    private val stopRunnable = Runnable {
        Log.i(TAG, "Alarm auto-stopped after timeout")
        RingResume.clear(this)
        AlarmNotifier.cancel(this, currentAlarmId)
        stopSelf()
    }

    companion object {
        private const val TAG = "AnqiRingtone"
        private const val AUTO_STOP_MS = 5 * 60_000L
        private const val SCREEN_WAKE_MS = 30_000L
        const val ACTION_STOP = "com.anqi.app.action.STOP_RINGING"

        /**
         * Id of the alarm currently ringing, or -1. The ring screen is normally
         * opened by the full-screen intent, but when the ROM blocks that the
         * user is left staring at the launcher while the phone screams. This
         * lets the main activity notice "something is ringing right now" and
         * jump straight to the dismiss screen as soon as it is opened.
         */
        @Volatile
        var activeAlarmId: Long = -1L
            private set

        /**
         * Bumped for every ring, including a second ring of the same alarm
         * after a snooze. Routing on the id alone would treat the snooze as
         * "already handled" and leave the user on the list page.
         */
        @Volatile
        var activeRingToken: Long = 0L
            private set

        private fun nextRingToken(): Long {
            activeRingToken += 1
            return activeRingToken
        }

        fun start(context: Context, alarm: Alarm) {
            // Written before the start attempt, so a ring that is cut short by a
            // kill still leaves a trace the restart path can pick up.
            RingResume.mark(context, alarm.id)
            Diag.log(context, "RING 开始响铃", "#${alarm.id}")
            val intent = Intent(context, RingtoneService::class.java)
                .putExtra(AlarmScheduler.EXTRA_ID, alarm.id)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (t: Throwable) {
                // No service, so nothing is looping the tone: the notification
                // has to make the sound itself or the alarm is silent.
                Log.e(TAG, "Unable to start ringing service", t)
                Diag.log(context, "RING 服务起不来，改用有声通知", "#${alarm.id}")
                AlarmNotifier.notify(context, alarm, audible = true)
            }
        }

        fun stop(context: Context) {
            RingResume.clear(context)
            context.stopService(Intent(context, RingtoneService::class.java))
        }

        /** True when the phone is in silent/vibrate mode, used to keep the UI honest. */
        fun inSilentMode(context: Context): Boolean {
            val audio = context.getSystemService(AudioManager::class.java)
            return audio?.ringerMode != AudioManager.RINGER_MODE_NORMAL
        }
    }
}
