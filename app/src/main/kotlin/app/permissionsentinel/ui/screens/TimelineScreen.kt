package app.permissionsentinel.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.permissionsentinel.R
import app.permissionsentinel.SentinelViewModel
import app.permissionsentinel.data.ActivitySession
import app.permissionsentinel.data.AppInfo
import app.permissionsentinel.data.Capability
import app.permissionsentinel.ui.components.CompactIconButton
import app.permissionsentinel.ui.components.CompactSearchField
import app.permissionsentinel.ui.components.FilterPill
import app.permissionsentinel.ui.components.PackageAppIcon
import app.permissionsentinel.ui.components.PageHeader
import app.permissionsentinel.ui.components.SectionLabel
import app.permissionsentinel.ui.components.SourceBadge
import app.permissionsentinel.ui.components.glassSurface
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private enum class TimelineRange {
    TODAY,
    YESTERDAY,
    SEVEN_DAYS,
}

private data class TimelineItem(
    val session: ActivitySession,
    val label: String,
    val date: LocalDate,
    val app: AppInfo?,
)

@Composable
fun TimelineScreen(
    vm: SentinelViewModel,
    labelFor: (String) -> String,
    bottomInset: Dp,
) {
    val state by vm.ui.collectAsStateWithLifecycle()
    val zone = remember { ZoneId.systemDefault() }
    val today = remember(state.heatmapTo) {
        val timestamp = state.heatmapTo.takeIf { it > 0L } ?: System.currentTimeMillis()
        Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
    }
    var range by rememberSaveable { mutableStateOf(TimelineRange.TODAY) }
    var query by rememberSaveable { mutableStateOf("") }
    var sensitiveOnly by rememberSaveable { mutableStateOf(false) }
    var filterMenuOpen by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()
    val appByPackage = remember(state.sensitiveApps) {
        state.sensitiveApps.associateBy { it.packageName }
    }

    val timelineItems = remember(state.sessions, range, query, sensitiveOnly, today, appByPackage) {
        val firstDate = when (range) {
            TimelineRange.TODAY -> today
            TimelineRange.YESTERDAY -> today.minusDays(1)
            TimelineRange.SEVEN_DAYS -> today.minusDays(6)
        }
        val lastDate = when (range) {
            TimelineRange.YESTERDAY -> today.minusDays(1)
            else -> today
        }
        val normalizedQuery = query.trim().lowercase(Locale.getDefault())
        state.sessions.asSequence()
            .map { session ->
                TimelineItem(
                    session = session,
                    label = labelFor(session.packageName),
                    date = Instant.ofEpochMilli(session.start).atZone(zone).toLocalDate(),
                    app = appByPackage[session.packageName],
                )
            }
            .filter { it.date in firstDate..lastDate }
            .filter { !sensitiveOnly || it.app != null }
            .filter {
                normalizedQuery.isEmpty() ||
                    it.label.lowercase(Locale.getDefault()).contains(normalizedQuery) ||
                    it.session.packageName.lowercase(Locale.getDefault()).contains(normalizedQuery)
            }
            .sortedByDescending { it.session.start }
            .toList()
    }
    LaunchedEffect(range, query, sensitiveOnly) {
        listState.scrollToItem(0)
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        PageHeader(
            title = stringResource(R.string.timeline_title),
            meta = stringResource(R.string.timeline_record_count, timelineItems.size),
        )
        Spacer(Modifier.height(16.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CompactSearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(R.string.search_records),
                modifier = Modifier.weight(1f),
            )
            Box {
                CompactIconButton(
                    icon = Icons.Outlined.FilterList,
                    contentDescription = stringResource(R.string.filter),
                    active = sensitiveOnly,
                    onClick = {
                        focusManager.clearFocus(force = true)
                        filterMenuOpen = true
                    },
                )
                DropdownMenu(
                    expanded = filterMenuOpen,
                    onDismissRequest = { filterMenuOpen = false },
                    shape = RoundedCornerShape(16.dp),
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.all_apps)) },
                        onClick = {
                            sensitiveOnly = false
                            filterMenuOpen = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.sensitive_apps_only)) },
                        onClick = {
                            sensitiveOnly = true
                            filterMenuOpen = false
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterPill(
                label = stringResource(R.string.day_today),
                selected = range == TimelineRange.TODAY,
                onClick = {
                    focusManager.clearFocus(force = true)
                    range = TimelineRange.TODAY
                },
            )
            FilterPill(
                label = stringResource(R.string.day_yesterday),
                selected = range == TimelineRange.YESTERDAY,
                onClick = {
                    focusManager.clearFocus(force = true)
                    range = TimelineRange.YESTERDAY
                },
            )
            FilterPill(
                label = stringResource(R.string.range_seven_days),
                selected = range == TimelineRange.SEVEN_DAYS,
                onClick = {
                    focusManager.clearFocus(force = true)
                    range = TimelineRange.SEVEN_DAYS
                },
            )
        }
        Spacer(Modifier.height(14.dp))

        if (!state.usageGranted) {
            AccessWarningBanner()
            Spacer(Modifier.height(12.dp))
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(bottom = bottomInset + 16.dp),
        ) {
            if (timelineItems.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.empty_timeline),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 20.dp),
                    )
                }
            } else if (range == TimelineRange.SEVEN_DAYS) {
                timelineItems
                    .groupBy { it.date }
                    .toSortedMap(compareByDescending<LocalDate> { it })
                    .forEach { (date, dayItems) ->
                        item(key = "header-$date") {
                            SectionLabel(
                                text = date.format(
                                    DateTimeFormatter
                                        .ofLocalizedDate(FormatStyle.MEDIUM)
                                        .withLocale(Locale.getDefault())
                                ),
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                            )
                        }
                        items(
                            items = dayItems,
                            key = { "${it.session.packageName}-${it.session.start}-${it.session.end}" },
                        ) { item ->
                            TimelineEvidenceRow(item, zone)
                        }
                    }
            } else {
                items(
                    items = timelineItems,
                    key = { "${it.session.packageName}-${it.session.start}-${it.session.end}" },
                ) { item ->
                    TimelineEvidenceRow(item, zone)
                }
            }
            item {
                Spacer(Modifier.height(16.dp))
                UnavailableObservationNote()
            }
        }
    }
}

