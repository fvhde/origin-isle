package com.originisle.android.sports

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import android.util.Log
import androidx.core.graphics.drawable.toBitmap
import java.io.File
import java.security.MessageDigest

/**
 * The crests (or player photos) for a match, taken from the picture Flashscore puts on its own
 * notification — no lookups, no network. See [MosaicSplit] for how the picture is put together.
 *
 * The away image always comes out whole, so it's kept on disk under the team's name: the next time
 * that team plays at home its crest is already there, clean, instead of the repaired one.
 */
object FlashscoreImages {

    private const val TAG = "FlashscoreImages"
    private const val OUT_PX = 96
    private const val CACHE_DIR = "crests"
    private const val CACHE_MAX_FILES = 400

    /** [real] is false while these are stand-ins (initials), so the card keeps asking for the picture. */
    data class Images(val home: Icon?, val away: Icon?, val both: Icon?, val real: Boolean = true)

    fun from(context: Context, picture: Icon?, home: String, away: String): Images {
        // No picture: Flashscore hasn't downloaded it yet (it posts again once it has), or it has
        // none at all for these players or teams. Crests seen in earlier matches, else initials.
        val bitmap = runCatching { picture?.let { readable(context, it) } }.getOrNull()
            ?: return standIns(context, home, away)
        return runCatching {
            val w = bitmap.width
            val h = bitmap.height
            val px = IntArray(w * h).also { bitmap.getPixels(it, 0, w, 0, 0, w, h) }
            val layout = MosaicSplit.layoutOf(px, w, h)
            val size = MosaicSplit.imageSize(w, layout)
            val awayPx = MosaicSplit.cleanAway(MosaicSplit.away(px, w, h, size), size, layout)
            val homePx = MosaicSplit.repairHome(MosaicSplit.home(px, w, h, size), awayPx, size, layout)
            val awayBmp = scaled(Bitmap.createBitmap(awayPx, size, size, Bitmap.Config.ARGB_8888))
            store(context, away, awayBmp)
            val homeIcon = cached(context, home)
                ?: Icon.createWithBitmap(scaled(Bitmap.createBitmap(homePx, size, size, Bitmap.Config.ARGB_8888)))
            Images(homeIcon, Icon.createWithBitmap(awayBmp), Icon.createWithBitmap(scaled(bitmap)))
        }.getOrElse {
            Log.w(TAG, "split failed", it)
            Images(cached(context, home), cached(context, away), Icon.createWithBitmap(scaled(bitmap)))
        }
    }

    private fun standIns(context: Context, home: String, away: String): Images {
        val h = cached(context, home)
        val a = cached(context, away)
        val hb = badge(home)
        val ab = badge(away)
        // Both together, the way Flashscore overlaps two player photos.
        val s = 56
        val off = Math.round(s * 0.7f)
        val both = Bitmap.createBitmap(off + s, off + s, Bitmap.Config.ARGB_8888)
        Canvas(both).apply {
            drawBitmap(Bitmap.createScaledBitmap(hb, s, s, true), 0f, 0f, null)
            drawBitmap(Bitmap.createScaledBitmap(ab, s, s, true), off.toFloat(), off.toFloat(), null)
        }
        return Images(
            h ?: Icon.createWithBitmap(hb),
            a ?: Icon.createWithBitmap(ab),
            Icon.createWithBitmap(scaled(both)),
            real = false,
        )
    }

    private val BADGE_COLORS = intArrayOf(
        0xFF3A63F0.toInt(), 0xFFE5533D.toInt(), 0xFF20A36B.toInt(), 0xFF7B5CFF.toInt(),
        0xFFE0A21B.toInt(), 0xFF2BA8C4.toInt(), 0xFFD6407A.toInt(), 0xFF4A4A55.toInt(),
    )

    /** A round badge with the name's initial ("Matiunina V." -> "M"), coloured from the name. */
    private fun badge(name: String): Bitmap {
        val n = FlashscoreParser.shortName(name.trim())
        val letter = n.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?"
        val size = OUT_PX
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = BADGE_COLORS[(n.lowercase().hashCode() and Int.MAX_VALUE) % BADGE_COLORS.size]
        c.drawCircle(size / 2f, size / 2f, size / 2f, p)
        p.color = Color.WHITE
        p.textAlign = Paint.Align.CENTER
        p.textSize = size * 0.5f
        p.typeface = Typeface.DEFAULT_BOLD
        c.drawText(letter, size / 2f, size / 2f - (p.descent() + p.ascent()) / 2, p)
        return b
    }

    /**
     * The picture as a plain ARGB_8888 bitmap. What comes out of another app's notification can be
     * a hardware bitmap, whose pixels can't be read (and which a software canvas refuses to draw).
     */
    private fun readable(context: Context, icon: Icon): Bitmap? {
        val d = icon.loadDrawable(context) ?: return null
        if (d is BitmapDrawable && d.bitmap != null) {
            val b = d.bitmap
            return if (b.config == Bitmap.Config.ARGB_8888) b else b.copy(Bitmap.Config.ARGB_8888, false)
        }
        return d.toBitmap(config = Bitmap.Config.ARGB_8888).let {
            if (it.config == Bitmap.Config.ARGB_8888) it else it.copy(Bitmap.Config.ARGB_8888, false)
        }
    }

    private fun scaled(b: Bitmap, px: Int = OUT_PX): Bitmap =
        if (b.width <= px && b.height <= px) b else Bitmap.createScaledBitmap(b, px, px, true)

    private fun dir(context: Context) = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }

    private fun fileFor(context: Context, name: String): File {
        val key = MessageDigest.getInstance("SHA-1")
            .digest(name.trim().lowercase().toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(dir(context), "$key.png")
    }

    private fun cached(context: Context, name: String): Icon? {
        val f = fileFor(context, name)
        if (!f.exists()) return null
        f.setLastModified(System.currentTimeMillis())
        return runCatching { Icon.createWithBitmap(android.graphics.BitmapFactory.decodeFile(f.path) ?: return null) }
            .getOrNull()
    }

    private fun store(context: Context, name: String, bitmap: Bitmap) {
        runCatching {
            val f = fileFor(context, name)
            if (f.exists()) { f.setLastModified(System.currentTimeMillis()); return }
            f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val files = dir(context).listFiles().orEmpty()
            if (files.size > CACHE_MAX_FILES) {
                files.sortedBy { it.lastModified() }.take(files.size - CACHE_MAX_FILES).forEach { it.delete() }
            }
        }.onFailure { Log.w(TAG, "crest cache write failed", it) }
    }
}
