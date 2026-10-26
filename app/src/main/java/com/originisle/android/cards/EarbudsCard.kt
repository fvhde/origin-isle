package com.originisle.android.cards

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import com.originisle.android.R
import com.originisle.android.island.OriginIslandConstants
import com.originisle.android.island.PlaygroundService
import com.originisle.android.ui.PREFS_NAME

/**
 * Earbuds / headphones connected: their name and battery on the island for a few seconds, like the
 * AirPods pop-up — and once more when they drop to [LOW_LEVEL].
 *
 * Needs "Nearby devices" (BLUETOOTH_CONNECT) to receive the connection broadcasts and read the
 * name; the "Earbuds" switch asks for it. The battery comes from the headset itself (HFP/AVRCP);
 * buds that only report it to their own app just show "Connected".
 */
@SuppressLint("MissingPermission") // Every entry point checks [hasPermission] first.
object EarbudsCard {

    /** Fixed cast id, next to [FlashlightCard.ID]. */
    const val ID = 35004

    /** The battery usually lands a moment after the connection; wait this long for it. */
    private const val BATTERY_WAIT_MS = 3000L
    private const val SHOW_MS = 4000L
    private const val LOW_LEVEL = 15
    // vivo's own battery colours, with their 30% tracks.
    private const val GREEN = "#FF29E683"
    private const val GREEN_TRACK = "#4C29E683"
    private const val RED = "#FFE44C55"
    private const val RED_TRACK = "#4CE44C55"

    // Hidden in the SDK, but the broadcast and its extra are what Settings itself uses.
    private const val ACTION_BATTERY_LEVEL_CHANGED = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
    private const val EXTRA_BATTERY_LEVEL = "android.bluetooth.device.extra.BATTERY_LEVEL"

    private const val TAG = "EarbudsCard"

    private val handler = Handler(Looper.getMainLooper())
    private var app: Context? = null

