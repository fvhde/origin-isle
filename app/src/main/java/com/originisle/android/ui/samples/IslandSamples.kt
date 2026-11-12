package com.originisle.android.ui.samples

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import com.originisle.android.MainActivity
import com.originisle.android.R
import com.originisle.android.cards.PaymentCard
import com.originisle.android.cards.ScoreCard
import com.originisle.android.island.OriginIslandConstants
import com.originisle.android.island.PlaygroundService
import com.originisle.android.sports.FlashscoreImages

/**
 * The in-app tester catalog: one ready-made card per supported OriginIsland type, plus the new
 * live football score. Each entry fires an `oi_*` intent at [PlaygroundService] exactly the way a
 * real cast would, so it exercises the same path end-to-end.
 */
object IslandSamples {

    val all: List<OriginSample> = listOf(
        OriginSample(
            "Football score", "Goal moment, then the scoreboard · 63'",
            IslandPreview(R.drawable.ic_soccer, "", IslandPreview.Right.Score(1, 0)),
        ) { ctx -> footballDemo(ctx) },
        OriginSample(
            "Tennis score", "Names and sets stacked, like Apple Sports",
            IslandPreview(R.drawable.ic_avatar, "", IslandPreview.Right.Score(2, 0)),
        ) { ctx ->
            ScoreCard.demo(
                ctx, "demo-tennis", 40021, "Alcaraz C. - Djokovic N.",
                listOf("Match started.", "End of 1st set. [6] - 4", "End of 2nd set. [6] - 2", "Start of 3rd set. [2] - 0"),
                FlashscoreImages.Images(badge("A", 0xFFE8743B.toInt()), badge("D", 0xFF3B6BE8.toInt()), badge("AD", 0xFF6B4BC8.toInt())),
            )
        },
        OriginSample(
            "Basketball score", "Quarter by quarter · Q3",
            IslandPreview(R.drawable.ic_alert, "", IslandPreview.Right.Score(52, 55)),
        ) { ctx ->
            ScoreCard.demo(
                ctx, "demo-basketball", 40022, "Lakers - Celtics",
                listOf("End of 1st quarter. 28 - 24", "End of 2nd quarter. 52 - 55", "Start of 3rd quarter. 52 - 55"),
                FlashscoreImages.Images(badge("L", 0xFF552583.toInt()), badge("C", 0xFF007A33.toInt()), badge("LC", 0xFF333333.toInt())),
            )
        },
        OriginSample(
            "App download", "Progress card + island ring · 75%",
            IslandPreview(R.drawable.ic_download, "Downloading", IslandPreview.Right.Progress(0.75f)),
        ) { ctx ->
            start(ctx, 40001) {
                putExtra("oi_scene", "NAVIGATION")
                putExtra("oi_template", OriginIslandConstants.TEMPLATE_PROGRESS_VISUAL)
                putExtra("oi_right_template", OriginIslandConstants.TEMPLATE_RIGHT_ISLAND_PROGRESS)
                putExtra("title", "Downloading")
                putExtra("text", "app-release.apk")
                putExtra("oi_left_content", "Downloading")
                putExtra("show_progress", true)
                putExtra("progress", 75)
                putExtra("progress_max", 100)
                putExtra("status_chip_text", "75%")
                putExtra("icon_res", R.drawable.ic_download)
            }
        },
        OriginSample(
            "Payment", "Apple-style: Processing… → Paid ✓ (green tick + glow)",
            IslandPreview(R.drawable.ic_payment, "€42.00", IslandPreview.Right.Success),
        ) { ctx ->
            PaymentCard.post(
                context = ctx, id = 40002, appIcon = null,
                amount = "€42.00", merchant = "Café de Flore", appLabel = "Google Wallet",
                clickResp = openApp(ctx),
            )
        },
        OriginSample(
            "Food delivery", "TAKEOUT · base card + capsule ETA",
            IslandPreview(R.drawable.ic_alert, "Delivery", IslandPreview.Right.Capsule("12 min")),
        ) { ctx ->
            start(ctx, 40003) {
                putExtra("oi_scene", "NAVIGATION")
                putExtra("oi_template", OriginIslandConstants.TEMPLATE_BASE)
                putExtra("oi_right_template", OriginIslandConstants.TEMPLATE_RIGHT_ISLAND_CAPSULE_TEXT)
                putExtra("title", "Courier on the way")
                putExtra("text", "Your order arrives soon")
                putExtra("oi_left_content", "Delivery")
                putExtra("status_chip_text", "12 min")
                putExtra("icon_res", R.drawable.ic_alert)
            }
        },
        OriginSample(
            "Ride hailing", "TAXI · driver arriving",
            IslandPreview(R.drawable.ic_alert, "Ride", IslandPreview.Right.Capsule("3 min")),
        ) { ctx ->
            start(ctx, 40004) {
                putExtra("oi_scene", "NAVIGATION")
                putExtra("oi_template", OriginIslandConstants.TEMPLATE_BASE)
                putExtra("oi_right_template", OriginIslandConstants.TEMPLATE_RIGHT_ISLAND_CAPSULE_TEXT)
                putExtra("title", "Driver arriving")
                putExtra("text", "Toyota Prius · 白 · AB-123-CD")
                putExtra("oi_left_content", "Ride")
                putExtra("status_chip_text", "3 min")
                putExtra("icon_res", R.drawable.ic_alert)
            }
        },
        OriginSample(
            "Flight boarding", "FLIGHT · symmetric route card",
            IslandPreview(R.drawable.ic_alert, "CDG → FCO", IslandPreview.Right.Capsule("Gate K21")),
        ) { ctx ->
            start(ctx, 40005) {
                putExtra("oi_scene", "NAVIGATION")
                putExtra("oi_template", OriginIslandConstants.TEMPLATE_TEXT_SYMMETRY)
                putExtra("oi_right_template", OriginIslandConstants.TEMPLATE_RIGHT_ISLAND_CAPSULE_TEXT)
                putExtra("title", "AF1980")
                putExtra("text", "Boarding")
                putExtra("oi_extra1", "CDG"); putExtra("oi_extra2", "12:40")
                putExtra("oi_extra3", "FCO"); putExtra("oi_extra4", "14:55")
                putExtra("oi_left_content", "CDG → FCO")
                putExtra("status_chip_text", "Gate K21")
                putExtra("icon_res", R.drawable.ic_alert)
            }
        },
        OriginSample(
            "Countdown timer", "TIMER · progress 40%",
            IslandPreview(R.drawable.ic_timer, "Timer", IslandPreview.Right.Progress(0.4f)),
        ) { ctx ->
            start(ctx, 40006) {
                putExtra("oi_scene", "NAVIGATION")
                putExtra("oi_template", OriginIslandConstants.TEMPLATE_PROGRESS_VISUAL)
                putExtra("oi_right_template", OriginIslandConstants.TEMPLATE_RIGHT_ISLAND_PROGRESS)
                putExtra("title", "Timer")
                putExtra("text", "6:00 remaining")
                putExtra("oi_left_content", "Timer")
                putExtra("show_progress", true)
                putExtra("progress", 40)
                putExtra("progress_max", 100)
                putExtra("status_chip_text", "6:00")
                putExtra("icon_res", R.drawable.ic_timer)
            }
        },
        OriginSample(
            "Loading", "Loading-dots right island",
            IslandPreview(R.drawable.ic_alert, "Working", IslandPreview.Right.Loading),
        ) { ctx ->
            start(ctx, 40007) {
                putExtra("oi_scene", "NAVIGATION")
                putExtra("oi_template", OriginIslandConstants.TEMPLATE_BASE)
                putExtra("oi_right_template", OriginIslandConstants.TEMPLATE_RIGHT_ISLAND_LOADING)
                putExtra("title", "Working…")
                putExtra("text", "Please wait")
                putExtra("oi_left_content", "Working")
                putExtra("icon_res", R.drawable.ic_alert)
            }
        },
        OriginSample(
            "Music player", "Wave island · media card",
            IslandPreview(R.drawable.ic_media_play, "Midnight City", IslandPreview.Right.Wave),
        ) { ctx ->
            start(ctx, 40008) {
                putExtra("oi_scene", "NAVIGATION")
                putExtra("oi_template", OriginIslandConstants.TEMPLATE_BASE)
                putExtra("oi_right_template", OriginIslandConstants.TEMPLATE_RIGHT_ISLAND_WAVE)
                putExtra("title", "Midnight City")
                putExtra("text", "M83")
                putExtra("oi_left_content", "Midnight City")
                putExtra("oi_wave_state", 1)
                putExtra("status_chip_text", "♪")
                putExtra("icon_res", R.drawable.ic_media_play)
            }
        },
        OriginSample(
            "Message", "Chat · avatar + sender left, message right",
            IslandPreview(R.drawable.ic_avatar, "Sarah", IslandPreview.Right.Text("Are we still on for tonight? 🍕")),
        ) { ctx ->
            // The same extras MessageCard sends for a real chat notification.
            start(ctx, 40013) {
                putExtra("oi_scene", "NAVIGATION")
                putExtra("oi_template", OriginIslandConstants.TEMPLATE_BASE)
                putExtra("title", "Sarah")
                putExtra("text", "Are we still on for tonight? 🍕")
                putExtra("subtext", "WhatsApp · 2 new messages")
                putExtra("oi_left_content", "Sarah")
                Icon.createWithResource(ctx, R.drawable.ic_avatar).let {
                    putExtra("oi_left_icon", it)
                    putExtra("oi_large_icon", it)
                }
                putExtra("oi_right_template", OriginIslandConstants.TEMPLATE_RIGHT_ISLAND_TEXT_ICON)
                putExtra("oi_show_right_icon", false)
                putExtra("oi_right_content", "Are we still on for tonight? 🍕")
                putExtra("status_chip_text", "Are we still on for tonight? 🍕")
                putExtra("icon_res", R.drawable.ic_chat)
            }
        },
        OriginSample(
            "Incoming call", "VOIPCALL · Decline / Answer buttons",
            IslandPreview(R.drawable.ic_call, "", IslandPreview.Right.Text("John")),
        ) { ctx ->
            start(ctx, 40009) {
                putExtra("oi_scene", "NAVIGATION")
                putExtra("oi_template", OriginIslandConstants.TEMPLATE_BUTTONS)
                // Same pill as a real call cast: icon on the left, the caller's name right of the camera.
                putExtra("oi_right_template", OriginIslandConstants.TEMPLATE_RIGHT_ISLAND_TEXT_ICON)
                putExtra("oi_show_right_icon", false)
                putExtra("title", "John")
                putExtra("text", "WhatsApp voice call")
                putExtra("oi_left_content", "")
                putExtra("oi_right_content", "John")
                putStringArrayListExtra("oi_button_titles", arrayListOf("Decline", "Answer"))
                putExtra("click_resp", openApp(ctx))
                putExtra("icon_res", R.drawable.ic_call)
            }
        },
        OriginSample(
            "Driving navigation", "Driving-navi template · maneuver + street + ETA",
            IslandPreview(R.drawable.ic_navigation, "350 m", IslandPreview.Right.Capsule("12 min")),
        ) { ctx ->
            // The template the real NavigationCard posts. Unproven on hardware: if nothing appears,
            // OriginOS rejected template 9 and the Cast tab's navigation toggle should stay off.
            start(ctx, 40011) {
                putExtra("oi_scene", "NAVIGATION")
                putExtra("oi_template", OriginIslandConstants.TEMPLATE_DRIVING_NAVI)
                putExtra("oi_right_template", OriginIslandConstants.TEMPLATE_RIGHT_ISLAND_CAPSULE_TEXT)
                putExtra("title", "350 m")
                putExtra("text", "Rue de Rivoli")
                putExtra("oi_nav_msg", "12 min · 4,5 km · 14:32")
                putExtra("oi_nav_assist", "Keep left")
                putExtra("oi_left_content", "350 m")
                putExtra("status_chip_text", "12 min")
                putExtra("icon_res", R.drawable.ic_navigation)
            }
        },
        OriginSample(
            "Navigation message", "Navigation template · nav icon + single message line",
            IslandPreview(R.drawable.ic_navigation, "Turn right", IslandPreview.Right.Capsule("2 min")),
        ) { ctx ->
            start(ctx, 40012) {
                putExtra("oi_scene", "NAVIGATION")
                putExtra("oi_template", OriginIslandConstants.TEMPLATE_NAVIGATION)
                putExtra("oi_right_template", OriginIslandConstants.TEMPLATE_RIGHT_ISLAND_CAPSULE_TEXT)
                putExtra("title", "Turn right")
                putExtra("text", "onto Quai des Tuileries")
                putExtra("oi_nav_msg", "Turn right onto Quai des Tuileries · 2 min")
                putExtra("oi_left_content", "Turn right")
                putExtra("status_chip_text", "2 min")
                putExtra("icon_res", R.drawable.ic_navigation)
            }
        },
        OriginSample(
            "Two buttons", "Buttons template · Deny / Receive",
            IslandPreview(R.drawable.ic_alert, "Share", IslandPreview.Right.None),
        ) { ctx ->
            start(ctx, 40010) {
                putExtra("oi_scene", "NAVIGATION")
                putExtra("oi_template", OriginIslandConstants.TEMPLATE_BUTTONS)
                putExtra("title", "Nearby share")
                putExtra("text", "Pixel 9 wants to share a file")
                putExtra("oi_left_content", "Share")
                putStringArrayListExtra("oi_button_titles", arrayListOf("Deny", "Receive"))
                putExtra("click_resp", openApp(ctx))
                putExtra("icon_res", R.drawable.ic_alert)
            }
        },
    )

