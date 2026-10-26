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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.permissionsentinel.R
import app.permissionsentinel.data.HeatmapGrid
import app.permissionsentinel.ui.theme.heatmapLevels
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle as JTextStyle
import java.util.Locale

/** Time intervals (hour ranges, end exclusive) forming the heatmap rows. */
val HeatmapDayIntervals: List<Pair<Int, Int>> = listOf(0 to 6, 6 to 12, 12 to 18, 18 to 24)

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
    support: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AnimatedValueText(
                value = value,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = support,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
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
 * Day-column heatmap per Figma spec: 7 weekday columns x [HeatmapDayIntervals.size]
 * time-interval rows. Cells show cumulative minutes so intensity is not
 * communicated by color alone.
 */
@Composable
fun ActivityHeatmap(
    grid: HeatmapGrid,
    selected: Pair<Int, Int>?, // (interval row, day column 0=Mon)
    onSelect: (Pair<Int, Int>?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val levels = heatmapLevels(isDarkThemeLocal())
    val maxMinutes = remember(grid) {
        var max = 1L
        for (day in 0..6) {
            for ((from, until) in HeatmapDayIntervals) {
                var sum = 0L
                for (hour in from until until) sum += grid[day, hour]
                if (sum > max) max = sum
            }
        }
        max
    }
    val cellShape = RoundedCornerShape(4.dp)
    val selectionBorder = MaterialTheme.colorScheme.onBackground

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Day label header; leading gutter aligns with time labels below.
        Row(Modifier.fillMaxWidth().height(16.dp)) {
            Spacer(Modifier.width(44.dp))
            for (day in 0..6) {
                Text(
                    text = DayOfWeek.of(day + 1)
                        .getDisplayName(JTextStyle.NARROW, Locale.getDefault()),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        for ((row, interval) in HeatmapDayIntervals.withIndex()) {
            val (from, until) = interval
            val rowSelected = selected?.first == row
            Row(
                Modifier.fillMaxWidth().height(32.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.heatmap_hour_range, from, until),
                    modifier = Modifier.width(44.dp),
                    fontSize = 10.sp,
                    lineHeight = 16.sp,
                    color = if (rowSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                for (day in 0..6) {
                    var minutes = 0L
                    for (hour in from until until) minutes += grid[day, hour]
                    val isSelected = selected == row to day
                    val level = if (minutes <= 0L) 0
                    else (1 + ((minutes.toFloat() / maxMinutes) * 3.99f).toInt()).coerceIn(1, 4)
                    val dayName = DayOfWeek.of(day + 1)
                        .getDisplayName(JTextStyle.FULL, Locale.getDefault())
                    val cellDescription = stringResource(
                        R.string.heatmap_cell_content,
                        dayName,
                        from,
                        until,
                        minutes,
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(cellShape)
                            .background(levels[level])
                            .then(
                                if (isSelected) Modifier.border(2.dp, selectionBorder, cellShape)
                                else Modifier.border(
                                    0.7.dp,
                                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.10f),
                                    cellShape,
                                )
                            )
                            .semantics { contentDescription = cellDescription }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                onSelect(if (isSelected) null else row to day)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = minutes.toString(),
                            fontSize = 10.sp,
                            lineHeight = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = when {
                                level >= 3 -> androidx.compose.ui.graphics.Color.White
                                else -> MaterialTheme.colorScheme.onBackground
                            },
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** Five-swatch intensity legend: Low ▢▢▢▢▢ High. */
@Composable
fun HeatmapLegend(modifier: Modifier = Modifier) {
    val levels = heatmapLevels(isDarkThemeLocal())
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            text = stringResource(R.string.legend_affordance),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.less),
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(3.dp))
            for (level in levels) {
                Box(
                    Modifier
                        .size(9.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(level),
                )
                Spacer(Modifier.width(3.dp))
            }
            Text(
                text = stringResource(R.string.more),
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun isDarkThemeLocal(): Boolean =
    MaterialTheme.colorScheme.background.luminance() < 0.5f
