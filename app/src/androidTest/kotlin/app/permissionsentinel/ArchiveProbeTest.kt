package app.permissionsentinel

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.permissionsentinel.data.ActivitySession
import app.permissionsentinel.data.SessionArchive
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.ZoneId

/**
 * Verifies that archived sessions outlive the system UsageStats retention:
 * a session 20 days back (far beyond the system event log, and older than the
 * 7-day heatmap default) is merged into the archive and served by
 * [SentinelRepository.refresh] inside the 30-day window.
 */
@RunWith(AndroidJUnit4::class)
class ArchiveProbeTest {

    private val tag = "SentinelProbe"

    @Test
    fun probe_archiveServesBeyondSystemRetention(): Unit = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as SentinelApplication
        val repo = app.repository
        val zone = ZoneId.systemDefault()

        val probePkg = "archive.probe.session"
        val start = System.currentTimeMillis() - 20L * 24 * 3600 * 1000
        val injected = ActivitySession(probePkg, start, start + 3 * 60_000)

        val archive = SessionArchive(app)
        archive.merge(listOf(injected), zone)

        val snapshot = repo.refresh(windowDays = 30)
        val found = snapshot.sessions.filter { it.packageName == probePkg }
        println("$tag archive probeSessions=${found.size} totalSessions=${snapshot.sessions.size}")
        println("$tag archive RESULT=ARCHIVED_HISTORY_SERVED=${found.isNotEmpty()}")
        println("$tag archive oldestArchivedDay=${archive.oldestDay()}")

        // Cleanup: strip the synthetic rows so the user archive stays clean.
        val dir = File(app.applicationInfo.dataDir, "session_archive")
        dir.listFiles()?.forEach { file ->
            val kept = file.readLines().filter { !it.startsWith("$probePkg|") }
            file.writeText(if (kept.isEmpty()) "" else kept.joinToString("\n", postfix = "\n"))
        }
        assertTrue("archived session not served beyond system retention", found.isNotEmpty())
    }
}
