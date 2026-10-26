package com.originisle.android.cards

import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Icon
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.originisle.android.R
import com.originisle.android.island.OriginIslandConstants
import com.originisle.android.island.PlaygroundService
import com.originisle.android.ui.PREFS_NAME

/**
 * The iPhone's camera / microphone indicator: while an app uses the camera the island shows a
 * green dot, while one records audio an orange dot. It disappears when they're released.
 *
 * Both signals are public and need no permission: camera availability from [CameraManager] and
 * active recordings from [AudioManager]. Neither says WHICH app — Android hides that from normal
 * apps — so the card doesn't either.
 *
 *  - Face unlock opens the camera while the phone is locked; a camera opened while locked is
 *    ignored until it's closed again.
 *  - A call keeps the mic open the whole time and already has its own card, so the mic dot stays
 *    off while the phone is in a call (including VoIP).
 *  - The flashlight holds a camera too ([FlashlightCard] has its own card), so a camera whose torch
 *    is on doesn't count.
 */
object PrivacyCard {

    /** Fixed cast id, next to [ChargingCard.ID]. */
    const val ID = 35002

    /** A camera/mic must stay on this long before the dot shows (no flashes from brief opens)… */
    private const val SHOW_AFTER_MS = 800L
    /** …and off this long before it goes — enough to ride out a front/back camera switch. */
    private const val HIDE_AFTER_MS = 300L
    private const val GREEN = 0xFF34C759.toInt()
    private const val ORANGE = 0xFFFF9F0A.toInt()

    private enum class Shown { NONE, CAMERA, MIC, BOTH }

    private val handler = Handler(Looper.getMainLooper())
    private var app: Context? = null

    private val camerasInUse = mutableSetOf<String>()
    /** Cameras opened while the keyguard was up (face unlock): ignored until they close. */
    private val camerasOpenedLocked = mutableSetOf<String>()
    private var micInUse = false
    private var shown = Shown.NONE

    private val cameraCallback = object : CameraManager.AvailabilityCallback() {
        override fun onCameraUnavailable(cameraId: String) {
            val context = app ?: return
            camerasInUse += cameraId
            if (context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true) {
                camerasOpenedLocked += cameraId
            }
            scheduleUpdate()
        }

        override fun onCameraAvailable(cameraId: String) {
            camerasInUse -= cameraId
            camerasOpenedLocked -= cameraId
            scheduleUpdate()
        }
    }

