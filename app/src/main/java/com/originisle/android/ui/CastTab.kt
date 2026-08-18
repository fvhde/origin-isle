package com.originisle.android.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.originisle.android.cards.SportsCard
import com.originisle.android.island.PlaygroundService
import com.originisle.android.service.NotificationCastListener
import com.originisle.android.ui.samples.IslandSamples
import kotlinx.coroutines.launch

@Composable
fun CastTab(context: Context, prefs: SharedPreferences, onRedoSetup: () -> Unit) {
    var castOn by remember { mutableStateOf(prefs.getBoolean("cast_notifications", false)) }
    var mediaOn by remember { mutableStateOf(prefs.getBoolean("cast_media_sessions", false)) }
    var includeMessages by remember { mutableStateOf(prefs.getBoolean("cast_include_messages", false)) }
    var ignoreSilent by remember { mutableStateOf(prefs.getBoolean("cast_ignore_silent", true)) }
    var lockscreenLiveCard by remember { mutableStateOf(prefs.getBoolean("cast_lockscreen_live_card", false)) }
    var autoDismissSec by remember { mutableStateOf(prefs.getInt("cast_auto_dismiss_seconds", 0)) }
    val tick = rememberResumeTick()
    val listenerText = remember(tick.intValue) { listenerStatusText(context) }
    val batteryText = remember(tick.intValue) { batteryStatusText(context) }
    val accessibilityText = remember(tick.intValue) { accessibilityStatusText(context) }
    // Neither vivo toggle is readable, so all we can track is whether the user has been sent to that
    // screen — the same ack flag the onboarding row writes.
    var autoStartAck by remember { mutableStateOf(prefs.getBoolean("onboarding_autostart_ack", false)) }
    val autoStartText = autoStartStatusText(autoStartAck)

    // Setup only needs attention again if something actually broke (OriginOS revoking notification
    // access or re-restricting battery is a known failure mode) — otherwise it stays collapsed to a
    // one-line status so it's not the first thing in the way on every open.
    val listenerOk = remember(tick.intValue) { isListenerEnabled(context) }
    val batteryOk = remember(tick.intValue) { isBatteryUnrestricted(context) }
    var setupExpanded by remember { mutableStateOf(!listenerOk || !batteryOk) }
    // Re-open on resume when something has broken since, or the card stays collapsed over a status
    // line saying access is gone. Only ever expands — collapsing stays the user's call.
    LaunchedEffect(listenerOk, batteryOk) {
        if (!listenerOk || !batteryOk) setupExpanded = true
    }
    var samplesExpanded by remember { mutableStateOf(false) }
    var updateStatus by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("Origin Isle", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Cast notifications to OriginIsland.", style = MaterialTheme.typography.bodyMedium)
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Casting", fontWeight = FontWeight.SemiBold)
                    ToggleRow("Cast notifications", castOn) {
                        castOn = it; prefs.edit().putBoolean("cast_notifications", it).apply()
                        if (it) PlaygroundService.keepAlive(context)
                    }
                    ToggleRow("Cast Media", mediaOn) {
                        mediaOn = it; prefs.edit().putBoolean("cast_media_sessions", it).apply()
                    }
                    Text(
                        "Mimic OriginPlayer for media Apps.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    ToggleRow("Cast non live notifications", includeMessages) {
                        includeMessages = it; prefs.edit().putBoolean("cast_include_messages", it).apply()
                    }
                    Text(
                        "Off: only live cards (score, navigation, etc...).\nOn: ALL",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text("Non live card delay", fontWeight = FontWeight.Medium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf(0 to "Never", 5 to "5s", 10 to "10s", 15 to "15s", 30 to "30s").forEach { (sec, label) ->
                            FilterChip(
                                selected = autoDismissSec == sec,
                                onClick = {
                                    autoDismissSec = sec
                                    prefs.edit().putInt("cast_auto_dismiss_seconds", sec).apply()
                                },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            )
                        }
                    }
                    ToggleRow("Ignore silenced notifications", ignoreSilent) {
                        ignoreSilent = it; prefs.edit().putBoolean("cast_ignore_silent", it).apply()
                    }
                    ToggleRow("Live card on lockscreen", lockscreenLiveCard) {
                        lockscreenLiveCard = it
                        prefs.edit().putBoolean("cast_lockscreen_live_card", it).apply()
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier.fillMaxWidth().clickable { setupExpanded = !setupExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (listenerOk && batteryOk) "Setup ✓" else "Setup — needs attention",
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(if (setupExpanded) "▲" else "▼", style = MaterialTheme.typography.bodySmall)
                    }
                    if (setupExpanded) {
                        Button(
                            onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Grant notification access") }
                        OutlinedButton(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Allow Origin Isle notifications") }
                        OutlinedButton(
                            onClick = { requestIgnoreBattery(context) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Stop OriginOS killing it (battery)") }
                        OutlinedButton(
                            onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Keep alive without status-bar icon") }
                        Text(
                            "Enable \"Origin Isle keep-alive\" under Accessibility to run in the background " +
                                "with NO status-bar icon. It reads nothing.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        OutlinedButton(
                            onClick = {
                                if (openAutoStartSettings(context)) {
                                    autoStartAck = true
                                    prefs.edit().putBoolean("onboarding_autostart_ack", true).apply()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Autostart + Associated startup") }
                        Text(
                            "Turn BOTH on, or closing the app from recents kills casting until you reboot.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(listenerText, style = MaterialTheme.typography.bodySmall)
                        Text(batteryText, style = MaterialTheme.typography.bodySmall)
                        Text(accessibilityText, style = MaterialTheme.typography.bodySmall)
                        Text(autoStartText, style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = onRedoSetup, modifier = Modifier.fillMaxWidth()) {
                            Text("Redo first-run setup")
                        }
                    }
                }
            }
        }
        item {
            Button(
                onClick = { recastAll(context) },
                modifier = Modifier.fillMaxWidth(),
                enabled = castOn || mediaOn,
            ) { Text("Recast all notifications now") }
        }
        item {
            Column {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            updateStatus = "Checking…"
                            updateStatus = when (val result = UpdateChecker.check(context)) {
                                is UpdateChecker.Result.UpToDate ->
                                    "You're on the latest version (v${result.current})."
                                is UpdateChecker.Result.UpdateAvailable -> {
                                    UpdateChecker.openRelease(context, result.url)
                                    "Update available: v${result.latest} — opening GitHub…"
                                }
                                is UpdateChecker.Result.Error -> result.message
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Check for updates") }
                updateStatus?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        item {
            Button(
                onClick = { stopAll(context)  },
                modifier = Modifier.fillMaxWidth(),
                enabled = true,
            ) { Text("Stop all cards") }
        }
        item {
            HorizontalDivider()
            Row(
                Modifier.fillMaxWidth().clickable { samplesExpanded = !samplesExpanded }.padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Sample cards", fontWeight = FontWeight.SemiBold)
                Text(if (samplesExpanded) "▲" else "▼", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (samplesExpanded) {
            items(IslandSamples.all) { sample ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(sample.name, fontWeight = FontWeight.Medium)
                            Text(sample.summary, style = MaterialTheme.typography.bodySmall)
                        }
                        ElevatedButton(onClick = { sample.post(context) }) { Text("Send") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun stopAll(context: Context) {
    context.startService(Intent(context, PlaygroundService::class.java).setAction(PlaygroundService.ACTION_STOP))
}

/**
 * Ask the bound notification listener to replay every current notification onto the island. If the
 * listener isn't connected (access not granted, or the binding went stale after a kill), keep it
 * alive and request a rebind so the sweep works on the next tap.
 */
private fun recastAll(context: Context) {
    PlaygroundService.keepAlive(context)
    val listener = NotificationCastListener.instance
    if (listener == null) {
        val message = when (NotificationCastListener.forceRebind(context)) {
            NotificationCastListener.RebindResult.NO_ACCESS ->
                "Notification access isn't granted — turn it on in Settings, then tap again."
            else ->
                "Listener not connected — reconnecting now. Give it a few seconds, then tap again."
        }
        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
        return
    }
    val count = listener.recastAll()
    android.widget.Toast.makeText(
        context, "Recast swept $count notifications.", android.widget.Toast.LENGTH_SHORT,
    ).show()
}
