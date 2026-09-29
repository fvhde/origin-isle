package com.originisle.android.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * First-run gate: walks through every authorization the app needs before letting the user into the
 * main screen. Only notification access is mandatory to continue; the rest are strongly recommended
 * (battery, keep-alive, auto-start) and can be granted later from Home's ⋯ › Permissions.
 *
 * The startup row matters more than "recommended" suggests: with "Associated startup" off, nothing
 * brings casting back after a swipe-away — not START_STICKY, not the restart alarm, not the
 * accessibility service — and only a reboot recovers.
 */
@Composable
fun OnboardingScreen(context: Context, prefs: SharedPreferences, onDone: () -> Unit) {
    val tick = rememberResumeTick()

    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { tick.intValue++ }

    val notifGranted = remember(tick.intValue) { isListenerEnabled(context) }
    val postGranted = remember(tick.intValue) {
        Build.VERSION.SDK_INT < 33 ||
            androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    val batteryOk = remember(tick.intValue) { isBatteryUnrestricted(context) }
    val accessOk = remember(tick.intValue) { isAccessibilityEnabled(context) }
    var autoStartAck by remember {
        mutableStateOf(prefs.getBoolean("onboarding_autostart_ack", false))
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = ScreenPadding,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Welcome to Origin Isle",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.7).sp,
                    lineHeight = 38.sp,
                    color = IsleColors.Text,
                )
                Text(
                    "A few permissions are needed before notifications can be cast to the island.",
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    color = IsleColors.TextTertiary,
                )
            }
        }
        item {
            GroupCard {
                OnboardingRow(
                    icon = IsleIcons.Bell,
                    tile = IsleColors.TileBlue,
                    title = "Notification access",
                    description = "Required — lets Origin Isle read notifications so it can re-cast them.",
                    granted = notifGranted,
                    mandatory = true,
                ) { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                GroupDivider()
                OnboardingRow(
                    icon = IsleIcons.Bell,
                    tile = IsleColors.TilePurple,
                    title = "Allow notifications",
                    description = "Lets Origin Isle show its own status notification.",
                    granted = postGranted,
                    mandatory = false,
                ) {
                    if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                GroupDivider()
                OnboardingRow(
                    icon = IsleIcons.Heart,
                    tile = IsleColors.TileTeal,
                    title = "Keep-alive",
                    description = "Reconnects casting when OriginOS restarts the app, instead of " +
                        "waiting for you to open it.",
                    granted = accessOk,
                    mandatory = false,
                ) { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                GroupDivider()
                OnboardingRow(
                    icon = IsleIcons.Battery,
                    tile = IsleColors.TileGreen,
                    title = "Battery unrestricted",
                    description = "Stops OriginOS from killing the background caster to save power.",
                    granted = batteryOk,
                    mandatory = false,
                ) { requestIgnoreBattery(context) }
                GroupDivider()
                OnboardingRow(
                    icon = IsleIcons.Power,
                    tile = IsleColors.TileAmber,
                    title = "Autostart + Associated startup",
                    description = "Turn BOTH on. Without them, closing the app from recents kills " +
                        "casting until you reboot.",
                    granted = autoStartAck,
                    mandatory = false,
                ) {
                    // Only ack if a screen actually opened — a ✓ the user never saw is worse than
                    // no ✓ for the one setting that decides whether casting survives a swipe-away.
                    if (openAutoStartSettings(context)) {
                        autoStartAck = true
                        prefs.edit().putBoolean("onboarding_autostart_ack", true).apply()
                    }
                }
            }
        }
        item {
            PillButton(
                if (notifGranted) "Continue" else "Grant notification access to continue",
                onClick = {
                    prefs.edit().putBoolean("onboarding_done", true).apply()
                    onDone()
                },
                enabled = notifGranted,
                modifier = Modifier.fillMaxWidth(),
                container = IsleColors.Accent,
                content = Color.White,
            )
        }
    }
}

@Composable
private fun OnboardingRow(
    icon: ImageVector,
    tile: Color,
    title: String,
    description: String,
    granted: Boolean,
    mandatory: Boolean,
    onAction: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        IconTile(icon, tile)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    title,
                    modifier = Modifier.weight(1f, fill = false),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = IsleColors.Text,
                )
                if (mandatory) {
                    Text(
                        "Required",
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(IsleColors.CastTagBg)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = IsleColors.CastTagFg,
                    )
                }
            }
            Text(description, fontSize = 13.sp, lineHeight = 18.sp, color = IsleColors.TextSecondary)
            if (!granted) {
                PillButton(
                    "Open settings",
                    onClick = onAction,
                    modifier = Modifier.padding(top = 8.dp),
                    container = IsleColors.SurfaceHigh,
                    content = IsleColors.AccentText,
                    height = 44.dp,
                )
            }
        }
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (granted) IsleColors.GoodIconBg else IsleColors.SurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (granted) {
                Icon(IsleIcons.Check, "Granted", Modifier.size(14.dp), tint = IsleColors.GoodText)
            }
        }
    }
}
