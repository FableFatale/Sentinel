package app.permissionsentinel.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.permissionsentinel.R
import app.permissionsentinel.SentinelViewModel
import app.permissionsentinel.data.AppInfo
import app.permissionsentinel.data.Capability
import app.permissionsentinel.data.UsageLogic
import app.permissionsentinel.ui.components.bouncyPressScale
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(vm: SentinelViewModel, labelFor: (String) -> String, bottomInset: androidx.compose.ui.unit.Dp) {
    val state by vm.ui.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<AppInfo?>(null) }
    val zone = remember { ZoneId.systemDefault() }

    val today = remember(state.heatmapTo) {
        Instant.ofEpochMilli(state.heatmapTo).atZone(zone).toLocalDate()
    }
    val minutesToday = remember(state.sessions, today) {
        val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        state.sessions.groupBy { it.packageName }
            .mapValues { (_, sessions) -> UsageLogic.minutesBetween(sessions, dayStart, Long.MAX_VALUE) }
    }

    val filtered = remember(state.sensitiveApps, query, filter) {
        val q = query.trim().lowercase()
        val cap = filter?.let { Capability.fromId(it) }
        state.sensitiveApps.filter { app ->
            (cap == null || app.sensitive.containsKey(cap)) &&
                (q.isEmpty() || app.label.lowercase().contains(q) || app.packageName.contains(q))
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.apps_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.apps_count, state.sensitiveApps.size),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.search_hint)) },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = filter == null,
                onClick = { filter = null },
                label = { Text(stringResource(R.string.filter_all)) },
            )
            for (cap in Capability.entries) {
                FilterChip(
                    selected = filter == cap.id,
                    onClick = { filter = if (filter == cap.id) null else cap.id },
                    label = { Text(capabilityTitle(cap)) },
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        if (filtered.isEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.empty_apps),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = bottomInset + 8.dp),
            ) {
                items(filtered, key = { it.packageName }) { app ->
                    AppRow(app, minutesToday[app.packageName] ?: 0) { selected = app }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    selected?.let { app ->
        ModalBottomSheet(onDismissRequest = { selected = null }) {
            AppDetail(app, minutesToday[app.packageName] ?: 0, zone)
        }
    }
}

@Composable
private fun AppRow(app: AppInfo, minutesToday: Long, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .bouncyPressScale(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                for (cap in Capability.entries) {
                    if (app.sensitive.containsKey(cap)) {
                        Icon(
                            imageVector = capIcon(cap),
                            contentDescription = cap.id,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (minutesToday > 0) {
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.minutes_val, minutesToday.toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AppDetail(app: AppInfo, minutesToday: Long, zone: ZoneId) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(app.label, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
        Text(
            app.packageName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.declares_caps),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(4.dp))
        for (cap in Capability.entries) {
            app.sensitive[cap]?.let {
                Text(
                    text = capabilityTitle(cap),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.minutes_today, minutesToday.toInt()),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(24.dp))
    }
}

private fun capIcon(cap: Capability): ImageVector = when (cap) {
    Capability.MIC -> Icons.Outlined.Mic
    Capability.CAMERA -> Icons.Outlined.PhotoCamera
    Capability.LOCATION -> Icons.Outlined.LocationOn
    Capability.CONTACTS -> Icons.Outlined.Person
}
