package com.originisle.android.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.originisle.android.service.KeepAliveAccessibilityService

/** Shared permission/status checks used by both [OnboardingScreen] and the Cast tab. */

/**
 * A counter that bumps on every ON_RESUME. None of these permissions can be observed, so use
 * `intValue` as a `remember` key to re-check them when the user comes back from Settings. Callers
 * can bump it themselves for grants that don't leave the activity, e.g. a permission dialog.
 */
@Composable
fun rememberResumeTick(): MutableIntState {
    val activity = LocalContext.current as? ComponentActivity
    val tick = remember { mutableIntStateOf(0) }
    DisposableEffect(activity) {
        if (activity == null) return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) tick.intValue++
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }
    return tick
}

fun isListenerEnabled(context: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

fun isAccessibilityEnabled(context: Context): Boolean {
    val flat = Settings.Secure.getString(
        context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ).orEmpty()
    val target = android.content.ComponentName(context, KeepAliveAccessibilityService::class.java)
    return flat.split(':').any { android.content.ComponentName.unflattenFromString(it) == target }
}

fun isBatteryUnrestricted(context: Context): Boolean {
    val pm = context.getSystemService(PowerManager::class.java)
    return pm?.isIgnoringBatteryOptimizations(context.packageName) == true
}

fun listenerStatusText(context: Context): String =
    if (isListenerEnabled(context)) "Notification access: granted ✓" else "Notification access: NOT granted"

// Not about the status-bar icon any more: the foreground service now always starts (its icon is
// hidden by the IMPORTANCE_NONE channel instead), so what this service still buys is the rebind it
// fires from onServiceConnected whenever OriginOS restarts the process.
fun accessibilityStatusText(context: Context): String =
    if (isAccessibilityEnabled(context)) {
        "Keep-alive: on ✓ (reconnects casting after a kill)"
    } else {
        "Keep-alive: off (slower to recover after a kill)"
    }

fun batteryStatusText(context: Context): String =
    if (isBatteryUnrestricted(context)) "Battery: unrestricted ✓" else "Battery: restricted (tap above)"

/** [acknowledged] only records that the user was sent to the screen, never that the toggles are on. */
fun autoStartStatusText(acknowledged: Boolean): String =
    if (acknowledged) {
        "Associated startup: opened ✓ (can't be verified — check it's still on)"
    } else {
        "Associated startup: not confirmed (tap above)"
    }

fun requestIgnoreBattery(context: Context) {
    if (isBatteryUnrestricted(context)) return
    runCatching {
        context.startActivity(
            Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.onFailure {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

/**
 * Deep-link to the per-app "Device management" page holding both "Autostart" and "Associated
 * startup", rather than the global list the user would have to hunt through. The activity takes its
 * target from a "packagename" extra and silently finishes if that's missing or unresolvable. The
 * fallbacks cover vivo builds without it, where startActivity throws instead.
 *
 * Returns whether anything was launched. Only [Settings.ACTION_APPLICATION_DETAILS_SETTINGS] is
 * guaranteed to exist, so false means the device has no reachable screen at all — but true is not
 * proof the user saw the right one, hence the hedged wording in [autoStartStatusText].
 */
fun openAutoStartSettings(context: Context): Boolean {
    val targets = listOf(
        Intent("permission.intent.action.softPermissionDetail")
            .setClassName(
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.SoftPermissionDetailActivity",
            )
            .putExtra("packagename", context.packageName),
        Intent().setClassName(
            "com.vivo.permissionmanager",
            "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
        ),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
    )
    return targets.any { intent ->
        runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
    }
}
