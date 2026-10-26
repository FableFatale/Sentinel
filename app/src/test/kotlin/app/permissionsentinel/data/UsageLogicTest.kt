package app.permissionsentinel.data

import app.permissionsentinel.data.UsageLogic.RawEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class UsageLogicTest {

    private val zone = ZoneId.of("Asia/Shanghai") // fixed zone: device-local assertions

    private fun at(day: String, time: String): Long =
        java.time.LocalDateTime.parse("$day T$time".replace(" ", ""))
            .atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `resume then pause produces one session`() {
        val events = listOf(
            RawEvent("com.a", at("2026-10-25", "10:00:00"), RawEvent.ACTIVITY_RESUMED),
            RawEvent("com.a", at("2026-10-25", "10:05:00"), RawEvent.ACTIVITY_PAUSED),
        )
        val sessions = UsageLogic.pairSessions(events, endAt = at("2026-10-25", "10:05:00"))
        assertEquals(1, sessions.size)
        assertEquals("com.a", sessions[0].packageName)
        assertEquals(5 * 60_000L, sessions[0].durationMs)
    }

    @Test
    fun `switch between apps closes previous at switch point`() {
        val events = listOf(
            RawEvent("com.a", at("2026-10-25", "10:00:00"), RawEvent.ACTIVITY_RESUMED),
            RawEvent("com.b", at("2026-10-25", "10:02:00"), RawEvent.ACTIVITY_RESUMED),
            RawEvent("com.a", at("2026-10-25", "10:03:00"), RawEvent.ACTIVITY_PAUSED),
            RawEvent("com.b", at("2026-10-25", "10:08:00"), RawEvent.ACTIVITY_PAUSED),
        )
        val sessions = UsageLogic.pairSessions(events, endAt = at("2026-10-25", "10:08:00"))
        assertEquals(2, sessions.size)
        assertEquals("com.a", sessions[0].packageName)
        assertEquals(2 * 60_000L, sessions[0].durationMs)
        assertEquals("com.b", sessions[1].packageName)
        assertEquals(6 * 60_000L, sessions[1].durationMs)
    }

    @Test
    fun `open session is closed at endAt`() {
        val events = listOf(
            RawEvent("com.a", at("2026-10-25", "10:00:00"), RawEvent.ACTIVITY_RESUMED),
        )
        val sessions = UsageLogic.pairSessions(events, endAt = at("2026-10-25", "10:01:30"))
        assertEquals(1, sessions.size)
        assertEquals(90_000L, sessions[0].durationMs)
    }

    @Test
    fun `stray pause without resume is ignored`() {
        val events = listOf(
            RawEvent("com.a", at("2026-10-25", "10:00:00"), RawEvent.ACTIVITY_PAUSED),
        )
        val sessions = UsageLogic.pairSessions(events, endAt = at("2026-10-25", "10:01:00"))
        assertTrue(sessions.isEmpty())
    }

    @Test
    fun `zero length sessions are dropped`() {
        val events = listOf(
            RawEvent("com.a", at("2026-10-25", "10:00:00"), RawEvent.ACTIVITY_RESUMED),
            RawEvent("com.a", at("2026-10-25", "10:00:00"), RawEvent.ACTIVITY_PAUSED),
        )
        val sessions = UsageLogic.pairSessions(events, endAt = at("2026-10-25", "10:00:00"))
        assertTrue(sessions.isEmpty())
    }

    @Test
    fun `bucketing splits session across hour boundary`() {
        // 2026-10-25 is a Sunday (7 iso -> index 6)
        val sessions = listOf(
            ActivitySession("com.a", at("2026-10-25", "13:50:00"), at("2026-10-25", "14:10:00")),
        )
        val grid = UsageLogic.bucketSessions(sessions, zone)
        assertEquals(10L, grid[6, 13])
        assertEquals(10L, grid[6, 14])
        assertEquals(0L, grid[6, 15])
    }

    @Test
    fun `bucketing rounds partial minutes up per hour slice`() {
        // 30 seconds -> 1 minute
        val sessions = listOf(
            ActivitySession("com.a", at("2026-10-25", "09:00:00"), at("2026-10-25", "09:00:30")),
        )
        val grid = UsageLogic.bucketSessions(sessions, zone)
        assertEquals(1L, grid[6, 9])
    }

    @Test
    fun `minutes today only counts overlap with today`() {
        val now = at("2026-10-25", "12:00:00")
        val sessions = listOf(
            ActivitySession("com.a", at("2026-10-24", "23:50:00"), at("2026-10-25", "00:10:00")),
            ActivitySession("com.b", at("2026-10-25", "08:00:00"), at("2026-10-25", "08:30:00")),
        )
        // 10 minutes after midnight + 30 minutes today
        assertEquals(40L, UsageLogic.minutesToday(sessions, now, zone))
    }

    @Test
    fun `sessions today includes every session overlapping today`() {
        val now = at("2026-10-25", "12:00:00")
        val sessions = listOf(
            ActivitySession("before", at("2026-10-24", "22:00:00"), at("2026-10-24", "23:00:00")),
            ActivitySession("crossing", at("2026-10-24", "23:50:00"), at("2026-10-25", "00:10:00")),
            ActivitySession("today", at("2026-10-25", "08:00:00"), at("2026-10-25", "08:30:00")),
            ActivitySession("after", at("2026-10-26", "00:00:00"), at("2026-10-26", "00:30:00")),
        )

        assertEquals(
            listOf("crossing", "today"),
            UsageLogic.sessionsToday(sessions, now, zone).map { it.packageName },
        )
    }

    @Test
    fun `pairSessions merges same-package activity-switch fragments`() {
        val t0 = at("2026-10-25", "10:00:00")
        val events = listOf(
            RawEvent("com.a", t0, RawEvent.ACTIVITY_RESUMED),
            RawEvent("com.a", t0 + 30_000, RawEvent.ACTIVITY_PAUSED),
            RawEvent("com.a", t0 + 31_000, RawEvent.ACTIVITY_RESUMED), // in-app switch
            RawEvent("com.a", t0 + 90_000, RawEvent.ACTIVITY_PAUSED),
        )
        val sessions = UsageLogic.pairSessions(events, endAt = t0 + 90_000)
        assertEquals(1, sessions.size)
        assertEquals(90_000L, sessions[0].durationMs)
    }

    @Test
    fun `pairSessions keeps distinct packages and real gaps separate`() {
        val t0 = at("2026-10-25", "10:00:00")
        val events = listOf(
            RawEvent("com.a", t0, RawEvent.ACTIVITY_RESUMED),
            RawEvent("com.a", t0 + 30_000, RawEvent.ACTIVITY_PAUSED),
            RawEvent("com.b", t0 + 31_000, RawEvent.ACTIVITY_RESUMED), // different app
            RawEvent("com.b", t0 + 60_000, RawEvent.ACTIVITY_PAUSED),
            RawEvent("com.a", t0 + 120_000, RawEvent.ACTIVITY_RESUMED), // real gap: 90s
            RawEvent("com.a", t0 + 150_000, RawEvent.ACTIVITY_PAUSED),
        )
        val sessions = UsageLogic.pairSessions(events, endAt = t0 + 150_000)
        assertEquals(3, sessions.size)
    }

    @Test
    fun `bucketSessions rounds each hour cell independently for cross-cell slices`() {
        // 13:59:50 -> 14:00:10 spans two hour cells; each cell holds real usage
        // and rounds up once, so 1+1=2. This is the cell semantics users verify
        // per cell — the aggregate inflation guard is the same-package fragment
        // merge in pairSessions, not the rounding.
        val sessions = listOf(
            ActivitySession("com.a", at("2026-10-25", "13:59:50"), at("2026-10-25", "14:00:10")),
        )
        val grid = UsageLogic.bucketSessions(sessions, zone)
        assertEquals(1L, grid[6, 13])
        assertEquals(1L, grid[6, 14])
    }

    @Test
    fun `fragment merge keeps per-cell totals from inflating on jittery sessions`() {
        // A jittery in-app session: 4 fragments of ~30s each would previously
        // bucket as 4 separate sessions (4+ minutes after per-cell rounding).
        // Merged into one 3-minute session, the grid reports 3 minutes.
        val t0 = at("2026-10-25", "09:59:30")
        val events = listOf(
            RawEvent("com.a", t0, RawEvent.ACTIVITY_RESUMED),
            RawEvent("com.a", t0 + 30_000, RawEvent.ACTIVITY_PAUSED),
            RawEvent("com.a", t0 + 31_000, RawEvent.ACTIVITY_RESUMED),
            RawEvent("com.a", t0 + 60_000, RawEvent.ACTIVITY_PAUSED),
            RawEvent("com.a", t0 + 61_000, RawEvent.ACTIVITY_RESUMED),
            RawEvent("com.a", t0 + 90_000, RawEvent.ACTIVITY_PAUSED),
            RawEvent("com.a", t0 + 91_000, RawEvent.ACTIVITY_RESUMED),
            RawEvent("com.a", t0 + 120_000, RawEvent.ACTIVITY_PAUSED),
        )
        val grid = UsageLogic.bucketSessions(UsageLogic.pairSessions(events, endAt = t0 + 120_000), zone)
        // 09:59:30-10:00:00 -> 1 minute in the 9 o'clock cell, 10:00:00-10:01:30
        // -> 2 minutes in the 10 o'clock cell. Before the fragment merge the
        // four jitter fragments would have rounded to 1+1+1+1=4 minutes.
        assertEquals(1L, grid[6, 9])
        assertEquals(2L, grid[6, 10])
    }

    @Test
    fun `bucketSessions still rounds a cell with partial usage up to one minute`() {
        val sessions = listOf(
            ActivitySession("com.a", at("2026-10-25", "09:00:00"), at("2026-10-25", "09:00:30")),
        )
        val grid = UsageLogic.bucketSessions(sessions, zone)
        assertEquals(1L, grid[6, 9])
    }

    @Test
    fun `sessionsInBucket matches only the overlapping hour`() {
        val sessions = listOf(
            ActivitySession("com.a", at("2026-10-25", "13:50:00"), at("2026-10-25", "14:10:00")),
            ActivitySession("com.b", at("2026-10-25", "15:00:00"), at("2026-10-25", "15:20:00")),
        )
        assertEquals(listOf("com.a"), UsageLogic.sessionsInBucket(sessions, 7, 14, zone).map { it.packageName })
        assertEquals(listOf("com.b"), UsageLogic.sessionsInBucket(sessions, 7, 15, zone).map { it.packageName })
        assertTrue(UsageLogic.sessionsInBucket(sessions, 7, 12, zone).isEmpty())
    }

    @Test
    fun `sessionsInDayInterval matches weekday and hour range`() {
        // 2026-10-25 is a Sunday (iso 7)
        val sessions = listOf(
            ActivitySession("com.a", at("2026-10-25", "13:50:00"), at("2026-10-25", "14:10:00")),
            ActivitySession("com.b", at("2026-10-25", "09:00:00"), at("2026-10-25", "09:20:00")),
            ActivitySession("com.c", at("2026-10-24", "13:50:00"), at("2026-10-24", "14:10:00")),
        )
        assertEquals(
            listOf("com.a"),
            UsageLogic.sessionsInDayInterval(sessions, 7, 12, 18, zone).map { it.packageName },
        )
        assertEquals(
            listOf("com.b"),
            UsageLogic.sessionsInDayInterval(sessions, 7, 6, 12, zone).map { it.packageName },
        )
        // Saturday column picks up the Saturday session only
        assertEquals(
            listOf("com.c"),
            UsageLogic.sessionsInDayInterval(sessions, 6, 12, 18, zone).map { it.packageName },
        )
        // Session starting exactly at the range start counts; end is exclusive
        val edge = listOf(
            ActivitySession("com.d", at("2026-10-25", "18:00:00"), at("2026-10-25", "18:30:00")),
        )
        assertEquals(1, UsageLogic.sessionsInDayInterval(edge, 7, 18, 24, zone).size)
        assertTrue(UsageLogic.sessionsInDayInterval(edge, 7, 12, 18, zone).isEmpty())
    }

    @Test
    fun `peak day interval returns the largest cumulative bucket`() {
        val minutes = Array(7) { LongArray(24) }
        minutes[3][18] = 30
        minutes[3][19] = 45
        minutes[5][8] = 60
        val peak = UsageLogic.peakDayInterval(
            HeatmapGrid(minutes),
            listOf(0 to 6, 6 to 12, 12 to 18, 18 to 24),
        )

        assertEquals(3, peak?.dayIndex)
        assertEquals(3, peak?.intervalIndex)
        assertEquals(75L, peak?.minutes)
    }

    @Test
    fun `peak day interval is absent for an empty grid`() {
        val peak = UsageLogic.peakDayInterval(
            HeatmapGrid(Array(7) { LongArray(24) }),
            listOf(0 to 6, 6 to 12, 12 to 18, 18 to 24),
        )

        assertEquals(null, peak)
    }
}
