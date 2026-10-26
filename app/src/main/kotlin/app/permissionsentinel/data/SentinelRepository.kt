package app.permissionsentinel.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId

data class UsageSnapshot(
    val sessions: List<ActivitySession>,
    val from: Long,
    val to: Long,
    val heatmap: HeatmapGrid,
    val usageGranted: Boolean,
)

/** Aggregates all real device sources; owns the observation log side effects. */
class SentinelRepository(
    context: Context,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    val recordStore = RecordStore(appContext)
    val settingsStore = SettingsStore(appContext)
    val cameraWatch = CameraWatch(appContext)
    val micWatch = MicWatch(appContext)
    private val sessionArchive = SessionArchive(appContext)

    private val _cameraBusy = MutableStateFlow(false)
    val cameraBusy: StateFlow<Boolean> = _cameraBusy

    private val _micBusy = MutableStateFlow(false)
    val micBusy: StateFlow<Boolean> = _micBusy

    init {
        cameraWatch.start()
        micWatch.start()
        scope.launch {
            cameraWatch.busy.drop(1).collect { busy ->
                _cameraBusy.value = busy
                recordStore.append(
                    SentinelRecord(
                        timestamp = System.currentTimeMillis(),
                        type = if (busy) RecordType.CAMERA_BUSY_ON else RecordType.CAMERA_BUSY_OFF,
                        // Availability callback does not identify the using app; never claim it.
                        packageName = UNKNOWN_ACTOR,
                    )
                )
            }
        }
        scope.launch {
            var lastBusy = false
            micWatch.clients.collect { clients ->
                val busy = clients.isNotEmpty()
                if (busy != lastBusy) {
                    lastBusy = busy
                    // Recording configurations do not expose the client package to us.
                    recordStore.append(
                        SentinelRecord(
                            timestamp = System.currentTimeMillis(),
                            type = if (busy) RecordType.MIC_BUSY_ON else RecordType.MIC_BUSY_OFF,
                            packageName = UNKNOWN_ACTOR,
                        )
                    )
                }
                _micBusy.value = busy
            }
        }
    }

    suspend fun refresh(windowDays: Int = DEFAULT_WINDOW_DAYS): UsageSnapshot = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val from = now - windowDays * DAY_MS
        val zone = ZoneId.systemDefault()
        val granted = UsageSource.isUsageAccessGranted(appContext)
        val live = if (granted) UsageSource.querySessions(appContext, from, now) else emptyList()
        // Persist what the system still reports, then serve the window from the
        // archive so history survives the system's short event-log retention.
        // Live entries win the (package, start) dedupe — their end is freshest.
        sessionArchive.merge(live, zone)
        val archived = sessionArchive.read(from, now, zone)
        val sessions = (live + archived)
            .distinctBy { it.packageName to it.start }
            .sortedBy { it.start }
        UsageSnapshot(
            sessions = sessions,
            from = from,
            to = now,
            heatmap = UsageLogic.bucketSessions(sessions, zone),
            usageGranted = granted,
        )
    }

    suspend fun clearRecords() = recordStore.clear()

    companion object {
        const val UNKNOWN_ACTOR = "(unknown)"
        const val DEFAULT_WINDOW_DAYS = 30
        private const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
