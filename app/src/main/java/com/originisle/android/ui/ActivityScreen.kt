package com.originisle.android.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.originisle.android.island.PlaygroundService
import com.originisle.android.log.CastLog
import com.originisle.android.service.NotificationCastListener
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Listener health plus the live log of what was cast or skipped, and why. */
@Composable
fun ActivityScreen(context: Context, onBack: () -> Unit) {
    // Tick once a second so "last event / connected N ago" and the live status stay fresh.
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { now = System.currentTimeMillis(); delay(1000) }
    }
    val connected = NotificationCastListener.instance != null
    // 0 = all, 1 = cast only, 2 = skipped only.
    var filter by remember { mutableIntStateOf(0) }
    val entries = CastLog.entries.filter {
        when (filter) {
            1 -> it.cast
            2 -> !it.cast
            else -> true
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = ScreenPadding) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                ScreenHeader("Activity", onBack) {
                    CircleIconButton(IsleIcons.Trash, "Clear log", onClick = { CastLog.clear() }, iconSize = 19.dp)
                }
                ListenerCard(connected, now, onReconnect = { reconnectListener(context) })
                SegmentedControl(
                    listOf("All", "Shown", "Skipped"), filter, { filter = it },
                    track = IsleColors.Surface, height = 40.dp,
                )
            }
        }
        if (entries.isEmpty()) {
            item {
                Box(Modifier.clip(RoundedCornerShape(24.dp)).background(IsleColors.Surface)) {
                    EmptyMessage(
                        if (CastLog.entries.isEmpty()) {
                            "Nothing yet. Trigger a notification (or tap Recast all) and it'll show here " +
                                "with whether it was cast or skipped, and why."
                        } else {
                            "Nothing here."
                        },
                    )
                }
            }
        }
        itemsIndexed(entries) { i, e ->
            Column(Modifier.clip(groupItemShape(i, entries.size)).background(IsleColors.Surface)) {
                if (i > 0) GroupDivider(inset = 0.dp)
                LogRow(e)
            }
        }
    }
}

@Composable
private fun ListenerCard(connected: Boolean, now: Long, onReconnect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(if (connected) IsleColors.GoodBg else IsleColors.BadBg)
            .padding(start = 18.dp, end = 12.dp, top = 16.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val dot = if (connected) IsleColors.Good else IsleColors.Bad
        Box(
            Modifier.size(24.dp).clip(CircleShape).background(dot.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) { Box(Modifier.size(12.dp).clip(CircleShape).background(dot)) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                if (connected) "Listening" else "Not listening",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = IsleColors.Text,
            )
            val subtle = if (connected) IsleColors.GoodSubtle else IsleColors.BadSubtle
            Text(
                "Connected ${agoText(NotificationCastListener.connectedAt, now)}",
                fontSize = 13.sp, color = subtle,
            )
            Text(
                "Last event ${agoText(NotificationCastListener.lastEventAt, now)}",
                fontSize = 13.sp, color = subtle,
            )
        }
        CircleIconButton(
            IsleIcons.Refresh, "Reconnect", onReconnect,
            background = if (connected) IsleColors.GoodButton else IsleColors.BadButton,
            tint = if (connected) IsleColors.GoodText else IsleColors.BadText,
            iconSize = 18.dp,
        )
    }
}

@Composable
private fun LogRow(e: CastLog.Entry) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 68.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(if (e.cast) IsleColors.GoodIconBg else IsleColors.SurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (e.cast) IsleIcons.Check else IsleIcons.Dash,
                if (e.cast) "Cast" else "Skipped",
                Modifier.size(16.dp),
                tint = if (e.cast) IsleColors.GoodText else IsleColors.TextSecondary,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                e.title.ifBlank { e.app },
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (e.cast) IsleColors.Text else IsleColors.TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${e.app} · ${clockText(e.time)}",
                fontSize = 12.sp,
                color = IsleColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            outcomeTag(e.outcome),
            modifier = Modifier
                .widthIn(max = 116.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(if (e.cast) IsleColors.CastTagBg else IsleColors.SurfaceHigh)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 14.sp,
            color = if (e.cast) IsleColors.CastTagFg else IsleColors.TextTertiary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** "skipped — silent notification" → "Silent notification"; the icon already says cast/skipped. */
private fun outcomeTag(outcome: String): String =
    outcome.substringAfter(" — ").replaceFirstChar { it.uppercase() }

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