    private val recordingCallback = object : AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configs: List<AudioRecordingConfiguration>) {
            micInUse = configs.any { !it.isClientSilenced }
            scheduleUpdate()
        }
    }

    private val modeListener = AudioManager.OnModeChangedListener { scheduleUpdate() }

    private val updateTask = Runnable { update() }

    /** Called when the notification listener connects (which is what keeps our process alive). */
    fun start(context: Context) {
        if (app != null) return
        val ctx = context.applicationContext
        app = ctx
        runCatching { ctx.getSystemService(CameraManager::class.java)?.registerAvailabilityCallback(cameraCallback, handler) }
        ctx.getSystemService(AudioManager::class.java)?.let { audio ->
            runCatching { audio.registerAudioRecordingCallback(recordingCallback, handler) }
            runCatching { audio.addOnModeChangedListener(ctx.mainExecutor, modeListener) }
            micInUse = audio.activeRecordingConfigurations.any { !it.isClientSilenced }
        }
        // Registering replays the cameras that are already open; treat those as a baseline the same
        // way, so whatever is in use right now shows (except face unlock, as above).
        scheduleUpdate()
    }

    fun stop() {
        val ctx = app ?: return
        runCatching { ctx.getSystemService(CameraManager::class.java)?.unregisterAvailabilityCallback(cameraCallback) }
        ctx.getSystemService(AudioManager::class.java)?.let { audio ->
            runCatching { audio.unregisterAudioRecordingCallback(recordingCallback) }
            runCatching { audio.removeOnModeChangedListener(modeListener) }
        }
        handler.removeCallbacks(updateTask)
        if (shown != Shown.NONE) cancel(ctx)
        camerasInUse.clear()
        camerasOpenedLocked.clear()
        micInUse = false
        app = null
    }

    /** Something outside changed (the torch, the casting switch): re-check. */
    fun refresh() = scheduleUpdate()

    private fun scheduleUpdate() {
        handler.removeCallbacks(updateTask)
        handler.postDelayed(updateTask, if (wanted() == Shown.NONE) HIDE_AFTER_MS else SHOW_AFTER_MS)
    }

    private fun update() {
        val context = app ?: return
        val next = wanted()
        if (next == shown) return
        shown = next
        if (next == Shown.NONE) cancel(context) else show(context, next)
    }

    private fun wanted(): Shown {
        val context = app ?: return Shown.NONE
        val enabled = context.getSharedPreferences(PREFS_NAME, 0).getBoolean("cast_notifications", false)
        val camera = camerasInUse.any { it !in camerasOpenedLocked && !FlashlightCard.isTorchOn(it) }
        val mode = context.getSystemService(AudioManager::class.java)?.mode ?: AudioManager.MODE_NORMAL
        val inCall = mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION ||
            mode == AudioManager.MODE_CALL_SCREENING || mode == AudioManager.MODE_CALL_REDIRECT ||
            mode == AudioManager.MODE_COMMUNICATION_REDIRECT
        val mic = micInUse && !inCall
        return when {
            !enabled -> Shown.NONE
            camera && mic -> Shown.BOTH
            camera -> Shown.CAMERA
            mic -> Shown.MIC
            else -> Shown.NONE
        }
    }

    private fun show(context: Context, what: Shown) {
        val (title, text, glyph) = when (what) {
            Shown.CAMERA -> Triple("Camera in use", "An app is using your camera", R.drawable.ic_camera)
            Shown.MIC -> Triple("Microphone in use", "An app is using your microphone", R.drawable.ic_mic)
            else -> Triple("Camera and microphone in use", "An app is using your camera and microphone", R.drawable.ic_camera)
        }
        // Like the iPhone: green when the camera is on (it usually brings the mic along), orange for mic only.
        val color = if (what == Shown.MIC) ORANGE else GREEN
        val icon = Icon.createWithResource(context, glyph)
        val click = PendingIntent.getActivity(
            context, ID, Intent(Settings.ACTION_PRIVACY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val intent = Intent(context, PlaygroundService::class.java).apply {
            action = PlaygroundService.ACTION_START
            putExtra("id", ID)
            // Live: it goes when the camera/mic is released, not after "Hide after".
            putExtra("is_ongoing", true)
            putExtra("oi_scene", "NAVIGATION")
            putExtra("oi_template", OriginIslandConstants.TEMPLATE_BASE) // 4
            putExtra("title", title)
            putExtra("text", text)
            // Pill: the glyph left of the camera, just the coloured dot right of it (no text = vivo's
            // icon-only variant).
            putExtra("oi_left_content", "")
            putExtra("oi_left_icon", icon)
            putExtra("oi_large_icon", icon)
            putExtra("oi_right_template", OriginIslandConstants.TEMPLATE_RIGHT_ISLAND_TEXT_ICON) // 4
            putExtra("oi_right_icon", dot(color))
            putExtra("oi_show_right_icon", true)
            putExtra("oi_right_content", "")
            putExtra("icon_res", glyph)
            putExtra("click_resp", click)
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

    /**
     * A small filled dot centred in a transparent square. A bitmap, not a tinted resource, so the
     * island keeps its colour; padded so it reads as a dot rather than filling the icon slot.
     */
    private fun dot(color: Int): Icon {
        val size = 48
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawCircle(size / 2f, size / 2f, size * 0.22f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
        return Icon.createWithBitmap(bitmap)
    }
}
