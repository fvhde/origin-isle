package com.originisle.android.island

/**
 * The complete vivo OriginOS "SuperX" atomic-notification (原子通知 / OriginIsland) protocol,
 * recovered verbatim from the original app's decompiled sources.
 *
 * A SuperX card is an ordinary [android.app.Notification] posted under the tag [SUPERX_TAG]
 * whose `extras` bundle carries these keys. The system reads them to render the island.
 * [OriginIslandBuilder.buildBundle] assembles a bundle out of these keys.
 *
 * This file is intentionally kept complete (including keys this app doesn't currently use) — it is
 * the primary reference value of this repository for anyone else reverse-engineering the protocol.
 */
object OriginIslandConstants {

    // --- top-level ---
    const val BUNDLE_KEY_OPERATION = "notification.superx.operation"
    const val BUNDLE_KEY_TEMPLATE = "notification.superx.template"
    const val BUNDLE_KEY_SHOW_NOTIFY = "notification.superx.showNotify"
    const val BUNDLE_KEY_VERSION_CODE = "notification.superx.versionCode"
    const val BUNDLE_KEY_SCENE = "notification.superx.scene"
    const val BUNDLE_KEY_KEEP_DURATION = "notification.superx.keepDuration"
    const val BUNDLE_KEY_IS_V_SUGGESTION = "notification.superx.isVSuggestion"
    const val BUNDLE_KEY_V_EXTRA_DATA = "notification.superx.vSuggestionExtraData"
    const val BUNDLE_KEY_BASE_INFOS = "notification.superx.baseInfos"
    const val BUNDLE_KEY_INFOS = "notification.superx.infos"
    const val BUNDLE_KEY_SHORT_INFOS = "notification.superx.shortInfos"
    const val BUNDLE_KEY_ISLAND_INFOS = "notification.superx.island"
    const val BUNDLE_KEY_CLICK_RESP = "notification.superx.clickResp"
    const val BUNDLE_KEY_CAPSULE = "notification.superx.capsule"
    const val BUNDLE_KEY_CHANGE_RECORD = "notification.superx.changedRecord"
    const val BUNDLE_KEY_REMOTE_IMAGE_ERROR = "remote_image_error"
    const val BUNDLE_KEY_NEW_NODE = "notification.superx.newNode"
    const val BUNDLE_KEY_DISPLAYS = "notification.superx.displays"
    const val BUNDLE_KEY_SOUND = "notification.superx.sound"
    const val BUNDLE_KEY_DISMISS_WHEN_KILL = "notification.superx.dismissWhenKill"
    // Seen on the native vivo music-widget card (com.vivo.musicwidgetmix) alongside showNotify=false;
    // not documented anywhere, recovered via `dumpsys notification` on a live card.
    const val BUNDLE_KEY_ISLAND_NOTIFY = "notification.superx.islandNotify"
    const val BUNDLE_KEY_CUSTOM_SUPERX = "notification.superx.customSuperx"
    const val BUNDLE_KEY_CARD_BG_COLOR = "notification.superx.cardBgColor"
    const val BUNDLE_KEY_KEEP_SCREEN_ON = "notification.superx.keepScreenOn"
    const val BUNDLE_KEY_DISABLE_INVERT_COLOR = "notification.superx.disableInvertColor"
    const val BUNDLE_KEY_CUSTOM_TEMPLATE = "notification.superx.customTemplate"
    const val BUNDLE_KEY_CUSTOM_TEMPLATE_AOD = "notification.superx.customTemplate.aodAdapter"

