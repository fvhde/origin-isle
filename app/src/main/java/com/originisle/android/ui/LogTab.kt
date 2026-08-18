package com.originisle.android.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.originisle.android.island.PlaygroundService
import com.originisle.android.log.CastLog
import com.originisle.android.service.NotificationCastListener
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogTab(context: Context) {
    // Tick once a second so "last event / connected N ago" and the live status stay fresh.
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { now = System.currentTimeMillis(); delay(1000) }
    }
    val connected = NotificationCastListener.instance != null

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Listener health", fontWeight = FontWeight.SemiBold)
                    Text(
                        if (connected) "● Connected" else "○ Disconnected",
                        color = if (connected) Color(0xFF2E7D32) else Color(0xFFC62828),
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "Connected: ${agoText(NotificationCastListener.connectedAt, now)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "Last notification event: ${agoText(NotificationCastListener.lastEventAt, now)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { reconnectListener(context) }, modifier = Modifier.weight(1f)) {
                            Text("Reconnect")
                        }
                        OutlinedButton(onClick = { CastLog.clear() }, modifier = Modifier.weight(1f)) {
                            Text("Clear log")
                        }
                    }
                }
            }
        }
        item {
            HorizontalDivider()
            Text(
                "Recent decisions (newest first)",
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (CastLog.entries.isEmpty()) {
            item {
                Text(
                    "Nothing yet. Trigger a notification (or tap Recast all) and it'll show here with " +
                        "whether it was cast or skipped, and why.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        items(CastLog.entries) { e ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                Text(
                    if (e.cast) "✓" else "–",
                    color = if (e.cast) Color(0xFF2E7D32) else Color(0xFF9E9E9E),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(20.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        "${clockText(e.time)}  ${e.app}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                    )
                    if (e.title.isNotBlank()) {
                        Text(e.title, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                    Text(
                        e.outcome,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (e.cast) Color(0xFF2E7D32) else Color(0xFF757575),
                    )
                }
            }
            HorizontalDivider()
        }
    }
}

private fun reconnectListener(context: Context) {
    PlaygroundService.keepAlive(context)
    val message = when (NotificationCastListener.forceRebind(context)) {
        NotificationCastListener.RebindResult.REBINDING ->
            "Rebinding listener… give it a few seconds."
        NotificationCastListener.RebindResult.ALREADY_CONNECTED ->
            "Listener is already connected."
        NotificationCastListener.RebindResult.NO_ACCESS ->
            "Notification access isn't granted — turn it on in Settings first."
        NotificationCastListener.RebindResult.FAILED ->
            "Couldn't rebind. Reboot, or toggle notification access off and on."
    }
    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
}

private fun clockText(ts: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ts))

private fun agoText(ts: Long, now: Long): String {
    if (ts <= 0L) return "never"
    val s = ((now - ts) / 1000).coerceAtLeast(0)
    return when {
        s < 2 -> "just now"
        s < 60 -> "${s}s ago"
        s < 3600 -> "${s / 60}m ago"
        else -> "${s / 3600}h ago"
    }
}
