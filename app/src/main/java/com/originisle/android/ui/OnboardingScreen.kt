package com.originisle.android.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * First-run gate: walks through every authorization the app needs before letting the user into the
 * main tabs. Only notification access is mandatory to continue; the rest are strongly recommended
 * (battery, keep-alive, auto-start) and can be granted later from the Cast tab's "Redo setup" button.
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

    Scaffold { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Welcome to Origin Isle",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "A few permissions are needed before notifications can be cast to the island.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            item {
                OnboardingRow(
                    title = "Notification access",
                    description = "Required — lets Origin Isle read notifications so it can re-cast them.",
                    granted = notifGranted,
                    mandatory = true,
                ) { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            }
            item {
                OnboardingRow(
                    title = "Allow notifications",
                    description = "Lets Origin Isle show its own status notification.",
                    granted = postGranted,
                    mandatory = false,
                ) {
                    if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            item {
                OnboardingRow(
                    title = "Keep-alive",
                    description = "Reconnects casting when OriginOS restarts the app, instead of " +
                        "waiting for you to open it.",
                    granted = accessOk,
                    mandatory = false,
                ) { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            }
            item {
                OnboardingRow(
                    title = "Battery unrestricted",
                    description = "Stops OriginOS from killing the background caster to save power.",
                    granted = batteryOk,
                    mandatory = false,
                ) { requestIgnoreBattery(context) }
            }
            item {
                OnboardingRow(
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
            item {
                Button(
                    onClick = {
                        prefs.edit().putBoolean("onboarding_done", true).apply()
                        onDone()
                    },
                    enabled = notifGranted,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (notifGranted) "Continue" else "Grant notification access to continue") }
            }
        }
    }
}

@Composable
private fun OnboardingRow(
    title: String,
    description: String,
    granted: Boolean,
    mandatory: Boolean,
    onAction: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    (if (mandatory) "$title (required)" else title),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (granted) "✓" else "○",
                    color = if (granted) Color(0xFF34C759) else Color(0xFF9E9E9E),
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(description, style = MaterialTheme.typography.bodySmall)
            if (!granted) {
                Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) { Text("Open settings") }
            }
        }
    }
}