    /** Arsenal - Chelsea through the real Flashscore path: kick-off, a goal, then the scorer. */
    fun footballDemo(ctx: Context) {
        val ball = Icon.createWithResource(ctx, R.drawable.ic_soccer)
        ScoreCard.demo(
            ctx, "demo-football", 40020, "Arsenal - Chelsea",
            listOf("⚽ Match started. 0 - 0", "⚽ Goal! [1] - 0", "⚽ Goalscorer: Gabriel Magalhães (63') [1] - 0"),
            FlashscoreImages.Images(badge("A", 0xFFDB0007.toInt()), badge("C", 0xFF034694.toInt()), ball),
        )
    }

    /** A round badge with initials, standing in for a crest or a player photo in the samples. */
    private fun badge(text: String, color: Int): Icon {
        val size = 96
        val b = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(b)
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        p.color = color
        c.drawCircle(size / 2f, size / 2f, size / 2f, p)
        p.color = android.graphics.Color.WHITE
        p.textAlign = android.graphics.Paint.Align.CENTER
        p.textSize = if (text.length > 1) 36f else 48f
        p.typeface = android.graphics.Typeface.DEFAULT_BOLD
        c.drawText(text, size / 2f, size / 2f - (p.descent() + p.ascent()) / 2, p)
        return Icon.createWithBitmap(b)
    }

    private fun start(context: Context, id: Int, configure: Intent.() -> Unit) {
        val intent = Intent(context, PlaygroundService::class.java).apply {
            action = PlaygroundService.ACTION_START
            putExtra("id", id)
            // vivo rejects a SuperX post with no content intent, so give every sample one.
            // A sample's own configure() may overwrite this with a more specific intent.
            putExtra("click_resp", openApp(context))
            // Force the island to actually take over the pill (football sets this; without it the
            // card is accepted but not surfaced, so the sample looks like it "does nothing").
            putExtra("oi_force_show", true)
            putExtra("oi_dismiss_when_kill", true)
            configure()
        }
        context.startService(intent)
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
