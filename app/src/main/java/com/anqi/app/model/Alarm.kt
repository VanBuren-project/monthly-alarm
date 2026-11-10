package com.anqi.app.model

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

enum class RepeatType {
    ONCE,
    DAILY,
    WORKDAYS,
    WEEKLY,
    MONTHLY_DAY,
    MONTHLY_LAST_DAY,
    MONTHLY_NTH_WEEKDAY,
    YEARLY
}

/**
 * One reminder. Time is stored as wall-clock hour/minute, so a timezone change
 * keeps the reminder at the same local time.
 */
data class Alarm(
    val id: Long = 0L,
    val hour: Int = 8,
    val minute: Int = 0,
    val label: String = "",
    val note: String = "",
    val repeatType: RepeatType = RepeatType.ONCE,
    val date: LocalDate? = null,
    val weekdays: Set<Int> = emptySet(),
    val monthlyDay: Int? = null,
    val nth: Int? = null,
    val nthWeekday: Int? = null,
    val yearlyMonth: Int? = null,
    val yearlyDay: Int? = null,
    val endDate: LocalDate? = null,
    val remainingCount: Int? = null,
    val enabled: Boolean = true,
    val vibrate: Boolean = true,
    val soundUri: String? = null,
    val lastFiredAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    val isRepeating: Boolean
        get() = repeatType != RepeatType.ONCE

    fun toJson(): JSONObject = JSONObject().apply {
        put("version", 1)
        put("id", id)
        put("hour", hour)
        put("minute", minute)
        put("label", label)
        put("note", note)
        put("repeat", repeatType.name)
        put("date", date?.toString() ?: JSONObject.NULL)
        put("weekdays", JSONArray(weekdays.sorted()))
        put("monthlyDay", monthlyDay ?: JSONObject.NULL)
        put("nth", nth ?: JSONObject.NULL)
        put("nthWeekday", nthWeekday ?: JSONObject.NULL)
        put("yearlyMonth", yearlyMonth ?: JSONObject.NULL)
        put("yearlyDay", yearlyDay ?: JSONObject.NULL)
        put("endDate", endDate?.toString() ?: JSONObject.NULL)
        put("remainingCount", remainingCount ?: JSONObject.NULL)
        put("enabled", enabled)
        put("vibrate", vibrate)
        put("soundUri", soundUri ?: JSONObject.NULL)
        put("lastFiredAt", lastFiredAt ?: JSONObject.NULL)
        put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(json: JSONObject): Alarm {
            val type = runCatching {
                RepeatType.valueOf(json.optString("repeat", RepeatType.ONCE.name))
            }.getOrDefault(RepeatType.ONCE)

            val days = mutableSetOf<Int>()
            json.optJSONArray("weekdays")?.let { array ->
                for (i in 0 until array.length()) {
                    val day = array.optInt(i, 0)
                    if (day in 1..7) days += day
                }
            }

            fun optionalInt(key: String): Int? =
                if (json.has(key) && !json.isNull(key)) json.optInt(key) else null

            fun optionalDate(key: String): LocalDate? =
                json.optString(key, "").takeIf { it.isNotBlank() }?.let {
                    runCatching { LocalDate.parse(it) }.getOrNull()
                }

            return Alarm(
                id = json.optLong("id", 0L),
                hour = json.optInt("hour", 8).coerceIn(0, 23),
                minute = json.optInt("minute", 0).coerceIn(0, 59),
                label = json.optString("label", ""),
                note = json.optString("note", ""),
                repeatType = type,
                date = optionalDate("date"),
                weekdays = days,
                monthlyDay = optionalInt("monthlyDay"),
                nth = optionalInt("nth"),
                nthWeekday = optionalInt("nthWeekday"),
                yearlyMonth = optionalInt("yearlyMonth"),
                yearlyDay = optionalInt("yearlyDay"),
                endDate = optionalDate("endDate"),
                remainingCount = optionalInt("remainingCount"),
                enabled = json.optBoolean("enabled", true),
                vibrate = json.optBoolean("vibrate", true),
                soundUri = if (json.has("soundUri") && !json.isNull("soundUri")) {
                    json.optString("soundUri").takeIf { it.isNotBlank() }
                } else null,
                lastFiredAt = if (json.has("lastFiredAt") && !json.isNull("lastFiredAt")) {
                    json.optLong("lastFiredAt")
                } else null,
                createdAt = json.optLong("createdAt", System.currentTimeMillis())
            )
        }
    }
}
