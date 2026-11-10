package com.anqi.app.alarm

import android.app.Service
import android.content.Context
import android.content.Intent
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

    override fun onCreate() {
        super.onCreate()
        AlarmNotifier.ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val alarmId = intent?.getLongExtra(AlarmScheduler.EXTRA_ID, -1L) ?: -1L
        val alarm = AlarmStore(this).find(alarmId)
        if (alarm == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        currentAlarmId = alarm.id
        val notification = AlarmNotifier.build(this, alarm)
        try {
            startForeground(AlarmNotifier.NOTIFICATION_BASE + alarm.id.toInt(), notification)
        } catch (t: Throwable) {
            Log.e(TAG, "startForeground failed", t)
            AlarmNotifier.notify(this, alarm)
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
        handler.removeCallbacks(stopRunnable)
        handler.postDelayed(stopRunnable, AUTO_STOP_MS)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        stopSound()
        stopVibration()
        releaseWakeLock()
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
        AlarmNotifier.cancel(this, currentAlarmId)
        stopSelf()
    }

    companion object {
        private const val TAG = "AnqiRingtone"
        private const val AUTO_STOP_MS = 5 * 60_000L
        private const val SCREEN_WAKE_MS = 30_000L
        const val ACTION_STOP = "com.anqi.app.action.STOP_RINGING"

        fun start(context: Context, alarm: Alarm) {
            val intent = Intent(context, RingtoneService::class.java)
                .putExtra(AlarmScheduler.EXTRA_ID, alarm.id)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (t: Throwable) {
                Log.e(TAG, "Unable to start ringing service", t)
                AlarmNotifier.notify(context, alarm)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RingtoneService::class.java))
        }

        /** True when the phone is in silent/vibrate mode, used to keep the UI honest. */
        fun inSilentMode(context: Context): Boolean {
            val audio = context.getSystemService(AudioManager::class.java)
            return audio?.ringerMode != AudioManager.RINGER_MODE_NORMAL
        }
    }
}
