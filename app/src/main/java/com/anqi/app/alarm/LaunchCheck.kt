package com.anqi.app.alarm

import android.content.Context
import androidx.core.content.edit

/**
 * The startup check: does this phone still let our reminders ring?
 *
 * ## The awkward truth this is built around
 *
 * The three switches that break an alarm on mainland ROMs — 允许自启动、
 * 锁屏显示、后台弹出界面 — have no readable state. They live in the vendor's
 * private permission store; there is no public getter, and probing the private
 * one is exactly the sort of guess that cost this project months of wrong
 * fixes. So the app never claims to know. It reports three honest states and
 * lets a real ring settle the question:
 *
 *  - [State.CONFIRMED] — the system itself confirms it (notification
 *    permission, exact alarms, full-screen intent and the battery exemption all
 *    have real getters and live in [systemItems]), or a swipe test actually rang
 *    after the app had been swiped away while the switches were claimed on.
 *  - [State.PENDING] — claimed on, not proven yet. A measurement may still be
 *    in flight.
 *  - [State.NEEDS_ACTION] — provably missing, never claimed, or a test already
 *    rang short.
 *
 * That is the whole trick: "已开启" is a claim we cannot make, but "已确认"
 * after a ring is a fact we can.
 *
 * ## What the check decides
 *
 * [shouldPrompt] is deliberately conservative. It only asks for the dialog when
 * the user can do something about it, and a failed measurement outranks the
 * "以后再说" snooze — it is the one piece of evidence that the switches are not
 * doing their job, so hiding it for a day would hide the only useful signal.
 *
 * Everything here is pure data in, verdict out, so the decision matrix is unit
 * tested without a device (see `LaunchCheckTest`).
 */
object LaunchCheck {

    // ------------------------------------------------------------- vocabulary

    enum class State { CONFIRMED, PENDING, NEEDS_ACTION }

    /**
     * Every row the dialog can show.
     *
     * The first four have real getters, so they are stated as fact. The last
     * three are the OEM switches: same label, same state, because the ROM gives
     * one page for all three on the phones that matter.
     */
    enum class Item(val label: String, val detail: String) {
        NOTIFICATIONS("通知", "提醒到点时什么都弹不出来"),
        EXACT_ALARM("精确闹钟", "时间会被系统往后挪几分钟"),
        FULL_SCREEN("全屏提醒", "锁屏上不亮屏，只响一声通知"),
        BATTERY("省电无限制", "熄屏久了会被压住，响得晚"),
        AUTOSTART("允许自启动", "清掉后台之后闹钟就不响了"),
        LOCK_SCREEN("锁屏显示", "锁屏时看不到，也关不掉"),
        POPUP("后台弹出界面", "响铃页面弹不到最前面")
    }

    /** The four rows the system can answer by itself. */
    val SYSTEM: List<Item> = listOf(Item.NOTIFICATIONS, Item.EXACT_ALARM, Item.FULL_SCREEN, Item.BATTERY)

    /** The three rows only the user can set, and only a ring can prove. */
    val MANUAL: List<Item> = listOf(Item.AUTOSTART, Item.LOCK_SCREEN, Item.POPUP)

    enum class Reason(val text: String) {
        MISSING_SYSTEM("有一项系统权限还没开"),
        NEVER_CONFIRMED("三个开关还没设置过"),
        MEASUREMENT_FAILED("上次实测没响——开关可能没生效"),
        AWAITING_MEASUREMENT("三项已确认，等一次实测")
    }

    data class ItemView(val item: Item, val state: State) {
        val label: String get() = item.label
        val detail: String get() = item.detail
    }

    /**
     * Everything the decision needs, captured once per foreground.
     *
     * @param confirmedAt when the user last said "三项都打开了"; 0 when never.
     * @param verdict the last swipe-test verdict, or null when never measured.
     * @param pendingAt when a measurement is due, 0 when none is in flight; only
     *   used to count down on screen, never to decide anything.
     * @param snoozedUntil end of a "以后再说" window, 0 when none.
     */
    data class Snapshot(
        val notifications: Boolean = true,
        val exactAlarms: Boolean = true,
        val fullScreen: Boolean = true,
        val batteryUnrestricted: Boolean = false,
        val confirmedAt: Long = 0L,
        val verdict: SwipeTest.Verdict? = null,
        val pendingAt: Long = 0L,
        val snoozedUntil: Long = 0L,
        val now: Long = 0L
    )

    // ----------------------------------------------------------------- state

    fun stateText(state: State): String = when (state) {
        State.CONFIRMED -> "已确认"
        State.PENDING -> "待确认"
        State.NEEDS_ACTION -> "需要你处理"
    }