    private val levels = mutableMapOf<String, Int>()
    /** Devices that connected and are waiting for their battery before the card goes up. */
    private val pending = mutableMapOf<String, Runnable>()
    /** Low battery is announced once per connection. */
    private val warnedLow = mutableSetOf<String>()
    private var showingAddress: String? = null
    private var hideTask: Runnable? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            runCatching { handle(intent) }.onFailure { Log.e(TAG, "earbuds card failed", it) }
        }
    }

    fun hasPermission(context: Context) =
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun enabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, 0)
        return prefs.getBoolean("cast_notifications", false) && prefs.getBoolean("cast_earbuds", false) &&
            hasPermission(context)
    }

    /** Called when the listener connects, and again when the switch or permission changes. */
    fun start(context: Context) {
        val ctx = context.applicationContext
        if (app != null || !enabled(ctx)) return
        app = ctx
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(ACTION_BATTERY_LEVEL_CHANGED)
        }
        // EXPORTED: these come from the Bluetooth app (its own uid), not the system, so a
        // not-exported receiver never gets them. They're protected broadcasts: no app can fake them.
        runCatching { ctx.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED) }
    }

    fun stop() {
        val ctx = app ?: return
        runCatching { ctx.unregisterReceiver(receiver) }
        pending.values.forEach(handler::removeCallbacks)
        pending.clear()
        if (showingAddress != null) hide(ctx)
        app = null
    }

    /** The switch changed: start or stop to match. */
    fun onSettingsChanged(context: Context, listenerConnected: Boolean) {
        if (enabled(context.applicationContext) && listenerConnected) start(context) else stop()
    }

    private fun handle(intent: Intent) {
        val context = app ?: return
        val device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return
        if (!isAudio(device)) return
        val address = device.address
        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                warnedLow -= address
                pending.remove(address)?.let(handler::removeCallbacks)
                // Show as soon as the battery arrives, or after the wait without it.
                val task = Runnable {
                    pending.remove(address)
                    show(context, device, levels[address] ?: batteryOf(device), low = false)
                }
                pending[address] = task
                handler.postDelayed(task, BATTERY_WAIT_MS)
            }
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                pending.remove(address)?.let(handler::removeCallbacks)
                levels -= address
                warnedLow -= address
                if (showingAddress == address) hide(context)
            }
            ACTION_BATTERY_LEVEL_CHANGED -> {
                val level = intent.getIntExtra(EXTRA_BATTERY_LEVEL, -1)
                if (level !in 0..100) return
                val before = levels.put(address, level)
                when {
                    pending.containsKey(address) -> {
                        pending.remove(address)?.let(handler::removeCallbacks)
                        show(context, device, level, low = false)
                    }
                    showingAddress == address -> show(context, device, level, low = level <= LOW_LEVEL)
                    level <= LOW_LEVEL && before != null && before > LOW_LEVEL && address !in warnedLow -> {
                        warnedLow += address
                        show(context, device, level, low = true)
                    }
                }
            }
        }
    }

    private fun isAudio(device: BluetoothDevice): Boolean =
        runCatching { device.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO }
            .getOrDefault(false)

    /**
     * Over-ear headphones (AirPods Max, Sony WH-…) get the headset icon, everything else buds. The
     * class says "headphones" for some; others report a generic headset, so the name decides then.
     */
    private fun isOverEar(device: BluetoothDevice, name: String): Boolean {
        val cls = runCatching { device.bluetoothClass?.deviceClass }.getOrNull()
        if (cls == BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES) return true
        val n = name.lowercase()
        return OVER_EAR_NAMES.any { it in n }
    }

    // Only names no earbuds share ("Studio Buds", "QuietComfort Earbuds" exist).
    private val OVER_EAR_NAMES = listOf("airpods max", "headphone", "casque", "wh-1000")

    /** BluetoothDevice.getBatteryLevel() is hidden; -1 when it isn't reachable or unknown. */
    private fun batteryOf(device: BluetoothDevice): Int = runCatching {
        BluetoothDevice::class.java.getMethod("getBatteryLevel").invoke(device) as Int
    }.getOrDefault(-1).takeIf { it in 0..100 } ?: -1

    private fun show(context: Context, device: BluetoothDevice, level: Int, low: Boolean) {
        if (!enabled(context)) return
        val name = runCatching { device.alias ?: device.name }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Headphones"
        val known = level in 0..100
        val isLow = known && level <= LOW_LEVEL
        val glyph = if (isOverEar(device, name)) R.drawable.ic_headphones else R.drawable.ic_earbuds
        val icon = Icon.createWithResource(context, glyph)
        val click = PendingIntent.getActivity(
            context, ID, Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val intent = Intent(context, PlaygroundService::class.java).apply {
            action = PlaygroundService.ACTION_START
            putExtra("id", ID)
            // Live: our own timer takes it down, not "Hide after".
            putExtra("is_ongoing", true)
            putExtra("oi_scene", "NAVIGATION")
            putExtra("oi_template", OriginIslandConstants.TEMPLATE_BASE) // 4
            // Laid out like OriginOS's own AirPods card (com.android.bluetooth VivoBatteryHelper):
            // "Connected" over the device name, a battery ring with "N%" beside them; on the pill
            // the headphones alone left of the camera and the ring right of it.
            putExtra("title", if (low) "Battery low" else "Connected")
            putExtra("text", name)
            putExtra("oi_left_content", "")
            putExtra("oi_left_icon", icon)
            putExtra("oi_large_icon", icon)
            putExtra("icon_res", glyph)
            if (known) {
                putExtra("oi_right_template", OriginIslandConstants.TEMPLATE_RIGHT_ISLAND_PROGRESS) // 2
                putExtra("oi_progress", level)
                putExtra("oi_card_ring", level)
                putExtra("oi_fg_color", if (isLow) RED else GREEN)
                putExtra("oi_bg_color", if (isLow) RED_TRACK else GREEN_TRACK)
            } else {
                putExtra("oi_right_template", OriginIslandConstants.TEMPLATE_RIGHT_ISLAND_TEXT_ICON) // 4
                putExtra("oi_show_right_icon", false)
                putExtra("oi_right_content", "Connected")
                putExtra("status_chip_text", "Connected")
            }
            if (low) putExtra("oi_light_color", RED)
            putExtra("click_resp", click)
        }
        context.startService(intent)
        showingAddress = device.address
        hideTask?.let(handler::removeCallbacks)
        hideTask = Runnable { hide(context) }.also { handler.postDelayed(it, SHOW_MS) }
    }

    private fun hide(context: Context) {
        hideTask?.let(handler::removeCallbacks)
        hideTask = null
        showingAddress = null
        context.startService(
            Intent(context, PlaygroundService::class.java)
                .setAction(PlaygroundService.ACTION_CANCEL)
                .putExtra("id", ID)
                .putExtra("oi_scene", "NAVIGATION"),
        )
    }
}
