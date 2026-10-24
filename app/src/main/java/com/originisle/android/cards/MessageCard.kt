package com.originisle.android.cards

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.service.notification.StatusBarNotification
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.app.NotificationCompat
import com.originisle.android.R
import com.originisle.android.island.OriginIslandConstants
import com.originisle.android.island.PlaygroundService
import com.originisle.android.service.IconCache
import com.originisle.android.ui.PREFS_NAME

/**
 * A one-off message (WhatsApp, Instagram, X, Messenger, Snapchat, SMS…, and any other plain
 * notification) as a chat bubble on the island:
 *
 *  - **Pill**: split around the camera the way Apple's Dynamic Island and OPPO's Fluid Cloud do —
 *    who on the left (avatar + name), what they said on the right (the message, as wide as the
 *    right half allows; vivo ellipsizes past that and scrolls it once when a new one lands).
 *  - **Card**: sender (or group) as the title, the full latest message, the avatar, and
 *    "WhatsApp · 3 new messages" underneath. Tapping opens the conversation.
 *
 * MessagingStyle notifications give the real sender, avatar and group; anything else falls back to
 * title/text, which is how X, Snapchat and Instagram's activity notifications are built.
 */
object MessageCard {

    /** Long enough for the island to ellipsize itself; short enough to keep the parcel small. */
    private const val LINE_MAX_CHARS = 60
    /**
     * Room for the name left of the camera. vivo gives each half 101dp; minus the camera gap,
     * padding, the 20dp avatar and its spacing that leaves ~53dp of 12dp text (SystemUIPlugin
     * dimens). vivo marquees any left text that overflows, which next to a scrolling message is too
     * much motion — so the name is ellipsized to fit here and never scrolls.
     */
    private const val NAME_WIDTH_DP = 48f
    private const val NAME_TEXT_DP = 12f

    internal data class Message(
        val sender: String,
        val text: String,
        val group: String?,
        val avatar: Icon?,
        val count: Int,
    )

    fun post(context: Context, sbn: StatusBarNotification, appLabel: String) {
        val n = sbn.notification
        val id = IconCache.castIdFor(sbn)
        val msg = read(context, sbn, appLabel)

        val appIcon = runCatching {
            Icon.createWithResource(sbn.packageName, context.packageManager.getApplicationInfo(sbn.packageName, 0).icon)
        }.getOrNull()
        val face = parcelSafeIcon(context, msg.avatar) ?: appIcon
        n.smallIcon?.let { IconCache.activeSmallIcons[id] = it }

        // Group chats keep just the sender on the pill (the group is the card's title).
        val top = msg.sender
        val subtext = if (msg.count > 1) "$appLabel · ${msg.count} new messages" else appLabel

        val buttons = if (context.getSharedPreferences(PREFS_NAME, 0).getBoolean("cast_show_actions", true)) {
            GenericCard.usableActions(n)
        } else {
            emptyList()
        }

        val intent = Intent(context, PlaygroundService::class.java).apply {
            action = PlaygroundService.ACTION_START
            putExtra("id", id)
            putExtra("is_ongoing", false)
            putExtra("oi_scene", "NAVIGATION")
            putExtra("oi_template",
                if (buttons.size >= 2) OriginIslandConstants.TEMPLATE_BUTTONS // 8
                else OriginIslandConstants.TEMPLATE_BASE, // 4
            )
            putExtra("title", msg.group ?: msg.sender)
            putExtra("text", if (msg.group != null) "${msg.sender}: ${msg.text}" else msg.text)
            putExtra("subtext", subtext)
            putExtra("source_app", appLabel)
            putExtra("source_pkg", sbn.packageName)

            putExtra("oi_left_content", shortName(context, top))
            face?.let {
                putExtra("oi_left_icon", it)
                putExtra("oi_large_icon", it)
            }
            // Right: the message as plain text, no icon (vivo's text-only variant) — every pixel of
            // the right half goes to the message.
            putExtra("oi_right_template", OriginIslandConstants.TEMPLATE_RIGHT_ISLAND_TEXT_ICON) // 4
            putExtra("oi_show_right_icon", false)
            putExtra("oi_right_content", msg.text.clip(LINE_MAX_CHARS))
            putExtra("status_chip_text", msg.text.clip(LINE_MAX_CHARS))
            putExtra("icon_res", R.mipmap.ic_launcher_round)
            putExtra("when", n.`when`)
            putExtra("source_content_intent", n.contentIntent ?: fallbackClickIntent(context, sbn.packageName))
            if (buttons.isNotEmpty()) putParcelableArrayListExtra("actions", ArrayList(buttons))
        }
        context.startService(intent)
    }

    /** Sender, text, group and avatar — from MessagingStyle when there is one, else title/text. */
    internal fun read(context: Context, sbn: StatusBarNotification, appLabel: String): Message {
        val n = sbn.notification
        val extras = n.extras
        val title = extras.getCharSequence(NotificationCompat.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val body = (extras.getCharSequence(NotificationCompat.EXTRA_TEXT)?.toString()?.trim().orEmpty())
            .ifBlank { extras.getCharSequence(NotificationCompat.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty() }
            .ifBlank { extras.getCharSequence(NotificationCompat.EXTRA_SUB_TEXT)?.toString()?.trim().orEmpty() }
        val largeIcon = when (val large = extras.get(NotificationCompat.EXTRA_LARGE_ICON)) {
            is Icon -> large
            is Bitmap -> Icon.createWithBitmap(shrinkForIcon(large))
            else -> null
        }

        val style = runCatching { NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n) }.getOrNull()
        val last = style?.messages?.lastOrNull()
        if (style != null && last != null) {
            val sender = last.person?.name?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                ?: style.user.name?.toString()?.trim()?.takeIf { last.person == null && it.isNotEmpty() }
                ?: title.ifBlank { appLabel }
            val group = style.conversationTitle?.toString()?.trim()
                ?.takeIf { it.isNotEmpty() && style.isGroupConversation && it != appLabel }
            val avatar = last.person?.icon?.let { runCatching { it.toIcon(context) }.getOrNull() } ?: largeIcon
            val text = last.text?.toString()?.trim().orEmpty().ifBlank { body }
            // A message count of 1 per notification is normal; apps that stack several unread
            // messages into one notification put them all in `messages`.
            return Message(sender, text.ifBlank { "New message" }, group, avatar, style.messages.size)
        }

        // Not MessagingStyle (X, Snapchat, Instagram activity, most non-chat apps): title is who /
        // what, text is the message.
        return Message(
            sender = title.ifBlank { appLabel },
            text = body.ifBlank { title }.ifBlank { "New notification" },
            group = null,
            avatar = largeIcon,
            count = 1,
        )
    }

    /**
     * First name only when the full name doesn't fit (Apple does the same), then ellipsized to the
     * measured width. Phone numbers ("+33 6 12…") aren't split into words.
     */
    private fun shortName(context: Context, name: String): String {
        val density = context.resources.displayMetrics.density
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = NAME_TEXT_DP * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val room = NAME_WIDTH_DP * density
        val full = name.replace('\n', ' ').trim()
        val first = full.substringBefore(' ')
        val pick = if (paint.measureText(full) > room && first.length >= 2 && first.first().isLetter()) first else full
        return TextUtils.ellipsize(pick, paint, room, TextUtils.TruncateAt.END).toString()
    }

    private fun String.clip(max: Int): String =
        replace('\n', ' ').let { if (it.length > max) it.take(max - 1) + "…" else it }
}
