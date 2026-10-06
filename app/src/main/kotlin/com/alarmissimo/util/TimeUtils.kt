package com.alarmissimo.util

import com.alarmissimo.data.model.AlarmSet
import com.alarmissimo.data.model.AlarmEvent
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Utility functions for time parsing and scheduling.
 */
object TimeUtils {

    /**
     * Computes the next trigger time in epoch milliseconds for [event] within [set].
     *
     * - **Absolute mode** ([AlarmSet.timeMode] == "absolute"): uses [AlarmEvent.time].
     *   - With [AlarmSet.specificDate]: fires once on that date+time (null if already past).
     *   - Without specificDate: uses weekly [AlarmSet.weekdays] repetition.
     * - **Relative mode** ([AlarmSet.timeMode] == "relative"): fires [AlarmEvent.offsetMinutes]
     *   before [AlarmSet.endTime] on the weekday schedule (or specificDate).
     *
     * Returns null if the alarm cannot be scheduled (no valid weekdays, past date, etc.).
     */
    fun computeTriggerMillis(set: AlarmSet, event: AlarmEvent): Long? {
        return if (set.timeMode == "relative") {
            val endTime = set.endTime ?: return null
            val absoluteTime = subtractMinutes(endTime, event.offsetMinutes)
            if (set.specificDate != null) {
                nextOccurrenceMillisForDate(absoluteTime, set.specificDate)
            } else {
                nextOccurrenceMillis(absoluteTime, set.weekdays)
            }
        } else {
            // absolute mode
            if (set.specificDate != null) {
                nextOccurrenceMillisForDate(event.time, set.specificDate)
            } else {
                nextOccurrenceMillis(event.time, set.weekdays)
            }
        }
    }

    /**
     * For display purposes: returns the computed absolute time string ("HH:mm") that an
     * event will fire, given the parent [set]. Handles both modes.
     */
    fun computeAbsoluteTriggerTime(set: AlarmSet, event: AlarmEvent): String {
        return if (set.timeMode == "relative") {
            val endTime = set.endTime ?: event.time
            subtractMinutes(endTime, event.offsetMinutes)
        } else {
            event.time
        }
    }

    /**
     * Subtracts [minutes] from a "HH:mm" time string and returns the result as "HH:mm".
     * Wraps around midnight (e.g., "00:05" - 10 min = "23:55").
     */
    fun subtractMinutes(time: String, minutes: Int): String {
        val parts = time.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val totalMinutes = ((h * 60 + m - minutes) % (24 * 60) + 24 * 60) % (24 * 60)
        return "%02d:%02d".format(totalMinutes / 60, totalMinutes % 60)
    }

    /**
     * Returns the epoch milliseconds of the next occurrence of [time] on the given [specificDate].
     * Returns null if the date+time is already in the past.
     *
     * @param time Time string in "HH:mm" format.
     * @param specificDate ISO date string "yyyy-MM-dd".
     */
    fun nextOccurrenceMillisForDate(time: String, specificDate: String): Long? {
        val parts = time.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: return null
        val dateParts = specificDate.split("-")
        val year = dateParts.getOrNull(0)?.toIntOrNull() ?: return null
        val month = dateParts.getOrNull(1)?.toIntOrNull()?.minus(1) ?: return null
        val day = dateParts.getOrNull(2)?.toIntOrNull() ?: return null
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return if (cal.timeInMillis > System.currentTimeMillis()) cal.timeInMillis else null
    }

    /**
     * Returns the epoch milliseconds of the next occurrence of [time] on one of [weekdays].
     * If no valid weekday is provided, returns null.
     *
     * @param time Time string in "HH:mm" format.
     * @param weekdays Days of week the alarm is active (1=Mon … 7=Sun).
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

        if (candidate <= now) candidate.add(Calendar.DAY_OF_YEAR, 1)

        for (i in 0..6) {
            val dow = calendarDayToSpec(candidate.get(Calendar.DAY_OF_WEEK))
            if (dow in weekdays) return candidate.timeInMillis
            candidate.add(Calendar.DAY_OF_YEAR, 1)
        }
        return null
    }

    /**
     * Returns the remaining time until [triggerMillis] as a display string.
     * Format: "X:MM h" for ≥ 60 min, "N min" for < 60 min.
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
     * Formats a date string "yyyy-MM-dd" to a human-readable "dd.MM.yyyy".
     * Returns the original string on parse failure.
     */
    fun formatDateDisplay(isoDate: String): String {
        return try {
            val sdfIn  = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val sdfOut = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
            sdfOut.format(sdfIn.parse(isoDate)!!)
        } catch (e: Exception) {
            isoDate
        }
    }

    /** Converts a [Calendar.DAY_OF_WEEK] value (Sun=1 … Sat=7) to 1=Mon … 7=Sun. */
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

    /** Short weekday label (Mo–So) for display, 1-indexed (1=Mon). */
    fun weekdayLabel(day: Int): String = when (day) {
        1 -> "Mo"; 2 -> "Di"; 3 -> "Mi"; 4 -> "Do"
        5 -> "Fr"; 6 -> "Sa"; 7 -> "So"; else -> ""
    }
}
