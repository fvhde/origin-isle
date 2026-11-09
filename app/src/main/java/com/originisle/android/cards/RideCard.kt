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

/**
 * A ride (or a delivery) on its way: the app's logo and where the trip is at on the left of the
 * camera, the time left in a capsule on the right, and on the card the trip line with that time
 * big on the right — or the app's own progress track when it posts an Android 16 Live Update.
 *
 * Ride apps don't agree on how a trip notification looks. Bolt posts an Android 16 Live Update on
 * some builds and a custom-layout notification on others — not ongoing, no progress the listener
 * can read — which made every trip update look like a brand new message.
 */
object RideCard {

    /** Ride-hailing and food delivery apps. */
    private val APPS = setOf(
        "ee.mtakso.client",            // Bolt
        "com.ubercab",                 // Uber
        "com.ubercab.eats",            // Uber Eats
        "com.heetch",                  // Heetch
        "taxi.android.client",         // FREENOW
        "me.lyft.android",             // Lyft
        "com.cabify.rider",            // Cabify
        "com.grabtaxi.passenger",      // Grab
        "com.careem.acma",             // Careem
        "sinet.startup.inDriver",      // inDrive
        "com.didiglobal.passenger",    // DiDi
        "ru.yandex.taxi",              // Yandex Go
        "com.deliveroo.orderapp",      // Deliveroo
    )

    /** "23 min", "1 h 5 min", "2 hrs" — what's left of the trip. */
    private val DURATION = Regex(
        """(?i)\b(?:\d{1,2}\s?(?:h|hr|hrs|hours?|std)\.?\s?)?\d{1,3}\s?(?:min|mins|minutes?|minuten|minuti|minutos?)\b\.?""" +
            """|\b\d{1,2}\s?(?:h|hr|hrs|hours?)\b""",
    )

    /** "03:52", "3h52" — an arrival time, when there's no duration. */
    private val CLOCK = Regex("""\b(?:[01]?\d|2[0-3])[:h][0-5]\d\b""")

    /** "in", "dans"… left dangling once the time is cut out of "Dropoff in 23 min". */
    private val TRAILING_LINK = Regex(
        """(?i)[\s·•:,–-]+(?:in|dans|en|em|tra|fra|binnen|om|za|at|à|a|um|by|vers)?\s*$""",
    )

