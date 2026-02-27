package com.alarmissimo.util

import java.util.Calendar

/**
 * Utility functions for time parsing and scheduling.
 */
object TimeUtils {

    /**
     * Returns the epoch milliseconds of the next occurrence of [time] on one of the [weekdays].
     *
     * If no valid weekday configuration is provided, returns null.
     *
     * @param time Time string in "HH:mm" format.
     * @param weekdays Days of week the alarm is active (1=Mon … 7=Sun, Calendar convention).
     * @return Next trigger time in epoch milliseconds, or null if not schedulable.
     */
    fun nextOccurrenceMillis(time: String, weekdays: List<Int>): Long? {
        if (weekdays.isEmpty()) return null
        val parts = time.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: return null

        val now = Calendar.getInstance()
        val candidate = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // If the time today has already passed, start from tomorrow
        if (candidate <= now) candidate.add(Calendar.DAY_OF_YEAR, 1)

        // Search forward up to 7 days for the next matching weekday
        for (i in 0..6) {
            val dow = calendarDayToSpec(candidate.get(Calendar.DAY_OF_WEEK))
            if (dow in weekdays) return candidate.timeInMillis
            candidate.add(Calendar.DAY_OF_YEAR, 1)
        }
        return null
    }

    /**
     * Returns the remaining time until [triggerMillis] as a display string.
     *
     * Format: "X:MM h" for ≥ 60 min, "N min" for < 60 min.
     *
     * @param triggerMillis Target epoch milliseconds.
     * @param nowMillis Current time in epoch milliseconds (defaults to [System.currentTimeMillis]).
     * @return Human-readable remaining time string.
     */
    fun remainingTime(triggerMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
        val diff = ((triggerMillis - nowMillis) / 1000 / 60).toInt().coerceAtLeast(0)
        return if (diff >= 60) {
            val h = diff / 60
            val m = diff % 60
            "%d:%02d h".format(h, m)
        } else {
            "$diff min"
        }
    }

    /**
     * Converts a [Calendar.DAY_OF_WEEK] value (Sun=1 … Sat=7) to
     * the app's Mon=1 … Sun=7 convention.
     */
    fun calendarDayToSpec(calDay: Int): Int = when (calDay) {
        Calendar.MONDAY    -> 1
        Calendar.TUESDAY   -> 2
        Calendar.WEDNESDAY -> 3
        Calendar.THURSDAY  -> 4
        Calendar.FRIDAY    -> 5
        Calendar.SATURDAY  -> 6
        Calendar.SUNDAY    -> 7
        else               -> 1
    }

    /** Short weekday label (Mon–Sun) for display, 1-indexed (1=Mon). */
    fun weekdayLabel(day: Int): String = when (day) {
        1 -> "Mo"; 2 -> "Di"; 3 -> "Mi"; 4 -> "Do"
        5 -> "Fr"; 6 -> "Sa"; 7 -> "So"; else -> ""
    }
}
