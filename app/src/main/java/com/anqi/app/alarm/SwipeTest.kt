package com.anqi.app.alarm

import android.content.Context
import androidx.core.content.edit
import com.anqi.app.data.AlarmStore

/**
 * Turns "清后台之后还响不响" from a month-long guess into a 60-second measurement.
 *
 * Everything else in the app talks about intentions — permissions granted, an
 * alarm armed, a guard running. None of that answers the only question a user
 * actually has after their phone stayed silent: *will this thing still ring when
 * I swipe it out of the recents list?* On mainland ROMs the honest answer is
 * "depends on a switch I cannot read", so the app measures it instead of
 * asserting it.
 *
 * The measurement reuses the production delivery path exactly — both
 * registrations, the broadcast one and the system-starts-the-service one, at the
 * same instant (see [AlarmScheduler.scheduleSwipeTest]). Nothing is faked: if a
 * ring survives a swipe here, a real reminder survives a swipe too.
 *
 * ## Why the process generation matters
 *
 * A ring that arrives while the process never died proves only that the ring
 * path works — it says nothing about surviving a swipe, because nothing was
 * swiped. So a snapshot of the process generation is taken when the test is
 * armed (see [noteProcessStart]) and compared when the ring lands:
 *
 *  - generation moved → the process was killed and the ring arrived anyway.
 *    That is the real thing, and it is the only outcome worth calling a pass.
 *  - generation unchanged → an honest "太早了，这次没测到清后台", not a pass.
 *
 * A test that simply never rang is judged by [settle], on the next process
 * start, because while the app is swiped away nothing of ours can run.
 */
object SwipeTest {

    private const val PREFS = "anqi_swipe"
    private const val KEY_PENDING_AT = "pending_at"
    private const val KEY_PENDING_ALARM = "pending_alarm"
    private const val KEY_PENDING_GEN = "pending_gen"
    private const val KEY_VERDICT_AT = "verdict_at"
    private const val KEY_VERDICT_RANG = "verdict_rang"
    private const val KEY_VERDICT_SWIPED = "verdict_swiped"
    private const val KEY_VERDICT_PATH = "verdict_path"
    private const val KEY_VERDICT_LATE = "verdict_late"
    private const val KEY_GEN = "gen"
    private const val KEY_SETUP_DONE = "setup_done"
    private const val KEY_SETUP_CONFIRMED_AT = "setup_confirmed_at"

    /** How far from the armed slot a ring can land and still count as this test. */
    private const val MATCH_WINDOW_MS = 5 * 60_000L

    /**
     * How long after a slot we give up waiting and call it a failure.
     *
     * Generous enough for a Doze delivery (the platform may hold a non-exact
     * wake-up for minutes) and short enough that the user sees the verdict the
     * moment they reopen the app.
     */
    private const val SETTLE_GRACE_MS = 90_000L

    /**
     * How long after a judgement a late ring may still overturn it.
     *
     * Doze can hold a non-exact alarm for far longer than [SETTLE_GRACE_MS], and
     * a phone that eventually rang is not a phone that stayed silent. Without
     * this, a ring that landed three minutes late would be discarded and the app
     * would report "没响" — sending the user to chase a switch that was already
     * fine, and hiding a genuinely late delivery that is worth knowing about.
     */
    private const val LATE_UPGRADE_WINDOW_MS = 30 * 60_000L

    const val DEFAULT_DELAY_MS = 60_000L

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---------------------------------------------------------------- process

    /**
     * Called once per process from `Application.onCreate`. A plain counter, so a
     * change across the armed slot is proof the process really was torn down.
     */
    fun noteProcessStart(context: Context) {
        val p = prefs(context)
        p.edit { putInt(KEY_GEN, p.getInt(KEY_GEN, 0) + 1) }
    }

    fun processGeneration(context: Context): Int = prefs(context).getInt(KEY_GEN, 0)

    // ------------------------------------------------------------------ state

    /** A [Verdict] is what the UI shows; null means "never measured". */
    data class Verdict(
        /** When the test slot was armed. */
        val armedAt: Long,
        /** Whether any ring for that slot arrived at all. */
        val rang: Boolean,
        /** Whether the process died between arming and ringing — i.e. a real swipe. */
        val swiped: Boolean,
        /** [AlarmScheduler.PATH_SERVICE], [AlarmScheduler.PATH_BROADCAST], or "". */
        val path: String,
        /**
         * True when the ring arrived after the deadline and only then turned a
         * provisional "没响" into a pass — late delivery is a real, reportable
         * condition, not a clean success.
         */
        val late: Boolean = false
    )

    /**
     * True while a test is armed and undecided.
     *
     * Once [settle] has called it a miss the test is decided, even though the
     * record stays on disk to give a late ring the chance to overturn it — so
     * this compares the verdict against the slot instead of trusting the slot's
     * mere presence.
     */
    fun isPending(context: Context): Boolean {
        val p = prefs(context)
        val at = p.getLong(KEY_PENDING_AT, 0L)
        if (at <= 0L) return false
        if (p.getLong(KEY_VERDICT_AT, 0L) == at) return false
        return System.currentTimeMillis() < at + LATE_UPGRADE_WINDOW_MS
    }

