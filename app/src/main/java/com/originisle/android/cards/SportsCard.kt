package com.originisle.android.cards

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.originisle.android.MainActivity
import com.originisle.android.R
import com.originisle.android.island.OriginIslandConstants
import com.originisle.android.island.PlaygroundService
import com.originisle.android.service.NotificationCastListener
import java.util.concurrent.ConcurrentHashMap

/**
 * Live football (soccer) score card for the OriginIsland, styled after Apple's live-sports
 * Dynamic Island.
 *
 * Mapping onto the SuperX protocol:
 *  - **Expanded card** uses [OriginIslandConstants.TEMPLATE_TEXT_SYMMETRY] (template 3):
 *    `leftMain/leftSub` = home team + score, centre = competition badge + match clock,
 *    `rightMain/rightSub` = away team + score. A symmetric scoreboard.
 *  - **Collapsed pill** adapts to how crowded the island is (see [isCrowded]).
 *
 * Call [post] to show/refresh a fixed score. [clear] ends it. [refresh] re-posts a live card if the
 * island's crowding changed since it was last shown.
 */
object SportsCard {

    /** Stable notification id for the demo match. Any int in an unused band works. */
    const val MATCH_ID = 55001

    /**
     * Most score apps never cancel their own notification once a match ends — the "FT" card just
     * sits there with a stale final score. Without an explicit timer, our card would sit on the
     * island forever too. Keep the final result up for a while, then auto-clear it.
     */
    private const val FULL_TIME_AUTO_CLEAR_MS = 5 * 60 * 1000L

    private val handler = Handler(Looper.getMainLooper())

    /** Everything needed to re-post a card when the island's crowding changes, plus the crowded
     *  state we last rendered it with (so [refresh] only re-posts when that decision flips). */
    private data class PostArgs(
        val home: String, val homeScore: Int, val away: String, val awayScore: Int,
        val clock: String, val competition: String, val id: Int,
        val homeLogo: Icon?, val awayLogo: Icon?, val centerLogo: Icon?,
        val clickResp: PendingIntent?, val crowded: Boolean,
    )
    private val lastPosts = ConcurrentHashMap<Int, PostArgs>()

    /**
     * Handler.postDelayed/removeCallbacksAndMessages match a token by *reference*, not value — a
     * plain `id: Int` reboxes to a fresh Integer on every call (match ids are well outside Java's
     * -128..127 boxing cache), so removal would silently never match the object scheduling created.
     * Keep one stable token object per id instead.
     */
    private val clearTokens = ConcurrentHashMap<Int, Any>()
    private fun tokenFor(id: Int): Any = clearTokens.getOrPut(id) { Any() }

