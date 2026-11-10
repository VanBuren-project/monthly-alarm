package com.anqi.app.alarm

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.net.toUri

/**
 * Opens the OEM screen that decides whether we may run again after the user
 * swipes the app out of the recents list.
 *
 * ## Why this exists at all
 *
 * Swiping an app away is the one gesture that genuinely breaks a third-party
 * alarm on mainland ROMs. It is not a scheduling bug: `setAlarmClock` and
 * `setExactAndAllowWhileIdle` already survive a plain process kill, and the
 * ringing service is started by the system, so delivery never depends on our
 * process being warm (see [AlarmScheduler]). What breaks is the *permission to
 * run at all*: MIUI/HyperOS, EMUI and ColorOS put a swiped-away app into a
 * frozen state and refuse to deliver alarms to it until the user either grants
 * 自启动 (autostart) or pins the app in the recents list.
 *
 * Every mature open-source alarm app answers this the same way — by sending the
 * user to that screen instead of pretending code can fix it:
 *
 *  - `judemanutd/AutoStarter` (Apache-2.0) ships exactly the component list below;
 *  - `fennifith/Alarmio` puts a battery-optimisation card in its settings;
 *  - `FossifyOrg/Clock` does the same and its maintainer states plainly in
 *    issue #176 that preinstalled clocks only look immune because the ROM ships
 *    an allowlist for them.
 *
 * ## What this deliberately does *not* do
 *
 * No reflection, no hidden API, no `READ_*_PERMISSION` probing, no permission is
 * requested or granted. The intent is an ordinary explicit `startActivity` into
 * another app's exported settings screen; the user performs the actual switch
 * and we never learn its state. That keeps the app clear of the Play policy on
 * hidden APIs, and it is why [resolve] can only offer a shortcut — never a
 * verdict. Whether the switch is on is decided empirically, by
 * [SwipeTest] actually ringing after a swipe.
 */
object OemAutostart {

    private const val TAG = "AnqiAutostart"

    /**
     * Component names, ordered most-likely-first.
     *
     * Only component names. Each one is tried with a real `startActivity` and
     * the first that does not throw wins, so a ROM that renamed or removed its
     * settings activity simply falls through to the next entry and finally to
     * the app-info page. That is the same degrade-gracefully strategy AutoStarter
     * uses (it checks the *package*, never the activity).
     */
    internal fun candidatesFor(manufacturer: String, brand: String): List<ComponentName> {
        val keys = setOf(manufacturer.lowercase(), brand.lowercase())
        fun has(vararg names: String) = names.any { it in keys }

        return when {
            has("xiaomi", "redmi", "poco") -> listOf(
                // The autostart manager. This is the screen MIUI users are told
                // to find by hand in the security app.
                component("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
                // Older MIUI builds kept it under a different name.
                component("com.miui.securitycenter", "com.miui.securitycenter.permission.autostart.AutoStartManagementActivity"),
            )

            has("huawei", "honor") -> listOf(
                component("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                component("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
            )

            has("oppo", "realme", "oneplus") -> listOf(
                component("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                component("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
                component("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
                component("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
            )

            has("vivo", "iqoo") -> listOf(
                component("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                component("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
            )

            has("samsung") -> listOf(
                component("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
                component("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity"),
            )

            has("meizu") -> listOf(
                component("com.meizu.safe", "com.meizu.safe.permission.SmartBGActivity"),
            )

            has("asus") -> listOf(
                component("com.asus.mobilemanager", "com.asus.mobilemanager.powersaver.PowerSaverSettings"),
                component("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity"),
            )

            else -> emptyList()
        }
    }

    /** Human-readable OEM name for the copy, or null when we have no specific screen. */
    internal fun oemLabel(manufacturer: String, brand: String): String? {
        val keys = setOf(manufacturer.lowercase(), brand.lowercase())
        fun has(vararg names: String) = names.any { it in keys }
        return when {
            has("xiaomi", "redmi", "poco") -> "小米 / 红米"
            has("huawei", "honor") -> "华为 / 荣耀"
            has("oppo", "realme", "oneplus") -> "OPPO / 一加 / realme"
            has("vivo", "iqoo") -> "vivo / iQOO"
            has("samsung") -> "三星"
            has("meizu") -> "魅族"
            has("asus") -> "华硕"
            else -> null
        }
    }

    fun label(context: Context): String? =
        oemLabel(Build.MANUFACTURER.orEmpty(), Build.BRAND.orEmpty())

    /**
     * Sends the user to the best available screen.
     *
     * @return true when an OEM-specific page opened, false when everything fell
     *   through and the caller is looking at the plain app-info page — which is
     *   still the right place on MIUI, because 自启动 / 锁屏显示 / 后台弹出界面
     *   all live behind it.
     */
    fun open(context: Context): Boolean {
        val components = candidatesFor(Build.MANUFACTURER.orEmpty(), Build.BRAND.orEmpty())
        for (component in components) {
            val intent = Intent().apply {
                setComponent(component)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            // ActivityNotFoundException means the ROM does not have this screen;
            // SecurityException means it has it but does not export it to us.
            // Either way the next candidate may work, so neither is fatal.
            val opened = runCatching { context.startActivity(intent) }
                .onFailure { Log.d(TAG, "No autostart screen at $component", it) }
                .isSuccess
            if (opened) {
                Diag.log(context, "OEM 打开自启动页", component.flattenToShortString())
                return true
            }
        }
        Diag.log(
            context,
            "OEM 没有自启动页，退回应用信息",
            "${Build.MANUFACTURER}/${Build.BRAND}"
        )
        return openAppInfo(context)
    }

    /** Battery-optimisation list, then app info if even that is unavailable. */
    fun openBattery(context: Context): Boolean {
        val opened = runCatching {
            context.startActivity(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.isSuccess
        return opened || openAppInfo(context)
    }

    fun openAppInfo(context: Context): Boolean {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(appUri(context))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }
            .recoverCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .isSuccess
    }

    private fun appUri(context: Context): Uri = "package:${context.packageName}".toUri()

    private fun component(pkg: String, cls: String) = ComponentName(pkg, cls)

    /**
     * Whether the system has been told not to optimise us away.
     *
     * Unlike the autostart switch, this one is readable with no permission at
     * all, so it is the one piece of the three-step setup the app can verify
     * instead of asking the user to trust it. Note it is only ever *read*: the
     * `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` prompt needs
     * `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, which Play restricts, so the user
     * is sent to the list instead (see [openBattery]).
     */
    fun isBatteryUnrestricted(context: Context): Boolean = runCatching {
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        power.isIgnoringBatteryOptimizations(context.packageName)
    }.getOrDefault(false)
}
