package com.originisle.android.sports

import com.originisle.android.sports.MosaicSplit.Layout
import org.junit.Assert.assertEquals
import org.junit.Test

/** Builds mosaics the way Flashscore does (decompiled `k02`) and checks they come apart again. */
class MosaicSplitTest {

    private val home = 0xFFFF0000.toInt()
    private val away = 0xFF0000FF.toInt()

    /** Two solid squares of [s], the second drawn over the first at s*(1-overlap). */
    private fun mosaic(s: Int, overlap: Double, circle: Boolean): Pair<IntArray, Int> {
        val off = Math.round(s * (1 - overlap)).toInt()
        val w = off + s
        val px = IntArray(w * w)
        fun draw(left: Int, color: Int) {
            for (y in 0 until s) for (x in 0 until s) {
                if (circle) {
                    val dx = x - s / 2.0 + 0.5; val dy = y - s / 2.0 + 0.5
                    if (dx * dx + dy * dy > (s / 2.0) * (s / 2.0)) continue
                }
                px[(left + y) * w + left + x] = color
            }
        }
        draw(0, home); draw(off, away)
        return px to w
    }

    @Test fun crestSizes() {
        assertEquals(100, MosaicSplit.imageSize(150, Layout.CRESTS))
        assertEquals(88, MosaicSplit.imageSize(150, Layout.PHOTOS))
    }

    @Test fun layoutFromFlashscoreSizes() {
        // A narrow crest leaves the spot the pixel test looks at empty: the size must decide.
        assertEquals(Layout.CRESTS, MosaicSplit.layoutOf(IntArray(150 * 150), 150, 150))
        assertEquals(Layout.PHOTOS, MosaicSplit.layoutOf(IntArray(168 * 168), 168, 168))
    }

    @Test fun detectsLayout() {
        val (crests, w1) = mosaic(100, 0.5, circle = false)
        assertEquals(Layout.CRESTS, MosaicSplit.layoutOf(crests, w1, w1))
        val (photos, w2) = mosaic(92, 0.3, circle = true) // 156 wide: the size alone can't tell
        assertEquals(Layout.PHOTOS, MosaicSplit.layoutOf(photos, w2, w2))
    }

    @Test fun awayComesOutWhole() {
        val (px, w) = mosaic(100, 0.5, circle = false)
        val a = MosaicSplit.away(px, w, w, 100)
        assertEquals(true, a.all { it == away })
    }

    @Test fun homeCornerIsRepaired() {
        val (px, w) = mosaic(100, 0.5, circle = false)
        val h = MosaicSplit.home(px, w, w, 100)
        assertEquals(away, h[99 * 100 + 99]) // as drawn: the away crest covers the corner
        val fixed = MosaicSplit.repairHome(h, MosaicSplit.away(px, w, w, 100), 100, Layout.CRESTS)
        assertEquals(true, fixed.all { it == home })
    }

    @Test fun roundCrestsSplitClean() {
        val (px, w) = mosaic(100, 0.5, circle = true)
        val a = MosaicSplit.cleanAway(MosaicSplit.away(px, w, w, 100), 100, Layout.CRESTS)
        assertEquals(0, a.count { it == home })
        val h = MosaicSplit.repairHome(MosaicSplit.home(px, w, w, 100), a, 100, Layout.CRESTS)
        assertEquals(0, h.count { it == away })
        assertEquals(home, h[50 * 100 + 50])
    }

    @Test fun photosSplitClean() {
        val (px, w) = mosaic(88, 0.3, circle = true)
        val a = MosaicSplit.cleanAway(MosaicSplit.away(px, w, w, 88), 88, Layout.PHOTOS)
        assertEquals(0, a.count { it == home })
        val h = MosaicSplit.repairHome(MosaicSplit.home(px, w, w, 88), a, 88, Layout.PHOTOS)
        // The two circles touch: a few edge pixels of the away photo are expected.
        assertEquals(true, h.count { it == away } < 20)
        assertEquals(home, h[44 * 88 + 44])
    }
}
