package app.permissionsentinel.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.permissionsentinel.R
import app.permissionsentinel.SentinelViewModel
import app.permissionsentinel.UiState
import app.permissionsentinel.data.Capability
import app.permissionsentinel.data.CapabilityCoverage
import app.permissionsentinel.ui.components.ActivityHeatmap
import app.permissionsentinel.ui.components.SectionCard
import app.permissionsentinel.ui.components.StatCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    vm: SentinelViewModel,
    labelFor: (String) -> String,
    bottomInset: androidx.compose.ui.unit.Dp,
) {
    val state by vm.ui.collectAsStateWithLifecycle()
    var selectedCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = { vm.refresh() },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.title_overview),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(14.dp))

            if (!state.usageGranted) {
                AccessBanner()
                Spacer(Modifier.height(12.dp))
            }

            LiveCard(state, labelFor)
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    label = stringResource(R.string.stat_records),
                    value = formatCount(state.recordCount),
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    label = stringResource(R.string.stat_apps),
                    value = formatCount(state.sensitiveApps.size),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))

            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.heatmap_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.weight(1f))
                    if (state.windowDays > 0) {
                        Text(
                            text = stringResource(R.string.heatmap_window, state.windowDays),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                state.heatmap?.let { grid ->
                    ActivityHeatmap(
                        grid = grid,
                        windowDays = state.windowDays,
                        sessions = state.sessions,
                        selected = selectedCell,
                        onSelect = { selectedCell = it },
                        labelFor = labelFor,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            CoverageCard(state)
            Spacer(Modifier.height(20.dp))
            Spacer(Modifier.height(bottomInset))
        }
    }
}

@Composable
internal fun AccessBanner() {
    val context = androidx.compose.ui.platform.LocalContext.current
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.banner_access),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(stringResource(R.string.open_settings))
            }
        }
    }
}

@Composable
private fun LiveCard(
    state: UiState,
    labelFor: (String) -> String,
) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.now_foreground),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                val fg = state.foregroundLabel
                Text(
                    text = fg ?: stringResource(R.string.no_usage_data),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(12.dp))
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (state.cameraBusy) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(8.dp)) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(8.dp),
                            strokeWidth = 1.5.dp,
                            color = if (state.cameraBusy) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (state.cameraBusy) stringResource(R.string.now_camera_busy)
                        else stringResource(R.string.now_camera_idle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state.cameraBusy) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CoverageCard(state: UiState) {
    SectionCard {
        Text(
            text = stringResource(R.string.coverage_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        val byCap = state.coverage.associateBy { it.capability }
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                CoverageRow(Capability.MIC, Icons.Outlined.Mic, byCap[Capability.MIC])

                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                CoverageRow(Capability.LOCATION, Icons.Outlined.LocationOn, byCap[Capability.LOCATION])
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                CoverageRow(Capability.CAMERA, Icons.Outlined.PhotoCamera, byCap[Capability.CAMERA])
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                CoverageRow(Capability.CONTACTS, Icons.Outlined.Person, byCap[Capability.CONTACTS])
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Text(
                text = stringResource(R.string.clipboard_motion),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.not_observable),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CoverageRow(
    capability: Capability,
    icon: ImageVector,
    coverage: app.permissionsentinel.data.CapabilityCoverage?,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = capability.id,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = capabilityTitle(capability),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = coverage?.let { formatCount(it.requestingApps) } ?: "0",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun capabilityTitle(capability: Capability): String = when (capability) {
    Capability.MIC -> stringResource(R.string.cap_mic)
    Capability.CAMERA -> stringResource(R.string.cap_camera)
    Capability.LOCATION -> stringResource(R.string.cap_location)
    Capability.CONTACTS -> stringResource(R.string.cap_contacts)
}

fun formatCount(n: Int): String = String.format(java.util.Locale.getDefault(), "%,d", n)
