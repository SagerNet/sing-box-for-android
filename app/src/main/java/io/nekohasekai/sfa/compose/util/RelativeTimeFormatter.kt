package io.nekohasekai.sfa.compose.util

import android.content.Context
import io.nekohasekai.sfa.R
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

object RelativeTimeFormatter {
    /**
     * Formats a date as relative time for recent dates (within 7 days)
     * or as full date/time for older dates.
     */
    fun format(context: Context, date: Date?): String {
        if (date == null) return ""

        val now = System.currentTimeMillis()
        val diff = now - date.time

        // Handle negative differences (future dates)
        if (diff < 0) {
            return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(date)
        }

        val seconds = TimeUnit.MILLISECONDS.toSeconds(diff)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
        val hours = TimeUnit.MILLISECONDS.toHours(diff)
        val days = TimeUnit.MILLISECONDS.toDays(diff)

        return when {
            seconds < 60 -> context.getString(R.string.time_just_now)
            minutes < 60 ->
                context.resources.getQuantityString(
                    R.plurals.time_minutes_ago,
                    minutes.toInt(),
                    minutes,
                )
            hours < 24 ->
                context.resources.getQuantityString(
                    R.plurals.time_hours_ago,
                    hours.toInt(),
                    hours,
                )
            days == 1L -> context.getString(R.string.time_yesterday)
            days < 7 ->
                context.resources.getQuantityString(
                    R.plurals.time_days_ago,
                    days.toInt(),
                    days,
                )
            else -> DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(date)
        }
    }

    /**
     * Formats a date as short relative time for compact displays.
     * Uses shorter format like "2h" instead of "2 hours ago".
     */
    fun formatShort(context: Context, date: Date?): String {
        if (date == null) return ""

        val now = System.currentTimeMillis()
        val diff = now - date.time

        // Handle negative differences (future dates)
        if (diff < 0) {
            return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(date)
        }

        val seconds = TimeUnit.MILLISECONDS.toSeconds(diff)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
        val hours = TimeUnit.MILLISECONDS.toHours(diff)
        val days = TimeUnit.MILLISECONDS.toDays(diff)

        return when {
            seconds < 60 -> context.getString(R.string.time_now)
            minutes < 60 -> context.getString(R.string.time_minutes_short, minutes)
            hours < 24 -> context.getString(R.string.time_hours_short, hours)
            days == 1L -> context.getString(R.string.time_yesterday_short)
            days < 7 -> context.getString(R.string.time_days_short, days)
            else -> DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(date)
        }
    }

    /**
     * Gets the exact date/time string for tooltips or detailed views.
     */
    fun formatExact(date: Date?): String {
        if (date == null) return ""
        return DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.MEDIUM).format(date)
    }
}