    /**
     * The trip notification itself, not the app's chat, promos or receipts: ongoing, a progress bar,
     * or one of the "live activity" channels Bolt posts its trip on.
     */
    fun isTrip(sbn: StatusBarNotification): Boolean {
        if (sbn.packageName !in APPS) return false
        val n = sbn.notification
        val extras = n.extras
        if (extras.getCharSequence(NotificationCompat.EXTRA_TITLE).isNullOrBlank()) return false
        if (NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n) != null) return false
        return sbn.isOngoing ||
            extras.getInt(NotificationCompat.EXTRA_PROGRESS_MAX, 0) > 0 ||
            n.channelId.orEmpty().contains("live", ignoreCase = true)
    }

    fun post(context: Context, sbn: StatusBarNotification, appLabel: String) {
        val n = sbn.notification
        val extras = n.extras
        val id = IconCache.castIdFor(sbn)

        val title = extras.getCharSequence(NotificationCompat.EXTRA_TITLE)?.toString()?.trim().orEmpty()
            .ifBlank { appLabel }
        val text = extras.getCharSequence(NotificationCompat.EXTRA_TEXT)?.toString()?.trim().orEmpty()
            .ifBlank { extras.getCharSequence(NotificationCompat.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty() }
            .ifBlank { extras.getCharSequence(NotificationCompat.EXTRA_SUB_TEXT)?.toString()?.trim().orEmpty() }
        // Android 16's chip text is made for exactly this spot; otherwise find the time in the text.
        val shortCritical = extras.getCharSequence("android.shortCriticalText")?.toString()?.trim().orEmpty()
        val eta = shortCritical.ifBlank { eta(title) ?: eta(text).orEmpty() }
        // Left of the camera only fits a word or two, and vivo scrolls anything longer.
        val status = MessageCard.shortName(context, status(title, eta))

        val appIcon = runCatching {
            Icon.createWithResource(sbn.packageName, context.packageManager.getApplicationInfo(sbn.packageName, 0).icon)
        }.getOrNull()
        val large = when (val l = extras.get(NotificationCompat.EXTRA_LARGE_ICON)) {
            is Icon -> parcelSafeIcon(context, l)
            is Bitmap -> Icon.createWithBitmap(shrinkForIcon(l))
            else -> null
        }
        n.smallIcon?.let { IconCache.activeSmallIcons[id] = it }

        val progress = extras.getInt(NotificationCompat.EXTRA_PROGRESS, 0)
        val progressMax = extras.getInt(NotificationCompat.EXTRA_PROGRESS_MAX, 0)
        val liveUpdate = progressMax > 0 &&
            extras.getString(NotificationCompat.EXTRA_TEMPLATE) == "android.app.Notification\$ProgressStyle"

        val intent = Intent(context, PlaygroundService::class.java).apply {
            action = PlaygroundService.ACTION_START
            putExtra("id", id)
            // The trip is live until the app takes its notification down.
            putExtra("is_ongoing", true)
            putExtra("oi_scene", "NAVIGATION")
            putExtra("title", title)
            putExtra("text", text.ifBlank { eta }.ifBlank { appLabel })
            // Base card: the time left, big on the right, where the app's name used to be.
            putExtra("subtext", eta)
            putExtra("source_app", appLabel)
            putExtra("source_pkg", sbn.packageName)
            putExtra("icon_res", R.mipmap.ic_launcher_round)
            putExtra("when", n.`when`)
            putExtra("source_content_intent", n.contentIntent ?: fallbackClickIntent(context, sbn.packageName))
            appIcon?.let { putExtra("oi_left_icon", it) }
            (large ?: appIcon)?.let { putExtra("oi_large_icon", it) }

            if (eta.isNotBlank()) {
                putExtra("oi_left_content", status)
                putExtra("oi_right_template", OriginIslandConstants.TEMPLATE_RIGHT_ISLAND_CAPSULE_TEXT) // 6
                putExtra("oi_right_content", eta)
                putExtra("status_chip_text", eta)
            } else {
                // "Your driver has arrived": no time to show, so the line itself goes on the right.
                putExtra("oi_left_content", "")
                putExtra("oi_right_template", OriginIslandConstants.TEMPLATE_RIGHT_ISLAND_TEXT_ICON) // 4
                putExtra("oi_show_right_icon", false)
                putExtra("oi_right_content", title)
                putExtra("status_chip_text", title)
            }

            if (liveUpdate) {
                // The app's own track: its start/end icons, the car riding the bar.
                putExtra("oi_template", OriginIslandConstants.TEMPLATE_PROGRESS_VISUAL) // 2
                putExtra("show_progress", true)
                putExtra("progress", progress)
                putExtra("progress_max", progressMax)
                val nodes = listOfNotNull(
                    extras.getParcelable("android.progressStartIcon", Icon::class.java),
                    extras.getParcelable("android.progressEndIcon", Icon::class.java),
                )
                if (nodes.isNotEmpty()) putParcelableArrayListExtra("oi_progress_nodes", ArrayList(nodes))
                extras.getParcelable("android.progressTrackerIcon", Icon::class.java)
                    ?.let { putExtra("oi_progress_indicator", it) }
                GenericCard.segmentColor(extras)?.let { putExtra("oi_progress_color", it) }
            } else {
                putExtra("oi_template", OriginIslandConstants.TEMPLATE_BASE) // 4
            }
        }
        context.startService(intent)
    }

    /** "Dropoff in 23 min" -> "23 min"; "Arriving at 03:52" -> "03:52"; null when there's no time. */
    internal fun eta(s: String): String? =
        (DURATION.find(s) ?: CLOCK.find(s))?.value?.trim()?.removeSuffix(".")

    /** "Dropoff in 23 min" -> "Dropoff": the line with its time taken out, for the left of the pill. */
    internal fun status(title: String, eta: String): String {
        if (eta.isBlank()) return title
        val at = title.indexOf(eta)
        if (at < 0) return title
        val before = title.substring(0, at).replace(TRAILING_LINK, "").trim()
        return before.ifBlank { title.substring(at + eta.length).replace(TRAILING_LINK, "").trim() }
            .ifBlank { title }
    }
}