@Composable
private fun TimelineEvidenceRow(
    item: TimelineItem,
    zone: ZoneId,
) {
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm:ss") }
    val start = Instant.ofEpochMilli(item.session.start).atZone(zone).format(timeFormatter)
    val end = Instant.ofEpochMilli(item.session.end).atZone(zone).format(timeFormatter)
    val fallback = item.app?.let(::primaryCapabilityIcon) ?: Icons.Outlined.Apps

    Column(
        Modifier
            .fillMaxWidth()
            .height(88.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(87.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PackageAppIcon(
                packageName = item.session.packageName,
                fallback = fallback,
                tintBackground = MaterialTheme.colorScheme.primary.copy(alpha = 0.09f),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.label,
                        fontSize = 16.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "$start–$end",
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                    )
                }
                Text(
                    text = stringResource(R.string.foreground_context),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                SourceBadge(stringResource(R.string.source_usage_stats))
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(start = 58.dp)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline)
        )
    }
}

@Composable
private fun UnavailableObservationNote() {
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(62.dp)
            .glassSurface(cornerRadius = 10.dp)
            .clickable {
                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.unavailable_observation_note),
            fontSize = 12.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = stringResource(R.string.sources_action),
            fontSize = 12.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

private fun primaryCapabilityIcon(app: AppInfo): ImageVector = when {
    app.sensitive.containsKey(Capability.CAMERA) -> Icons.Outlined.PhotoCamera
    app.sensitive.containsKey(Capability.MIC) -> Icons.Outlined.Mic
    app.sensitive.containsKey(Capability.LOCATION) -> Icons.Outlined.LocationOn
    app.sensitive.containsKey(Capability.CONTACTS) -> Icons.Outlined.Person
    else -> Icons.Outlined.Apps
}