    fun pendingAt(context: Context): Long = prefs(context).getLong(KEY_PENDING_AT, 0L)

    /** Which reminder the armed test will ring; needed to cancel its PendingIntents. */
    fun pendingAlarmId(context: Context): Long? {
        val id = prefs(context).getLong(KEY_PENDING_ALARM, -1L)
        return if (id < 0L) null else id
    }

    fun verdict(context: Context): Verdict? {
        val at = prefs(context).getLong(KEY_VERDICT_AT, 0L)
        if (at <= 0L) return null
        val p = prefs(context)
        return Verdict(
            armedAt = at,
            rang = p.getBoolean(KEY_VERDICT_RANG, false),
            swiped = p.getBoolean(KEY_VERDICT_SWIPED, false),
            path = p.getString(KEY_VERDICT_PATH, "").orEmpty(),
            late = p.getBoolean(KEY_VERDICT_LATE, false)
        )
    }

    /** Whether the user has walked through the three OEM steps at least once. */
    fun setupDone(context: Context): Boolean = prefs(context).getBoolean(KEY_SETUP_DONE, false)

    /**
     * When the user last claimed all three switches are on, or 0 when they never
     * said so.
     *
     * Kept apart from [setupDone] because the two answer different questions:
     * `setupDone` is "we have shown the walkthrough once, stop nagging", while
     * this one is a timestamp that a measurement can be compared against. Only a
     * verdict armed *after* this moment may confirm the switches, otherwise an
     * old pass would keep a freshly-broken setup looking fine.
     */
    fun setupConfirmedAt(context: Context): Long = prefs(context).getLong(KEY_SETUP_CONFIRMED_AT, 0L)

    fun markSetupDone(context: Context) {
        prefs(context).edit { putBoolean(KEY_SETUP_DONE, true) }
    }

    /**
     * Records "三项都打开了" and returns the moment it was recorded, so the caller
     * can arm a measurement against it in one step.
     *
     * This is deliberately just the user's word. It buys no trust by itself — it
     * only buys a slot in which the phone can prove or disprove it.
     */
    fun markSetupConfirmed(context: Context, now: Long = System.currentTimeMillis()): Long {
        prefs(context).edit {
            putBoolean(KEY_SETUP_DONE, true)
            putLong(KEY_SETUP_CONFIRMED_AT, now)
        }
        return now
    }

    /**
     * Called when a measurement proves the switches are *not* working.
     *
     * The confirmation is dropped but the timestamp is kept in [setupConfirmedAt]
     * terms only as long as the failed verdict is newer than it — see
     * [LaunchCheck.manualState], which reads a failure as NEEDS_ACTION without
     * needing anything erased here. Kept as a separate hook so the UI can clear
     * a stale "已确认" line the moment it knows better.
     */
    fun clearSetupConfirmed(context: Context) {
        prefs(context).edit { remove(KEY_SETUP_CONFIRMED_AT) }
    }

    // ----------------------------------------------------------------- action

    /**
     * Arms a test ring [delayMs] from now and forgets the previous verdict, so
     * the screen can never show "passed" while a new measurement is in flight.
     */
    fun start(context: Context, alarmId: Long, delayMs: Long = DEFAULT_DELAY_MS): Boolean {
        val store = AlarmStore(context)
        // A disabled reminder is one the app has promised not to ring, so the
        // test must not ring it either — otherwise "off" would still make noise.
        val alarm = store.find(alarmId)?.takeIf { it.enabled } ?: return false
        val triggerAt = System.currentTimeMillis() + delayMs
        prefs(context).edit {
            putLong(KEY_PENDING_AT, triggerAt)
            putLong(KEY_PENDING_ALARM, alarm.id)
            putInt(KEY_PENDING_GEN, processGeneration(context))
            remove(KEY_VERDICT_AT)
            remove(KEY_VERDICT_RANG)
            remove(KEY_VERDICT_SWIPED)
            remove(KEY_VERDICT_LATE)
            remove(KEY_VERDICT_PATH)
        }
        AlarmScheduler(context).scheduleSwipeTest(alarm, triggerAt)
        Diag.log(context, "SWIPE 已排期实测", "${delayMs / 1000} 秒后 · 现在可以把应用划掉")
        return true
    }

    fun cancel(context: Context) {
        AlarmScheduler(context).cancelSwipeTest()
        prefs(context).edit {
            remove(KEY_PENDING_AT)
            remove(KEY_PENDING_ALARM)
            remove(KEY_PENDING_GEN)
        }
        Diag.log(context, "SWIPE 实测已取消")
    }

