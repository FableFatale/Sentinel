package app.permissionsentinel.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.selection.toggleable
import androidx.core.graphics.drawable.toBitmap
import java.util.Locale

@Composable
fun Modifier.glassSurface(
    cornerRadius: Dp,
    active: Boolean = false,
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val baseColor = when {
        dark && active -> Color(0xFF1F2A3D).copy(alpha = 0.78f)
        dark -> Color(0xFF202127).copy(alpha = 0.62f)
        active -> Color(0xFFEFF6FF).copy(alpha = 0.80f)
        else -> Color.White.copy(alpha = 0.68f)
    }
    val borderColor = when {
        dark && active -> MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
        dark -> Color.White.copy(alpha = 0.13f)
        active -> MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
        else -> Color.White.copy(alpha = 0.90f)
    }
    val highlight = Color.White.copy(
        alpha = when {
            dark && active -> 0.14f
            dark -> 0.09f
            active -> 0.72f
            else -> 0.78f
        }
    )
    val shadowColor = Color.Black.copy(alpha = if (dark) 0.24f else 0.10f)

    return this
        .shadow(
            elevation = if (active) 10.dp else 7.dp,
            shape = shape,
            ambientColor = shadowColor,
            spotColor = shadowColor,
        )
        .clip(shape)
        .background(baseColor)
        .drawBehind {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(highlight, Color.Transparent),
                    endY = size.height * 0.78f,
                )
            )
        }
        .border(0.8.dp, borderColor, shape)
}

@Composable
fun PageHeader(
    title: String,
    meta: String? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        if (!meta.isNullOrBlank()) {
            Text(
                text = meta,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .glassSurface(cornerRadius = 14.dp)
                    .padding(horizontal = 14.dp, vertical = 5.dp),
                maxLines = 1,
            )
        }
    }
}

@Composable
fun CompactSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .height(44.dp)
            .glassSurface(cornerRadius = 10.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onBackground,
            ),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inner()
                }
            },
        )
    }
}

@Composable
fun CompactIconButton(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(44.dp)
            .glassSurface(cornerRadius = 10.dp, active = active)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (active) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
fun FilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .height(34.dp)
            .glassSurface(cornerRadius = 17.dp, active = selected)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
fun PackageAppIcon(
    packageName: String,
    fallback: ImageVector,
    modifier: Modifier = Modifier,
    tintBackground: Color = MaterialTheme.colorScheme.surface,
) {
    val context = LocalContext.current
    val bitmap: ImageBitmap? = remember(packageName) {
        runCatching {
            context.packageManager
                .getApplicationIcon(packageName)
                .toBitmap(width = 96, height = 96)
                .asImageBitmap()
        }.getOrNull()
    }
    Box(
        modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(tintBackground),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
            )
        } else {
            Icon(
                imageVector = fallback,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
fun SourceBadge(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .height(22.dp)
            .glassSurface(cornerRadius = 11.dp)
            .padding(horizontal = 12.dp, vertical = 3.dp),
        maxLines = 1,
    )
}

@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(Locale.getDefault()),
        fontSize = 11.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
        maxLines = 1,
    )
}

@Composable
fun SettingsRow(
    label: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    danger: Boolean = false,
    showChevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val outline = MaterialTheme.colorScheme.outline
    Row(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            )
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
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = if (danger) {
                if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
                    Color(0xFFFDA29B)
                } else {
                    Color(0xFFB42318)
                }
            } else {
                MaterialTheme.colorScheme.onBackground
            },
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (value != null) {
            Text(
                text = value,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        } else if (showChevron) {
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
fun CompactSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val thumbOffset = animateDpAsState(
        targetValue = if (checked) 20.dp else 2.dp,
        animationSpec = tween(160),
        label = "switchThumb",
    )
    Box(
        Modifier
            .width(42.dp)
            .height(24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (checked) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline
            )
            .toggleable(
                value = checked,
                role = Role.Switch,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onValueChange = onCheckedChange,
            )
    ) {
        Box(
            Modifier
                .offset(x = thumbOffset.value, y = 2.dp)
                .size(20.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White)
        )
    }
}
