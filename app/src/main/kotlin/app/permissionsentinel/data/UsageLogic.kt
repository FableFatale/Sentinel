package app.permissionsentinel.data

/**
 * Pure derivation logic for usage events, heatmap bucketing and overview stats.
 * Everything here is plain Kotlin so it is unit tested without a device.
 */
object UsageLogic {

    /** Raw event as read from UsageStatsManager before pairing. */
    data class RawEvent(
        val packageName: String,
        val timestamp: Long,
        val type: Int,
    ) {
        companion object {
            // Event.EventKind values (API 29+)
            const val ACTIVITY_RESUMED = 1
            const val ACTIVITY_PAUSED = 2
            const val ACTIVITY_STOPPED = 3
        }
    }

    /**
     * Pairs foreground lifecycle events into sessions.
     * A RESUMED opens (or switches) the current session; PAUSED/STOPPED from the same
     * package closes it. A still-open session at stream end is closed at [endAt].
     */
    fun pairSessions(events: List<RawEvent>, endAt: Long): List<ActivitySession> {
        val sorted = events.sortedBy { it.timestamp }
        val out = mutableListOf<ActivitySession>()
        var current: ActivitySession? = null
        for (e in sorted) {
            when (e.type) {
                RawEvent.ACTIVITY_RESUMED -> {
                    // The next RESUMED implies the previous foreground app lost focus.
                    current?.let { open ->
                        if (open.end == Long.MIN_VALUE) out += open.copy(end = e.timestamp)
                    }
                    current = ActivitySession(e.packageName, e.timestamp, Long.MIN_VALUE)
                }
                RawEvent.ACTIVITY_PAUSED, RawEvent.ACTIVITY_STOPPED -> {
                    val open = current
                    if (open != null && open.packageName == e.packageName && open.end == Long.MIN_VALUE) {
                        out += open.copy(end = e.timestamp)
                        current = null
                    }
                }
            }
        }
        current?.let { open ->
            if (open.end == Long.MIN_VALUE) out += open.copy(end = maxOf(endAt, open.start + 1))
        }
        return out.filter { it.durationMs > 0 }
    }

    /**
     * Buckets sessions into a 7x24 minute grid. Sessions spanning multiple hours are
     * split at hour boundaries. Days are 0=Monday..6=Sunday in [zone].
     */
    fun bucketSessions(
        sessions: List<ActivitySession>,
        zone: java.time.ZoneId,
    ): HeatmapGrid {
        val grid = Array(7) { LongArray(24) }
        for (s in sessions) {
            var cursor = s.start
            val end = s.end
            while (cursor < end) {
                val zdt = java.time.Instant.ofEpochMilli(cursor).atZone(zone)
                val hourStart = zdt.truncatedTo(java.time.temporal.ChronoUnit.HOURS)
                    .toInstant().toEpochMilli()
                val hourEnd = hourStart + 3_600_000L
                val sliceEnd = minOf(end, hourEnd)
                val minutes = (sliceEnd - cursor + 59_999L) / 60_000L
                val dayIdx = (zdt.dayOfWeek.value - 1)
                grid[dayIdx][zdt.hour] += minutes
                cursor = sliceEnd
            }
        }
        return HeatmapGrid(grid)
    }

    /** Total minutes of [sessions] that overlap the calendar day of [now] in [zone]. */
    fun minutesToday(sessions: List<ActivitySession>, now: Long, zone: java.time.ZoneId): Long {
        val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return minutesBetween(sessions, dayStart, dayEnd)
    }

    fun minutesBetween(sessions: List<ActivitySession>, from: Long, to: Long): Long {
        var ms = 0L
        for (s in sessions) {
            val start = maxOf(s.start, from)
            val end = minOf(s.end, to)
            if (end > start) ms += end - start
        }
        return ms / 60_000L
    }

    /** Sessions overlapping a given day-of-week+hour bucket (for heatmap tap details). */
    fun sessionsInBucket(
        sessions: List<ActivitySession>,
        dayOfWeekIso: Int,
        hour: Int,
        zone: java.time.ZoneId,
    ): List<ActivitySession> = sessions.filter { s ->
        var hit = false
        var cursor = s.start
        while (cursor < s.end && !hit) {
            val zdt = java.time.Instant.ofEpochMilli(cursor).atZone(zone)
            if (zdt.dayOfWeek.value == dayOfWeekIso && zdt.hour == hour) hit = true
            else {
                val hourStart = zdt.truncatedTo(java.time.temporal.ChronoUnit.HOURS)
                    .toInstant().toEpochMilli()
                cursor = hourStart + 3_600_000L
            }
        }
        hit
    }

    /**
     * Sessions overlapping weekday [dayOfWeekIso] (1=Mon..7=Sun) within the hour range
     * [fromHour, untilHour) — used by the day-column heatmap interval selection.
     */
    fun sessionsInDayInterval(
        sessions: List<ActivitySession>,
        dayOfWeekIso: Int,
        fromHour: Int,
        untilHour: Int,
        zone: java.time.ZoneId,
    ): List<ActivitySession> = sessions.filter { s ->
        var hit = false
        var cursor = s.start
        while (cursor < s.end && !hit) {
            val zdt = java.time.Instant.ofEpochMilli(cursor).atZone(zone)
            if (zdt.dayOfWeek.value == dayOfWeekIso && zdt.hour in fromHour until untilHour) {
                hit = true
            } else {
                val hourStart = zdt.truncatedTo(java.time.temporal.ChronoUnit.HOURS)
                    .toInstant().toEpochMilli()
                cursor = hourStart + 3_600_000L
            }
        }
        hit
    }
}
