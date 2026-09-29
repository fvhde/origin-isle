package com.originisle.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared building blocks for the redesigned screens: grouped cards, settings rows, pills, segments. */

/** Page padding shared by every screen. */
val ScreenPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 32.dp)

private val GroupRadius = 24.dp

/** Back button + large title + an optional trailing action, for the Apps and Activity screens. */
@Composable
fun ScreenHeader(title: String, onBack: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CircleIconButton(IsleIcons.Back, "Back", onBack)
        Text(
            title,
            modifier = Modifier.weight(1f).padding(start = 6.dp),
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.5).sp,
            color = IsleColors.Text,
        )
        trailing()
    }
}

@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    background: Color = IsleColors.Surface,
    tint: Color = IsleColors.Text,
    iconSize: Dp = 20.dp,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = background,
        contentColor = tint,
        modifier = Modifier.size(44.dp).semantics { role = Role.Button },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription, Modifier.size(iconSize), tint = tint)
        }
    }
}

/** A full-width (or weighted) rounded button with an optional leading icon. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconSize: Dp = 18.dp,
    container: Color = IsleColors.Surface,
    content: Color = IsleColors.Text,
    enabled: Boolean = true,
    height: Dp = 52.dp,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(height / 2),
        color = container,
        contentColor = content,
        modifier = modifier.heightIn(min = height).alpha(if (enabled) 1f else 0.4f).semantics { role = Role.Button },
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(iconSize), tint = content)
                Spacer(Modifier.width(8.dp))
            }
            Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = content)
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(horizontal = 16.dp),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = IsleColors.TextSecondary,
    )
}

/** A rounded grey card holding a group of rows. */
@Composable
fun GroupCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(GroupRadius)).background(IsleColors.Surface),
        content = content,
    )
}

/**
 * The shape of row [index] of [count] in a group drawn one row per lazy item: only the outer corners
 * are rounded, so consecutive rows read as one card.
 */
fun groupItemShape(index: Int, count: Int): Shape {
    val top = if (index == 0) GroupRadius else 0.dp
    val bottom = if (index == count - 1) GroupRadius else 0.dp
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

/** Hairline between rows, inset to line up with the row titles after the icon tile. */
@Composable
fun GroupDivider(inset: Dp = 62.dp) {
    Box(Modifier.padding(start = inset).fillMaxWidth().height(1.dp).background(IsleColors.SurfaceHigh))
}

@Composable
fun IconTile(icon: ImageVector, color: Color, size: Dp = 32.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(10.dp)).background(color),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(18.dp), tint = Color.White) }
}

/** Icon tile + title (+ subtitle) + trailing content. Clickable when [onClick] is set. */
@Composable
fun SettingRow(
    icon: ImageVector,
    tileColor: Color,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .heightIn(min = 60.dp)
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconTile(icon, tileColor)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = IsleColors.Text)
            if (subtitle != null) {
                Text(subtitle, fontSize = 13.sp, color = IsleColors.TextSecondary, lineHeight = 17.sp)
            }
        }
        trailing()
    }
}

/** A [SettingRow] with a switch; the whole row toggles. */
@Composable
fun ToggleRow(
    icon: ImageVector,
    tileColor: Color,
    title: String,
    checked: Boolean,
    subtitle: String? = null,
    onChange: (Boolean) -> Unit,
) {
    SettingRow(
        icon, tileColor, title,
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        subtitle = subtitle,
    ) {
        IsleSwitch(checked, onCheckedChange = null, modifier = Modifier.padding(end = 6.dp))
    }
}

/** A row that opens another screen: tile, title, optional value, chevron. */
@Composable
fun NavRow(icon: ImageVector, tileColor: Color, title: String, value: String? = null, onClick: () -> Unit) {
    SettingRow(icon, tileColor, title, onClick = onClick) {
        if (value != null) Text(value, fontSize = 14.sp, color = IsleColors.TextTertiary)
        Icon(IsleIcons.ChevronRight, null, Modifier.padding(end = 8.dp).size(18.dp), tint = IsleColors.Chevron)
    }
}

@Composable
fun IsleSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = IsleColors.Accent,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = IsleColors.SwitchOff,
            uncheckedBorderColor = Color.Transparent,
        ),
    )
}

/** iOS-style segmented control. */
@Composable
fun SegmentedControl(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    track: Color = IsleColors.SurfaceHigh,
    height: Dp = 44.dp,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(14.dp))
            .background(track)
            .padding(3.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (on) IsleColors.SurfaceHigher else Color.Transparent)
                    .selectable(selected = on, role = Role.Tab, onClick = { onSelect(i) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (on) Color.White else IsleColors.TextTertiary,
                )
            }
        }
    }
}

/** A small rounded choice button (delay picker, sample picker). */
@Composable
fun ChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectedContainer: Color = IsleColors.Accent,
    selectedContent: Color = Color.White,
    shape: Shape = RoundedCornerShape(12.dp),
    height: Dp = 44.dp,
    horizontalPadding: Dp = 0.dp,
) {
    Box(
        modifier
            .height(height)
            .clip(shape)
            .background(if (selected) selectedContainer else IsleColors.SurfaceHigh)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = horizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) selectedContent else IsleColors.TextChip,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Centered grey message for empty lists. */
@Composable
fun EmptyMessage(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 48.dp),
        fontSize = 15.sp,
        color = IsleColors.TextSecondary,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}
