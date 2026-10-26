package com.originisle.android.cards

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import com.originisle.android.R
import com.originisle.android.island.OriginIslandConstants
import com.originisle.android.island.PlaygroundService
import com.originisle.android.service.TorchOffReceiver
import com.originisle.android.ui.PREFS_NAME

/**
 * Flashlight on: a torch on the island for as long as it's lit; tapping the card (or "Turn off")
 * switches it off. [CameraManager]'s torch callback and setTorchMode need no permission.
 */
object FlashlightCard {

    /** Fixed cast id, next to [PrivacyCard.ID]. */
    const val ID = 35003

    private val handler = Handler(Looper.getMainLooper())
    private var app: Context? = null
    private val torchesOn = mutableSetOf<String>()
    private var showing = false

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (enabled) torchesOn += cameraId else torchesOn -= cameraId
            update()
            PrivacyCard.refresh()
        }

        override fun onTorchModeUnavailable(cameraId: String) {
            // An app opened this camera, which turns its torch off.
            torchesOn -= cameraId
            update()
        }
    }

    fun isTorchOn(cameraId: String) = cameraId in torchesOn

    fun start(context: Context) {
        if (app != null) return
        val ctx = context.applicationContext
        app = ctx
        runCatching { ctx.getSystemService(CameraManager::class.java)?.registerTorchCallback(torchCallback, handler) }
    }

    fun stop() {
        val ctx = app ?: return
        runCatching { ctx.getSystemService(CameraManager::class.java)?.unregisterTorchCallback(torchCallback) }
        if (showing) cancel(ctx)
        torchesOn.clear()
        app = null
    }

    /** The casting switch changed: put the card up or take it down to match. */
    fun refresh() = update()

    /** From the card's button: switch every lit torch off. The callback then takes the card down. */
    fun turnOff(context: Context) {
        val camera = context.getSystemService(CameraManager::class.java) ?: return
        // All of them, not just the ones we've seen lit: the tap may come after a process restart.
        (torchesOn + runCatching { camera.cameraIdList.toList() }.getOrDefault(emptyList()))
            .forEach { runCatching { camera.setTorchMode(it, false) } }
    }

    private fun update() {
        val context = app ?: return
        val enabled = context.getSharedPreferences(PREFS_NAME, 0).getBoolean("cast_notifications", false)
        val on = enabled && torchesOn.isNotEmpty()
        if (on == showing) return
        showing = on
        if (on) show(context) else cancel(context)
    }

    private fun show(context: Context) {
        val icon = Icon.createWithResource(context, R.drawable.ic_flashlight)
        val off = PendingIntent.getBroadcast(
            context, ID, Intent(context, TorchOffReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val offAction = Notification.Action.Builder(icon, "Turn off", off).build()
        val intent = Intent(context, PlaygroundService::class.java).apply {
            action = PlaygroundService.ACTION_START
            putExtra("id", ID)
            putExtra("is_ongoing", true)
            putExtra("oi_scene", "NAVIGATION")
            putExtra("oi_template", OriginIslandConstants.TEMPLATE_BASE) // 4
            putExtra("title", "Flashlight on")
            putExtra("text", "Tap to turn it off")
            putExtra("oi_left_content", "")
            putExtra("oi_left_icon", icon)
            putExtra("oi_large_icon", icon)
            putExtra("oi_right_template", OriginIslandConstants.TEMPLATE_RIGHT_ISLAND_TEXT_ICON) // 4
            putExtra("oi_show_right_icon", false)
            putExtra("oi_right_content", "On")
            putExtra("status_chip_text", "On")
            putExtra("icon_res", R.drawable.ic_flashlight)
            putParcelableArrayListExtra("actions", arrayListOf(offAction))
            putExtra("source_content_intent", off)
        }
        context.startService(intent)
    }

    private fun cancel(context: Context) {
        context.startService(
            Intent(context, PlaygroundService::class.java)
                .setAction(PlaygroundService.ACTION_CANCEL)
                .putExtra("id", ID)
                .putExtra("oi_scene", "NAVIGATION"),
        )
    }
}
