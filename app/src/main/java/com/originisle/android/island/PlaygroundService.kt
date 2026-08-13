package com.originisle.android.island

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.originisle.android.R
import com.originisle.android.service.IconCache
import com.originisle.android.service.KeepAliveAccessibilityService
import java.util.concurrent.ConcurrentHashMap

/**
 * Posts, updates and ends OriginIsland "SuperX" cards.
 *
 * Callers drive it with an [Intent] full of `oi_*` extras (the same contract the original app
 * used — see [readAndPost]); the service maps those onto [OriginIslandBuilder.buildBundle] and
 * posts the result as a notification tagged [OriginIslandConstants.SUPERX_TAG]. That tag is what
 * makes OriginOS render it in the island rather than the shade.
 */
class PlaygroundService : Service() {

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_CANCEL = "ACTION_CANCEL"
        // Just brings the foreground service up (no card) so the process — and the notification
        // listener living in it — stays alive for background casts.
        const val ACTION_KEEPALIVE = "ACTION_KEEPALIVE"

        /** Start/keep the foreground service alive. Safe to call from a foreground context. */
        fun keepAlive(context: Context) {
            context.startService(Intent(context, PlaygroundService::class.java).setAction(ACTION_KEEPALIVE))
        }

        // Keep-alive channel created at IMPORTANCE_NONE (blocked): a foreground service on a blocked
        // channel keeps running but its notification is SUPPRESSED — no status-bar icon, even on
        // OriginOS which force-badges IMPORTANCE_MIN foreground services.
        const val CHANNEL_ID = "originisle_keepalive"

        /** Ids of cards currently on the island — used to detect a crowded island. */
        @Volatile
        var activeCardIds: Set<Int> = emptySet()