    /**
     * Show or update a football score card.
     *
     * @param clock the match clock text shown in the pill chip and card centre, e.g. "67'", "HT", "FT".
     * @param clickResp what tapping the card opens; defaults to opening this app (used by the sample).
     *   Real casts should pass the source notification's own content intent so the card opens the
     *   score app, not us.
     */
    fun post(
        context: Context,
        home: String,
        homeScore: Int,
        away: String,
        awayScore: Int,
        clock: String,
        competition: String = "Premier League",
        id: Int = MATCH_ID,
        homeLogo: Icon? = null,
        awayLogo: Icon? = null,
        centerLogo: Icon? = null,
        clickResp: PendingIntent? = null,
    ) {
        val crowded = isCrowded(context, id)
        lastPosts[id] = PostArgs(
            home, homeScore, away, awayScore, clock, competition, id,
            homeLogo, awayLogo, centerLogo, clickResp, crowded,
        )
        val intent = Intent(context, PlaygroundService::class.java).apply {
            action = PlaygroundService.ACTION_START
            putExtra("id", id)
            putExtra("is_ongoing", true)
            putExtra("oi_auto_dismissible", false)
            putExtra("oi_scene", "NAVIGATION") // any granted scene works as the carrier
            putExtra("oi_template", OriginIslandConstants.TEMPLATE_TEXT_SYMMETRY) // 3
            putExtra("oi_right_template", OriginIslandConstants.TEMPLATE_RIGHT_ISLAND_TEXT_ICON) // 4
            // With real crests, show a badge on each side; otherwise score-only. vivo REQUIRES a
            // left icon (a transparent one keeps a tiny placeholder), but the right icon is optional.
            if (homeLogo != null) putExtra("oi_left_icon", homeLogo)
            if (awayLogo != null) {
                putExtra("oi_right_icon", awayLogo)
                putExtra("oi_show_right_icon", true)
            } else {
                putExtra("oi_show_right_icon", false)
            }

            // Expanded scoreboard card (home | clock | away). The minute lives here, not on the pill.
            // vivo silently REJECTS a SuperX post with empty content text — SportsParser.statusOf()
            // returns "" when it can't identify a minute/HT/FT/extra-time marker in the source text.
            putExtra("title", competition)
            putExtra("text", clock.ifBlank { "Live" })
            putExtra("oi_extra1", home)
            putExtra("oi_extra2", homeScore.toString())
            putExtra("oi_extra3", away)
            putExtra("oi_extra4", awayScore.toString())

            // Collapsed pill adapts to whether the island is crowded:
            //  - alone:   "[crest] 1  ·camera·  1 [crest]"     (a score under each badge)
            //  - crowded: "[crest]  ·camera·  1-1 [crest]"     (full score on the RIGHT only — vivo
            //    clips a card's left edge when a reduced card sits beside it, which drops the home score)
            if (crowded) {
                // The reduced side-card's right slot is narrow — "1 - 1" (with spaces) truncates to "1".
                // Use a compact "1-1" so both scores survive.
                putExtra("oi_left_content", "")
                putExtra("oi_right_content", "$homeScore-$awayScore")
                putExtra("status_chip_text", "$homeScore-$awayScore")
            } else {
                putExtra("oi_left_content", homeScore.toString())
                putExtra("oi_right_content", awayScore.toString())
                putExtra("status_chip_text", awayScore.toString())
            }
            // Expanded card centre image: a composited home-vs-away crest when available.
            centerLogo?.let { putExtra("oi_large_icon", it) }

            // Card image / notification icon (a ball when we have crests, else an invisible left slot).
            putExtra("icon_res", if (homeLogo != null) R.drawable.ic_soccer else R.drawable.ic_transparent)
            putExtra("oi_force_show", true)
            putExtra("oi_dismiss_when_kill", true)
            putExtra("oi_keep_duration", 0)
            // A content intent is required for vivo to accept the SuperX post. Real casts pass the
            // source app's own intent; the in-app sample has none, so it falls back to opening us.
            putExtra(
                "click_resp",
                clickResp ?: PendingIntent.getActivity(
                    context, id, Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
        }
        context.startService(intent)

        // Any update for this match (extra time, a late correction) cancels a pending auto-clear.
        // Once the match reaches full time, schedule one: the source app's own notification often
        // just sits there unchanged after FT, so nothing would otherwise ever dismiss this card.
        handler.removeCallbacksAndMessages(tokenFor(id))
        if (clock == "FT") {
            handler.postDelayed({ clear(context, id) }, tokenFor(id), FULL_TIME_AUTO_CLEAR_MS)
        }
    }

    /**
     * Is the island crowded around *this* card — i.e. would its left edge get clipped?
     * True when another SuperX card of ours is currently up, OR a media session is actively
     * playing (its media capsule sits beside our card and pushes/clips it). In that case we put the
     * full "1-1" score on the right slot; otherwise we show a score under each crest.
     */
    private fun isCrowded(context: Context, currentId: Int): Boolean {
        if (PlaygroundService.activeCardIds.any { it != currentId }) return true
        return try {
            val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val listener = ComponentName(context, NotificationCastListener::class.java)
            msm.getActiveSessions(listener).any { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        } catch (e: SecurityException) {
            // Needs notification-listener access; if not granted, treat as not crowded.
            Log.w("SportsCard", "media session query denied: ${e.message}")
            false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Re-post any live card whose crowded state has flipped since it was last shown — e.g. the media
     * player was stopped, so a card that was showing the compact "1-1" can go back to a score under
     * each crest. Cheap: crests are cached and nothing is re-posted unless the decision changed.
     * Called from the notification listener's 1s poll.
     */
    fun refresh(context: Context) {
        for ((id, a) in lastPosts) {
            // Card no longer on the island (dismissed / cancelled elsewhere) — drop it, never resurrect.
            if (id !in PlaygroundService.activeCardIds) {
                lastPosts.remove(id)
                continue
            }
            if (isCrowded(context, id) != a.crowded) {
                post(
                    context, a.home, a.homeScore, a.away, a.awayScore, a.clock, a.competition, id,
                    a.homeLogo, a.awayLogo, a.centerLogo, a.clickResp,
                )
            }
        }
    }

    fun clear(context: Context, id: Int = MATCH_ID) {
        lastPosts.remove(id)
        handler.removeCallbacksAndMessages(tokenFor(id))
        clearTokens.remove(id)
        context.startService(
            Intent(context, PlaygroundService::class.java)
                .setAction(PlaygroundService.ACTION_CANCEL)
                .putExtra("id", id)
                .putExtra("oi_scene", "NAVIGATION"),
        )
    }
}
