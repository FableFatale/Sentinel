package app.permissionsentinel.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.permissionsentinel.R
import app.permissionsentinel.data.ActivitySession
import app.permissionsentinel.data.HeatmapGrid
import app.permissionsentinel.data.UsageLogic
import app.permissionsentinel.ui.theme.heatmapColor
import java.time.ZoneId

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) { content() }
    }
}

@Composable
fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            AnimatedValueText(
                value = value,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

/** Slides the old value up / new value in whenever [value] changes. */
@Composable
fun AnimatedValueText(
    value: String,
    style: androidx.compose.ui.text.TextStyle,
    color: androidx.compose.ui.graphics.Color,
) {
    AnimatedContent(
        targetState = value,
        transitionSpec = {
            (slideInVertically { it / 2 } + fadeIn(tween(160))) togetherWith
                (slideOutVertically { -it / 2 } + fadeOut(tween(120)))
        },
        label = "animatedValue",
    ) { v ->
        Text(text = v, style = style, color = color, maxLines = 1)
    }
}

/** EasyUI-style press feedback: the card gently shrinks while touched. */
@Composable
fun Modifier.bouncyPressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.975f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "pressScale",
    )
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
}

/**
 * Interactive 7x24 heatmap: rows = weekday (Mon..Sun), columns = hour (0..23).
 * Aggregated over the queried window. Tap a cell to inspect its apps.
 */
@Composable
fun ActivityHeatmap(
    grid: HeatmapGrid,
    windowDays: Int,
    sessions: List<ActivitySession>,
    selected: Pair<Int, Int>?,
    onSelect: (Pair<Int, Int>?) -> Unit,
    labelFor: (String) -> String,
    modifier: Modifier = Modifier,
) {
    val zone = ZoneId.systemDefault()
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    val maxMinutes = grid.maxMinutes.coerceAtLeast(1L)

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(112.dp)
                .pointerInput(grid, selected) {
                    detectTapGestures { pos ->
                        val cellW = size.width / 24f
                        val cellH = size.height / 7f
                        val col = (pos.x / cellW).toInt().coerceIn(0, 23)
                        val row = (pos.y / cellH).toInt().coerceIn(0, 6)
                        onSelect(if (selected == row to col) null else row to col)
                    }
                }
        ) {
            val gap = 2.dp.toPx()
            val cellW = (size.width - gap * 23) / 24f
            val cellH = (size.height - gap * 6) / 7f
            for (day in 0..6) {
                for (hour in 0..23) {
                    val minutes = grid[day, hour]
                    val fraction = minutes.toFloat() / maxMinutes
                    val color = if (minutes <= 0L) emptyColor else heatmapColor(emptyColor, accent, fraction)
                    val isSel = selected == day to hour
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(hour * (cellW + gap), day * (cellH + gap)),
                        size = Size(cellW, cellH),
                        cornerRadius = CornerRadius(if (isSel) 0.dp.toPx() else 2.dp.toPx()),
                        alpha = if (isSel) 1f else 1f,
                    )
                    if (isSel) {
                        drawRoundRect(
                            color = accent,
                            topLeft = Offset(hour * (cellW + gap) - 1.dp.toPx(), day * (cellH + gap) - 1.dp.toPx()),
                            size = Size(cellW + 2.dp.toPx(), cellH + 2.dp.toPx()),
                            cornerRadius = CornerRadius(3.dp.toPx()),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()),
                        )
                    }
                }
            }
        }

        androidx.compose.foundation.layout.Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
        ) {
            Text(
                text = stringResource(R.string.heatmap_legend_by_day),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.less),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(6.dp))
            LegendSwatch(0f)
            LegendSwatch(0.4f)
            LegendSwatch(0.7f)
            LegendSwatch(1f)
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.more),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        selected?.let { (day, hour) ->
            val isoDay = day + 1
            val bucketSessions = UsageLogic.sessionsInBucket(sessions, isoDay, hour, zone)
            val totalMin = UsageLogic.minutesBetween(bucketSessions, 0, Long.MAX_VALUE)
            Spacer(Modifier.height(10.dp))
            val topApps = bucketSessions
                .groupBy { it.packageName }
                .map { (pkg, list) -> pkg to UsageLogic.minutesBetween(list, 0, Long.MAX_VALUE) }
                .sortedByDescending { it.second }
                .take(3)
            val dayName = java.time.DayOfWeek.of(isoDay)
                .getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
            Text(
                text = "$dayName %02d:00 – %02d:00 · %d min".format(hour, (hour + 1) % 24, totalMin),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            for ((pkg, minutes) in topApps) {
                Text(
                    text = "· ${labelFor(pkg)} · $minutes min",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (topApps.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_usage_data),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LegendSwatch(fraction: Float) {
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    Canvas(Modifier.width(14.dp).height(14.dp)) {
        drawRoundRect(
            color = heatmapColor(emptyColor, accent, fraction),
            topLeft = Offset.Zero,
            size = Size(size.width, size.height),
            cornerRadius = CornerRadius(2.dp.toPx()),
        )
    }
}
