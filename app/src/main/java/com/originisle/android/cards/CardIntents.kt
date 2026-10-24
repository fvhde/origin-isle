package com.originisle.android.cards

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import androidx.core.graphics.drawable.toBitmap
import com.originisle.android.MainActivity

/**
 * A content intent that opens the SOURCE app's own notification target — or, if the source
 * notification has none, falls back to just launching the app. vivo requires every SuperX post to
 * carry a content intent or it silently rejects it; this is that fallback.
 */
internal fun fallbackClickIntent(context: Context, pkg: String): PendingIntent {
    val launch = context.packageManager.getLaunchIntentForPackage(pkg) ?: Intent(context, MainActivity::class.java)
    return PendingIntent.getActivity(
        context, pkg.hashCode(), launch,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/**
 * Cap a bitmap's longer side before it's ever wrapped in an [android.graphics.drawable.Icon] and
 * crosses a Binder call (`startService()`/`notify()` both parcel the Icon). A source notification's
 * bitmap isn't guaranteed to be icon-sized — a video's own frame (e.g. Firefox playback) can be
 * full-resolution — and passing it as-is throws "Could not copy bitmap to parcel blob" and crashes,
 * which then crash-loops the whole listener since it re-casts on every reconnect.
 */
internal fun shrinkForIcon(bitmap: Bitmap, maxDimension: Int = 200): Bitmap {
    val w = bitmap.width
    val h = bitmap.height
    if (w <= maxDimension && h <= maxDimension) return bitmap
    val scale = maxDimension.toFloat() / maxOf(w, h)
    return Bitmap.createScaledBitmap(bitmap, (w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1), true)
}

/**
 * An [Icon] that's safe to put in an Intent. Bitmap-backed icons (a contact's avatar, a chat's large
 * icon) can be full-size and blow the parcel up exactly like [shrinkForIcon] describes, and URI-backed
 * ones point into the source app where SystemUI may not be allowed to read them — so both are
 * rendered once and shrunk here. Resource icons are just a package + id and pass through untouched.
 */
internal fun parcelSafeIcon(context: Context, icon: Icon?, maxDimension: Int = 144): Icon? {
    if (icon == null) return null
    if (icon.type == Icon.TYPE_RESOURCE) return icon
    return runCatching {
        val drawable = icon.loadDrawable(context) ?: return null
        val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: maxDimension
        val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: maxDimension
        val scale = minOf(1f, maxDimension.toFloat() / maxOf(w, h))
        Icon.createWithBitmap(
            drawable.toBitmap((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1)),
        )
    }.getOrNull()
}

/** "125" -> "02:05", "3725" -> "01:02:05" — a stopwatch-style elapsed-time string. */
internal fun formatElapsed(totalSeconds: Long): String {
    val s = totalSeconds % 60
    val m = (totalSeconds / 60) % 60
    val h = totalSeconds / 3600
    return if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
