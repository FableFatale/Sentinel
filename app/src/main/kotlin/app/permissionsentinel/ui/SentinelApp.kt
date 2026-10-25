package app.permissionsentinel.ui

import android.content.Context
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewTimeline
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ViewTimeline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.permissionsentinel.R
import app.permissionsentinel.SentinelViewModel
import app.permissionsentinel.data.PackageSource
import app.permissionsentinel.ui.screens.AppsScreen
import app.permissionsentinel.ui.screens.OverviewScreen
import app.permissionsentinel.ui.screens.SettingsScreen
import app.permissionsentinel.ui.screens.TimelineScreen

private data class Tab(
    val titleRes: Int,
    val filled: ImageVector,
    val outlined: ImageVector,
)

/** System haptic tick — deterministic on OEM builds where view-level feedback is muted. */
object Haptics {
    fun tick(context: Context) {
        try {
            val vibrator = context.getSystemService(VibratorManager::class.java)
                ?.defaultVibrator ?: return
            if (!vibrator.hasVibrator()) return
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        } catch (_: Exception) {
        }
    }
}

@Composable
fun SentinelApp(vm: SentinelViewModel) {
    var tab by rememberSaveable { mutableStateOf(0) }
    val context = LocalContext.current
    val labelFor = remember(context) {
        { pkg: String -> PackageSource.labelFor(context, pkg) }
    }
    // The live snapshot screens draw into; the glass bar blurs it behind itself.
    val glassLayer = rememberGraphicsLayer()

    val tabs = listOf(
        Tab(R.string.tab_overview, Icons.Filled.GridView, Icons.Outlined.GridView),
        Tab(R.string.tab_timeline, Icons.Filled.ViewTimeline, Icons.Outlined.ViewTimeline),
        Tab(R.string.tab_apps, Icons.Filled.Apps, Icons.Outlined.Apps),
        Tab(R.string.tab_settings, Icons.Filled.Settings, Icons.Outlined.Settings),
    )

    // Screens keep scrolling content under the glass bar, so reserve bar height + margins.
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 86.dp

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            GlassBottomBar(
                tabs = tabs,
                selected = tab,
                contentLayer = glassLayer,
                onSelect = { index ->
                    // Light haptic only when the tab actually changes.
                    if (tab != index) {
                        Haptics.tick(context)
                        tab = index
                    }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .drawWithContent {
                    glassLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(glassLayer)
                }
        ) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (
                        slideInHorizontally(tween(220)) { dir * it / 8 } +
                            fadeIn(tween(200))
                        ) togetherWith (
                        slideOutHorizontally(tween(180)) { -dir * it / 8 } +
                            fadeOut(tween(140))
                        )
                },
                label = "screens",
            ) { current ->
                when (current) {
                    0 -> OverviewScreen(
                        vm = vm,
                        labelFor = labelFor,
                        bottomInset = bottomInset,
                        onOpenTimeline = {
                            if (tab != 1) {
                                Haptics.tick(context)
                                tab = 1
                            }
                        },
                    )
                    1 -> TimelineScreen(vm, labelFor, bottomInset)
                    2 -> AppsScreen(vm, labelFor, bottomInset)
                    else -> SettingsScreen(vm, bottomInset)
                }
            }
        }
    }
}

/** Floating frosted-glass bottom bar; blurs the scrolling content passing underneath. */
@Composable
private fun GlassBottomBar(
    tabs: List<Tab>,
    selected: Int,
    contentLayer: GraphicsLayer,
    onSelect: (Int) -> Unit,
) {
    val density = LocalDensity.current
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val shape = RoundedCornerShape(30.dp)
    val tint = MaterialTheme.colorScheme.surface.copy(alpha = if (dark) 0.60f else 0.68f)
    val borderColor = if (dark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.55f)
    val highlight = if (dark) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.85f)
    val blurPx = with(density) { 14.dp.toPx() }
    var barOrigin by remember { mutableStateOf(Offset.Zero) }

    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp)
            .padding(bottom = 10.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(66.dp)
                .onGloballyPositioned { barOrigin = it.positionInRoot() }
                .clip(shape)
        ) {
            // 1. Blurred live copy of the content behind the bar (renderEffect blur).
            Spacer(
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        renderEffect = BlurEffect(blurPx, blurPx, TileMode.Decal)
                    }
                    .drawBehind {
                        translate(-barOrigin.x, -barOrigin.y) {
                            drawLayer(contentLayer)
                        }
                    }
            )
            // 2. Frost tint.
            Box(Modifier.matchParentSize().background(tint))
            // 3. Hairline border + top light highlight for the glass edge.
            Box(
                Modifier
                    .matchParentSize()
                    .border(0.7.dp, borderColor, shape)
                    .drawBehind {
                        drawLine(
                            brush = Brush.horizontalGradient(
                                listOf(Color.Transparent, highlight, Color.Transparent)
                            ),
                            start = Offset(size.width * 0.12f, 0.75f),
                            end = Offset(size.width * 0.88f, 0.75f),
                            strokeWidth = 1.4f,
                        )
                    }
            )
            // 4. Items.
            Row(Modifier.matchParentSize(), verticalAlignment = Alignment.CenterVertically) {
                for ((index, item) in tabs.withIndex()) {
                    BottomBarItem(
                        item = item,
                        selected = selected == index,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelect(index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomBarItem(
    item: Tab,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.90f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "barPress",
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.18f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "barIcon",
    )
    val pillAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(180),
        label = "barPill",
    )
    val tint = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                shape = CircleShape,
                modifier = Modifier
                    .size(38.dp)
                    .graphicsLayer { alpha = pillAlpha },
            ) {}
            Icon(
                imageVector = if (selected) item.filled else item.outlined,
                contentDescription = stringResource(item.titleRes),
                tint = tint,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer { scaleX = iconScale; scaleY = iconScale },
            )
        }
        Text(
            text = stringResource(item.titleRes),
            fontSize = 11.sp,
            color = tint,
        )
    }
}

@Composable
private fun LoadingPlaceholder() {
    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}