    // --- baseInfos ---
    const val BUNDLE_KEY_BASE_ICON = "notification.superx.baseInfos.icon"
    const val BUNDLE_KEY_BASE_TITLE = "notification.superx.baseInfos.title"
    const val BUNDLE_KEY_BASE_CONTENT = "notification.superx.baseInfos.content"
    const val BUNDLE_KEY_BASE_CONTENT_ICON = "notification.superx.baseInfos.contentIcon"
    const val BUNDLE_KEY_BASE_SUB_INFO = "notification.superx.baseInfos.subInfo"
    const val BUNDLE_KEY_BASE_SUB_TEXT = "notification.superx.baseInfos.subText"
    const val BUNDLE_KEY_BASE_SUB_TEXT_COLOR = "notification.superx.baseInfos.subTextColor"
    const val BUNDLE_KEY_BASE_SUB_CAPSULE_BG_COLOR = "notification.superx.baseInfos.subCapsuleBgColor"
    const val BUNDLE_KEY_BASE_SUB_IMAGE = "notification.superx.baseInfos.subImage"
    const val BUNDLE_KEY_BASE_SUB_INFO_CLICK_RESP = "notification.superx.baseInfos.subInfoClickResp"
    const val BUNDLE_KEY_BASE_SUB_IMAGE_LIST = "notification.superx.baseInfos.subImageList"
    const val BUNDLE_KEY_BASE_SUB_INFO_CLICK_RESP_LIST = "notification.superx.baseInfos.subInfoClickRespList"
    const val BUNDLE_KEY_BASE_SUB_PROGRESS = "notification.superx.baseInfos.subProgress"
    const val BUNDLE_KEY_BASE_SUB_PROGRESS_COLOR = "notification.superx.baseInfos.subProgressColor"
    const val BUNDLE_KEY_BASE_SUB_PROGRESS_BG_COLOR = "notification.superx.baseInfos.subProgressBgColor"
    const val BUNDLE_KEY_BASE_PROGRESS_STATE = "notification.superx.baseInfos.progressState"
    const val BUNDLE_KEY_BASE_PROGRESS_CONTENT = "notification.superx.baseInfos.progressContent"
    const val BUNDLE_KEY_BASE_LOADING_COLOR = "notification.superx.baseInfos.loadingColor"
    const val BUNDLE_KEY_BASE_GENERATING_STATUS = "notification.superx.baseInfos.generatingStatus"
    const val BUNDLE_KEY_BASE_ICON_STATUS_TYPE = "notification.superx.baseInfos.iconStatusType"
    const val BUNDLE_KEY_BASE_ICON_ROUND_CORNER = "notification.superx.baseInfos.iconRoundCorner"

    // --- infos (expanded card body) ---
    const val BUNDLE_KEY_INFO_DESCRIBE = "notification.superx.infos.describe"
    const val BUNDLE_KEY_INFO_CORE_INFO = "notification.superx.infos.coreInfo"
    const val BUNDLE_KEY_INFO_IMAGE = "notification.superx.infos.image"
    const val BUNDLE_KEY_INFO_IMAGE_CLICK_RESP = "notification.superx.infos.imageClickResp"
    const val BUNDLE_KEY_INFO_NODE_ICON = "notification.superx.infos.nodeIcon"
    const val BUNDLE_KEY_INFO_INDICATOR_ICON = "notification.superx.infos.indicatorIcon"
    const val BUNDLE_KEY_INFO_INDICATOR_LOC = "notification.superx.infos.indicatorLoc"
    const val BUNDLE_KEY_INFO_PROGRESS = "notification.superx.infos.progress"
    const val BUNDLE_KEY_INFO_PROGRESS_COLOR = "notification.superx.infos.progressColor"
    const val BUNDLE_KEY_INFO_BG_COLOR = "notification.superx.infos.BgColor"
    const val BUNDLE_KEY_INFO_LEFT_MAIN = "notification.superx.infos.leftMain"
    const val BUNDLE_KEY_INFO_LEFT_SUB = "notification.superx.infos.leftSub"
    const val BUNDLE_KEY_INFO_MID_MAIN_TYPE = "notification.superx.infos.midMainType"
    const val BUNDLE_KEY_INFO_MID_MAIN_ICON = "notification.superx.infos.midMainIcon"
    const val BUNDLE_KEY_INFO_MID_MAIN_MSG = "notification.superx.infos.midMainMsg"
    const val BUNDLE_KEY_INFO_MID_SUB_TYPE = "notification.superx.infos.midSubType"
    const val BUNDLE_KEY_INFO_MID_SUB_ICON = "notification.superx.infos.midSubIcon"
    const val BUNDLE_KEY_INFO_MID_SUB_MSG = "notification.superx.infos.midSubMsg"
    const val BUNDLE_KEY_INFO_MID_TOP_MSG = "notification.superx.infos.midTopMsg"
    const val BUNDLE_KEY_INFO_RIGHT_MAIN = "notification.superx.infos.rightMain"
    const val BUNDLE_KEY_INFO_RIGHT_MAIN_EXTRA = "notification.superx.infos.rightMainExtra"
    const val BUNDLE_KEY_INFO_RIGHT_SUB = "notification.superx.infos.rightSub"
    const val BUNDLE_KEY_INFO_SUB_REVERSE = "notification.superx.infos.subReverse"
    const val BUNDLE_KEY_INFO_NAV_ICON = "notification.superx.infos.navIcon"
    const val BUNDLE_KEY_INFO_NAV_MSG = "notification.superx.infos.navMsg"
    const val BUNDLE_KEY_INFO_BTN_TYPE = "notification.superx.infos.btnType"
    const val BUNDLE_KEY_INFO_BTN_TEXT_LIST = "notification.superx.infos.btnTextList"
    const val BUNDLE_KEY_INFO_BTN_ICON_LIST = "notification.superx.infos.btnIconList"
    const val BUNDLE_KEY_INFO_BTN_TEXT_COLOR_LIST = "notification.superx.infos.btnTextColorList"
    const val BUNDLE_KEY_INFO_BTN_COLOR_LIST = "notification.superx.infos.btnColorList"
    const val BUNDLE_KEY_INFO_BTN_CLICK_RESP_LIST = "notification.superx.infos.btnClickRespList"
    const val BUNDLE_KEY_INFO_DIR_ICON = "notification.superx.infos.dirIcon"
    const val BUNDLE_KEY_INFO_DIR_SUB_TEXT = "notification.superx.infos.dirSubText"
    const val BUNDLE_KEY_INFO_DIR_ASSIST_TEXT = "notification.superx.infos.dirAssistText"
    const val BUNDLE_KEY_INFO_DIR_ASSIST_ICON = "notification.superx.infos.dirAssistIcon"
    const val BUNDLE_KEY_INFO_DIR_ASSIST_TYPE = "notification.superx.infos.dirAssistType"
    const val BUNDLE_KEY_INFO_DIR_ASSIST_TEXT_COLOR = "notification.superx.infos.dirAssistTextColor"
    const val BUNDLE_KEY_INFO_DIR_ASSIST_BTN_COLOR = "notification.superx.infos.dirAssistBtnColor"
    const val BUNDLE_KEY_INFO_LANE_LIST = "notification.superx.infos.laneList"
    const val BUNDLE_KEY_INFO_MAIN_HIGHLIGHT_TEXT = "notification.superx.infos.mainHighlightText"
    const val BUNDLE_KEY_INFO_MAIN_NORMAL_TEXT = "notification.superx.infos.mainNormalText"

