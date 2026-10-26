package app.permissionsentinel.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
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
import app.permissionsentinel.ui.components.SourceBadge
import app.permissionsentinel.ui.components.StatCard
import app.permissionsentinel.ui.components.glassSurface
import app.permissionsentinel.ui.theme.warningColor
import app.permissionsentinel.watch.WatchAccessibility
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    vm: SentinelViewModel,
    bottomInset: androidx.compose.ui.unit.Dp,
    onOpenTimeline: () -> Unit,
    onOpenApps: (Capability) -> Unit,
    onOpenSources: () -> Unit,
) {
    val state by vm.ui.collectAsStateWithLifecycle()
    var selectedCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var showMetricInfo by remember { mutableStateOf(false) }
    val zone = remember { ZoneId.systemDefault() }
    val context = LocalContext.current
    // The two system switches are independent; re-read both on every resume.
    var resumeKey by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumeKey++
        onPauseOrDispose { }
    }
    val watchEnabled = remember(resumeKey) { WatchAccessibility.isEnabled(context) }
    val todayMinutes = remember(state.sessions, zone) {
        UsageLogic.minutesToday(state.sessions, System.currentTimeMillis(), zone)
    }
    val updatedAt = remember(state.lastRefreshedAt) {
        state.lastRefreshedAt.takeIf { it > 0 }?.let {
            DateTimeFormatter.ofPattern("HH:mm:ss", Locale.getDefault())
                .format(Instant.ofEpochMilli(it).atZone(zone))
        }
    }

    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = { vm.refresh() },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // Title row: refresh button with the update time as small print.
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.tab_overview),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                if (updatedAt != null) {
                    Text(
                        text = stringResource(R.string.updated_at, updatedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(6.dp))
                }
                IconButton(
                    onClick = { vm.refresh() },
                    enabled = !state.refreshing,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = stringResource(R.string.refresh),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Missing-access hints. When everything is granted the header stays clean.
            if (!state.usageGranted) {
                SourceBadge(text = stringResource(R.string.source_standard_unavailable))
                AccessWarningBanner()
            }
            if (!watchEnabled) {
                WatchWarningBanner()
            }

            // Precisely scoped summary metrics.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    label = stringResource(R.string.stat_today_activity),
                    value = stringResource(
                        R.string.stat_today_minutes_value,
                        formatCount(todayMinutes.toInt()),
                    ),
                    support = stringResource(R.string.stat_today_activity_support),
                    modifier = Modifier.weight(1f),
                    onInfoClick = { showMetricInfo = true },
                )
                StatCard(
                    label = stringResource(R.string.stat_apps),
                    value = stringResource(
                        R.string.app_count_value,
                        formatCount(state.sensitiveApps.size),
                    ),
                    support = stringResource(R.string.stat_apps_support),
                    modifier = Modifier.weight(1f),
                )
            }
            CameraLiveBanner(state)
            MicLiveBanner(state)

            // Permission declarations first: the always-available, audit-verified source.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.coverage_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                state.coverage
                    .sortedByDescending { it.requestingApps }
                    .forEach { coverage ->
                        CoverageRow(
                            coverage = coverage,
                            icon = when (coverage.capability) {
                                Capability.MIC -> Icons.Outlined.Mic
                                Capability.CAMERA -> Icons.Outlined.PhotoCamera
                                Capability.LOCATION -> Icons.Outlined.LocationOn
                                Capability.CONTACTS -> Icons.Outlined.Person
                            },
                            onClick = { onOpenApps(coverage.capability) },
                        )
                    }
            }

            // Foreground heatmap in a collapsible card; the peak line stays visible.
            Column(
                Modifier
                    .fillMaxWidth()
                    .glassSurface(cornerRadius = 16.dp)
                    .animateContentSize()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                var heatmapExpanded by rememberSaveable { mutableStateOf(false) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.heatmap_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    WindowRangeMenu(vm, state)
                    IconButton(onClick = { heatmapExpanded = !heatmapExpanded }) {
                        Icon(
                            imageVector = if (heatmapExpanded) Icons.Outlined.ExpandLess
                            else Icons.Outlined.ExpandMore,
                            contentDescription = if (heatmapExpanded) stringResource(R.string.collapse)
                            else stringResource(R.string.expand),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                state.heatmap?.let { grid ->
                    HeatmapPeakSummary(grid)
                    AnimatedVisibility(visible = heatmapExpanded) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            ActivityHeatmap(
                                grid = grid,
                                selected = selectedCell,
                                onSelect = { selectedCell = it },
                            )
                            HeatmapLegend()
                            selectedCell?.let { (row, day) ->
                                SelectedIntervalBar(state, row, day, zone, onOpenTimeline)
                            }
                        }
                    }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .glassSurface(cornerRadius = 12.dp)
                    .clickable(onClick = onOpenSources)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = warningColor(),
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(18.dp),
                )
                Column(
                    Modifier
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = stringResource(R.string.monitoring_limit_title),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = stringResource(R.string.monitoring_limit_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = stringResource(R.string.sources_action),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(18.dp),
                )
            }

            Spacer(Modifier.height(bottomInset))
        }
    }

    if (showMetricInfo) {
        AlertDialog(
            onDismissRequest = { showMetricInfo = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            title = { Text(stringResource(R.string.today_metric_info_title)) },
            text = { Text(stringResource(R.string.today_metric_info_body)) },
            confirmButton = {
                TextButton(onClick = { showMetricInfo = false }) {
                    Text(stringResource(R.string.confirm_read))
                }
            },
        )
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
            .glassSurface(cornerRadius = 8.dp)
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

/** Independent banner for the accessibility watch — a separate system switch. */
@Composable
internal fun WatchWarningBanner() {
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .glassSurface(cornerRadius = 8.dp)
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
            text = stringResource(R.string.banner_watch_off),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
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
                .glassSurface(cornerRadius = 8.dp, active = true)
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

/** Blue info bar while any app records audio (attribution is not observable to us). */
@Composable
private fun MicLiveBanner(state: UiState) {
    AnimatedVisibility(visible = state.micBusy) {
        Row(
            Modifier
                .fillMaxWidth()
                .glassSurface(cornerRadius = 8.dp, active = true)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Mic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = stringResource(R.string.now_mic_busy),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** "Last N days" control opening a 7/14/30-day range menu. */
@Composable
private fun WindowRangeMenu(vm: SentinelViewModel, state: UiState) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .height(34.dp)
                .glassSurface(cornerRadius = 17.dp)
                .clickable { expanded = true }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.heatmap_window_total, state.windowDays),
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
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
            tonalElevation = 0.dp,
            shadowElevation = 6.dp,
        ) {
            for (days in listOf(7, 14, 30)) {
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
    val totalMinutes = remember(state.heatmap, row, day) {
        var minutes = 0L
        for (hour in from until until) {
            minutes += state.heatmap?.get(day, hour) ?: 0L
        }
        minutes
    }
    val dayName = DayOfWeek.of(day + 1).getDisplayName(TextStyle.SHORT, Locale.getDefault())
    Row(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .glassSurface(cornerRadius = 8.dp, active = true)
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

@Composable
private fun HeatmapPeakSummary(grid: app.permissionsentinel.data.HeatmapGrid) {
    val peak = remember(grid) {
        UsageLogic.peakDayInterval(grid, HeatmapDayIntervals)
    } ?: return
    val (from, until) = HeatmapDayIntervals[peak.intervalIndex]
    val dayName = DayOfWeek.of(peak.dayIndex + 1)
        .getDisplayName(TextStyle.FULL, Locale.getDefault())
    Text(
        text = stringResource(
            R.string.heatmap_peak_summary,
            dayName,
            from,
            until,
            peak.minutes,
        ),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(cornerRadius = 10.dp, active = true)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    )
}

/** Declaration count, inventory ratio and route to the matching app list. */
@Composable
private fun CoverageRow(
    coverage: CapabilityCoverage,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    val outline = MaterialTheme.colorScheme.outline
    val ratio = if (coverage.totalApps == 0) 0f
    else coverage.requestingApps.toFloat() / coverage.totalApps
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
            .drawBehind {
                val stroke = 1.dp.toPx()
                drawLine(
                    color = outline,
                    start = Offset(0f, size.height - stroke / 2),
                    end = Offset(size.width, size.height - stroke / 2),
                    strokeWidth = stroke,
                )
            },
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = coverage.capability.id,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = capabilityTitle(coverage.capability),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    R.string.coverage_count_ratio,
                    coverage.requestingApps,
                    (ratio * 100).toInt(),
                ),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.height(7.dp))
        LinearProgressIndicator(
            progress = { ratio },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
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