    fun systemItems(s: Snapshot): List<ItemView> = listOf(
        ItemView(Item.NOTIFICATIONS, if (s.notifications) State.CONFIRMED else State.NEEDS_ACTION),
        ItemView(Item.EXACT_ALARM, if (s.exactAlarms) State.CONFIRMED else State.NEEDS_ACTION),
        ItemView(Item.FULL_SCREEN, if (s.fullScreen) State.CONFIRMED else State.NEEDS_ACTION),
        ItemView(Item.BATTERY, if (s.batteryUnrestricted) State.CONFIRMED else State.NEEDS_ACTION)
    )

    /**
     * One state for all three switches, because they share one page and one
     * proof: a reminder that rang while the app was swiped away.
     *
     * A *late* ring still counts as confirmed. It proves the process was woken
     * and the alarm was delivered — which is exactly what these switches govern.
     * Lateness is its own problem and already has its own banner, so refusing
     * the evidence here would send the user chasing a switch that works.
     *
     * A verdict is only credited if it was measured *after* the confirmation;
     * otherwise an old pass would keep a freshly-broken setup looking fine.
     */
    fun manualState(confirmedAt: Long, verdict: SwipeTest.Verdict?): State {
        if (confirmedAt <= 0L) return State.NEEDS_ACTION
        val v = verdict?.takeIf { it.armedAt >= confirmedAt } ?: return State.PENDING
        return if (v.rang) State.CONFIRMED else State.NEEDS_ACTION
    }

    fun manualState(s: Snapshot): State = manualState(s.confirmedAt, s.verdict)

    fun manualItems(s: Snapshot): List<ItemView> =
        MANUAL.map { ItemView(it, manualState(s)) }

    fun reasons(s: Snapshot): List<Reason> = buildList {
        if (systemItems(s).any { it.state != State.CONFIRMED }) add(Reason.MISSING_SYSTEM)
        when (manualState(s)) {
            State.NEEDS_ACTION ->
                add(if (s.confirmedAt > 0L) Reason.MEASUREMENT_FAILED else Reason.NEVER_CONFIRMED)
            State.PENDING -> add(Reason.AWAITING_MEASUREMENT)
            State.CONFIRMED -> Unit
        }
    }

    /**
     * Whether to open the dialog on this foreground.
     *
     * Three rules, in order:
     *
     *  1. Nothing to report → stay quiet. A confirmed, fully-granted phone must
     *     never see a prompt just because the process restarted.
     *  2. A failed measurement always prompts, snooze or not.
     *  3. "待确认" on its own does not prompt: the only thing left to do is wait
     *     for the test to ring, and a dialog would ask the user to fix something
     *     that is not broken. It stays visible in settings instead.
     */
    fun shouldPrompt(s: Snapshot): Boolean {
        val r = reasons(s)
        if (r.isEmpty()) return false
        if (Reason.MEASUREMENT_FAILED in r) return true
        if (r.all { it == Reason.AWAITING_MEASUREMENT }) return false
        return s.now >= s.snoozedUntil
    }

    /** One line for the "上次检查" row in settings. */
    fun summary(s: Snapshot): String {
        val manual = when (manualState(s)) {
            State.CONFIRMED -> "三个开关已确认"
            State.PENDING -> "三个开关待确认"
            State.NEEDS_ACTION ->
                if (s.confirmedAt > 0L) "三个开关没生效" else "三个开关还没设置"
        }
        val missing = systemItems(s).count { it.state != State.CONFIRMED }
        val system = if (missing == 0) "系统权限都开了" else "系统权限还差 $missing 项"
        return "$manual · $system"
    }

    // ---------------------------------------------------------------- storage

    private const val PREFS = "anqi_launch"
    private const val KEY_SNOOZE_UNTIL = "snooze_until"

    /** How long "以后再说" buys. Chosen by the user: one day. */
    const val SNOOZE_MS = 24 * 60 * 60 * 1000L

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun snoozedUntil(context: Context): Long = prefs(context).getLong(KEY_SNOOZE_UNTIL, 0L)

    fun snooze(context: Context, now: Long = System.currentTimeMillis()) {
        prefs(context).edit { putLong(KEY_SNOOZE_UNTIL, now + SNOOZE_MS) }
        Diag.log(context, "LAUNCH 稍后再提", "24 小时内不再主动弹出")
    }

    /** Called once the switches are confirmed, so the next prompt is a fresh one. */
    fun clearSnooze(context: Context) {
        prefs(context).edit { remove(KEY_SNOOZE_UNTIL) }
    }
}
