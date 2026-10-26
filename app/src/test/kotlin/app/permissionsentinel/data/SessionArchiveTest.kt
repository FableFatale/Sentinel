package app.permissionsentinel.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class SessionArchiveTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    @get:Rule
    val tmp = TemporaryFolder()

    private fun newArchive(): SessionArchive = SessionArchive(File(tmp.root, "session_archive"))

    private fun dayFile(day: String): File =
        File(tmp.root, "session_archive/sessions-$day.log")

    private fun dayAt(day: String, hour: Int, minute: Int): Long =
        LocalDateTime.of(LocalDate.parse(day), LocalTime.of(hour, minute))
            .atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `merge writes one file per day and read returns window`() {
        val archive = newArchive()
        val sessions = listOf(
            ActivitySession("com.a", dayAt("2026-10-20", 10, 0), dayAt("2026-10-20", 10, 30)),
            ActivitySession("com.b", dayAt("2026-10-21", 22, 0), dayAt("2026-10-22", 1, 15)),
        )
        archive.merge(sessions, zone)
        assertTrue(dayFile("2026-10-20").exists())
        assertTrue(dayFile("2026-10-21").exists())
        // The session belongs to its start day, not split across files.
        assertTrue(dayFile("2026-10-21").readText().contains("com.b|"))

        val read = archive.read(dayAt("2026-10-20", 0, 0), dayAt("2026-10-22", 23, 59), zone)
            .sortedBy { it.start }
        assertEquals(2, read.size)
        assertEquals("com.a", read[0].packageName)
        assertEquals("com.b", read[1].packageName)
    }

    @Test
    fun `merge dedupes by package and start and updates end`() {
        val archive = newArchive()
        val start = dayAt("2026-10-20", 10, 0)
        archive.merge(listOf(ActivitySession("com.a", start, start + 60_000)), zone)
        archive.merge(listOf(ActivitySession("com.a", start, start + 180_000)), zone)
        val lines = dayFile("2026-10-20").readLines().filter { it.isNotBlank() }
        assertEquals(1, lines.size)
        assertEquals(start + 180_000, lines[0].split("|")[2].toLong())
        assertEquals(1, archive.read(start - 1, start + 1, zone).size)
    }

    @Test
    fun `read filters sessions outside the window`() {
        val archive = newArchive()
        val inWindow = ActivitySession("com.in", dayAt("2026-10-20", 12, 0), dayAt("2026-10-20", 12, 5))
        val before = ActivitySession("com.old", dayAt("2026-10-01", 12, 0), dayAt("2026-10-01", 12, 5))
        archive.merge(listOf(inWindow, before), zone)
        val read = archive.read(dayAt("2026-10-20", 0, 0), dayAt("2026-10-20", 23, 0), zone)
        assertEquals(listOf("com.in"), read.map { it.packageName })
    }

    @Test
    fun `read window spilling from previous day is included`() {
        val archive = newArchive()
        // Starts 23:50 Monday, ends 00:10 Tuesday: overlaps the Tuesday window.
        val start = dayAt("2026-10-19", 23, 50)
        archive.merge(listOf(ActivitySession("com.night", start, start + 20 * 60_000)), zone)
        val read = archive.read(dayAt("2026-10-20", 0, 0), dayAt("2026-10-20", 6, 0), zone)
        assertEquals(listOf("com.night"), read.map { it.packageName })
    }

    @Test
    fun `malformed lines are skipped on read`() {
        val archive = newArchive()
        dayFile("2026-10-20").apply { parentFile.mkdirs() }.writeText(
            "garbage\ncom.a|abc|5\ncom.a|1|2|3\n|1|2\n" +
                "com.ok|${dayAt("2026-10-20", 9, 0)}|${dayAt("2026-10-20", 9, 2)}\n"
        )
        val read = archive.read(dayAt("2026-10-20", 0, 0), dayAt("2026-10-20", 23, 0), zone)
        assertEquals(listOf("com.ok"), read.map { it.packageName })
    }

    @Test
    fun `oldestDay reports earliest archived file`() {
        val archive = newArchive()
        assertNull(archive.oldestDay())
        archive.merge(
            listOf(
                ActivitySession("com.a", dayAt("2026-10-20", 10, 0), dayAt("2026-10-20", 10, 5)),
                ActivitySession("com.a", dayAt("2026-10-18", 10, 0), dayAt("2026-10-18", 10, 5)),
            ),
            zone,
        )
        assertEquals(LocalDate.parse("2026-10-18"), archive.oldestDay())
    }
}
