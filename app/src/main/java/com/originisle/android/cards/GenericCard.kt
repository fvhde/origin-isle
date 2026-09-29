package com.originisle.android.cards

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.originisle.android.R
import com.originisle.android.island.OriginIslandConstants
import com.originisle.android.island.PlaygroundService
import com.originisle.android.service.IconCache
import kotlin.math.abs

/**
 * Casts an "ordinary" notification — downloads, navigation, calls, progress, plain messages — as a
 * base-template island card. This is the fallback card type: anything that isn't a live score, a
 * payment or a media session goes through here.
 */
object GenericCard {
    private const val RIGHT_TEXT_MAX_CHARS = 8

    /**
     * @param isLive whether [NotificationCastListener] classified this as a live/persistent
     * notification (ongoing, call, or progress) rather than a one-off message — determines whether
     * the card is eligible for the auto-dismiss timer. Computed once by the caller so this doesn't
     * re-derive its own, possibly-drifting definition of "live".
     */
    fun post(context: Context, sbn: StatusBarNotification, isLive: Boolean) {
        val n = sbn.notification
        val extras = n.extras
        val id = IconCache.castIdFor(sbn)
        val isCall = n.category == NotificationCompat.CATEGORY_CALL ||
            extras.getString(NotificationCompat.EXTRA_TEMPLATE) == "android.app.Notification\$CallStyle"

        val text = extras.getCharSequence(NotificationCompat.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        val bigText = extras.getCharSequence(NotificationCompat.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()
        val subText = extras.getCharSequence(NotificationCompat.EXTRA_SUB_TEXT)?.toString()?.trim().orEmpty()
        val appLabel = runCatching {
            context.packageManager.getApplicationLabel(
                context.packageManager.getApplicationInfo(sbn.packageName, 0),
            ).toString()
        }.getOrDefault(sbn.packageName)
        // Some ongoing notifications (Maps' turn-by-turn among them) deliberately leave the title
        // empty and render their own custom view instead — falling back to the literal word
        // "Notification" there read as if that were the actual message. The source app's own name
        // ("Maps") is a far more useful fallback.
        val title = extras.getCharSequence(NotificationCompat.EXTRA_TITLE)?.toString()?.trim().orEmpty()
            .ifBlank { appLabel }

        val progress = extras.getInt(NotificationCompat.EXTRA_PROGRESS, 0)
        val progressMax = extras.getInt(NotificationCompat.EXTRA_PROGRESS_MAX, 0)
        val indeterminate = extras.getBoolean(NotificationCompat.EXTRA_PROGRESS_INDETERMINATE, false)
        val hasProgress = progressMax > 0 && !indeterminate

        // Chip text: chronometer elapsed -> short critical text -> percentage.
        //
        // The percentage is LAST on purpose. Android 16 Live Updates post a progress bar and a
        // purpose-built short chip string together by design, so a navigation notification carries
        // both "2 min" and a journey progress bar — synthesising "43%" from the bar and ignoring the
        // OS's own string threw away the better text.
        val showChrono = extras.getBoolean(NotificationCompat.EXTRA_SHOW_CHRONOMETER, false)
        val percentText = if (hasProgress) "${progress * 100 / progressMax}%" else ""
        // "android.shortCriticalText" (Android 16 promoted-notification chip); read by raw key
        // so it works even when the compiled core library predates the typed constant.
        val shortCritical = extras.getCharSequence("android.shortCriticalText")?.toString()?.trim().orEmpty()
        val chip = when {
            showChrono && n.`when` > 0 -> formatElapsed(abs(System.currentTimeMillis() - n.`when`) / 1000)
            shortCritical.isNotBlank() -> shortCritical
            else -> percentText
        }

        n.smallIcon?.let { IconCache.activeSmallIcons[id] = it }
        when (val large = extras.get(NotificationCompat.EXTRA_LARGE_ICON)) {
            is Icon -> IconCache.activeLargeIcons[id] = large
            is Bitmap -> IconCache.activeLargeBitmaps[id] = shrinkForIcon(large)
            else -> runCatching {
                // No large icon in the notification -> use the source app's colourful launcher
                // icon as the card image so the expanded card doesn't fall back to a mono icon.
                val info = context.packageManager.getApplicationInfo(sbn.packageName, 0)
                IconCache.activeLargeIcons[id] = Icon.createWithResource(sbn.packageName, info.icon)
            }
        }

        // Calls: use the caller's avatar (or the app's colourful icon) as the PILL's left icon too —
        // otherwise the pill falls back to the notification's generic monochrome phone glyph.
        val pillIcon = if (isCall) callIcon(context, sbn) else null

        // Per-app styling: map known apps (Google Pay/Wallet/Maps …) to a nicer card + chip.
        val style = styleFor(sbn.packageName, title, "$text $bigText $subText")
        val isMaps = sbn.packageName == "com.google.android.apps.maps"
        // Maps: keep the maneuver (title) on the left and the distance/ETA (text) in the pill,
        // so the next turn is visible without expanding the card.
        val finalChip = if (isMaps) text.ifBlank { chip } else style?.chip ?: chip
        // The island's progress ring (right-template 2) has NO content key — buildBundle writes
        // progressValue/colors and nothing else — so routing a card there DISCARDS oi_right_content
        // entirely. Only take the ring when the chip is nothing more than the percentage the ring
        // already draws; anything real (a distance, an ETA, a shortCriticalText) belongs in the
        // capsule instead. This is what made Maps show a bare ring on an Android 16 base while an
        // older one showed the distance: same APK, opposite pill, purely from progressMax > 0.
        val ringSaysItAll = finalChip.isBlank() || finalChip == percentText

        val intent = Intent(context, PlaygroundService::class.java).apply {
            action = PlaygroundService.ACTION_START
            putExtra("id", id)
            putExtra("is_ongoing", isLive)
            putExtra("oi_scene", "NAVIGATION")
            putExtra("title", title)
            // vivo silently REJECTS a SuperX post with empty content text — falling back to a plain
            // notification instead (some notifications, e.g. Maps at the very start of navigation,
            // briefly have no text/bigText/subText at all) — so this can never end up blank.
            putExtra("text", text.ifBlank { bigText }.ifBlank { subText }.ifBlank { finalChip }.ifBlank { appLabel })
            putExtra("subtext", subText)
            putExtra("source_app", appLabel)
            putExtra("source_pkg", sbn.packageName)
            putExtra("oi_left_content", title)
            putExtra("oi_right_content", finalChip)
            putExtra("status_chip_text", finalChip)
            putExtra("icon_res", R.mipmap.ic_launcher_round)
            putExtra("when", n.`when`)
            // vivo requires a content intent; fall back to launching the source app.
            putExtra("source_content_intent", n.contentIntent ?: fallbackClickIntent(context, sbn.packageName))
            n.actions?.let { putParcelableArrayListExtra("actions", ArrayList(it.toList())) }
            style?.iconStatus?.let { putExtra("oi_icon_status_type", it) }
            pillIcon?.let {
                putExtra("oi_left_icon", it)
                putExtra("oi_large_icon", it)
            }
            when {
                style != null -> {
                    putExtra("oi_template", style.template)
                    putExtra("oi_right_template", style.rightTemplate)
                }
                hasProgress && ringSaysItAll -> {
                    putExtra("oi_template", OriginIslandConstants.TEMPLATE_BASE)                 // 4
                    putExtra("oi_right_template", OriginIslandConstants.TEMPLATE_RIGHT_ISLAND_PROGRESS) // 2
                    putExtra("show_progress", true)
                    putExtra("progress", progress)
                    putExtra("progress_max", progressMax)
                }
                // Progress bar AND text worth reading: the capsule wins, and oi_left_content keeps
                // the title — for Maps that's the maneuver on the left with the distance/ETA in the
                // pill, which is exactly what the isMaps branch above was written to produce.
                hasProgress -> {
                    putExtra("oi_template", OriginIslandConstants.TEMPLATE_BASE)                     // 4
                    putExtra("oi_right_template", OriginIslandConstants.TEMPLATE_RIGHT_ISLAND_CAPSULE_TEXT) // 6
                }
                else -> {
                    putExtra("oi_left_content", "")
                    if (!isCall) callIcon(context, sbn)?.let { putExtra("oi_left_icon", it) }
                    putExtra("oi_template", OriginIslandConstants.TEMPLATE_BASE) // 4
                    if (finalChip.isNotBlank()) {
                        putExtra("oi_right_template", OriginIslandConstants.TEMPLATE_RIGHT_ISLAND_CAPSULE_TEXT) // 6
                    } else {
                        putExtra("oi_right_template", OriginIslandConstants.TEMPLATE_RIGHT_ISLAND_TEXT_ICON) // 4
                        putExtra("oi_show_right_icon", false)
                        val body = text.ifBlank { bigText }.ifBlank { subText }
                        // Calls: the caller's avatar sits on the left, so the right of the camera
                        // holds just their name — not "Name: Incoming call" cut to a few letters.
                        val combined = when {
                            isCall -> title
                            title.isNotBlank() && title != appLabel && body.isNotBlank() -> "$title: $body"
                            else -> body
                        }
                        putExtra("oi_right_content", combined.take(RIGHT_TEXT_MAX_CHARS).let {
                            if (combined.length > RIGHT_TEXT_MAX_CHARS) "$it…" else it
                        })
                    }
                }
            }
        }
        context.startService(intent)
    }

    /** A colourful icon for a call: the caller's avatar if the notification carries one, else the app icon. */
    private fun callIcon(context: Context, sbn: StatusBarNotification): Icon? {
        when (val large = sbn.notification.extras.get(NotificationCompat.EXTRA_LARGE_ICON)) {
            is Icon -> return large
            is Bitmap -> return Icon.createWithBitmap(shrinkForIcon(large))
        }
        return runCatching {
            val info = context.packageManager.getApplicationInfo(sbn.packageName, 0)
            Icon.createWithResource(sbn.packageName, info.icon)
        }.getOrNull()
    }

    /** Card style for a known source app, or null to use the generic mapping. */
    private data class CastStyle(
        val template: Int,
        val rightTemplate: Int = OriginIslandConstants.TEMPLATE_RIGHT_ISLAND_CAPSULE_TEXT,
        val iconStatus: Int = -1,
        val chip: String? = null,
    )

    private fun styleFor(pkg: String, title: String, body: String): CastStyle? {
        val t = "$title $body".lowercase()
        val base = OriginIslandConstants.TEMPLATE_BASE
        return when (pkg) {
            // Google Wallet (payments + passes) and standalone Google Pay.
            "com.google.android.apps.walletnfcrel",
            "com.google.android.apps.nbu.paisa.user",
            -> when {
                listOf("boarding", "flight", "gate", "depart", "terminal", "airline")
                    .any { t.contains(it) } -> CastStyle(base, chip = "✈")
                listOf("train", "platform", "rail", "track", "sncf", "coach", "wagon")
                    .any { t.contains(it) } -> CastStyle(base, chip = "🚆")
                // otherwise treat as a payment -> green success tick
                else -> CastStyle(base, iconStatus = OriginIslandConstants.ICON_STATUS_SUCCESS, chip = "✓")
            }
            else -> null
        }
    }
}
