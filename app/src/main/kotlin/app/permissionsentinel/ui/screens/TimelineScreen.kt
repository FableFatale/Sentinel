package app.permissionsentinel.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.permissionsentinel.R
import app.permissionsentinel.SentinelViewModel
import app.permissionsentinel.data.ActivitySession
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TimelineScreen(vm: SentinelViewModel, labelFor: (String) -> String, bottomInset: androidx.compose.ui.unit.Dp) {
    val state by vm.ui.collectAsStateWithLifecycle()
    val zone = remember { ZoneId.systemDefault() }
    val today = remember(state.heatmapTo) { Instant.ofEpochMilli(state.heatmapTo).atZone(zone).toLocalDate() }
    var selectedDay by rememberSaveable { mutableStateOf(today.toEpochDay()) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.timeline_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(14.dp))

        if (!state.usageGranted) {
            AccessBanner()
            Spacer(Modifier.height(12.dp))
        }

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (offset in 6 downTo 0) {
                val day = today.minusDays(offset.toLong())
                val selected = day.toEpochDay() == selectedDay
                FilterChip(
                    selected = selected,
                    onClick = { selectedDay = day.toEpochDay() },
                    label = { Text(dayLabel(day, today, zone)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    shape = RoundedCornerShape(10.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        val day = LocalDate.ofEpochDay(selectedDay)
        val daySessions = remember(state.sessions, selectedDay) {
            val matching = state.sessions
                .filter {
                    Instant.ofEpochMilli(it.start).atZone(zone).toLocalDate() == day ||
                        Instant.ofEpochMilli(it.end).atZone(zone).toLocalDate() == day
                }
                .sortedByDescending { it.start }
            // Presentation-level merge: blink sessions of the same app (<5s gap)
            // collapse into one row; stored data is untouched.
            val out = mutableListOf<ActivitySession>()
            for (s in matching) {
                val last = out.lastOrNull()
                if (last != null && last.packageName == s.packageName && last.start - s.end < 5_000L) {
                    out[out.size - 1] = ActivitySession(s.packageName, s.start, last.end)
                } else {
                    out += s
                }
            }
            out
        }
        if (daySessions.isEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.empty_timeline),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = bottomInset + 16.dp),
            ) {
                items(daySessions, key = { it.start.toString() + it.packageName }) { session ->
                    SessionRow(session, zone, labelFor)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun dayLabel(day: LocalDate, today: LocalDate, zone: ZoneId): String {
    return when (day) {
        today -> stringResource(R.string.day_today)
        today.minusDays(1) -> stringResource(R.string.day_yesterday)
        else -> day.format(DateTimeFormatter.ofPattern("E M/d"))
    }
}

@Composable
private fun SessionRow(session: ActivitySession, zone: ZoneId, labelFor: (String) -> String) {
    val fmt = DateTimeFormatter.ofPattern("HH:mm")
    val start = Instant.ofEpochMilli(session.start).atZone(zone).toLocalDateTime().format(fmt)
    val end = Instant.ofEpochMilli(session.end).atZone(zone).toLocalDateTime().format(fmt)
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = labelFor(session.packageName),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                )
                Text(
                    text = "$start – $end",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val minutes = session.durationMs / 60_000L
            Text(
                text = if (minutes > 0) stringResource(R.string.minutes_val, minutes.toInt())
                else stringResource(R.string.minutes_lt1),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}
