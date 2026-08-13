package com.originisle.android.cards

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.originisle.android.R
import com.originisle.android.island.OriginIslandConstants
import com.originisle.android.island.PlaygroundService
import com.originisle.android.service.IconCache
import com.originisle.android.service.MediaControlReceiver
import kotlin.math.abs

/**
 * Casts an active media session (music, podcasts, video) as a card with play-pause — plus prev/next
 * when the session actually supports skipping — wired back to the source app through
 * [MediaControlReceiver].
 */
object MediaCard {

    fun post(context: Context, sbn: StatusBarNotification, controller: MediaController) {
        val id = IconCache.castIdFor(sbn)
        val n = sbn.notification
        IconCache.activeControllers[id] = controller

        val md = controller.metadata
        val track = md?.getString(MediaMetadata.METADATA_KEY_TITLE)?.trim().orEmpty()
            .ifBlank { extrasTitle(sbn) }
            .ifBlank { appLabel(context, sbn.packageName) }

        val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)?.trim().orEmpty()
            .ifBlank { "Now playing" }
        val playing = controller.playbackState?.state == PlaybackState.STATE_PLAYING

        n.smallIcon?.let { IconCache.activeSmallIcons[id] = it }
        val albumArt: Bitmap? = (md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART))?.let(::shrinkForIcon)
        albumArt?.let { IconCache.activeLargeBitmaps[id] = it }

        val showChrono = n.extras.getBoolean(NotificationCompat.EXTRA_SHOW_CHRONOMETER, false)
        val chip = if (showChrono && n.`when` > 0) {
            formatElapsed(abs(System.currentTimeMillis() - n.`when`) / 1000)
        } else {
            if (playing) "Playing" else "Paused"
        }

        fun mediaAction(action: String) = PendingIntent.getBroadcast(
            context, (id.toString() + action).hashCode(),
            Intent(action).setPackage(context.packageName)
                .putExtra("cast_id", id)
                .putExtra(MediaControlReceiver.EXTRA_TOKEN, controller.sessionToken),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        fun findSourceAction(titleKeywords: List<String>): PendingIntent? =
            n.actions?.firstOrNull { a ->
                val title = a.title?.toString()?.lowercase().orEmpty()
                titleKeywords.any { title.contains(it) }
            }?.actionIntent

        val prevSourceAction = findSourceAction(listOf("previous", "skip back", "rewind"))
        val nextSourceAction = findSourceAction(listOf("next", "skip forward", "fast forward"))
        val sessionActions = controller.playbackState?.actions ?: 0L
        val hasPrev = prevSourceAction != null || (sessionActions and PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0L
        val hasNext = nextSourceAction != null || (sessionActions and PlaybackState.ACTION_SKIP_TO_NEXT) != 0L

        val actions = arrayListOf<Notification.Action>()
        if (hasPrev) {
            actions.add(
                makeAction(context, R.drawable.ic_media_prev, prevSourceAction ?: mediaAction(MediaControlReceiver.ACTION_PREV)),
            )
        }
        actions.add(
            makeAction(
                context,
                if (playing) R.drawable.ic_media_pause else R.drawable.ic_media_play,
                mediaAction(MediaControlReceiver.ACTION_PLAY_PAUSE),
            ),
        )
        if (hasNext) {
            actions.add(
                makeAction(context, R.drawable.ic_media_next, nextSourceAction ?: mediaAction(MediaControlReceiver.ACTION_NEXT)),
            )
        }

        val intent = Intent(context, PlaygroundService::class.java).apply {
            action = PlaygroundService.ACTION_START
            putExtra("id", id)
            putExtra("is_ongoing", true)
            putExtra("oi_auto_dismissible", false)
            putExtra("oi_scene", "NAVIGATION")
            putExtra("title", track)
            putExtra("text", artist)
            putExtra("source_pkg", sbn.packageName)
            putExtra("oi_left_content", if (albumArt != null) "" else track)

            putExtra("status_chip_text", chip)

            albumArt?.let { putExtra("oi_left_icon", Icon.createWithBitmap(it)) }
            putExtra("icon_res", R.drawable.ic_media_play)
            putExtra("oi_template", OriginIslandConstants.TEMPLATE_BUTTONS)
            putExtra("oi_bg_color", "#FF262626")
            putExtra("oi_fg_color", "#FFFFFFFF")
            putExtra(
                "oi_displays",
                OriginIslandConstants.DISPLAY_STATUSBAR or OriginIslandConstants.DISPLAY_AOD,
            )
            if (playing) {
                putExtra("oi_right_template", OriginIslandConstants.TEMPLATE_RIGHT_ISLAND_WAVE)
                putExtra("oi_wave_state", 1)
            } else {
                putExtra("oi_right_template", OriginIslandConstants.TEMPLATE_RIGHT_ISLAND_TEXT_ICON)
            }

            putParcelableArrayListExtra("actions", actions)
            putExtra(
                "source_content_intent",
                n.contentIntent ?: fallbackClickIntent(context, sbn.packageName),
            )
        }
        context.startService(intent)
    }

    private fun extrasTitle(sbn: StatusBarNotification): String =
        sbn.notification.extras.getCharSequence(NotificationCompat.EXTRA_TITLE)?.toString()?.trim().orEmpty()

    private fun appLabel(context: Context, pkg: String): String = runCatching {
        context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    private fun makeAction(context: Context, iconRes: Int, pi: PendingIntent): Notification.Action =
        Notification.Action.Builder(Icon.createWithResource(context, iconRes), "", pi).build()
}
