package com.originisle.android.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The redesign's line icons, from the same 24×24 SVG paths as the design mockups. Drawn in black;
 * `Icon`'s tint recolors them.
 */
object IsleIcons {
    val Bell = stroke("bell", "M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9", "M10.3 21a1.94 1.94 0 0 0 3.4 0")
    val Music = stroke("music", "M9 18V5l12-2v13", circle(6f, 18f, 3f), circle(18f, 16f, 3f))
    val Clock = stroke("clock", circle(12f, 12f, 9f), "M12 7v5l3 2")
    val Moon = stroke("moon", "M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9Z")
    val Lock = stroke("lock", roundRect(5f, 11f, 14f, 10f, 2f), "M8 11V7a4 4 0 0 1 8 0v4")
    val Grid = stroke(
        "grid",
        roundRect(4f, 4f, 6.5f, 6.5f, 1.6f), roundRect(13.5f, 4f, 6.5f, 6.5f, 1.6f),
        roundRect(4f, 13.5f, 6.5f, 6.5f, 1.6f), roundRect(13.5f, 13.5f, 6.5f, 6.5f, 1.6f),
    )
    val Pulse = stroke("pulse", "M22 12h-4l-3 9L9 3l-3 9H2")
    val ChevronRight = stroke("chevron-right", "m9 18 6-6-6-6", width = 2f)
    val Back = stroke("back", "m15 18-6-6 6-6", width = 2.2f)
    val Send = stroke("send", "M22 2 11 13", "M22 2 15 22l-4-9-9-4 20-7z")
    val Refresh = stroke(
        "refresh",
        "M3 12a9 9 0 0 1 15-6.7L21 8", "M21 3v5h-5", "M21 12a9 9 0 0 1-15 6.7L3 16", "M3 21v-5h5",
    )
    val Trash = stroke("trash", "M3 6h18", "M8 6V4h8v2", "M6 6l1 14h10l1-14")
    val Search = stroke("search", circle(11f, 11f, 7f), "m21 21-4.3-4.3")
    val Close = stroke("close", "M18 6 6 18", "m6 6 12 12")
    val Check = stroke("check", "M20 6 9 17l-5-5", width = 2.6f)
    val Dash = stroke("dash", "M6 12h12", width = 2.6f)
    val Shield = stroke("shield", "M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6z")
    val Battery = stroke("battery", roundRect(2f, 7f, 17f, 10f, 2f), "M22 11v2", "M6 10v4")
    val Heart = stroke("heart", "M19 14c1.5-1.5 3-3.2 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.8 0-3 .5-4.5 2-1.5-1.5-2.7-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4 3 5.5l7 7Z")
    val Power = stroke("power", "M12 2v10", "M18.4 6.6a9 9 0 1 1-12.8 0")
    val Download = stroke("download", "M12 3v12", "m7 10 5 5 5-5", "M5 21h14")

    val More = filled("more", circle(5f, 12f, 2f), circle(12f, 12f, 2f), circle(19f, 12f, 2f))
    val Stop = filled("stop", roundRect(5f, 5f, 14f, 14f, 3f))
}

private fun circle(cx: Float, cy: Float, r: Float) =
    "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0Z"

private fun roundRect(x: Float, y: Float, w: Float, h: Float, r: Float) =
    "M${x + r} ${y}h${w - 2 * r}a$r $r 0 0 1 $r ${r}v${h - 2 * r}a$r $r 0 0 1 ${-r} ${r}" +
        "h${-(w - 2 * r)}a$r $r 0 0 1 ${-r} ${-r}v${-(h - 2 * r)}a$r $r 0 0 1 $r ${-r}Z"

private fun stroke(name: String, vararg paths: String, width: Float = 2f): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        paths.forEach {
            addPath(
                pathData = addPathNodes(it),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

private fun filled(name: String, vararg paths: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        paths.forEach { addPath(pathData = addPathNodes(it), fill = SolidColor(Color.Black)) }
    }.build()
