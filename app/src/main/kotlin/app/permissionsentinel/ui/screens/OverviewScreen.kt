package app.permissionsentinel.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.permissionsentinel.R
import app.permissionsentinel.SentinelViewModel
import app.permissionsentinel.UiState
import app.permissionsentinel.data.Capability
import app.permissionsentinel.data.CapabilityCoverage
import app.permissionsentinel.data.UsageLogic
import app.permissionsentinel.ui.components.ActivityHeatmap
import app.permissionsentinel.ui.components.HeatmapDayIntervals
import app.permissionsentinel.ui.components.HeatmapLegend
import app.permissionsentinel.ui.components.StatCard
import app.permissionsentinel.ui.theme.detailBarColor
import app.permissionsentinel.ui.theme.warningColor
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    vm: SentinelViewModel,
    labelFor: (String) -> String,
    bottomInset: androidx.compose.ui.unit.Dp,
    onOpenTimeline: () -> Unit,
) {
    val state by vm.ui.collectAsStateWithLifecycle()
    var selectedCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val zone = remember { ZoneId.systemDefault() }

    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = { vm.refresh() },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Activity overview: title, compact stats, access warning.
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = stringResource(R.string.title_overview),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
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
                if (!state.usageGranted) {
                    AccessWarningBanner()
                }
                CameraLiveBanner(state)
            }

            // Activity heatmap.
            Column(
                Modifier.animateContentSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.heatmap_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.weight(1f))
                    WindowRangeMenu(vm, state)
                }
                state.heatmap?.let { grid ->
                    ActivityHeatmap(
                        grid = grid,
                        selected = selectedCell,
                        onSelect = { selectedCell = it },
                    )
                }
                HeatmapLegend()
                selectedCell?.let { (row, day) ->
                    SelectedIntervalBar(state, row, day, zone, onOpenTimeline)
                }
            }

            // Permission coverage.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.coverage_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                val byCap = state.coverage.associateBy { it.capability }
                Row(Modifier.fillMaxWidth()) {
                    CoverageCell(Capability.MIC, Icons.Outlined.Mic, byCap[Capability.MIC], Modifier.weight(1f))
                    Spacer(Modifier.width(16.dp))
                    CoverageCell(Capability.CAMERA, Icons.Outlined.PhotoCamera, byCap[Capability.CAMERA], Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth()) {
                    CoverageCell(Capability.LOCATION, Icons.Outlined.LocationOn, byCap[Capability.LOCATION], Modifier.weight(1f))
                    Spacer(Modifier.width(16.dp))
                    CoverageCell(Capability.CONTACTS, Icons.Outlined.Person, byCap[Capability.CONTACTS], Modifier.weight(1f))
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.clipboard_motion),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = stringResource(R.string.not_observable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Spacer(Modifier.height(bottomInset))
        }
    }
}

/** Outlined banner with amber triangle; opens the usage-access settings page. */
@Composable
internal fun AccessWarningBanner() {
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.WarningAmber,
            contentDescription = null,
            tint = warningColor(),
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = stringResource(R.string.banner_access),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
            contentPadding = PaddingValues(horizontal = 8.dp),
            modifier = Modifier.height(36.dp),
        ) {
            Text(
                text = stringResource(R.string.open_settings),
                fontSize = 13.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Blue info bar shown only while a camera is actually in use (live signal). */
@Composable
private fun CameraLiveBanner(state: UiState) {
    AnimatedVisibility(visible = state.cameraBusy) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(detailBarColor())
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.PhotoCamera,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = stringResource(R.string.now_camera_busy),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** "Last N days ⌄" control opening a 7/14/28-day range menu. */
@Composable
private fun WindowRangeMenu(vm: SentinelViewModel, state: UiState) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.clickable { expanded = true },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.heatmap_window, state.windowDays),
                fontSize = 13.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(
                imageVector = Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (days in listOf(7, 14, 28)) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.heatmap_window, days)) },
                    onClick = {
                        expanded = false
                        if (days != state.windowDays) vm.setWindowDays(days)
                    },
                )
            }
        }
    }
}

/** Tinted bar summarizing the selected weekday interval; taps through to Timeline. */
@Composable
private fun SelectedIntervalBar(
    state: UiState,
    row: Int,
    day: Int,
    zone: ZoneId,
    onOpenTimeline: () -> Unit,
) {
    val (from, until) = HeatmapDayIntervals[row]
    val bucketSessions = remember(state.sessions, row, day) {
        UsageLogic.sessionsInDayInterval(state.sessions, day + 1, from, until, zone)
    }
    val totalMinutes = UsageLogic.minutesBetween(bucketSessions, 0, Long.MAX_VALUE)
    val dayName = DayOfWeek.of(day + 1).getDisplayName(TextStyle.SHORT, Locale.getDefault())
    Row(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(detailBarColor())
            .clickable(onClick = onOpenTimeline)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "$dayName %02d:00 – %02d:00".format(from, until % 24),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(R.string.minutes_val, totalMinutes.toInt()),
            fontSize = 13.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
        )
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = stringResource(R.string.tab_timeline),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** Flat coverage cell: 20dp icon + 14sp name + count, hairline bottom border. */
@Composable
private fun CoverageCell(
    capability: Capability,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    coverage: CapabilityCoverage?,
    modifier: Modifier = Modifier,
) {
    val outline = MaterialTheme.colorScheme.outline
    Row(
        modifier
            .height(44.dp)
            .drawBehind {
                val stroke = 1.dp.toPx()
                drawLine(
                    color = outline,
                    start = Offset(0f, size.height - stroke / 2),
                    end = Offset(size.width, size.height - stroke / 2),
                    strokeWidth = stroke,
                )
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = capability.id,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = capabilityTitle(capability),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = coverage?.let { formatCount(it.requestingApps) } ?: "0",
            fontSize = 13.sp,
            lineHeight = 16.sp,
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