    /**
     * Called from [RingtoneService] the moment a ring actually starts.
     *
     * Two rings must be told apart: one that lands inside the match window, and
     * one that Doze held for minutes and delivers after [settle] already called
     * the test a miss. The second case still has to be recorded — a phone that
     * rang late did not stay silent, and refusing that evidence would send the
     * user chasing a switch that was already correct. It is reported as [Verdict.late]
     * rather than a clean pass, because a late alarm is its own problem.
     *
     * Anything outside both windows is ignored, so an unrelated reminder cannot
     * launder itself into a swipe-test result.
     */
    fun noteDelivery(context: Context, path: String) {
        val p = prefs(context)
        val at = p.getLong(KEY_PENDING_AT, 0L)
        if (at <= 0L) return
        val now = System.currentTimeMillis()
        val delta = now - at
        if (delta < -MATCH_WINDOW_MS) return
        val judgedMiss = p.getLong(KEY_VERDICT_AT, 0L) == at &&
            !p.getBoolean(KEY_VERDICT_RANG, false)
        val late = judgedMiss && delta <= LATE_UPGRADE_WINDOW_MS
        if (!late && delta > MATCH_WINDOW_MS) return

        val armedGeneration = p.getInt(KEY_PENDING_GEN, -1)
        val swiped = armedGeneration >= 0 && processGeneration(context) != armedGeneration
        p.edit {
            putLong(KEY_VERDICT_AT, at)
            putBoolean(KEY_VERDICT_RANG, true)
            putBoolean(KEY_VERDICT_SWIPED, swiped)
            putBoolean(KEY_VERDICT_LATE, late)
            putString(KEY_VERDICT_PATH, path)
            remove(KEY_PENDING_AT)
            remove(KEY_PENDING_ALARM)
            remove(KEY_PENDING_GEN)
        }
        when {
            late -> Diag.log(
                context,
                "SWIPE 迟到才响",
                "晚了 ${delta / 1000} 秒，系统把提醒压了很久，建议把省电策略设为无限制"
            )
            swiped -> Diag.log(
                context,
                "SWIPE 实测通过",
                if (path == AlarmScheduler.PATH_SERVICE) "进程已被杀，仍由系统唤起服务" else "进程已被杀，仍由广播送达"
            )
            else -> Diag.log(context, "SWIPE 响铃正常", "但进程没有重启，这次没测到清后台")
        }
    }

    /**
     * Judges an overdue test as a failure.
     *
     * Must run on every process start (see [SelfHeal]): while the app is swiped
     * away nothing of ours executes, so the first moment we are alive again is
     * the first moment we can say "that ring never came".
     *
     * The slot is deliberately left on disk so [noteDelivery] can still overturn
     * this verdict within [LATE_UPGRADE_WINDOW_MS]; [isPending] treats a judged
     * slot as decided regardless.
     *
     * Past that window the measurement is forgotten instead of judged: a slot
     * from last week proves nothing except that the user stopped caring, and
     * turning it into "没响" would pin a permanent warning banner on a phone
     * whose alarm may well be fine.
     *
     * @return true when this call produced a new failed verdict.
     */
    fun settle(context: Context): Boolean {
        val p = prefs(context)
        val at = p.getLong(KEY_PENDING_AT, 0L)
        if (at <= 0L) return false
        val now = System.currentTimeMillis()
        val judged = p.getLong(KEY_VERDICT_AT, 0L) == at
        if (now > at + LATE_UPGRADE_WINDOW_MS) {
            p.edit {
                remove(KEY_PENDING_AT)
                remove(KEY_PENDING_ALARM)
                remove(KEY_PENDING_GEN)
            }
            // A judged miss keeps its verdict: that failure really happened. Only
            // an unjudged slot is dropped, and only it deserves a log line — an
            // entry saying "过期，不再判定" next to a real verdict would read as
            // if the verdict had been withdrawn.
            if (!judged) {
                Diag.log(
                    context,
                    "SWIPE 实测已过期",
                    "排期 ${Diag.time(at)}，超过 ${LATE_UPGRADE_WINDOW_MS / 60_000} 分钟未回到前台，不再判定"
                )
            }
            return false
        }
        if (now < at + SETTLE_GRACE_MS) return false
        if (judged) return false
        val armedGeneration = p.getInt(KEY_PENDING_GEN, -1)
        val restarted = armedGeneration >= 0 && processGeneration(context) != armedGeneration
        p.edit {
            putLong(KEY_VERDICT_AT, at)
            putBoolean(KEY_VERDICT_RANG, false)
            putBoolean(KEY_VERDICT_LATE, false)
            // Not "swiped": we cannot prove the user swiped. What is provable is
            // that the ring did not happen, which is the failure worth reporting.
            putBoolean(KEY_VERDICT_SWIPED, restarted)
            remove(KEY_VERDICT_PATH)
        }
        Diag.log(
            context,
            "SWIPE 实测没响",
            "排期 ${Diag.time(at)}，迟 ${(now - at) / 1000} 秒仍无触发"
        )
        return true
    }
}