        const val ORIGIN_CHANNEL_ID = "originisle_island_hi"
        const val FGS_ID = 9999
    }

    private lateinit var notificationManager: NotificationManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isForegroundActive = false

    private val activeIds = linkedSetOf<Int>()
    private val originScenes = HashMap<Int, String>()
    private val originChangeRecord = HashMap<Int, Int>()
    private val autoDismissTasks = ConcurrentHashMap<Int, Runnable>()
    // Which card id currently occupies each scene. Only NAVIGATION is granted on most devices, so
    // every card shares it; vivo merges same-scene cards, so a new card must end the previous one
    // or it inherits stale fields (a ride ETA lingering under a flight card, etc.).
    private val sceneCurrentId = HashMap<String, Int>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        OriginIslandBuilder.grantScenes(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureForeground()
        try {
            when (intent?.action) {
                ACTION_START -> readAndPost(intent)
                ACTION_CANCEL -> cancelNotification(intent)
                ACTION_STOP -> stopEverything()
                ACTION_KEEPALIVE -> Unit // ensureForeground() above is all that's needed
            }
        } catch (e: Throwable) {
            Log.e("PlaygroundService", "onStartCommand failed", e)
        }
        // STICKY so the system relaunches the service (and our process + listener) if it's killed.
        return START_STICKY
    }

    private fun ensureForeground() {
        if (isForegroundActive) return
        // If the keep-alive AccessibilityService is enabled, it anchors the process for us — so we
        // DON'T start a foreground service, and skipping it removes the status-bar icon that OriginOS
        // force-shows for any FGS.
        //
        // IMPORTANT: we only SKIP starting the FGS here; we must NEVER tear down an already-running
        // FGS the instant accessibility connects. Doing that left the process with no anchor for a
        // moment, OriginOS killed it, and the kill disconnected (disabled) the accessibility service.
        // So the FGS just isn't (re)started once accessibility is on — the icon clears on the next
        // process start, and the process always keeps an anchor.
        if (isKeepAliveAccessibilityEnabled()) return
        createNotificationChannel(CHANNEL_ID)
        val n = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Origin Isle")
            .setContentText("Casting to OriginIsland")
            // A fully transparent small icon: OriginOS force-shows foreground-service icons in the
            // status bar even at IMPORTANCE_MIN, so we make the icon invisible. The entry still shows
            // (with text) in the expanded notification panel; only the status-bar glyph disappears.
            .setSmallIcon(R.drawable.ic_transparent)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setSilent(true)
            .setOngoing(true)
            .build()
        try {
            startForeground(FGS_ID, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } catch (_: Exception) {
            startForeground(FGS_ID, n)
        }
        isForegroundActive = true
    }

    // --- posting -----------------------------------------------------------------

    /** Read the `oi_*` extras contract off [intent] and post/refresh a SuperX card. */
    private fun readAndPost(intent: Intent) {
        val id = intent.getIntExtra("id", -1)
        if (id == -1) return

        val scene = intent.getStringExtra("oi_scene").orEmptyOr("NAVIGATION")
        val title = intent.getStringExtra("title").orEmpty()
        val text = intent.getStringExtra("text").orEmpty()
        val leftContent = intent.getStringExtra("oi_left_content").orEmpty()
        val rightContent = intent.getStringExtra("oi_right_content").orEmpty()
        val statusChip = intent.getStringExtra("status_chip_text").orEmpty()

        val template = intent.getIntExtra("oi_template", 0)
        val rightTemplate = intent.getIntExtra("oi_right_template", 0)

        val extra1 = intent.getStringExtra("oi_extra1").orEmpty()
        val extra2 = intent.getStringExtra("oi_extra2").orEmpty()
        val extra3 = intent.getStringExtra("oi_extra3").orEmpty()
        val extra4 = intent.getStringExtra("oi_extra4").orEmpty()
        val subtext = intent.getStringExtra("subtext")
        val navMsg = intent.getStringExtra("oi_nav_msg")

        val bgColor = OriginIslandBuilder.parseColor(intent.getStringExtra("oi_bg_color"), 0)
        val fgColor = OriginIslandBuilder.parseColor(intent.getStringExtra("oi_fg_color"), 0)
        val cardBgColor = OriginIslandBuilder.parseColor(intent.getStringExtra("oi_card_bg_color"), 0)
        val lightColor = OriginIslandBuilder.parseColor(intent.getStringExtra("oi_light_color"), 0)

        val showProgress = intent.getBooleanExtra("show_progress", false)
        val progressMax = intent.getIntExtra("progress_max", 0)
        val rawProgress = intent.getIntExtra("progress", 0)
        val progress = when {
            intent.hasExtra("oi_progress") -> intent.getIntExtra("oi_progress", 0)
            showProgress && progressMax > 0 -> rawProgress * 100 / progressMax
            else -> rawProgress
        }

        val keepDuration = intent.getIntExtra("oi_keep_duration", 0)
        // Surfaces the card appears on: lockscreen|statusbar|AOD — NOT the plain notification-shade
        // bit (1). The SuperX post is a real, HIGH-importance notification under Origin Isle's own
        // identity; including the notification bit made it ALSO show as a duplicate shade entry
        // ("Origin Isle: Starting navigation" etc, mirroring whatever the source app's text was),
        // on top of the island rendering. Dropping it should leave the island/lockscreen/AOD intact.
        val displays = intent.getIntExtra("oi_displays", 65808)
        val islandShowTime = intent.getIntExtra("oi_island_show_time", 0)
        val forceShow = intent.getBooleanExtra("oi_force_show", false)
        val dismissWhenKill = intent.getBooleanExtra("oi_dismiss_when_kill", true)
        val keepScreenOn = intent.getBooleanExtra("oi_keep_screen_on", false)
        val disableInvert = intent.getBooleanExtra("oi_disable_invert", true)
        val showRightIcon = intent.getBooleanExtra("oi_show_right_icon", true)
        val showLeftIcon = intent.getBooleanExtra("oi_show_left_icon", true)
        val lightMode = intent.getIntExtra("oi_light_mode", 0)
        val generatingStatus = intent.getIntExtra("oi_generating_status", 0)
        val iconStatusType = intent.getIntExtra("oi_icon_status_type", -1)
        val waveState = intent.getIntExtra("oi_wave_state", 1)
        val progressState = if (intent.getBooleanExtra("progress_indeterminate", false)) 1 else 0

        val customTemplate: RemoteViews? =
            intent.getParcelableExtra("oi_custom_template", RemoteViews::class.java)

        // Icons: prefer the ones the listener captured for this cast id, else the app icon.
        val iconRes = intent.getIntExtra("icon_res", R.mipmap.ic_launcher_round)
        val baseIcon: Icon = IconCache.activeSmallIcons[id]
            ?: Icon.createWithResource(this, iconRes)
        // Optional distinct island icons (e.g. home/away club crests); fall back to the base icon.
        val leftIcon: Icon = intent.getParcelableExtra("oi_left_icon", Icon::class.java) ?: baseIcon
        val rightIcon: Icon = intent.getParcelableExtra("oi_right_icon", Icon::class.java) ?: baseIcon
        val largeIcon: Icon? = intent.getParcelableExtra("oi_large_icon", Icon::class.java)
            ?: IconCache.activeLargeIcons[id]
            ?: IconCache.activeLargeBitmaps[id]?.let { Icon.createWithBitmap(it) }

        // Actions -> OriginActions.
        val actions = intent.getParcelableArrayListExtra("actions", Notification.Action::class.java)
            ?.map { a ->
                OriginIslandBuilder.OriginAction(
                    title = a.title?.toString().orEmpty(),
                    icon = a.getIcon(), // getIcon() = the Icon; the bare `icon` field is a legacy int res
                    pendingIntent = a.actionIntent,
                )
            } ?: emptyList()

        val clickResp: PendingIntent? =
            intent.getParcelableExtra("source_content_intent", PendingIntent::class.java)
                ?: intent.getParcelableExtra("click_resp", PendingIntent::class.java)

        val leftDoubleLine = intent.getStringArrayListExtra("oi_left_doubleline") ?: emptyList()
        val rightDoubleLine = intent.getStringArrayListExtra("oi_right_doubleline") ?: emptyList()
        val buttonTitles = intent.getStringArrayListExtra("oi_button_titles") ?: emptyList()

        // capsule / right-island chip text: prefer explicit chip, else right content.
        val chip = statusChip.ifBlank { rightContent }

        val changeRecord = ((originChangeRecord[id] ?: 0) + 1).also { originChangeRecord[id] = it }

        val bundle = OriginIslandBuilder.buildBundle(
            context = this,
            template = template,
            title = title,
            content = text,
            leftContent = leftContent,
            rightContent = chip,
            extra1 = extra1, extra2 = extra2, extra3 = extra3, extra4 = extra4,
            rightTemplate = rightTemplate,
            defaultIcon = baseIcon,
            leftIcon = leftIcon,
            rightIcon = rightIcon,
            accentIcon = baseIcon,
            progress = progress,
            bgColor = bgColor,
            fgColor = fgColor,
            scene = scene,
            clickResp = clickResp,
            largeIcon = largeIcon,
            subText = subtext,
            navMsg = navMsg,
            actions = actions,
            keepDuration = keepDuration,
            displays = displays,
            dismissWhenKill = dismissWhenKill,
            islandShowTime = islandShowTime,
            forceShow = forceShow,
            showRightIcon = showRightIcon,
            showLeftIcon = showLeftIcon,
            progressState = progressState,
            progressMarkers = showProgress && progressMax > 0,
            cardBgColor = cardBgColor,
            keepScreenOn = keepScreenOn,
            disableInvertColor = disableInvert,
            lightColor = lightColor,
            lightMode = lightMode,
            generatingStatus = generatingStatus,
            iconStatusType = iconStatusType,
            leftDoubleLine = leftDoubleLine,
            rightDoubleLine = rightDoubleLine,
            buttonTitles = buttonTitles,
            customTemplate = customTemplate,
            waveState = waveState,
            changeRecord = changeRecord,
        )

        val sourceApp = intent.getStringExtra("source_app").orEmpty()
        val isOngoing = intent.getBooleanExtra("is_ongoing", false)

        // Scene replace ONLY between tester/sports sample cards (id >= 40000) so switching samples
        // doesn't leave stale fields. Real cast notifications (id 20000-29999) are NOT ended against
        // each other — vivo happily stacks several live cards (a main one + reduced ones), and ending
        // one mid-update is what left a match card half-rendered (one badge / one score). Cards are
        // still cleared when their own notification is dismissed (onNotificationRemoved -> cancel).
        if (id >= 40000) {
            val prevInScene = sceneCurrentId[scene]
            if (prevInScene != null && prevInScene != id && prevInScene >= 40000) {
                endOrigin(prevInScene, scene)
                activeIds.remove(prevInScene)
                originChangeRecord.remove(prevInScene)
                originScenes.remove(prevInScene)
            }
            sceneCurrentId[scene] = id
        }

        activeIds.add(id)
        originScenes[id] = scene
        activeCardIds = activeIds.toSet()
        postSuperX(id, iconRes, title, text, sourceApp, isOngoing, scene, bundle)

        // Cancel existing auto-dismiss timer for this card id if present
        autoDismissTasks.remove(id)?.let { mainHandler.removeCallbacks(it) }

        // Schedule auto-dismiss timer if configured and notification is marked auto-dismissible (plain messages)
        val autoDismissible = intent.getBooleanExtra("oi_auto_dismissible", false)
        val autoDismissSec = getSharedPreferences("experimental_prefs", 0).getInt("cast_auto_dismiss_seconds", 0)
        if (autoDismissSec > 0 && autoDismissible) {
            val task = Runnable {
                val cancelIntent = Intent(this@PlaygroundService, PlaygroundService::class.java).apply {
                    action = ACTION_CANCEL
                    putExtra("id", id)
                    putExtra("oi_scene", scene)
                }
                cancelNotification(cancelIntent)
            }
            autoDismissTasks[id] = task
            mainHandler.postDelayed(task, autoDismissSec * 1000L)
        }
    }

    /**
     * Post the SuperX card exactly the way the original did: a HIGH-importance channel, real
     * title/text (NOT empty), no `setSilent`. On vivo a low-importance/silent/empty notification
     * is minimised and the island never renders it — that was the bug.
     */
    private fun postSuperX(
        id: Int,
        iconRes: Int,
        title: String,
        text: String,
        sourceApp: String,
        isOngoing: Boolean,
        scene: String,
        bundle: android.os.Bundle,
    ) {
        OriginIslandBuilder.grantScenes(this)
        createNotificationChannel(ORIGIN_CHANNEL_ID)
        val cancel = Intent(this, PlaygroundService::class.java)
            .setAction(ACTION_CANCEL)
            .putExtra("id", id)
            .putExtra("oi_scene", scene)
        val deletePi = PendingIntent.getService(
            this, id, cancel,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(this, ORIGIN_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(isOngoing)
            .setAutoCancel(isOngoing)
            .setDeleteIntent(deletePi)
            .setSmallIcon(iconRes)
            .setExtras(bundle)
        if (sourceApp.isNotEmpty()) builder.setSubText(sourceApp)
        notificationManager.notify(OriginIslandConstants.SUPERX_TAG, id, builder.build())
    }

    // --- ending ------------------------------------------------------------------

    private fun endOrigin(id: Int, scene: String) {
        try {
            OriginIslandBuilder.grantScenes(this)
            createNotificationChannel(ORIGIN_CHANNEL_ID)
            val n = NotificationCompat.Builder(this, ORIGIN_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("")
                .setContentText("")
                .setSilent(true)
                .setExtras(OriginIslandBuilder.buildEndBundle(scene))
                .build()
            notificationManager.notify(OriginIslandConstants.SUPERX_TAG, id, n)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun cancelNotification(intent: Intent) {
        val id = intent.getIntExtra("id", -1)
        if (id == -1) return
        autoDismissTasks.remove(id)?.let { mainHandler.removeCallbacks(it) }
        val scene = originScenes.remove(id) ?: intent.getStringExtra("oi_scene")
        if (scene != null) {
            endOrigin(id, scene)
            mainHandler.postDelayed({
                notificationManager.cancel(OriginIslandConstants.SUPERX_TAG, id)
            }, 350L)
        } else {
            notificationManager.cancel(id)
        }
        activeIds.remove(id)
        originChangeRecord.remove(id)
        activeCardIds = activeIds.toSet()
        // Keep the sceneCurrentId entry (now stale) so the NEXT different card clears this scene's
        // leftovers via the staleness-aware replace in readAndPost.
        // NOTE: we deliberately do NOT stopSelf() when idle. The notification listener runs in the
        // background, and Android 14 forbids starting a foreground service from the background — so
        // if this service stopped, the next real cast could not restart it and casting would break.
        // Keeping it alive (on an IMPORTANCE_MIN, icon-less channel) is the cost of reliable casting.
        // Use the "Stop all cards" button (ACTION_STOP) to shut it down fully.
    }

    private fun stopEverything() {
        autoDismissTasks.values.forEach { mainHandler.removeCallbacks(it) }
        autoDismissTasks.clear()
        val hadScenes = originScenes.isNotEmpty()
        HashMap(originScenes).forEach { (id, scene) -> endOrigin(id, scene) }
        originScenes.clear()
        originChangeRecord.clear()
        sceneCurrentId.clear()
        val ids = activeIds.toList()
        activeIds.clear()
        activeCardIds = emptySet()
        val finish = {
            ids.forEach {
                notificationManager.cancel(OriginIslandConstants.SUPERX_TAG, it)
                notificationManager.cancel(it)
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        if (hadScenes) mainHandler.postDelayed({ finish() }, 350L) else finish()
    }

    /** True when the user has enabled the keep-alive AccessibilityService (no FGS icon needed then). */
    private fun isKeepAliveAccessibilityEnabled(): Boolean {
        val flat = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val target = ComponentName(this, KeepAliveAccessibilityService::class.java)
        return flat.split(':').any { ComponentName.unflattenFromString(it) == target }
    }

    private fun createNotificationChannel(channelId: String) {
        if (notificationManager.getNotificationChannel(channelId) != null) return
        val isIsland = channelId == ORIGIN_CHANNEL_ID
        val name = if (isIsland) "OriginIsland" else "Background keep-alive"
        // Island channel must be HIGH (4) like the original — LOW gets minimised and never reaches
        // the island. The keep-alive channel is NONE (blocked) so its foreground-service notification
        // is suppressed entirely — the service keeps running with no status-bar icon.
        val importance = if (isIsland) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_NONE
        val channel = NotificationChannel(channelId, name, importance).apply {
            description = "Channel for $name Service"
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun String?.orEmptyOr(fallback: String): String {
        val v = this?.trim().orEmpty()
        return v.ifBlank { fallback }
    }
}
