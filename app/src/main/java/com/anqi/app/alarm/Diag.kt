package com.anqi.app.alarm

import android.content.Context
import androidx.core.content.edit
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * A tiny ring buffer of "what did the alarm machinery actually do", kept in
 * SharedPreferences.
 *
 * It exists because the failure being debugged ("it never rang") is invisible
 * from the outside: after the ROM clears the app there is no process left to ask,
 * and `adb logcat` is gone by the time the phone is picked up. Logging every
 * arm/wake-up/fire lets the user open the app and show exactly which link broke —
 * without the app needing any network permission.
 */
object Diag {
    private const val PREFS = "anqi_diag"
    private const val KEY_LOG = "ring"
    private const val MAX_LINES = 240
    private val LOCK = Any()
    private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")

    fun log(context: Context, event: String, detail: String = "") {
        val app = context.applicationContext
        val line = buildString {
            append(LocalDateTime.now().format(STAMP))
            append("  ")
            append(event)
            if (detail.isNotBlank()) {
                append("  ")
                append(detail)
            }
        }
        synchronized(LOCK) {
            val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val existing = prefs.getString(KEY_LOG, "") ?: ""
            val lines = if (existing.isBlank()) emptyList() else existing.split('\n')
            prefs.edit { putString(KEY_LOG, (lines + line).takeLast(MAX_LINES).joinToString("\n")) }
        }
    }

    fun read(context: Context): List<String> {
        val raw = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LOG, "") ?: ""
        return if (raw.isBlank()) emptyList() else raw.split('\n')
    }

    fun clear(context: Context) {
        synchronized(LOCK) {
            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit { remove(KEY_LOG) }
        }
    }

    /** Formats an absolute timestamp the same way the log lines stamp themselves. */
    fun time(millis: Long): String =
        LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), java.time.ZoneId.systemDefault())
            .format(STAMP)
}
