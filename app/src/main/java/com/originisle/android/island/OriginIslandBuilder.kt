package com.originisle.android.island

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Bundle
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.widget.RemoteViews
import com.originisle.android.R

/**
 * Assembles a vivo SuperX / OriginIsland notification-extras [Bundle] and grants the app the
 * SuperX scenes it needs. Ported verbatim from the original app.
 *
 * The single interesting reflective call is [grantScenes], which invokes the hidden
 * `NotificationManager.setSuperXInfosSceneList(List, List, List, List)` to whitelist this
 * package for every scene in [OriginIslandConstants.SUPERX_SCENES]. This only succeeds on
 * vivo OriginOS builds; elsewhere it logs "unavailable" and no-ops.
 */
object OriginIslandBuilder {

    private const val TAG = "OriginIslandBuilder"

    /** A card action: label + optional icon + optional pending intent. */
    data class OriginAction(
        val title: String,
        val icon: Icon?,
        val pendingIntent: PendingIntent?,
    )

    /** Colour has no alpha byte set -> treat as "unspecified / use default". */
    private fun isDefaultColor(color: Int): Boolean = (color ushr 24) == 0

    fun grantScenes(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        try {
            val method = NotificationManager::class.java.getMethod(
                "setSuperXInfosSceneList",
                List::class.java, List::class.java, List::class.java, List::class.java,
            )
            val scenes = ArrayList(OriginIslandConstants.SUPERX_SCENES)
            val enabled = ArrayList<String>()
            val pkgs = ArrayList<String>()
            val allowed = ArrayList<String>()
            repeat(scenes.size) {
                enabled.add("true")
                pkgs.add(context.packageName)
                allowed.add("true")
            }
            method.invoke(nm, scenes, enabled, pkgs, allowed)
            Log.d(TAG, "setSuperXInfosSceneList granted for ${context.packageName}")
        } catch (e: Exception) {
            Log.w(TAG, "setSuperXInfosSceneList unavailable: ${e.message}")
        }
    }

    fun parseColor(colorStr: String?, defaultColor: Int): Int = try {
        Color.parseColor(colorStr)
    } catch (_: Exception) {
        defaultColor
    }

    /** Bundle that removes/ends a previously-posted card for [scene]. */
    fun buildEndBundle(scene: String): Bundle = Bundle().apply {
        putInt(OriginIslandConstants.BUNDLE_KEY_OPERATION, 2)
        putBoolean(OriginIslandConstants.BUNDLE_KEY_SHOW_NOTIFY, false)
        putString(OriginIslandConstants.BUNDLE_KEY_SCENE, scene)
        putInt(OriginIslandConstants.BUNDLE_KEY_CHANGE_RECORD, Int.MAX_VALUE)
    }

    // NotificationManager also exposes two other hidden SuperX probes, discovered but not currently
    // used by this app (no card posting path needs them):
    //   getSceneStatus(String pkg, String scene): Boolean   — whether a scene is currently granted
    //   isSupportCustomFun(String pkg, String scene): Boolean — whether template 7 (custom
    //     RemoteViews) is supported for that scene; on this device it returns false.
    // Call them the same way as [grantScenes]: reflectively, via NotificationManager.

