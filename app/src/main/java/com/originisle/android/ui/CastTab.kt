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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.originisle.android.cards.SportsCard
import com.originisle.android.island.PlaygroundService
import com.originisle.android.service.NotificationCastListener
import com.originisle.android.ui.samples.IslandSamples

@Composable
fun CastTab(context: Context, prefs: SharedPreferences, onRedoSetup: () -> Unit) {
    var castOn by remember { mutableStateOf(prefs.getBoolean("cast_notifications", false)) }
    var mediaOn by remember { mutableStateOf(prefs.getBoolean("cast_media_sessions", false)) }
    var includeMessages by remember { mutableStateOf(prefs.getBoolean("cast_include_messages", false)) }
    var ignoreSilent by remember { mutableStateOf(prefs.getBoolean("cast_ignore_silent", true)) }
    var autoDismissSec by remember { mutableStateOf(prefs.getInt("cast_auto_dismiss_seconds", 0)) }
    val listenerText = remember { listenerStatusText(context) }
    val batteryText = remember { batteryStatusText(context) }
    val accessibilityText = remember { accessibilityStatusText(context) }

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
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Setup", fontWeight = FontWeight.SemiBold)
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
                    Text(listenerText, style = MaterialTheme.typography.bodySmall)
                    Text(batteryText, style = MaterialTheme.typography.bodySmall)
                    Text(accessibilityText, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "OriginOS also has a separate \"Auto-start\" allow-list in Settings → Battery. " +
                            "Enable Origin Isle there too, or the caster is killed when the screen is off.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(onClick = onRedoSetup, modifier = Modifier.fillMaxWidth()) {
                        Text("Redo first-run setup")
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Casting", fontWeight = FontWeight.SemiBold)
                    ToggleRow("Cast notifications", castOn) {
                        castOn = it; prefs.edit().putBoolean("cast_notifications", it).apply()
                        if (it) PlaygroundService.keepAlive(context)
                    }
                    ToggleRow("Also cast plain chat messages", includeMessages) {
                        includeMessages = it; prefs.edit().putBoolean("cast_include_messages", it).apply()
                    }
                    Text(
                        "Off = only live cards (downloads, navigation, calls, progress). On= ALL",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    ToggleRow("Ignore silent notifications", ignoreSilent) {
                        ignoreSilent = it; prefs.edit().putBoolean("cast_ignore_silent", it).apply()
                    }
                    Text(
                        "On = muted/silent notifications are ignored and won't appear on the island.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    ToggleRow("Cast media sessions", mediaOn) {
                        mediaOn = it; prefs.edit().putBoolean("cast_media_sessions", it).apply()
                    }
                    Text(
                        "Pick which apps are allowed in the Apps tab.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Text("Auto-dismiss island capsule", fontWeight = FontWeight.Medium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val options = listOf(0 to "Never", 5 to "5s", 10 to "10s", 15 to "15s", 30 to "30s")
                        options.forEach { (sec, label) ->
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
                    Text(
                        "Clears the island capsule after specified duration without touching your actual notification.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        item {
            Button(
                onClick = { recastAll(context) },
                modifier = Modifier.fillMaxWidth(),
                enabled = castOn || mediaOn,
            ) { Text("Recast all notifications now") }
            Text(
                "Sweeps every notification currently on the phone and casts the supported ones " +
                    "(downloads, navigation, calls, media, live scores) to the island.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { SportsCard.clear(context) }, modifier = Modifier.weight(1f)) {
                    Text("Clear football")
                }
                OutlinedButton(onClick = { stopAll(context) }, modifier = Modifier.weight(1f)) {
                    Text("Stop all cards")
                }
            }
        }
        item {
            HorizontalDivider()
            Text("Sample cards (tester)", fontWeight = FontWeight.SemiBold)
        }
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
        runCatching {
            android.service.notification.NotificationListenerService.requestRebind(
                android.content.ComponentName(context, NotificationCastListener::class.java),
            )
        }
        android.widget.Toast.makeText(
            context, "Listener not connected yet — grant notification access, reboot app, then tap again.",
            android.widget.Toast.LENGTH_LONG,
        ).show()
        return
    }
    val count = listener.recastAll()
    android.widget.Toast.makeText(
        context, "Recast swept $count notifications.", android.widget.Toast.LENGTH_SHORT,
    ).show()
}
