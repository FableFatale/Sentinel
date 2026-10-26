package app.permissionsentinel.data

/** Sensitive capability taxonomy used across the app. */
enum class Capability(val id: String) {
    MIC("mic"),
    CAMERA("camera"),
    LOCATION("location"),
    CONTACTS("contacts");

    companion object {
        fun fromId(id: String): Capability? = entries.firstOrNull { it.id == id }
    }
}

/** Observed state of one capability for one foreign app. */
enum class PermState {
    /** App declares the permission but grant flags are not visible to us. */
    REQUESTED_ONLY,

    /** Grant flag visible from our process. */
    GRANTED,

    /** Grant flag visible and explicitly not granted. */
    DENIED,
}

data class AppInfo(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    /** Capabilities the app declares, never empty for rows shown in Apps screen filter. */
    val sensitive: Map<Capability, PermState>,
    val requestedSensitiveCount: Int,
)

/** One contiguous foreground session of an app, derived from UsageEvents. */
data class ActivitySession(
    val packageName: String,
    val start: Long,
    val end: Long,
) {
    val durationMs: Long get() = (end - start).coerceAtLeast(0)
}

/** A locally persisted observation record. */
enum class RecordType(val id: String) {
    CAMERA_BUSY_ON("cam_on"),
    CAMERA_BUSY_OFF("cam_off"),
    MIC_BUSY_ON("mic_on"),
    MIC_BUSY_OFF("mic_off"),
    PERMISSION_DIALOG("pdlg"),
    SENSITIVE_FG("sfg");

    companion object {
        fun fromId(id: String): RecordType? = entries.firstOrNull { it.id == id }
    }
}

data class SentinelRecord(
    val timestamp: Long,
    val type: RecordType,
    val packageName: String,
    /** Optional extra, e.g. busy duration in ms when available. */
    val extra: Long = -1L,
)

/** Aggregated heatmap grid: minutes per day-of-week (0=Mon..6=Sun) and hour 0..23. */
data class HeatmapGrid(val minutes: Array<LongArray>) {
    operator fun get(day: Int, hour: Int): Long = minutes[day][hour]
    val maxMinutes: Long
        get() = minutes.maxOf { row -> row.max() }

    override fun equals(other: Any?): Boolean =
        other is HeatmapGrid && minutes.contentDeepEquals(other.minutes)

    override fun hashCode(): Int = minutes.contentDeepHashCode()
}

/** Compact stats shown on the Overview header. */
data class OverviewStats(
    val sensitiveMinutesToday: Long,
    val monitoredApps: Int,
    val recordCount: Int,
    val cameraBusy: Boolean,
    val micBusy: Boolean,
)

/** Live "current sensitive activity" snapshot. */
data class SensitiveNow(
    val foregroundPackage: String?,
    val foregroundSince: Long,
    val foregroundCapabilities: Set<Capability>,
    val cameraBusy: Boolean,
    val micBusy: Boolean,
)

data class CapabilityCoverage(
    val capability: Capability,
    val requestingApps: Int,
    val grantedApps: Int,
    val totalApps: Int,
)
