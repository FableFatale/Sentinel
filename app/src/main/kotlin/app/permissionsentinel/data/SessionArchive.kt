package app.permissionsentinel.data

import android.content.Context
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Append-friendly local archive of foreground sessions, one file per day
 * (`sessions-YYYY-MM-DD.log`, lines `pkg|start|end`).
 *
 * The system UsageStats event log only spans days to weeks; this archive is
 * what makes the heatmap and timeline reliable beyond that window. Pure file
 * IO on the app's private storage — no new dependency, nothing leaves device.
 */
class SessionArchive internal constructor(private val dir: File) {

    constructor(context: Context) : this(
        File(context.applicationInfo.dataDir, "session_archive"),
    )

    /**
     * Merges sessions into the per-day files, deduplicating by (package, start)
     * and updating the end of sessions that are still in flight.
     */
    fun merge(sessions: List<ActivitySession>, zone: ZoneId) {
        if (sessions.isEmpty()) return
        dir.mkdirs()
        sessions
            .groupBy { Instant.ofEpochMilli(it.start).atZone(zone).toLocalDate() }
            .forEach { (day, daySessions) ->
                val file = fileFor(day)
                val existing = LinkedHashMap<String, ActivitySession>()
                if (file.exists()) {
                    file.readLines().forEach { line ->
                        decode(line)?.let { existing[key(it)] = it }
                    }
                }
                for (session in daySessions) existing[key(session)] = session
                file.writeText(
                    existing.values.joinToString(separator = "\n", postfix = "\n") { encode(it) }
                )
            }
    }

    /** All archived sessions overlapping [from, to). */
    fun read(from: Long, to: Long, zone: ZoneId): List<ActivitySession> {
        if (!dir.isDirectory) return emptyList()
        // Start one day earlier: sessions can begin before the window and spill in.
        var day = Instant.ofEpochMilli(from).atZone(zone).toLocalDate().minusDays(1)
        val lastDay = Instant.ofEpochMilli(to).atZone(zone).toLocalDate()
        val out = mutableListOf<ActivitySession>()
        while (!day.isAfter(lastDay)) {
            val file = fileFor(day)
            if (file.exists()) {
                file.readLines().forEach { line ->
                    decode(line)?.let {
                        if (it.end > from && it.start < to) out += it
                    }
                }
            }
            day = day.plusDays(1)
        }
        return out
    }

    /** Oldest archived day, or null when the archive is empty. */
    fun oldestDay(): LocalDate? {
        if (!dir.isDirectory) return null
        return dir.listFiles()
            ?.filter { it.name.startsWith(FILE_PREFIX) && it.name.endsWith(FILE_SUFFIX) }
            ?.minByOrNull { it.name }
            ?.let {
                runCatching {
                    LocalDate.parse(it.name.removePrefix(FILE_PREFIX).removeSuffix(FILE_SUFFIX))
                }.getOrNull()
            }
    }

    private fun fileFor(day: LocalDate): File =
        File(dir, "$FILE_PREFIX$day$FILE_SUFFIX")

    private fun key(s: ActivitySession) = "${s.packageName}|${s.start}"

    private fun encode(s: ActivitySession): String =
        "${s.packageName.replace('|', '_')}|${s.start}|${s.end}"

    private fun decode(line: String): ActivitySession? {
        val parts = line.trim().split('|')
        if (parts.size != 3) return null
        val start = parts[1].toLongOrNull() ?: return null
        val end = parts[2].toLongOrNull() ?: return null
        if (end < start || parts[0].isEmpty()) return null
        return ActivitySession(parts[0], start, end)
    }

    private companion object {
        const val FILE_PREFIX = "sessions-"
        const val FILE_SUFFIX = ".log"
    }
}
