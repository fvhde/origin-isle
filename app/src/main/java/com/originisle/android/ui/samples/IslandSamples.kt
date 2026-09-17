package com.originisle.android.ui.samples

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import com.originisle.android.MainActivity
import com.originisle.android.R
import com.originisle.android.cards.PaymentCard
import com.originisle.android.cards.SportsCard
import com.originisle.android.island.OriginIslandConstants
import com.originisle.android.island.PlaygroundService

/**
 * The in-app tester catalog: one ready-made card per supported OriginIsland type, plus the new
 * live football score. Each entry fires an `oi_*` intent at [PlaygroundService] exactly the way a
 * real cast would, so it exercises the same path end-to-end.
 */
object IslandSamples {

    val all: List<OriginSample> = listOf(
        OriginSample("⚽ Football score", "Scoreboard · ARS 1 – 1 MAN · 65' (generic badges)") { ctx ->
            // Sample uses a generic ball on both sides instead of fetching real crests.
            val ball = Icon.createWithResource(ctx, R.drawable.ic_soccer)
            SportsCard.post(
                ctx, home = "ARS", homeScore = 1, away = "MAN", awayScore = 1, clock = "65'",
                homeLogo = ball, awayLogo = ball,
            )
        },
        OriginSample("App download", "Progress card + island ring · 75%") { ctx ->
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
                putExtra("icon_res", R.drawable.ic_alert)
            }
        },
        OriginSample("Payment", "Apple-style: Processing… → Paid ✓ (green tick + glow)") { ctx ->
            PaymentCard.post(
                context = ctx, id = 40002, appIcon = null,
                amount = "€42.00", merchant = "Café de Flore", appLabel = "Google Wallet",
                clickResp = openApp(ctx),
            )
        },
        OriginSample("Food delivery", "TAKEOUT · base card + capsule ETA") { ctx ->
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
        OriginSample("Ride hailing", "TAXI · driver arriving") { ctx ->
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
        OriginSample("Flight boarding", "FLIGHT · symmetric route card") { ctx ->
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
        OriginSample("Countdown timer", "TIMER · progress 40%") { ctx ->
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
        OriginSample("Loading", "Loading-dots right island") { ctx ->
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
        OriginSample("Music player", "Wave island · media card") { ctx ->
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
        OriginSample("Incoming call", "VOIPCALL · Decline / Answer buttons") { ctx ->
            start(ctx, 40009) {
                putExtra("oi_scene", "NAVIGATION")
                putExtra("oi_template", OriginIslandConstants.TEMPLATE_BUTTONS)
                putExtra("oi_right_template", OriginIslandConstants.TEMPLATE_RIGHT_ISLAND_ICON_TEXT)
                putExtra("title", "Caller")
                putExtra("text", "WhatsApp voice call")
                putExtra("oi_left_content", "Alex")
                putExtra("oi_right_content", "calling")
                putStringArrayListExtra("oi_button_titles", arrayListOf("Decline", "Answer"))
                putExtra("click_resp", openApp(ctx))
                putExtra("icon_res", R.drawable.ic_call)
            }
        },
        OriginSample("Driving navigation", "Driving-navi template · maneuver + street + ETA") { ctx ->
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
        OriginSample("Navigation message", "Navigation template · nav icon + single message line") { ctx ->
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
        OriginSample("Two buttons", "Buttons template · Deny / Receive") { ctx ->
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