    // --- shortInfos (collapsed) ---
    const val BUNDLE_KEY_SHORT_INFO_ICON = "notification.superx.shortInfos.icon"
    const val BUNDLE_KEY_SHORT_INFO_IMAGE = "notification.superx.shortInfos.image"
    const val BUNDLE_KEY_SHORT_INFO_IMAGE_CLICK_RESP = "notification.superx.shortInfos.imageClickResp"
    const val BUNDLE_KEY_SHORT_INFO_ORIGIN_IMAGE = "notification.superx.shortInfos.OriginBImage"
    const val BUNDLE_KEY_SHORT_INFO_DESCRIBE_SHORT = "notification.superx.shortInfos.describeShort"
    const val BUNDLE_KEY_SHORT_INFO_CORE_INFO_SHORT = "notification.superx.shortInfos.coreInfoShort"

    // --- island (the pill / dynamic-island bar) ---
    const val BUNDLE_KEY_ISLAND_LEFT_TEMPLATE = "island.superx.leftTemplate"
    const val BUNDLE_KEY_ISLAND_LEFT_INFO = "island.superx.leftInfo"
    const val BUNDLE_KEY_ISLAND_RIGHT_TEMPLATE = "island.superx.rightTemplate"
    const val BUNDLE_KEY_ISLAND_RIGHT_INFO = "island.superx.rightInfo"
    const val BUNDLE_KEY_ISLAND_FORCE_SHOW = "island.superx.forceShow"
    const val BUNDLE_KEY_ISLAND_FORCE_SHOW_CARD = "island.superx.forceShowCard"
    const val BUNDLE_KEY_ISLAND_CALLBACK_INFOS = "island.superx.callbackInfos"
    const val BUNDLE_KEY_ISLAND_CARD_TEMPLATE = "island.superx.template"
    const val BUNDLE_KEY_ISLAND_SHOW_TIME = "island.superx.showTime"
    const val BUNDLE_KEY_ISLAND_LEFT_ICON = "island.superx.leftInfo.icon"
    const val BUNDLE_KEY_ISLAND_LEFT_CONTENT = "island.superx.leftInfo.content"
    const val BUNDLE_KEY_ISLAND_LEFT_DOUBLELINE = "island.superx.leftInfo.doublelineText"
    const val BUNDLE_KEY_ISLAND_LEFT_GENERATING = "island.superx.leftInfo.generatingStatus"
    const val BUNDLE_KEY_ISLAND_RIGHT_WAVE_COLOR = "island.superx.rightInfo.waveColor"
    const val BUNDLE_KEY_ISLAND_RIGHT_WAVE_STATE = "island.superx.rightInfo.waveState"
    const val BUNDLE_KEY_ISLAND_RIGHT_PROGRESS = "island.superx.rightInfo.progressValue"
    const val BUNDLE_KEY_ISLAND_RIGHT_PROGRESS_COLOR = "island.superx.rightInfo.progressColor"
    const val BUNDLE_KEY_ISLAND_RIGHT_PROGRESS_BG_COLOR = "island.superx.rightInfo.progressBgColor"
    const val BUNDLE_KEY_ISLAND_RIGHT_PROGRESS_STATE = "island.superx.rightInfo.progressState"
    const val BUNDLE_KEY_ISLAND_RIGHT_PROGRESS_CONTENT = "island.superx.rightInfo.progressContent"
    const val BUNDLE_KEY_ISLAND_RIGHT_LOADING_COLOR = "island.superx.rightInfo.loadingColor"
    const val BUNDLE_KEY_ISLAND_RIGHT_ICON = "island.superx.rightInfo.icon"
    const val BUNDLE_KEY_ISLAND_RIGHT_CONTENT = "island.superx.rightInfo.content"
    const val BUNDLE_KEY_ISLAND_RIGHT_CAPSULE_CONTENT = "island.superx.rightInfo.capsuleContent"
    const val BUNDLE_KEY_ISLAND_RIGHT_BG_COLOR = "island.superx.rightInfo.capsuleBgColor"
    const val BUNDLE_KEY_ISLAND_RIGHT_CLICK_RESP = "island.superx.rightInfo.clickResp"
    const val BUNDLE_KEY_ISLAND_RIGHT_DOUBLELINE = "island.superx.rightInfo.doublelineText"
    const val BUNDLE_KEY_ISLAND_RIGHT_PLAY_PAG = "island.superx.rightInfo.playPAG"
    const val BUNDLE_KEY_ISLAND_CLICK = "island.superx.islandClick"
    const val BUNDLE_KEY_ISLAND_CLICK_RESP = "island.superx.clickResp"
    const val BUNDLE_KEY_ISLAND_BASE_INFOS = "island.superx.baseInfos"
    const val BUNDLE_KEY_ISLAND_CARD_INFOS = "island.superx.infos"
    const val BUNDLE_KEY_ISLAND_SHOW_BAR_WHEN_CARD = "island.superx.showBarWhenCard"
    const val BUNDLE_KEY_ISLAND_DISMISS_CARD = "island.superx.dismissCard"
    const val BUNDLE_KEY_ISLAND_PRIORITY = "island.superx.priority"
    const val BUNDLE_KEY_ISLAND_TYPE = "island.superx.islandType"
    const val BUNDLE_KEY_ISLAND_SHOW_TYPE = "island.superx.islandShowType"
    const val BUNDLE_KEY_ISLAND_STATE = "island.superx.state"
    const val BUNDLE_KEY_ISLAND_PERMANENT = "island.superx.permanent"
    const val BUNDLE_KEY_ISLAND_LANDING_INFO = "island.superx.landingInfo"
    const val BUNDLE_KEY_ISLAND_LANDING_PKG = "island.superx.landingPkg"
    const val BUNDLE_KEY_ISLAND_CAPSULE_WIDTH = "island.superx.capsule.width"

