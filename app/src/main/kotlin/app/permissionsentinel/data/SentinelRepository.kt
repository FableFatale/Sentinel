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

    private val _cameraBusy = MutableStateFlow(false)
    val cameraBusy: StateFlow<Boolean> = _cameraBusy

    init {
        cameraWatch.start()
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
    }

    suspend fun refresh(): UsageSnapshot = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val from = now - QUERY_WINDOW_MS
        val granted = UsageSource.isUsageAccessGranted(appContext)
        val sessions = if (granted) UsageSource.querySessions(appContext, from, now) else emptyList()
        UsageSnapshot(
            sessions = sessions,
            from = from,
            to = now,
            heatmap = UsageLogic.bucketSessions(sessions, ZoneId.systemDefault()),
            usageGranted = granted,
        )
    }

    suspend fun clearRecords() = recordStore.clear()

    companion object {
        const val UNKNOWN_ACTOR = "(unknown)"
        const val QUERY_WINDOW_MS: Long = 28L * 24 * 60 * 60 * 1000
    }
}
