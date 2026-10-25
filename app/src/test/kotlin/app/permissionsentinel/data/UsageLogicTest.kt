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
    fun `sessionsInBucket matches only the overlapping hour`() {
        val sessions = listOf(
            ActivitySession("com.a", at("2026-10-25", "13:50:00"), at("2026-10-25", "14:10:00")),
            ActivitySession("com.b", at("2026-10-25", "15:00:00"), at("2026-10-25", "15:20:00")),
        )
        assertEquals(listOf("com.a"), UsageLogic.sessionsInBucket(sessions, 7, 14, zone).map { it.packageName })
        assertEquals(listOf("com.b"), UsageLogic.sessionsInBucket(sessions, 7, 15, zone).map { it.packageName })
        assertTrue(UsageLogic.sessionsInBucket(sessions, 7, 12, zone).isEmpty())
    }
}