    // --- capsule (the tiny right chip) ---
    const val BUNDLE_KEY_CAPSULE_STATE = "notification.superx.capsule.state"
    const val BUNDLE_KEY_CAPSULE_ICON = "notification.superx.capsule.icon"
    const val BUNDLE_KEY_CAPSULE_CONTENT = "notification.superx.capsule.content"
    const val BUNDLE_KEY_CAPSULE_NEW_NODE = "notification.superx.capsule.newNode"
    const val BUNDLE_KEY_CAPSULE_CONTENT_COLOR = "notification.superx.capsule.contentColor"
    const val BUNDLE_KEY_CAPSULE_BG_COLOR = "notification.superx.capsule.bgColor"
    const val BUNDLE_KEY_CAPSULE_SHOW_TIME = "notification.superx.capsule.showTime"
    const val BUNDLE_KEY_CAPSULE_CLICK_RESP = "notification.superx.capsule.clickResp"
    const val BUNDLE_KEY_CAPSULE_LANDING_AUTO_HIDE = "notification.superx.capsule.landingAutoHide"

    // --- light effect ---
    const val BUNDLE_KEY_EFFECT_IS_LIGHT = "notification.superx.lightEffect.isLight"
    const val BUNDLE_KEY_EFFECT_LIGHT_INFO = "notification.superx.lightEffectInfo"
    const val BUNDLE_KEY_EFFECT_LIGHT_MAIN_COLOR = "notification.superx.lightEffectInfo.mainColor"
    const val BUNDLE_KEY_EFFECT_LIGHT_MODE = "notification.superx.lightEffectInfo.mode"
    const val BUNDLE_KEY_EFFECT_LIGHT_SOS_WARN = "notification.superx.lightEffectInfo.sosWarn"
    const val BUNDLE_KEY_EFFECT_LIGHT_REPEAT = "notification.superx.lightEffectInfo.sosWarnRepeatCount"

