package app.permissionsentinel.ui

import android.content.Context
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.shadow
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.permissionsentinel.R
import app.permissionsentinel.SentinelViewModel
import app.permissionsentinel.data.Capability
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

/** Short, explicit navigation pulse for OEM builds that reject predefined effects. */
object Haptics {
    fun tick(context: Context) {
        try {
            val vibrator = context.getSystemService(VibratorManager::class.java)
                ?.defaultVibrator ?: return
            if (!vibrator.hasVibrator()) return
            vibrator.vibrate(
                VibrationEffect.createWaveform(
                    longArrayOf(0L, 45L),
                    intArrayOf(0, 180),
                    -1,
                )
            )
        } catch (_: Exception) {
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SentinelApp(vm: SentinelViewModel) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var requestedAppCapability by rememberSaveable { mutableStateOf<String?>(null) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val labelFor = remember(context) {
        { pkg: String -> PackageSource.labelFor(context, pkg) }
    }
    val glassLayer = rememberGraphicsLayer()
    val tabs = listOf(
        Tab(R.string.tab_overview, Icons.Filled.GridView, Icons.Outlined.GridView),
        Tab(R.string.tab_timeline, Icons.Filled.ViewTimeline, Icons.Outlined.ViewTimeline),
        Tab(R.string.tab_apps, Icons.Filled.Apps, Icons.Outlined.Apps),
        Tab(R.string.tab_settings, Icons.Filled.Settings, Icons.Outlined.Settings),
    )
    val keyboardVisible = WindowInsets.isImeVisible
    val focusManager = LocalFocusManager.current
    BackHandler(enabled = keyboardVisible) {
        focusManager.clearFocus(force = true)
    }
    // Bar: 64dp height + 12dp bottom padding + slack, so the last list item
    // scrolls fully clear of the floating navigation.
    val bottomInset = if (keyboardVisible) 16.dp else 100.dp

    fun selectTab(index: Int) {
        if (tab == index) return
        if (settings.hapticsEnabled) Haptics.tick(context)
        tab = index
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding(),
                )
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        glassLayer.record { this@drawWithContent.drawContent() }
                        drawLayer(glassLayer)
                    }
            ) {
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        val direction = if (targetState > initialState) 1 else -1
                        (
                            slideInHorizontally(
                                animationSpec = tween(230, easing = FastOutSlowInEasing),
                            ) { direction * it / 10 } + fadeIn(tween(180))
                            ) togetherWith (
                            slideOutHorizontally(
                                animationSpec = tween(200, easing = FastOutSlowInEasing),
                            ) { -direction * it / 10 } + fadeOut(tween(140))
                            )
                    },
                    label = "screenTransition",
                ) { current ->
                    when (current) {
                        0 -> OverviewScreen(
                            vm = vm,
                            bottomInset = bottomInset,
                            onOpenTimeline = { selectTab(1) },
                            onOpenApps = { capability ->
                                requestedAppCapability = capability.id
                                selectTab(2)
                            },
                            onOpenSources = { selectTab(3) },
                        )
                        1 -> TimelineScreen(vm, labelFor, bottomInset)
                        2 -> AppsScreen(
                            vm = vm,
                            labelFor = labelFor,
                            bottomInset = bottomInset,
                            initialCapability = requestedAppCapability?.let(Capability::fromId),
                        )
                        else -> SettingsScreen(vm, bottomInset)
                    }
                }
            }

            AnimatedVisibility(
                visible = !keyboardVisible,
                enter = fadeIn(tween(160)) + slideInVertically(tween(220)) { it / 3 },
                exit = fadeOut(tween(120)) + slideOutVertically(tween(180)) { it / 3 },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                GlassBottomBar(
                    tabs = tabs,
                    selected = tab,
                    contentLayer = glassLayer,
                    onSelect = { index ->
                        if (index == 2) requestedAppCapability = null
                        selectTab(index)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 12.dp),
                )
            }
        }
    }
}

/** Four equal targets over a shared, animated glass selection pill. */
@Composable
private fun GlassBottomBar(
    tabs: List<Tab>,
    selected: Int,
    contentLayer: GraphicsLayer,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val shape = RoundedCornerShape(32.dp)
    // 85-92% frost: readable over any content, blur still reads through.
    val tint = if (dark) Color(0xFF18181C).copy(alpha = 0.88f)
    else Color(0xFFF8FAFC).copy(alpha = 0.90f)
    val borderColor = if (dark) Color.White.copy(alpha = 0.20f)
    else Color.Black.copy(alpha = 0.10f)
    val highlight = Color.White.copy(alpha = if (dark) 0.10f else 0.34f)
    val selectedColor = if (dark) Color(0xFF3A3A40).copy(alpha = 0.86f)
    else Color.White.copy(alpha = 0.92f)
    val blurPx = with(density) { 14.dp.toPx() }
    // RenderEffect needs API 31+; on older or driver-broken devices fall back
    // to a solid (fully opaque) bar so text stays legible.
    val blurSupported = android.os.Build.VERSION.SDK_INT >= 31
    var barOrigin by remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(
        modifier
            .height(64.dp)
            .shadow(
                elevation = if (dark) 18.dp else 14.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = if (dark) 0.32f else 0.13f),
                spotColor = Color.Black.copy(alpha = if (dark) 0.32f else 0.13f),
            )
            .onGloballyPositioned { barOrigin = it.positionInRoot() }
            .clip(shape)
    ) {
        Spacer(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (blurSupported) {
                        renderEffect = BlurEffect(blurPx, blurPx, TileMode.Decal)
                    }
                }
                .drawBehind {
                    if (blurSupported) {
                        translate(-barOrigin.x, -barOrigin.y) {
                            drawLayer(contentLayer)
                        }
                    }
                }
        )
        // Without blur support the tint carries the whole job: make it solid.
        Box(
            Modifier
                .fillMaxSize()
                .background(if (blurSupported) tint else if (dark) Color(0xFF18181C) else Color(0xFFF8FAFC))
        )

        val itemWidth = (maxWidth - 12.dp) / tabs.size
        val selectedX by animateDpAsState(
            targetValue = 6.dp + itemWidth * selected,
            animationSpec = tween(240, easing = FastOutSlowInEasing),
            label = "navigationPill",
        )
        Box(
            Modifier
                .offset(x = selectedX, y = 6.dp)
                .width(itemWidth)
                .height(52.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(selectedColor)
                .drawBehind {
                    drawLine(
                        color = Color.White.copy(alpha = if (dark) 0.08f else 0.30f),
                        start = Offset(size.width * 0.20f, 0.6f),
                        end = Offset(size.width * 0.80f, 0.6f),
                        strokeWidth = 1.2f,
                    )
                }
        )

        Row(
            Modifier
                .fillMaxSize()
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { index, item ->
                BottomBarItem(
                    item = item,
                    selected = selected == index,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    onClick = { onSelect(index) },
                )
            }
        }

        Box(
            Modifier
                .fillMaxSize()
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
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "navigationPress",
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "navigationIcon",
    )
    val tint = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier
            .fillMaxHeight()
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .selectable(
                selected = selected,
                role = Role.Tab,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .offset(y = 2.dp)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = if (selected) item.filled else item.outlined,
            contentDescription = stringResource(item.titleRes),
            tint = tint,
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer { scaleX = iconScale; scaleY = iconScale },
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = stringResource(item.titleRes),
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = tint,
            maxLines = 1,
        )
    }
}
