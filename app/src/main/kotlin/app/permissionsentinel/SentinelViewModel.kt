package app.permissionsentinel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.permissionsentinel.data.AppSettings
import app.permissionsentinel.data.ActivitySession
import app.permissionsentinel.data.AppInfo
import app.permissionsentinel.data.CapabilityCoverage
import app.permissionsentinel.data.HeatmapGrid
import app.permissionsentinel.data.PackageSource
import app.permissionsentinel.data.SentinelRepository
import app.permissionsentinel.data.UsageLogic
import app.permissionsentinel.data.UsageSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class UiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val usageGranted: Boolean = false,
    val sessions: List<ActivitySession> = emptyList(),
    val windowDays: Int = 0,
    val heatmap: HeatmapGrid? = null,
    val heatmapFrom: Long = 0,
    val heatmapTo: Long = 0,
    val sensitiveApps: List<AppInfo> = emptyList(),
    val coverage: List<CapabilityCoverage> = emptyList(),
    val recordCount: Int = 0,
    val cameraBusy: Boolean = false,
    val foregroundPackage: String? = null,
    val foregroundLabel: String? = null,
)

class SentinelViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as SentinelApplication).repository

    val settings: StateFlow<AppSettings> = repo.settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui

    init {
        viewModelScope.launch {
            repo.cameraBusy.collect { busy -> _ui.update { it.copy(cameraBusy = busy) } }
        }
        viewModelScope.launch {
            repo.recordStore.records.collect { records ->
                _ui.update { it.copy(recordCount = records.size) }
            }
        }
        viewModelScope.launch {
            while (isActive) {
                pollForegroundOnce()
                delay(5_000)
            }
        }
        refresh(initial = true)
    }

    private suspend fun pollForegroundOnce() {
        val granted = _ui.value.usageGranted
        val pkg = if (granted) {
            val now = System.currentTimeMillis()
            // Self is included for the live card only; timeline/heatmap keep it excluded.
            UsageSource.querySessions(getApplication(), now - 120_000, now, excludeSelf = false)
                .lastOrNull()?.packageName
        } else null
        val label = pkg?.let { PackageSource.labelFor(getApplication(), it) }
        _ui.update { it.copy(foregroundPackage = pkg, foregroundLabel = label) }
    }

    fun refresh(initial: Boolean = false) {
        viewModelScope.launch {
            _ui.update {
                it.copy(loading = initial && it.loading, refreshing = !initial)
            }
            val snapshot = repo.refresh()
            val apps = PackageSource.loadSensitiveApps(getApplication())
            val days = ((snapshot.to - snapshot.from) / DAY_MS).toInt()
            _ui.update {
                it.copy(
                    loading = false,
                    refreshing = false,
                    usageGranted = snapshot.usageGranted,
                    sessions = snapshot.sessions,
                    heatmap = snapshot.heatmap,
                    heatmapFrom = snapshot.from,
                    heatmapTo = snapshot.to,
                    windowDays = days,
                    sensitiveApps = apps,
                    coverage = PackageSource.coverage(apps),
                )
            }
            pollForegroundOnce()
        }
    }

    fun setThemeMode(mode: Int) {
        viewModelScope.launch { repo.settingsStore.setThemeMode(mode) }
    }

    fun setLanguageTag(tag: String) {
        viewModelScope.launch { repo.settingsStore.setLanguageTag(tag) }
    }

    fun clearRecords() {
        viewModelScope.launch { repo.clearRecords() }
    }

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