    // --- vcard ---
    const val BUNDLE_KEY_VCARD_MAIN_TEXT = "notification.vcard.infos.mainText"
    const val BUNDLE_KEY_VCARD_SUB_TEXT = "notification.vcard.infos.subText"

    // --- card body template ids (BUNDLE_KEY_TEMPLATE) ---
    const val TEMPLATE_PRIORITY_INFO = 1
    const val TEMPLATE_PROGRESS_VISUAL = 2
    const val TEMPLATE_TEXT_SYMMETRY = 3   // <- scoreboard-style: left / mid / right
    const val TEMPLATE_BASE = 4
    const val TEMPLATE_NAVIGATION = 5
    const val TEMPLATE_CUSTOM = 6
    const val TEMPLATE_NOTIF_CUSTOM = 7    // custom RemoteViews (NOT supported on-device: supportCustomFun=false)
    const val TEMPLATE_BUTTONS = 8
    const val TEMPLATE_DRIVING_NAVI = 9

    // --- right-island template ids (BUNDLE_KEY_ISLAND_RIGHT_TEMPLATE) ---
    const val TEMPLATE_RIGHT_ISLAND_WAVE = 1
    const val TEMPLATE_RIGHT_ISLAND_PROGRESS = 2
    const val TEMPLATE_RIGHT_ISLAND_LOADING = 3
    const val TEMPLATE_RIGHT_ISLAND_TEXT_ICON = 4
    const val TEMPLATE_RIGHT_ISLAND_ICON_TEXT = 5
    const val TEMPLATE_RIGHT_ISLAND_CAPSULE_TEXT = 6
    /** Two stacked lines (`rightInfo.doublelineText`, exactly two), e.g. a tennis set score per player. */
    const val TEMPLATE_RIGHT_ISLAND_DOUBLE_LINE = 7

    // --- base sub-info kinds ---
    const val BASE_SUB_INFO_NONE = 0
    const val BASE_SUB_INFO_TEXT = 1
    const val BASE_SUB_INFO_CAPSULE = 2
    const val BASE_SUB_INFO_IMAGE = 3
    const val BASE_SUB_INFO_IMAGE_LIST = 4
    const val BASE_SUB_INFO_PROGRESS = 5
    const val BASE_SUB_INFO_LOADING = 6

    // --- button styles ---
    const val BTN_TYPE_FILLED = 1
    const val BTN_TYPE_ICON_TEXT = 2

    // --- icon status ---
    const val ICON_STATUS_SUCCESS = 0
    const val ICON_STATUS_FAIL = 1
    const val ICON_STATUS_ERROR = 2

    // --- island click behaviour ---
    const val ISLAND_CLICK_SHOW_CARD = 0
    const val ISLAND_CLICK_LANDING = 1
    const val ISLAND_CLICK_FEEDBACK = 2

    // --- display surfaces (bitmask for BUNDLE_KEY_DISPLAYS) ---
    const val DISPLAY_NOTIFICATION = 1
    const val DISPLAY_LOCKSCREEN = 16
    const val DISPLAY_STATUSBAR = 256
    const val DISPLAY_WIDGET = 4096
    const val DISPLAY_AOD = 65536
    const val DISPLAY_MAGICBOX = 1048576
    const val DISPLAY_V_SUGGESTION = 16777216

    /** The tag every SuperX notification must be posted under. */
    const val SUPERX_TAG = "VIVO_SUPERX_TAG"

    val STATUS_MAIN_COLOR = intArrayOf(-13451435, -1225401, -25256)

    /** The scenes the system will grant an app via [OriginIslandBuilder.grantScenes]. */
    val SUPERX_SCENES: List<String> = listOf(
        "NAVIGATION", "MOVIE", "HEALTH_REGISTER", "TAXI", "TAKEOUT", "DELIEVERY",
        "CAR_STATE", "METTING", "TRAIN", "FLIGHT", "INCALLING", "VOIPCALL",
        "TIMER", "RIDE_GUIDE", "CRITICAL",
    )
}
