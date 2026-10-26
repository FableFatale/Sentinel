package app.permissionsentinel.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import app.permissionsentinel.data.AppInfo
import app.permissionsentinel.data.Capability
import app.permissionsentinel.data.PermState
import app.permissionsentinel.ui.components.CompactIconButton
import app.permissionsentinel.ui.components.CompactSearchField
import app.permissionsentinel.ui.components.FilterPill
import app.permissionsentinel.ui.components.PackageAppIcon
import app.permissionsentinel.ui.components.PageHeader
import app.permissionsentinel.ui.components.SectionLabel
import app.permissionsentinel.ui.components.bouncyPressScale
import java.util.Locale

private enum class AppTypeFilter {
    ALL,
    USER,
    SYSTEM,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(
    vm: SentinelViewModel,
    labelFor: (String) -> String,
    bottomInset: Dp,
    initialCapability: Capability? = null,
) {
    val state by vm.ui.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var capabilityId by rememberSaveable(initialCapability?.id) {
        mutableStateOf(initialCapability?.id)
    }
    val capability = capabilityId?.let(Capability::fromId)
    var appType by rememberSaveable { mutableStateOf(AppTypeFilter.ALL) }
    var typeMenuOpen by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<AppInfo?>(null) }
    val focusManager = LocalFocusManager.current

    val filtered = remember(state.sensitiveApps, query, capability, appType) {
        val normalizedQuery = query.trim().lowercase(Locale.getDefault())
        state.sensitiveApps.filter { app ->
            (capability == null || app.sensitive.containsKey(capability)) &&
                when (appType) {
                    AppTypeFilter.ALL -> true
                    AppTypeFilter.USER -> !app.isSystem
                    AppTypeFilter.SYSTEM -> app.isSystem
                } &&
                (
                    normalizedQuery.isEmpty() ||
                        app.label.lowercase(Locale.getDefault()).contains(normalizedQuery) ||
                        app.packageName.lowercase(Locale.getDefault()).contains(normalizedQuery)
                    )
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        PageHeader(
            title = stringResource(R.string.apps_title),
            meta = stringResource(R.string.apps_visible_count, filtered.size),
        )
        Spacer(Modifier.height(16.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CompactSearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(R.string.search_hint),
                modifier = Modifier.weight(1f),
            )
            Box {
                CompactIconButton(
                    icon = Icons.Outlined.FilterList,
                    contentDescription = stringResource(R.string.filter),
                    active = appType != AppTypeFilter.ALL,
                    onClick = {
                        focusManager.clearFocus(force = true)
                        typeMenuOpen = true
                    },
                )
                DropdownMenu(
                    expanded = typeMenuOpen,
                    onDismissRequest = { typeMenuOpen = false },
                    shape = RoundedCornerShape(16.dp),
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.all_apps)) },
                        onClick = {
                            appType = AppTypeFilter.ALL
                            typeMenuOpen = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.user_apps)) },
                        onClick = {
                            appType = AppTypeFilter.USER
                            typeMenuOpen = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.system_apps)) },
                        onClick = {
                            appType = AppTypeFilter.SYSTEM
                            typeMenuOpen = false
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
                label = stringResource(R.string.filter_all),
                selected = capability == null,
                onClick = {
                    focusManager.clearFocus(force = true)
                    capabilityId = null
                },
            )
            FilterPill(
                label = stringResource(R.string.cap_camera),
                selected = capability == Capability.CAMERA,
                onClick = {
                    focusManager.clearFocus(force = true)
                    capabilityId = Capability.CAMERA.id
                },
            )
            FilterPill(
                label = stringResource(R.string.cap_mic),
                selected = capability == Capability.MIC,
                onClick = {
                    focusManager.clearFocus(force = true)
                    capabilityId = Capability.MIC.id
                },
            )
            FilterPill(
                label = stringResource(R.string.cap_location),
                selected = capability == Capability.LOCATION,
                onClick = {
                    focusManager.clearFocus(force = true)
                    capabilityId = Capability.LOCATION.id
                },
            )
        }
        Spacer(Modifier.height(16.dp))
        SectionLabel(stringResource(R.string.app_inventory))
        Spacer(Modifier.height(4.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomInset + 16.dp),
        ) {
            if (filtered.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.empty_apps),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 20.dp),
                    )
                }
            } else {
                items(filtered, key = { it.packageName }) { app ->
                    AppInventoryRow(app = app, onClick = { selected = app })
                }
            }
        }
    }

    selected?.let { app ->
        ModalBottomSheet(
            onDismissRequest = { selected = null },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        ) {
            AppDetail(app)
        }
    }
}

@Composable
private fun AppInventoryRow(
    app: AppInfo,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val context = LocalContext.current
    val capabilitySummary = Capability.entries
        .filter { app.sensitive.containsKey(it) }
        .joinToString(" · ") { capability ->
            context.getString(
                when (capability) {
                    Capability.MIC -> R.string.cap_mic
                    Capability.CAMERA -> R.string.cap_camera
                    Capability.LOCATION -> R.string.cap_location
                    Capability.CONTACTS -> R.string.cap_contacts
                }
            )
        }

    Column(
        Modifier
            .fillMaxWidth()
            .height(66.dp)
            .bouncyPressScale(interaction)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(65.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PackageAppIcon(
                packageName = app.packageName,
                fallback = primaryCapabilityIcon(app),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = capabilitySummary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
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
private fun AppDetail(app: AppInfo) {
    Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text(
            text = app.label,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = app.packageName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(18.dp))
        SectionLabel(stringResource(R.string.declares_caps))
        Spacer(Modifier.height(6.dp))
        Capability.entries.forEach { cap ->
            app.sensitive[cap]?.let { state ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = capabilityIcon(cap),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = capabilityTitle(cap),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = permissionStateLabel(state),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun permissionStateLabel(state: PermState): String = when (state) {
    PermState.REQUESTED_ONLY -> stringResource(R.string.permission_declared)
    PermState.GRANTED -> stringResource(R.string.status_granted)
    PermState.DENIED -> stringResource(R.string.permission_not_granted)
}

private fun primaryCapabilityIcon(app: AppInfo): ImageVector = when {
    app.sensitive.containsKey(Capability.CAMERA) -> Icons.Outlined.PhotoCamera
    app.sensitive.containsKey(Capability.MIC) -> Icons.Outlined.Mic
    app.sensitive.containsKey(Capability.LOCATION) -> Icons.Outlined.LocationOn
    app.sensitive.containsKey(Capability.CONTACTS) -> Icons.Outlined.Person
    else -> Icons.Outlined.Apps
}

private fun capabilityIcon(capability: Capability): ImageVector = when (capability) {
    Capability.CAMERA -> Icons.Outlined.PhotoCamera
    Capability.MIC -> Icons.Outlined.Mic
    Capability.LOCATION -> Icons.Outlined.LocationOn
    Capability.CONTACTS -> Icons.Outlined.Person
}
