package com.originisle.android.ui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.originisle.android.island.PlaygroundService
import com.originisle.android.service.NotificationCastListener
import com.originisle.android.ui.samples.IslandSamples
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The main screen: listener status, the sample-card tester, casting settings, and links to the Apps
 * and Activity screens. Setup/permissions and the update check live in the ⋯ menu.
 */
@Composable
fun HomeScreen(
    context: Context,
    prefs: SharedPreferences,
    onRedoSetup: () -> Unit,
    onOpenApps: () -> Unit,
    onOpenActivity: () -> Unit,
) {
    var castOn by remember { mutableStateOf(prefs.getBoolean("cast_notifications", false)) }
    var mediaOn by remember { mutableStateOf(prefs.getBoolean("cast_media_sessions", false)) }
    var includeMessages by remember { mutableStateOf(prefs.getBoolean("cast_include_messages", false)) }
    var ignoreSilent by remember { mutableStateOf(prefs.getBoolean("cast_ignore_silent", true)) }
    var lockscreenLiveCard by remember { mutableStateOf(prefs.getBoolean("cast_lockscreen_live_card", false)) }
    var autoDismissSec by remember { mutableStateOf(prefs.getInt("cast_auto_dismiss_seconds", 0)) }
    val appsOff = remember { prefs.getStringSet("cast_ignored_apps", emptySet()).orEmpty().size }
    val tick = rememberResumeTick()
    // Setup only needs attention again if something actually broke (OriginOS revoking notification
    // access or re-restricting battery is a known failure mode) — then Home shows a one-line banner;
    // otherwise the permissions stay behind the ⋯ menu so they're not in the way on every open.
    val listenerOk = remember(tick.intValue) { isListenerEnabled(context) }
    val batteryOk = remember(tick.intValue) { isBatteryUnrestricted(context) }
    var permissionsOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var updateStatus by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // Poll the listener once a second so the status pill stays live.
    var connected by remember { mutableStateOf(NotificationCastListener.instance != null) }
    LaunchedEffect(Unit) {
        while (true) { connected = NotificationCastListener.instance != null; delay(1000) }
    }

    if (permissionsOpen) {
        PermissionsSheet(context, prefs, onDismiss = { permissionsOpen = false }, onRedoSetup = onRedoSetup)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = ScreenPadding,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item {
            Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Origin Isle",
                        fontSize = 34.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.7).sp,
                        lineHeight = 38.sp,
                        color = IsleColors.Text,
                    )
                    StatusPill(connected, onClick = onOpenActivity)
                }
                Box {
                    CircleIconButton(IsleIcons.More, "More", onClick = { menuOpen = true })
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        offset = DpOffset(0.dp, 8.dp),
                        shape = RoundedCornerShape(18.dp),
                        containerColor = IsleColors.Menu,
                    ) {
                        MenuItem("Check for updates") {
                            menuOpen = false
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
                        }
                        MenuItem("Permissions") {
                            menuOpen = false
                            permissionsOpen = true
                        }
                    }
                }
            }
            updateStatus?.let {
                Text(
                    it,
                    modifier = Modifier.padding(start = 4.dp, top = 12.dp),
                    fontSize = 13.sp,
                    color = IsleColors.TextSecondary,
                )
            }
        }

        if (!listenerOk || !batteryOk) {
            item { SetupAttentionBanner(onClick = { permissionsOpen = true }) }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SampleTester(context)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton(
                        "Recast all", onClick = { recastAll(context) },
                        modifier = Modifier.weight(1f), icon = IsleIcons.Refresh,
                        enabled = castOn || mediaOn,
                    )
                    PillButton(
                        "Stop all", onClick = { stopAll(context) },
                        modifier = Modifier.weight(1f), icon = IsleIcons.Stop, iconSize = 16.dp,
                        content = IsleColors.Danger,
                    )
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Send to island")
                GroupCard {
                    ToggleRow(IsleIcons.Bell, IsleColors.TileBlue, "Notifications", castOn) {
                        castOn = it; prefs.edit().putBoolean("cast_notifications", it).apply()
                        if (it) PlaygroundService.keepAlive(context)
                    }
                    GroupDivider()
                    ToggleRow(
                        IsleIcons.Music, IsleColors.TileOrange, "Media", mediaOn,
                        subtitle = "Mimic OriginPlayer for media apps",
                    ) {
                        mediaOn = it; prefs.edit().putBoolean("cast_media_sessions", it).apply()
                    }
                }
            }
        }

        item {
            Column(
                Modifier.alpha(if (castOn) 1f else 0.4f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SectionLabel("Notifications")
                GroupCard {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SegmentedControl(
                            listOf("Live only", "Everything"),
                            selected = if (includeMessages) 1 else 0,
                            onSelect = {
                                includeMessages = it == 1
                                prefs.edit().putBoolean("cast_include_messages", includeMessages).apply()
                            },
                        )
                        Text(
                            if (includeMessages) "Every notification, plain messages included."
                            else "Only live cards — score, navigation, payment, etc.",
                            modifier = Modifier.padding(horizontal = 4.dp),
                            fontSize = 13.sp,
                            color = IsleColors.TextSecondary,
                        )
                    }
                    if (includeMessages) {
                        GroupDivider()
                        SettingRow(IsleIcons.Clock, IsleColors.TileTeal, "Hide after", subtitle = "Non live cards")
                        Row(
                            Modifier.padding(start = 62.dp, end = 16.dp, bottom = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            listOf(0 to "Never", 5 to "5s", 10 to "10s", 15 to "15s", 30 to "30s").forEach { (sec, label) ->
                                ChoiceChip(
                                    label,
                                    selected = autoDismissSec == sec,
                                    onClick = {
                                        autoDismissSec = sec
                                        prefs.edit().putInt("cast_auto_dismiss_seconds", sec).apply()
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                    GroupDivider()
                    ToggleRow(
                        IsleIcons.Moon, IsleColors.TilePurple, "Skip silent", ignoreSilent,
                        subtitle = "Ignore silenced notifications",
                    ) {
                        ignoreSilent = it; prefs.edit().putBoolean("cast_ignore_silent", it).apply()
                    }
                    GroupDivider()
                    ToggleRow(
                        IsleIcons.Lock, IsleColors.TileGreen, "Lock screen", lockscreenLiveCard,
                        subtitle = "Live card on lockscreen",
                    ) {
                        lockscreenLiveCard = it
                        prefs.edit().putBoolean("cast_lockscreen_live_card", it).apply()
                    }
                }
            }
        }

        item {
            GroupCard {
                NavRow(
                    IsleIcons.Grid, IsleColors.TileAmber, "Apps",
                    value = if (appsOff == 0) "All on" else "$appsOff off",
                    onClick = onOpenApps,
                )
                GroupDivider()
                NavRow(IsleIcons.Pulse, IsleColors.TileGray, "Activity", onClick = onOpenActivity)
            }
        }
    }
}

/** Green "Listening" / red "Not listening" pill under the title; opens the Activity screen. */
@Composable
private fun StatusPill(connected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (connected) IsleColors.GoodBg else IsleColors.BadBg)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (connected) IsleColors.Good else IsleColors.Bad))
        Text(
            if (connected) "Listening" else "Not listening",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (connected) IsleColors.GoodText else IsleColors.BadText,
        )
    }
}

