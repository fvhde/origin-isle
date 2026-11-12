package com.originisle.android.sports

/**
 * Pulls the two logos (or player photos) back out of the picture Flashscore puts on a match
 * notification. Flashscore draws it itself (decompiled, `MOSAIC` / `MOSAIC_CIRCLE`): the home image
 * at the top-left, the away image drawn over it, offset down and right by a fraction of its size.
 *
 *  - Crests and flags, `MOSAIC`: offset ½, so a W-wide picture holds two (2/3)W images.
 *  - Player photos, `MOSAIC_CIRCLE`: each cut to a circle, offset 0.7, images (1/1.7)W wide. The
 *    circles barely touch, so both come out clean.
 *
 * The away image is on top, so it is always whole. The home one loses its bottom-right quarter
 * under it ([repairHome] puts a believable one back, or for photos just cuts the circle out).
 * Pure pixel math over ARGB IntArrays.
 */
object MosaicSplit {

    enum class Layout(val overlap: Double) { CRESTS(0.5), PHOTOS(0.3) }

    /** Side of one image inside a [w]-wide mosaic. */
    fun imageSize(w: Int, layout: Layout): Int = Math.round(w / (2 - layout.overlap)).toInt()

    /**
     * Which layout a mosaic is. Flashscore's images are ~100 px, so the size says it: 1.5 × 100 =
     * 150 for crests, 1.7 × 100 = 170 (168 seen) for photos. Only an unexpected size falls back to
     * looking: just right of the home image, above the away one, is empty in the photo layout but
     * still inside the home crest in the crest layout — which a narrow crest can fool.
     */
    fun layoutOf(px: IntArray, w: Int, h: Int): Layout {
        if (w <= 155) return Layout.CRESTS
        if (w >= 160) return Layout.PHOTOS
        val x = (w * 0.633).toInt().coerceIn(0, w - 1)
        val y = (h * 0.367).toInt().coerceIn(0, h - 1)
        return if (alpha(px[y * w + x]) < 16) Layout.PHOTOS else Layout.CRESTS
    }

    /** The away image: the bottom-right [size] square, as drawn. */
    fun away(px: IntArray, w: Int, h: Int, size: Int): IntArray = crop(px, w, h, w - size, h - size, size)

    /**
     * Where the away image is see-through (round crests, shield corners, outside a photo's circle),
     * its top-left corner still shows the home image under it. Clear those pixels: a photo is cut
     * to its circle; a crest is cleared wherever its mirror image on the right is empty.
     */
    fun cleanAway(away: IntArray, size: Int, layout: Layout): IntArray {
        val out = away.copyOf()
        if (layout == Layout.PHOTOS) return circle(out, size)
        val overlap = size - Math.round(size * (1 - layout.overlap)).toInt()
        for (y in 0 until overlap) for (x in 0 until overlap) {
            if (alpha(away[y * size + (size - 1 - x)]) < 32) out[y * size + x] = 0
        }
        return out
    }

    /** The home image: the top-left [size] square, as drawn (its corner may hold part of the away one). */
    fun home(px: IntArray, w: Int, h: Int, size: Int): IntArray = crop(px, w, h, 0, 0, size)

    /**
     * Rebuild the home image's bottom-right quarter, which the away image was drawn over. Where the
     * away image is transparent the home pixels are still there and are kept; where it isn't, the
     * home image is mirrored from its bottom-left quarter (crests, flags and faces are close to
     * symmetric). The away image's own alpha there is read off its top-right quarter, mirrored.
     */
    fun repairHome(home: IntArray, away: IntArray, size: Int, layout: Layout): IntArray {
        val out = home.copyOf()
        // A photo is a circle: anything outside it in the home square is the away photo.
        if (layout == Layout.PHOTOS) return circle(out, size)
        val offset = Math.round(size * (1 - layout.overlap)).toInt() // where the away image starts
        val half = size / 2
        for (y in offset until size) for (x in offset until size) {
            val ax = x - offset
            val ay = y - offset
            // The away pixel at (ax, ay) is under the home one; its mirror (size-1-ax, ay) isn't.
            val awayAlpha = alpha(away[ay * size + (size - 1 - ax).coerceIn(0, size - 1)])
            if (awayAlpha < 32) continue
            val mx = size - 1 - x
            out[y * size + x] = if (mx < half) home[y * size + mx] else 0
        }
        return out
    }

    private fun circle(px: IntArray, size: Int): IntArray {
        val r = size / 2.0
        for (y in 0 until size) for (x in 0 until size) {
            val dx = x - r + 0.5
            val dy = y - r + 0.5
            if (dx * dx + dy * dy > r * r) px[y * size + x] = 0
        }
        return px
    }

    private fun crop(px: IntArray, w: Int, h: Int, left: Int, top: Int, size: Int): IntArray {
        val out = IntArray(size * size)
        for (y in 0 until size) {
            val sy = top + y
            if (sy < 0 || sy >= h) continue
            for (x in 0 until size) {
                val sx = left + x
                if (sx in 0 until w) out[y * size + x] = px[sy * w + sx]
            }
        }
        return out
    }

    private fun alpha(argb: Int): Int = argb ushr 24
}
