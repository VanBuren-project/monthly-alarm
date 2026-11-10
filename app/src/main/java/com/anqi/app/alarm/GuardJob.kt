package com.anqi.app.alarm

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log
import com.anqi.app.data.AlarmStore

/**
 * A third, independent way back from the dead.
 *
 * The guard service handles the common case (the process is kept alive), and the
 * six-hour heartbeat handles "the process was gone for a while, wake it up". This
 * job is deliberately a *different* subsystem: `JobScheduler` keeps its own
 * bookkeeping, survives reboots through `setPersisted`, and the platform
 * re-schedules periodic jobs on its own. If whatever the ROM did also swept
 * AlarmManager, this still fires and puts the queue back.
 *
 * It is a fallback, not the primary mechanism — periodic jobs are inexact and
 * can be deferred while the phone sleeps, so it never decides *when* a reminder
 * rings.
 */
class GuardJob : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        val store = AlarmStore(this)
        val late = runCatching { SelfHeal.heal(this) }.getOrDefault(0)
        GuardService.start(this)
        Diag.log(
            this,
            "JOB 定时校检",
            "${store.all().count { it.enabled }} 个已启用" + if (late > 0) "，补响 $late 个" else ""
        )
        Log.i(TAG, "Job re-armed ${store.all().size} alarm(s)")
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean = true

    companion object {
        private const val TAG = "AnqiGuardJob"
        private const val JOB_ID = 4101
        private const val INTERVAL_MS = 15 * 60_000L

        /**
         * Idempotent: JobScheduler replaces the existing job with the same id, so
         * calling this from every process start is cheap and cannot pile up.
         */
        fun schedule(context: Context) {
            val scheduler = context.applicationContext
                .getSystemService(JobScheduler::class.java) ?: return
            val component = ComponentName(context, GuardJob::class.java)
            if (scheduler.getPendingJob(JOB_ID) != null) return
            val info = JobInfo.Builder(JOB_ID, component)
                .setPeriodic(INTERVAL_MS)
                // Survives a reboot without waiting for BOOT_COMPLETED, and needs
                // no network: it only touches local state.
                .setPersisted(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
                .apply {
                    // Job priorities only exist from 33; below that the platform
                    // orders jobs itself and there is nothing to ask for.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        setPriority(JobInfo.PRIORITY_LOW)
                    }
                }
                .build()
            runCatching { scheduler.schedule(info) }
                .onFailure {
                    Log.e(TAG, "Unable to schedule the guard job", it)
                    Diag.log(context, "JOB 排期失败", it.javaClass.simpleName)
                }
        }
    }
}
