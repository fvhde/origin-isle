package com.originisle.android.cards

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import com.originisle.android.R
import com.originisle.android.island.OriginIslandConstants
import com.originisle.android.island.PlaygroundService
import com.originisle.android.ui.PREFS_NAME

/**
 * Charging card: plug in and the island shows the battery level as a green ring with the time left
 * until full, then gets out of the way a few seconds later — or stays up for the whole charge when
 * "Keep while charging" is on. Reaching 100% while still plugged in shows a brief "Fully charged".
 *
 * Driven by ACTION_BATTERY_CHANGED, registered by the notification listener (which is what keeps
 * our process alive anyway); nothing here needs a permission.
 */
object ChargingCard {

    /** Fixed cast id, outside the notification (20000-29999), match (30000-34999) and sample (40000+) bands. */
    const val ID = 35001

    private const val BRIEF_MS = 6000L
    private const val GREEN = "#FF34C759"

    private val handler = Handler(Looper.getMainLooper())

    /** null until the first broadcast after (re)registering, which is a baseline, not a plug-in. */
    private var plugged: Boolean? = null
    private var lastLevel = -1
    private var showing = false
    private var announcedFull = false

    private var hideTask: Runnable? = null

    fun onBatteryChanged(context: Context, intent: Intent) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS_NAME, 0)
        val enabled = prefs.getBoolean("cast_notifications", false) && prefs.getBoolean("cast_charging", true)
        val keep = prefs.getBoolean("cast_charging_keep", false)

        val nowPlugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100 / scale
        val full = level >= 100 ||
            intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_FULL
        val was = plugged
        plugged = nowPlugged

        if (!enabled) {
            if (showing) hide(app)
        } else if (!nowPlugged) {
            announcedFull = false
            if (showing) hide(app)
        } else if (was == false) {
            // Just plugged in.
            announcedFull = full
            show(app, level, full, glow = true)
            if (!keep) hideLater(app)
        } else if (was == null) {
            // Baseline after a (re)connect while already plugged in: only "keep" puts a card up.
            announcedFull = full
            if (keep) show(app, level, full, glow = false)
        } else if (full && !announcedFull) {
            announcedFull = true
            show(app, level, full = true, glow = true)
            if (!keep) hideLater(app)
        } else if (showing && level != lastLevel) {
            show(app, level, full, glow = false)
        }
        lastLevel = level
    }

    private fun show(context: Context, level: Int, full: Boolean, glow: Boolean) {
        showing = true
        val remaining = if (full) -1L
        else context.getSystemService(BatteryManager::class.java)?.computeChargeTimeRemaining() ?: -1L
        val title = if (full) "Fully charged" else "Charging"
        val text = if (remaining > 0) "$level% · ${formatRemaining(remaining)} until full" else "$level%"
        val bolt = Icon.createWithResource(context, R.drawable.ic_charging)
        val click = PendingIntent.getActivity(
            context, ID, Intent(Intent.ACTION_POWER_USAGE_SUMMARY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val intent = Intent(context, PlaygroundService::class.java).apply {
            action = PlaygroundService.ACTION_START
            putExtra("id", ID)
            // Live: our own timers decide when it goes, not the "Hide after" setting.
            putExtra("is_ongoing", true)
            putExtra("oi_scene", "NAVIGATION")
            putExtra("oi_template", OriginIslandConstants.TEMPLATE_BASE) // 4
            putExtra("oi_right_template", OriginIslandConstants.TEMPLATE_RIGHT_ISLAND_PROGRESS) // 2
            putExtra("oi_progress", level)
            putExtra("title", title)
            putExtra("text", text)
            putExtra("oi_left_content", "$level%")
            putExtra("oi_left_icon", bolt)
            putExtra("oi_large_icon", bolt)
            putExtra("icon_res", R.drawable.ic_charging)
            putExtra("oi_fg_color", GREEN)
            if (glow) {
                putExtra("oi_light_color", GREEN)
                putExtra("oi_force_show", true)
            }
            putExtra("click_resp", click)
        }
        context.startService(intent)
    }

    private fun hideLater(context: Context) {
        hideTask?.let(handler::removeCallbacks)
        hideTask = Runnable { hide(context) }.also { handler.postDelayed(it, BRIEF_MS) }
    }

    private fun hide(context: Context) {
        hideTask?.let(handler::removeCallbacks)
        hideTask = null
        showing = false
        context.startService(
            Intent(context, PlaygroundService::class.java)
                .setAction(PlaygroundService.ACTION_CANCEL)
                .putExtra("id", ID)
                .putExtra("oi_scene", "NAVIGATION"),
        )
    }

    /** Settings changed: take a card down that's no longer wanted, or put one up for "keep". */
    fun onSettingsChanged(context: Context) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS_NAME, 0)
        val enabled = prefs.getBoolean("cast_notifications", false) && prefs.getBoolean("cast_charging", true)
        val keep = prefs.getBoolean("cast_charging_keep", false)
        when {
            showing && (!enabled || !keep) -> hide(app)
            !showing && enabled && keep && plugged == true -> show(app, lastLevel, lastLevel >= 100, glow = false)
        }
    }

    /** 4_800_000 -> "1 h 20 min", 2_100_000 -> "35 min". */
    private fun formatRemaining(ms: Long): String {
        val totalMin = ((ms + 59_999) / 60_000).toInt()
        val h = totalMin / 60
        val m = totalMin % 60
        return when {
            h == 0 -> "$m min"
            m == 0 -> "$h h"
            else -> "$h h $m min"
        }
    }
}
