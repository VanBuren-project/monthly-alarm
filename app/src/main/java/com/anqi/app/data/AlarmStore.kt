package com.anqi.app.data

import android.content.Context
import androidx.core.content.edit
import com.anqi.app.model.Alarm
import org.json.JSONArray

/**
 * Small, transactional-enough store. The app is single-process; every mutation
 * is synchronised so a broadcast receiver cannot observe a half-written list.
 */
class AlarmStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("anqi_store", Context.MODE_PRIVATE)

    fun all(): List<Alarm> = synchronized(LOCK) {
        val raw = prefs.getString(KEY_ALARMS, "[]") ?: "[]"
        runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    array.optJSONObject(i)?.let { add(Alarm.fromJson(it)) }
                }
            }
        }.getOrDefault(emptyList())
    }

    fun find(id: Long): Alarm? = all().firstOrNull { it.id == id }

    fun saveAll(alarms: List<Alarm>) = synchronized(LOCK) {
        val array = JSONArray()
        alarms.forEach { array.put(it.toJson()) }
        prefs.edit(commit = true) { putString(KEY_ALARMS, array.toString()) }
    }

    fun upsert(alarm: Alarm): Alarm = synchronized(LOCK) {
        val current = all().toMutableList()
        val index = current.indexOfFirst { it.id == alarm.id }
        val saved = if (alarm.id == 0L) {
            alarm.copy(id = nextIdLocked())
        } else {
            alarm
        }
        if (index >= 0) current[index] = saved else current += saved
        saveAll(current)
        saved
    }

    fun delete(id: Long) {
        saveAll(all().filterNot { it.id == id })
    }

    fun nextId(): Long = synchronized(LOCK) { nextIdLocked() }

    private fun nextIdLocked(): Long =
        (all().maxOfOrNull { it.id } ?: 0L) + 1L

    var snoozeMinutes: Int
        get() = prefs.getInt(KEY_SNOOZE, 10).coerceIn(1, 120)
        set(value) = prefs.edit { putInt(KEY_SNOOZE, value.coerceIn(1, 120)) }

    companion object {
        private const val KEY_ALARMS = "alarms"
        private const val KEY_SNOOZE = "snooze_minutes"
        private val LOCK = Any()
    }
}