@Composable
private fun MenuItem(text: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = IsleColors.Text) },
        onClick = onClick,
        modifier = Modifier.widthIn(min = 200.dp).heightIn(min = 48.dp),
    )
}

/** Island preview + sample picker + send button: fires any [IslandSamples] card on demand. */
@Composable
private fun SampleTester(context: Context) {
    var selected by remember { mutableIntStateOf(0) }
    val sample = IslandSamples.all[selected]
    var sentAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(sentAt) {
        if (sentAt != 0L) { delay(1600); sentAt = 0L }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(IsleColors.Surface)
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .heightIn(min = 104.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(IsleColors.PreviewBg)
                .padding(start = 10.dp, end = 10.dp, top = 14.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // What the compact island will look like once this sample is sent.
            IslandPreviewView(sample.preview)
            Text(
                sample.summary,
                modifier = Modifier.padding(top = 12.dp),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = IsleColors.PreviewLabel,
                textAlign = TextAlign.Center,
            )
        }
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(IslandSamples.all) { i, s ->
                ChoiceChip(
                    s.name,
                    selected = i == selected,
                    onClick = { selected = i },
                    selectedContainer = IsleColors.Text,
                    selectedContent = IsleColors.Background,
                    shape = RoundedCornerShape(18.dp),
                    height = 36.dp,
                    horizontalPadding = 14.dp,
                )
            }
        }
        PillButton(
            if (sentAt != 0L) "Sent" else "Send to island",
            onClick = { sample.post(context); sentAt = System.currentTimeMillis() },
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
            icon = IsleIcons.Send,
            container = IsleColors.Accent,
            content = Color.White,
            height = 48.dp,
        )
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