    private fun transparentIcon(): Icon =
        Icon.createWithBitmap(Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888))

    private fun colorSpan(text: String, color: Int): CharSequence {
        if (text.isEmpty() || isDefaultColor(color)) return text
        return SpannableString(text).apply {
            setSpan(ForegroundColorSpan(color), 0, text.length, 33)
        }
    }

    /**
     * Build the full SuperX extras bundle. Every parameter maps 1:1 onto the fields the
     * original app exposed; see [OriginIslandConstants] for the semantics of each template.
     */
    fun buildBundle(
        context: Context,
        template: Int,
        title: String,
        content: String,
        leftContent: String,
        rightContent: String,
        extra1: String = "",
        extra2: String = "",
        extra3: String = "",
        extra4: String = "",
        rightTemplate: Int = 0,
        defaultIcon: Icon,
        leftIcon: Icon,
        rightIcon: Icon,
        accentIcon: Icon,
        progress: Int = 0,
        bgColor: Int = 0,
        fgColor: Int = 0,
        scene: String,
        clickResp: PendingIntent? = null,
        // 0 = create/update the card, 2 = end it (see buildEndBundle). The original posts with 0;
        // a non-zero create operation makes vivo's interceptor silently drop the notification.
        operation: Int = 0,
        largeIcon: Icon? = null,
        subText: String? = null,
        navMsg: String? = null,
        navAssistText: String? = null,
        actions: List<OriginAction> = emptyList(),
        keepDuration: Int = 0,
        displays: Int = 0,
        sound: Boolean = false,
        dismissWhenKill: Boolean = true,
        newNode: Int = 0,
        islandShowTime: Int = 0,
        capsuleShowTime: Int = 0,
        forceShow: Boolean = false,
        progressState: Int = 0,
        progressMarkers: Boolean = false,
        // Template 2: explicit milestone icons along the bar + the icon that rides it, and the bar
        // colour. Empty/null/0 keeps the defaults above.
        progressNodeIcons: List<Icon> = emptyList(),
        progressIndicatorIcon: Icon? = null,
        progressColor: Int = 0,
        showRightIcon: Boolean = true,
        showLeftIcon: Boolean = true,
        cardBgColor: Int = 0,
        keepScreenOn: Boolean = false,
        disableInvertColor: Boolean = true,
        lightColor: Int = 0,
        lightMode: Int = 0,
        generatingStatus: Int = 0,
        iconStatusType: Int = -1,
        leftDoubleLine: List<String> = emptyList(),
        rightDoubleLine: List<String> = emptyList(),
        buttonTitles: List<String> = emptyList(),
        customTemplate: RemoteViews? = null,
        waveState: Int = 1,
        waveColorList: List<String>? = null,
        changeRecord: Int = 0,
        // The native vivo music-widget card (com.vivo.musicwidgetmix) posts its SuperX notification
        // with this false — confirmed via `dumpsys notification` while it was live — and never shows
        // up in the notification shade, unlike every card this app posts with it hardcoded true.
        // Testing whether flipping it is what actually gates shade visibility.
        showNotify: Boolean = true,
        islandNotify: Boolean = false,
    ): Bundle {
        val C = OriginIslandConstants
        val bundle = Bundle()
        bundle.putInt(C.BUNDLE_KEY_OPERATION, operation)
        bundle.putBoolean(C.BUNDLE_KEY_SHOW_NOTIFY, showNotify)
        if (islandNotify) bundle.putBoolean(C.BUNDLE_KEY_ISLAND_NOTIFY, true)
        bundle.putInt(C.BUNDLE_KEY_TEMPLATE, template)
        bundle.putString(C.BUNDLE_KEY_SCENE, scene)
        bundle.putInt(C.BUNDLE_KEY_CHANGE_RECORD, changeRecord)
        bundle.putBoolean(C.BUNDLE_KEY_SOUND, sound)
        bundle.putBoolean(C.BUNDLE_KEY_DISMISS_WHEN_KILL, dismissWhenKill)
        if (keepDuration > 0) bundle.putInt(C.BUNDLE_KEY_KEEP_DURATION, keepDuration)
        if (displays != 0) bundle.putInt(C.BUNDLE_KEY_DISPLAYS, displays)
        if (newNode > 0) bundle.putInt(C.BUNDLE_KEY_NEW_NODE, newNode)
        if (!isDefaultColor(cardBgColor)) bundle.putInt(C.BUNDLE_KEY_CARD_BG_COLOR, cardBgColor)
        if (keepScreenOn) bundle.putBoolean(C.BUNDLE_KEY_KEEP_SCREEN_ON, true)
        if (disableInvertColor) bundle.putBoolean(C.BUNDLE_KEY_DISABLE_INVERT_COLOR, true)
        if (!isDefaultColor(lightColor)) {
            bundle.putBoolean(C.BUNDLE_KEY_EFFECT_IS_LIGHT, true)
            bundle.putBundle(C.BUNDLE_KEY_EFFECT_LIGHT_INFO, Bundle().apply {
                putInt(C.BUNDLE_KEY_EFFECT_LIGHT_MODE, lightMode)
                putInt(C.BUNDLE_KEY_EFFECT_LIGHT_MAIN_COLOR, lightColor)
            })
        }
        if (clickResp != null) bundle.putParcelable(C.BUNDLE_KEY_CLICK_RESP, clickResp)

        // First action that has a pending intent becomes the capsule sub-info button.
        val firstActionable = actions.firstOrNull { it.pendingIntent != null }
        val actionPi = firstActionable?.pendingIntent
        val actionTitle = firstActionable?.title?.takeIf { it.isNotBlank() }

        val baseIcon = largeIcon ?: defaultIcon

        // --- baseInfos ---
        val base = Bundle()
        base.putParcelable(C.BUNDLE_KEY_BASE_ICON, baseIcon)
        base.putCharSequence(C.BUNDLE_KEY_BASE_TITLE, title)
        base.putCharSequence(C.BUNDLE_KEY_BASE_CONTENT, content)
        when {
            template == 8 -> base.putInt(C.BUNDLE_KEY_BASE_SUB_INFO, 0)
            actionPi != null && actionTitle != null -> {
                base.putInt(C.BUNDLE_KEY_BASE_SUB_INFO, 2)
                base.putString(C.BUNDLE_KEY_BASE_SUB_TEXT, actionTitle)
                base.putParcelable(C.BUNDLE_KEY_BASE_SUB_INFO_CLICK_RESP, actionPi)
                base.putInt(
                    C.BUNDLE_KEY_BASE_SUB_TEXT_COLOR,
                    if (!isDefaultColor(fgColor)) fgColor else Color.parseColor("#FF1C1C1E"),
                )
                base.putInt(
                    C.BUNDLE_KEY_BASE_SUB_CAPSULE_BG_COLOR,
                    if (!isDefaultColor(bgColor)) bgColor else Color.parseColor("#FFF2F2F2"),
                )
            }
            template == 2 -> base.putInt(C.BUNDLE_KEY_BASE_SUB_INFO, 0)
            template == 4 -> {
                base.putInt(C.BUNDLE_KEY_BASE_SUB_INFO, 1)
                var str = subText?.takeIf { it.isNotBlank() } ?: extra1
                if (str.isBlank()) str = " "
                base.putString(C.BUNDLE_KEY_BASE_SUB_TEXT, str)
            }
            !subText.isNullOrBlank() -> {
                base.putInt(C.BUNDLE_KEY_BASE_SUB_INFO, 1)
                base.putString(C.BUNDLE_KEY_BASE_SUB_TEXT, subText)
            }
            else -> base.putInt(C.BUNDLE_KEY_BASE_SUB_INFO, 0)
        }
        if (generatingStatus > 0) base.putInt(C.BUNDLE_KEY_BASE_GENERATING_STATUS, generatingStatus)
        if (iconStatusType >= 0) base.putInt(C.BUNDLE_KEY_BASE_ICON_STATUS_TYPE, iconStatusType)
        bundle.putBundle(C.BUNDLE_KEY_BASE_INFOS, base)

        // --- infos (expanded card body) ---
        val infos = Bundle()
        when (template) {
            1 -> {
                infos.putString(C.BUNDLE_KEY_INFO_DESCRIBE, extra1)
                infos.putString(C.BUNDLE_KEY_INFO_CORE_INFO, extra2)
                infos.putParcelable(C.BUNDLE_KEY_INFO_IMAGE, largeIcon ?: defaultIcon)
                if (clickResp != null) infos.putParcelable(C.BUNDLE_KEY_INFO_IMAGE_CLICK_RESP, clickResp)
            }
            2 -> {
                val nodeIcons = ArrayList<Icon>()
                if (progressNodeIcons.isNotEmpty()) {
                    // The source's own milestones (an Android 16 Live Update's start/end icons),
                    // with its tracker icon riding the bar.
                    nodeIcons.addAll(progressNodeIcons)
                    progressIndicatorIcon?.let {
                        infos.putParcelable(C.BUNDLE_KEY_INFO_INDICATOR_ICON, it)
                        infos.putInt(C.BUNDLE_KEY_INFO_INDICATOR_LOC, 2)
                    }
                } else if (progressMarkers) {
                    nodeIcons.add(accentIcon)
                    nodeIcons.add(defaultIcon)
                    nodeIcons.add(accentIcon)
                    infos.putParcelable(C.BUNDLE_KEY_INFO_INDICATOR_ICON, accentIcon)
                    infos.putInt(C.BUNDLE_KEY_INFO_INDICATOR_LOC, 2)
                } else {
                    val t = transparentIcon()
                    nodeIcons.add(t)
                    nodeIcons.add(t)
                }
                infos.putParcelableArrayList(C.BUNDLE_KEY_INFO_NODE_ICON, nodeIcons)
                infos.putInt(C.BUNDLE_KEY_INFO_PROGRESS, progress.coerceIn(0, 100))
                infos.putInt(
                    C.BUNDLE_KEY_INFO_PROGRESS_COLOR,
                    if (!isDefaultColor(progressColor)) progressColor
                    else context.resources.getColor(R.color.vivo_super_x_progress_bar_default_color, null),
                )
                infos.putInt(
                    C.BUNDLE_KEY_INFO_BG_COLOR,
                    context.resources.getColor(R.color.vivo_super_x_progress_bar_bkg_default_color, null),
                )
            }
            4 -> {
                // BASE had no case here at all — the expanded-card `infos` bundle stayed completely
                // empty. That looks to be exactly why OriginOS auto-generates a companion "compat"
                // notification for BASE-template cards (Maps navigation, media, payments): with no
                // expanded content of its own to show, it falls back to a classic notification.
                // Template-3 (scoreboard) cards populate `infos` fully and never get this duplicate.
                infos.putString(C.BUNDLE_KEY_INFO_DESCRIBE, title)
                infos.putString(C.BUNDLE_KEY_INFO_CORE_INFO, content)
                infos.putParcelable(C.BUNDLE_KEY_INFO_IMAGE, largeIcon ?: defaultIcon)
                if (clickResp != null) infos.putParcelable(C.BUNDLE_KEY_INFO_IMAGE_CLICK_RESP, clickResp)
            }
            3 -> {
                infos.putString(C.BUNDLE_KEY_INFO_LEFT_MAIN, extra1)
                infos.putString(C.BUNDLE_KEY_INFO_LEFT_SUB, extra2)
                infos.putInt(C.BUNDLE_KEY_INFO_MID_MAIN_TYPE, 1)
                infos.putParcelable(C.BUNDLE_KEY_INFO_MID_MAIN_ICON, largeIcon ?: defaultIcon)
                infos.putInt(C.BUNDLE_KEY_INFO_MID_SUB_TYPE, 2)
                infos.putString(C.BUNDLE_KEY_INFO_MID_SUB_MSG, content)
                infos.putString(C.BUNDLE_KEY_INFO_RIGHT_MAIN, extra3)
                infos.putString(C.BUNDLE_KEY_INFO_RIGHT_SUB, extra4)
            }
            5 -> {
                infos.putParcelable(C.BUNDLE_KEY_INFO_NAV_ICON, largeIcon ?: defaultIcon)
                val joined = listOf(title, content).filter { it.isNotBlank() }.joinToString(" • ")
                infos.putString(C.BUNDLE_KEY_INFO_NAV_MSG, navMsg ?: joined)
            }
            7 -> if (customTemplate != null) {
                bundle.putParcelable(C.BUNDLE_KEY_CUSTOM_TEMPLATE, customTemplate)
            }
            8 -> {
                // Build up to 3 filled buttons from actions (preferred) or buttonTitles+clickResp.
                val triples: List<Triple<String, Icon?, PendingIntent>> = when {
                    actions.any { it.pendingIntent != null } ->
                        actions.filter { it.pendingIntent != null }
                            .map { Triple(it.title, it.icon, it.pendingIntent!!) }
                    buttonTitles.isNotEmpty() && clickResp != null ->
                        buttonTitles.map { Triple(it, null as Icon?, clickResp) }
                    else -> emptyList()
                }.take(3)
                if (triples.isNotEmpty()) {
                    val textColor = if (!isDefaultColor(fgColor)) fgColor else Color.parseColor("#FF1A1A1A")
                    val btnColor = if (!isDefaultColor(bgColor)) bgColor else Color.parseColor("#FFEFEFEF")
                    infos.putInt(C.BUNDLE_KEY_INFO_BTN_TYPE, 1)
                    infos.putStringArrayList(C.BUNDLE_KEY_INFO_BTN_TEXT_LIST, ArrayList(triples.map { it.first }))
                    infos.putParcelableArrayList(C.BUNDLE_KEY_INFO_BTN_ICON_LIST, ArrayList(triples.map { it.second }))
                    infos.putIntegerArrayList(C.BUNDLE_KEY_INFO_BTN_TEXT_COLOR_LIST, ArrayList(triples.map { textColor }))
                    infos.putIntegerArrayList(C.BUNDLE_KEY_INFO_BTN_COLOR_LIST, ArrayList(triples.map { btnColor }))
                    infos.putParcelableArrayList(C.BUNDLE_KEY_INFO_BTN_CLICK_RESP_LIST, ArrayList(triples.map { it.third }))
                }
            }
            9 -> {
                infos.putParcelable(C.BUNDLE_KEY_INFO_DIR_ICON, largeIcon ?: defaultIcon)
                infos.putString(C.BUNDLE_KEY_INFO_MAIN_HIGHLIGHT_TEXT, title)
                infos.putString(C.BUNDLE_KEY_INFO_MAIN_NORMAL_TEXT, content)
                infos.putCharSequence(C.BUNDLE_KEY_INFO_DIR_SUB_TEXT, navMsg ?: content)
                // Assist line: the secondary hint a driving card can carry under the maneuver
                // ("Keep left", "Then turn right"). Type 1 = plain text.
                navAssistText?.takeIf { it.isNotBlank() }?.let {
                    infos.putInt(C.BUNDLE_KEY_INFO_DIR_ASSIST_TYPE, 1)
                    infos.putString(C.BUNDLE_KEY_INFO_DIR_ASSIST_TEXT, it)
                    if (!isDefaultColor(fgColor)) {
                        infos.putInt(C.BUNDLE_KEY_INFO_DIR_ASSIST_TEXT_COLOR, fgColor)
                    }
                }
                // BUNDLE_KEY_INFO_LANE_LIST (lane guidance) is deliberately NOT written: the
                // protocol catalogues the key but not its element type, and handing vivo a wrongly
                // typed list would throw inside the system's own unparcel, on a live nav card.
            }
        }
        bundle.putBundle(C.BUNDLE_KEY_INFOS, infos)

        // --- shortInfos (collapsed) ---
        val shortImage = largeIcon ?: defaultIcon
        val shortInfos = Bundle()
        shortInfos.putParcelable(C.BUNDLE_KEY_SHORT_INFO_IMAGE, shortImage)
        shortInfos.putString(C.BUNDLE_KEY_SHORT_INFO_CORE_INFO_SHORT, content)
        shortInfos.putString(C.BUNDLE_KEY_SHORT_INFO_DESCRIBE_SHORT, title)
        if (clickResp != null) shortInfos.putParcelable(C.BUNDLE_KEY_SHORT_INFO_IMAGE_CLICK_RESP, clickResp)
        bundle.putBundle(C.BUNDLE_KEY_SHORT_INFOS, shortInfos)

        // --- island (the pill) ---
        val island = Bundle()
        // Left template 3 = icon + two stacked lines; vivo only honours it with exactly two lines
        // (anything else falls back to icon-only), so pick it from the data rather than trusting callers.
        island.putInt(C.BUNDLE_KEY_ISLAND_LEFT_TEMPLATE, if (leftDoubleLine.size == 2) 3 else 1)
        island.putInt(C.BUNDLE_KEY_ISLAND_RIGHT_TEMPLATE, rightTemplate)
        if (islandShowTime > 0) island.putInt(C.BUNDLE_KEY_ISLAND_SHOW_TIME, islandShowTime)
        if (forceShow) island.putBoolean(C.BUNDLE_KEY_ISLAND_FORCE_SHOW, true)
        // Explicitly opt out of vivo's own "companion bar" — without this, a NAVIGATION-scene card
        // gets a second, untagged, stripped-down copy of the notification auto-generated as a
        // separate promoted chip ("Notification: Starting navigation… [Exit navigation]") in the
        // shade, on top of the island itself.
        island.putBoolean(C.BUNDLE_KEY_ISLAND_SHOW_BAR_WHEN_CARD, false)
        island.putInt(C.BUNDLE_KEY_ISLAND_CLICK, 0)
        if (clickResp != null) island.putParcelable(C.BUNDLE_KEY_ISLAND_CLICK_RESP, clickResp)

        val left = Bundle()
        if (showLeftIcon) left.putParcelable(C.BUNDLE_KEY_ISLAND_LEFT_ICON, leftIcon)
        left.putString(C.BUNDLE_KEY_ISLAND_LEFT_CONTENT, leftContent)
        if (leftDoubleLine.isNotEmpty()) {
            left.putCharSequenceArrayList(C.BUNDLE_KEY_ISLAND_LEFT_DOUBLELINE, ArrayList(leftDoubleLine))
        }
        island.putBundle(C.BUNDLE_KEY_ISLAND_LEFT_INFO, left)

        val right = Bundle()
        when (rightTemplate) {
            1 -> {
                val colors = waveColorList?.takeIf { it.isNotEmpty() }
                    ?: if (!isDefaultColor(fgColor)) listOf(String.format("#%06X", 0xFFFFFF and fgColor)) else null
                if (colors != null) {
                    right.putStringArrayList(C.BUNDLE_KEY_ISLAND_RIGHT_WAVE_COLOR, ArrayList(colors))
                }
                right.putInt(C.BUNDLE_KEY_ISLAND_RIGHT_WAVE_STATE, waveState)
            }
            2 -> {
                right.putInt(C.BUNDLE_KEY_ISLAND_RIGHT_PROGRESS, progress.coerceIn(0, 100))
                right.putInt(
                    C.BUNDLE_KEY_ISLAND_RIGHT_PROGRESS_COLOR,
                    if (!isDefaultColor(fgColor)) fgColor
                    else context.resources.getColor(R.color.vivo_super_x_progress_bar_default_color, null),
                )
                if (!isDefaultColor(bgColor)) right.putInt(C.BUNDLE_KEY_ISLAND_RIGHT_PROGRESS_BG_COLOR, bgColor)
                right.putInt(C.BUNDLE_KEY_ISLAND_RIGHT_PROGRESS_STATE, progressState)
            }
            3 -> if (!isDefaultColor(fgColor)) right.putInt(C.BUNDLE_KEY_ISLAND_RIGHT_LOADING_COLOR, fgColor)
            4, 5 -> {
                if (showRightIcon) right.putParcelable(C.BUNDLE_KEY_ISLAND_RIGHT_ICON, rightIcon)
                // No content at all (not even " ") = vivo's icon-only variant (103): just the icon.
                if (rightContent.isNotEmpty()) {
                    right.putCharSequence(C.BUNDLE_KEY_ISLAND_RIGHT_CONTENT, colorSpan(rightContent, fgColor))
                }
                if (clickResp != null) right.putParcelable(C.BUNDLE_KEY_ISLAND_RIGHT_CLICK_RESP, clickResp)
            }
            6 -> {
                right.putCharSequence(C.BUNDLE_KEY_ISLAND_RIGHT_CAPSULE_CONTENT, colorSpan(rightContent, fgColor))
                if (!isDefaultColor(bgColor)) right.putInt(C.BUNDLE_KEY_ISLAND_RIGHT_BG_COLOR, bgColor)
                if (clickResp != null) right.putParcelable(C.BUNDLE_KEY_ISLAND_RIGHT_CLICK_RESP, clickResp)
            }
        }
        if (rightDoubleLine.isNotEmpty()) {
            right.putCharSequenceArrayList(C.BUNDLE_KEY_ISLAND_RIGHT_DOUBLELINE, ArrayList(rightDoubleLine))
        }
        island.putBundle(C.BUNDLE_KEY_ISLAND_RIGHT_INFO, right)
        bundle.putBundle(C.BUNDLE_KEY_ISLAND_INFOS, island)

        // --- capsule (tiny right chip) ---
        val capsule = Bundle()
        capsule.putInt(C.BUNDLE_KEY_CAPSULE_STATE, 1)
        capsule.putParcelable(C.BUNDLE_KEY_CAPSULE_ICON, rightIcon)
        capsule.putString(C.BUNDLE_KEY_CAPSULE_CONTENT, rightContent)
        if (!isDefaultColor(fgColor)) capsule.putInt(C.BUNDLE_KEY_CAPSULE_CONTENT_COLOR, fgColor)
        if (!isDefaultColor(bgColor)) capsule.putInt(C.BUNDLE_KEY_CAPSULE_BG_COLOR, bgColor)
        if (capsuleShowTime > 0) capsule.putInt(C.BUNDLE_KEY_CAPSULE_SHOW_TIME, capsuleShowTime)
        if (newNode > 0) capsule.putInt(C.BUNDLE_KEY_CAPSULE_NEW_NODE, newNode)
        if (clickResp != null) capsule.putParcelable(C.BUNDLE_KEY_CAPSULE_CLICK_RESP, clickResp)
        bundle.putBundle(C.BUNDLE_KEY_CAPSULE, capsule)

        return bundle
    }
}
