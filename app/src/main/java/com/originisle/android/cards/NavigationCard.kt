package com.originisle.android.cards

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Icon
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.originisle.android.R
import com.originisle.android.island.OriginIslandConstants
import com.originisle.android.island.PlaygroundService
import com.originisle.android.service.IconCache

/**
 * Turn-by-turn navigation (Maps, anything posting `CATEGORY_NAVIGATION`) cast onto vivo's own
 * driving-navigation card — template 9 — instead of the generic base card.
 *
 * Waze is not among them: it publishes no turn data to the notification system at all (see
 * `NAV_APPS` in [com.originisle.android.service.NotificationCastListener]), so there is nothing
 * here to build a card from.
 *
 * Template 9 is what OriginOS uses for its native navigation island: a highlighted maneuver line, a
 * normal-weight street line under it, a sub-text lane for the journey summary, and the maneuver
 * arrow as the card icon. [com.originisle.android.island.OriginIslandBuilder] has always been able
 * to build it; until now nothing ever asked for it.
 *
 * The default, unconditional path for any recognised navigation notification — confirmed rendering
 * correctly on hardware, so there's no toggle/fallback to [GenericCard] to opt out of it anymore.
 */
object NavigationCard {

    /**
     * A journey summary is typically "12 min · 4,5 km · 14:32" — the pill only has room for the
     * leading figure, and that's the one worth showing.
     */
    private val SUMMARY_SEPARATORS = charArrayOf('·', '•', '|')

    fun post(context: Context, sbn: StatusBarNotification) {
        val n = sbn.notification
        val extras = n.extras
        val id = IconCache.castIdFor(sbn)

        val appLabel = runCatching {
            context.packageManager.getApplicationLabel(
                context.packageManager.getApplicationInfo(sbn.packageName, 0),
            ).toString()
        }.getOrDefault(sbn.packageName)

        // Maps deliberately leaves the title empty on some frames and renders its own custom view;
        // the app's name beats the literal word "Notification" as a fallback (same reasoning as
        // GenericCard).
        val maneuver = extras.getCharSequence(NotificationCompat.EXTRA_TITLE)?.toString()?.trim().orEmpty()
            .ifBlank { appLabel }
        val street = extras.getCharSequence(NotificationCompat.EXTRA_TEXT)?.toString()?.trim().orEmpty()
            .ifBlank { extras.getCharSequence(NotificationCompat.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty() }
        val summary = extras.getCharSequence(NotificationCompat.EXTRA_SUB_TEXT)?.toString()?.trim().orEmpty()

        // Pill chip: the OS's own promoted-notification string if there is one (Android 16 Live
        // Updates post it precisely because it's the one figure that fits a chip), else the leading
        // figure of the journey summary, else the maneuver itself.
        val shortCritical = extras.getCharSequence("android.shortCriticalText")?.toString()?.trim().orEmpty()
        val chip = shortCritical.ifBlank { summary.leadingFigure() }.ifBlank { maneuver }

        // The maneuver arrow rides in the notification's large icon; keep it for the card's dirIcon
        // and the pill's left icon, so neither falls back to a monochrome glyph.
        val maneuverIcon: Icon? = when (val large = extras.get(NotificationCompat.EXTRA_LARGE_ICON)) {
            is Icon -> large
            is Bitmap -> Icon.createWithBitmap(shrinkForIcon(large))
            else -> null
        }
        // Maps sends a black arrow in light mode. Whiten it for the pill only, the card can be light.
        val pillIcon: Icon? = maneuverIcon?.let { runCatching { whiteArrow(context, it) }.getOrDefault(it) }
        n.smallIcon?.let { IconCache.activeSmallIcons[id] = it }

        val intent = Intent(context, PlaygroundService::class.java).apply {
            action = PlaygroundService.ACTION_START
            putExtra("id", id)
            // Navigation is by definition live — never eligible for the auto-dismiss timer.
            putExtra("is_ongoing", true)
            putExtra("oi_scene", "NAVIGATION")
            putExtra("oi_template", OriginIslandConstants.TEMPLATE_DRIVING_NAVI)                    // 9
            putExtra("oi_right_template", OriginIslandConstants.TEMPLATE_RIGHT_ISLAND_CAPSULE_TEXT) // 6
            putExtra("title", maneuver)
            // vivo silently REJECTS a SuperX post with empty content text, and Maps genuinely has
            // no street line for the first frames of a journey — so this can never end up blank.
            putExtra("text", street.ifBlank { summary }.ifBlank { appLabel })
            putExtra("subtext", summary)
            putExtra("oi_nav_msg", summary.ifBlank { street })
            putExtra("source_app", appLabel)
            putExtra("source_pkg", sbn.packageName)
            putExtra("oi_left_content", maneuver)
            putExtra("oi_right_content", chip)
            putExtra("status_chip_text", chip)
            putExtra("icon_res", R.mipmap.ic_launcher_round)
            putExtra("oi_bg_color", "#FF262626")
            putExtra("oi_fg_color", "#FFFFFFFF")
            putExtra("when", n.`when`)
            // vivo requires a content intent; fall back to launching the source app.
            putExtra("source_content_intent", n.contentIntent ?: fallbackClickIntent(context, sbn.packageName))
            n.actions?.let { putParcelableArrayListExtra("actions", ArrayList(it.toList())) }
            maneuverIcon?.let { putExtra("oi_large_icon", it) }
            pillIcon?.let { putExtra("oi_left_icon", it) }
        }
        context.startService(intent)
    }

    private fun whiteArrow(context: Context, icon: Icon): Icon {
        val drawable = icon.loadDrawable(context) ?: return icon
        val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: 192
        val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: 192
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, w, h)
        drawable.draw(Canvas(bitmap))
        return Icon.createWithBitmap(shrinkForIcon(bitmap)).setTint(Color.WHITE)
    }

    /** "12 min · 4,5 km · 14:32" -> "12 min". Blank in, blank out. */
    private fun String.leadingFigure(): String =
        split(*SUMMARY_SEPARATORS).firstOrNull()?.trim().orEmpty()
}
