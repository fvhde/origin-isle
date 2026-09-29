package com.originisle.android.ui.samples

import android.content.Context
import androidx.annotation.DrawableRes

/**
 * A ready-made OriginIsland demo card, used by the in-app tester to fire an example of each
 * supported type without waiting for a real notification. [post] builds and sends the card;
 * [preview] describes what the compact island will look like, for the tester's preview.
 */
data class OriginSample(
    val name: String,
    val summary: String,
    val preview: IslandPreview,
    val post: (Context) -> Unit,
)

/**
 * The compact island a sample produces: an icon + left text, and one of the right-island templates.
 * Mirrors the extras the sample sends (`icon_res`, `oi_left_content`, `oi_right_template`, chip).
 */
data class IslandPreview(
    @DrawableRes val icon: Int,
    val left: String,
    val right: Right,
) {
    sealed interface Right {
        /** TEMPLATE_RIGHT_ISLAND_CAPSULE_TEXT: text in a grey capsule. */
        data class Capsule(val text: String) : Right
        /** TEMPLATE_RIGHT_ISLAND_PROGRESS: a progress ring. */
        data class Progress(val fraction: Float) : Right
        /** TEMPLATE_RIGHT_ISLAND_LOADING: three dots. */
        data object Loading : Right
        /** TEMPLATE_RIGHT_ISLAND_WAVE: animated sound bars. */
        data object Wave : Right
        /** TEMPLATE_RIGHT_ISLAND_TEXT_ICON with the icon hidden: plain text (a call's caller name). */
        data class Text(val text: String) : Right
        /** The payment card's final state: the green success tick. */
        data object Success : Right
        /** The football card: crest + score on each side. */
        data class Score(val home: Int, val away: Int) : Right
        /** No right island (buttons template). */
        data object None : Right
    }
}
