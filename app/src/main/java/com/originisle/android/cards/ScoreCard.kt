package com.originisle.android.cards

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.originisle.android.MainActivity
import com.originisle.android.R
import com.originisle.android.island.OriginIslandConstants
import com.originisle.android.island.PlaygroundService
import com.originisle.android.service.NotificationCastListener
import com.originisle.android.sports.FlashscoreImages
import com.originisle.android.sports.FlashscoreParser
import com.originisle.android.sports.FlashscoreShare
import com.originisle.android.sports.FlashscoreParser.Format
import com.originisle.android.sports.FlashscoreParser.Kind
import com.originisle.android.sports.FlashscoreParser.Side
import com.originisle.android.sports.LiveMatch
import com.originisle.android.ui.PREFS_NAME
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * A live match from Flashscore on the island, any sport, on vivo's scoreboard template (3):
 * home name and score on the left, away on the right, Flashscore's own crests (or player photos)
 * in the middle, the match clock or period under them and the last scorer above.
 *
 * The pill depends on how the sport keeps score:
 *  - goals and points (football, hockey, basketball…): `[crest] 1 ·camera· 1 [crest]`, the full
 *    `1-1` on the right when the island is crowded. A goal pops `[crest] Goal! … Scorer 63'` for a
 *    few seconds first.
 *  - sets (tennis, volleyball…): the two names stacked on the left and their sets (or games per
 *    set) stacked on the right, the way Apple Sports shows tennis.
 *
 * Flashscore posts one notification per match (tagged with its match id) and replaces it on every
 * event; [onNotification] turns each one into an update of the same card.
 */
object ScoreCard {

    private const val TAG = "ScoreCard"

    /** Score apps rarely take their "Finished" notification down; keep the result up this long. */
    private const val FINAL_CLEAR_MS = 60_000L
    /** No news for this long: the final must have been missed, so the card goes. */
    private const val STALE_MS = 3 * 60 * 60 * 1000L
    /**
     * A "Finished" older than this (e.g. replayed after a reconnect) was already shown, and cleared
     * after [FINAL_CLEAR_MS].
     */
    private const val FINAL_FRESH_MS = 2 * 60 * 1000L
    /** How long a goal holds the pill, counted from when it's actually sent. */
    private const val MOMENT_MS = 5_000L
    /**
     * vivo's island ignores an update that comes less than ~10 s after the previous one (the data
     * changes, the island doesn't), so one card is never sent more often than this; anything in
     * between is held and sent at the next allowed moment, with whatever is newest by then.
     */
    private const val MIN_GAP_MS = 5_000L
    /**
     * vivo shows two island cards at most (one in front, one beside it) and never brings a third
     * forward, however fresh its news. So no more matches than this are up at once.
     */
    private const val MAX_UP = 2
    /** How often a picture that couldn't be read is tried again. */
    private const val PICTURE_RETRY_MS = 2_000L
    /** When the post that brings a new card forward is sent once more, in case vivo dropped it. */
    private const val PROMOTE_RETRY_MS = 7_000L


    private val handler = Handler(Looper.getMainLooper())
    private val matches = ConcurrentHashMap<String, LiveMatch>()

    private class Card(
        var images: FlashscoreImages.Images,
        var click: PendingIntent?,
        val id: Int,
    ) {
        var shownStatus = ""
        var crowded = false
        var postedAt = 0L
        var updatedAt = 0L
        /** A goal or red card to show on the pill, and when it went out (0 = not yet). */
        var moment: LiveMatch.Moment? = null
        var momentShownAt = 0L
        /** When this card was last sent to the island. */
        var lastSent = 0L
        var pendingForce = false
        /** The next send raises [node]: the real post that follows a new card's first one. */
        var promotePending = false
        var sendScheduled = false
        /** Raised on every event so vivo brings this match to the front; kept between events. */
        var node = 0
        /** Taken off the island by hand: not re-posted until the match's next event. */
        var dismissed = false
        /** When the picture was last tried, so one that can't be read yet isn't redone every second. */
        var pictureTriedAt = 0L
    }
    private val cards = ConcurrentHashMap<String, Card>()
    /** The last [Card.node] handed out: each event gets a higher one, even within the same second. */
    private var lastNode = 0

    /** Handler tokens per match: posting and removal match by reference, so one stable object each. */
    private val tokens = ConcurrentHashMap<String, Any>()
    private fun token(tag: String): Any = tokens.getOrPut(tag) { Any() }

    /** Stable card id per match (band 30000–34999). */
    fun castId(matchId: String): Int = (matchId.hashCode() and Int.MAX_VALUE) % 5000 + 30000

    enum class Result {
        /** Shown or updated on the island. */
        CAST,
        /** A match, but nothing to show: replayed from long ago, or the same update again. */
        OLD,
        /** Not a match (news, transfers…): handle it like any other app's notification. */
        NOT_A_MATCH,
    }

    fun onNotification(context: Context, sbn: StatusBarNotification): Result {
        val tag = sbn.tag ?: return Result.NOT_A_MATCH
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(NotificationCompat.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(NotificationCompat.EXTRA_TEXT)
            ?: extras.getCharSequence(NotificationCompat.EXTRA_BIG_TEXT))?.toString().orEmpty()
        val event = FlashscoreParser.parse(title, text) ?: return Result.NOT_A_MATCH

        val now = System.currentTimeMillis()
        val age = now - sbn.postTime
        // Left over in the shade (a listener reconnect replays all of them): not live any more.
        if (age > STALE_MS || (event.kind == Kind.FINISHED && age > FINAL_FRESH_MS)) return Result.OLD
        val match = matches.getOrPut(tag) { LiveMatch(tag) }
        // OriginOS redelivers an app's notifications whenever one of them changes: same post time,
        // nothing new. Except the picture: a match's first notification comes without it, and the
        // copy with it can carry the same post time — so that's checked before giving up on it.
        if (match.lastPost == sbn.postTime) {
            refreshPicture(context, sbn)
            return Result.OLD
        }

        val moment = match.apply(event, sbn.postTime)
        val repeat = match.isRepeat
        val card = cards.getOrPut(tag) {
            Card(FlashscoreImages.from(context, pictureOf(sbn), match.home, match.away), null, castId(tag))
        }
        // Flashscore posts first and adds the crests once they've downloaded: keep trying until
        // they're there (initials stand in meanwhile, or for good when it has no picture).
        if (needsPicture(card)) {
            pictureOf(sbn)?.let { card.images = FlashscoreImages.from(context, it, match.home, match.away) }
        }
        card.click = sbn.notification.contentIntent ?: card.click
        card.updatedAt = now
        if (repeat && moment == null) {
            // The same event posted again (crests downloaded): update quietly.
            if (!card.dismissed) post(context, tag, force = false)
            return Result.CAST
        }
        card.dismissed = false
        show(context, tag, moment)
        prune(now)
        return Result.CAST
    }

    private fun needsPicture(card: Card): Boolean =
        !card.images.real || card.images.home == null || card.images.away == null || card.images.both == null

    /**
     * If this match's card is still on stand-ins (initials) and the notification now has its
     * picture, use it. A match's first notification comes without the picture and Flashscore posts
     * it again a fraction of a second later with it; this is called for that copy however it
     * arrives — as a redelivery, or from the listener's 1 s poll.
     */
    fun refreshPicture(context: Context, sbn: StatusBarNotification) {
        val tag = sbn.tag ?: return
        val card = cards[tag] ?: return
        val now = System.currentTimeMillis()
        // Not once per post time: Flashscore can swap a picture we can't read yet for the real one
        // and keep the post time, so a failed try is simply retried a little later.
        if (!needsPicture(card) || now - card.pictureTriedAt < PICTURE_RETRY_MS) return
        val match = matches[tag] ?: return
        card.pictureTriedAt = now
        val picture = pictureOf(sbn) ?: return
        val images = FlashscoreImages.from(context, picture, match.home, match.away)
        if (!images.real) return
        card.images = images
        if (!card.dismissed) post(context, tag, force = false)
    }

    /** The source notification went away (swiped, or opened): so does the card. */
    fun onRemoved(context: Context, sbn: StatusBarNotification) {
        val tag = sbn.tag ?: return
        if (cards.containsKey(tag) || matches.containsKey(tag)) clear(context, tag)
    }

    /**
     * Once a second from the listener's poll: move the match clock on, follow the island getting
     * crowded or not, and drop cards that went quiet. Re-posts only when what's shown changes, and
     * without raising [Card.node], so a clock ticking on one match never takes the front from the
     * match that last had news.
     */
    fun tick(context: Context) {
        val now = System.currentTimeMillis()
        for ((tag, card) in cards) {
            if (now - card.updatedAt > STALE_MS) { clear(context, tag); continue }
            // Dismissed from the island by hand: leave it down until the next event (but keep it,
            // so the final or staleness still takes it away properly).
            if (!card.dismissed && card.postedAt > 0 && now - card.postedAt > 5_000 &&
                card.id !in PlaygroundService.activeCardIds
            ) {
                card.dismissed = true
            }
            if (card.dismissed || card.moment != null) continue
            val match = matches[tag] ?: continue
            if (match.status(now) != card.shownStatus || isCrowded(context, card.id) != card.crowded) {
                post(context, tag, force = false)
            }
        }
    }

    private fun show(context: Context, tag: String, moment: LiveMatch.Moment?) {
        val match = matches[tag] ?: return
        val card = cards[tag] ?: return
        handler.removeCallbacksAndMessages(token(tag))
        // With several matches up, the one that just had news goes in front. Seconds, so it keeps
        // going up across restarts (vivo only moves a card forward when the value rises).
        card.node = maxOf(((System.currentTimeMillis() / 1000) - 1_700_000_000L).toInt(), lastNode + 1)
        lastNode = card.node
        makeRoom(context, tag)
        card.moment = moment.takeIf { match.format != Format.SETS }
        card.momentShownAt = 0
        post(context, tag, force = true)
        // With a moment, the final's clear waits for it to have been seen (see send).
        if (card.moment == null) scheduleFinalClear(context, tag)
    }

    /**
     * Takes down the match with the oldest news while [MAX_UP] others are up, so [keep] isn't the
     * hidden third. A match taken down is kept and comes back with its next event.
     */
    private fun makeRoom(context: Context, keep: String) {
        while (true) {
            val up = cards.entries.filter { it.key != keep && !it.value.dismissed }
            if (up.size < MAX_UP) return
            val oldest = up.minByOrNull { it.value.node } ?: return
            stepDown(context, oldest.value)
        }
    }

    private fun stepDown(context: Context, card: Card) {
        handler.removeCallbacksAndMessages(card)
        card.dismissed = true
        card.sendScheduled = false
        card.pendingForce = false
        card.promotePending = false
        card.moment = null
        // Back as a new card next time, so it's brought forward the way new cards are.
        card.lastSent = 0
        card.postedAt = 0
        cancel(context, card.id)
    }

    private fun scheduleFinalClear(context: Context, tag: String) {
        if (matches[tag]?.finished != true) return
        handler.postDelayed({ clear(context, tag) }, token(tag), FINAL_CLEAR_MS)
    }

    /** Ask for this card to be (re)sent: now if vivo will take it, else at the next allowed moment. */
    private fun post(context: Context, tag: String, force: Boolean) {
        val card = cards[tag] ?: return
        if (force) card.pendingForce = true
        val wait = card.lastSent + MIN_GAP_MS - System.currentTimeMillis()
        if (wait <= 0) {
            send(context, tag)
            return
        }
        if (card.sendScheduled) return
        card.sendScheduled = true
        handler.postDelayed({
            card.sendScheduled = false
            if (cards[tag] === card) send(context, tag)
        }, card, wait)
    }

    private fun send(context: Context, tag: String) {
        val match = matches[tag] ?: return
        val card = cards[tag] ?: return
        // Off the island (by hand, or to make room): nothing goes out until its next event.
        if (card.dismissed) return
        val now = System.currentTimeMillis()
        var force = card.pendingForce
        card.pendingForce = false
        val first = card.lastSent == 0L
        card.lastSent = now
        if (card.promotePending) {
            card.promotePending = false
            card.node += 1
            lastNode = maxOf(lastNode, card.node)
            // vivo sometimes drops an update this soon after a card was created: raise it once more
            // a little later, unless another match has had news since.
            handler.postDelayed({
                if (cards[tag] === card && !card.dismissed && card.node == lastNode) {
                    card.node += 1
                    lastNode = card.node
                    post(context, tag, force = false)
                }
            }, card, PROMOTE_RETRY_MS)
        }
        // The goal moment: shown once, for MOMENT_MS from when it actually goes out, then the
        // normal pill (and, after a final, the clear).
        var moment = card.moment
        if (first) {
            // vivo won't put a brand-new card in front on its first post: only an update that
            // raises the card's node does. So this first post just creates it, quietly, and the
            // real one — popping, a node higher, with the goal if there is one — follows at the
            // next moment vivo takes an update. By then Flashscore's picture is in too.
            force = false
            moment = null
            card.promotePending = true
            post(context, tag, force = true)
        } else if (moment != null) {
            if (card.momentShownAt == 0L) {
                card.momentShownAt = now
                handler.postDelayed({
                    if (cards[tag] !== card) return@postDelayed
                    card.moment = null
                    post(context, tag, force = false)
                    scheduleFinalClear(context, tag)
                }, token(tag), MOMENT_MS)
            } else if (now - card.momentShownAt >= MOMENT_MS) {
                moment = null
            }
        }
        // Never half a goal: no scorer, no moment — the normal crests and score instead.
        if (moment == LiveMatch.Moment.GOAL && match.scorer == null) moment = null
        val status = match.status(now)
        val crowded = isCrowded(context, card.id)
        card.shownStatus = status
        card.crowded = crowded
        card.postedAt = now

        val sets = match.format == Format.SETS
        val (hs, aws) = if (sets) match.sets() else match.homeScore to match.awayScore
        val homeName = FlashscoreParser.shortName(match.home)
        val awayName = FlashscoreParser.shortName(match.away)
        val images = card.images

        val intent = Intent(context, PlaygroundService::class.java).apply {
            action = PlaygroundService.ACTION_START
            putExtra("id", card.id)
            // Must stay ongoing: OriginOS takes a non-ongoing SuperX card down after ~5 s.
            putExtra("is_ongoing", true)
            putExtra("oi_scene", "NAVIGATION")
            putExtra("oi_template", OriginIslandConstants.TEMPLATE_TEXT_SYMMETRY) // 3
            // Card header: the sport, and the clock or period. vivo rejects an empty content text.
            putExtra("title", sportName(match))
            putExtra("text", status.ifBlank { "Live" })
            putExtra("oi_extra1", homeName)
            putExtra("oi_extra2", hs.toString())
            putExtra("oi_extra3", awayName)
            putExtra("oi_extra4", aws.toString())
            match.topLine()?.let { putExtra("oi_mid_top", it) }
            images.both?.let { putExtra("oi_large_icon", it) }
            putExtra(
                "icon_res",
                when {
                    images.home == null && !sets -> R.drawable.ic_transparent // no crest: keep the slot empty
                    match.isFootball -> R.drawable.ic_soccer
                    else -> R.mipmap.ic_launcher_round
                },
            )
            putExtra("oi_force_show", force)
            putExtra("oi_new_node", card.node)
            putExtra("oi_dismiss_when_kill", true)
            putExtra("oi_keep_duration", 0)
            putExtra("click_resp", card.click ?: openApp(context, card.id))

            when {
                moment == LiveMatch.Moment.GOAL -> {
                    // A goal looks the same whoever scored: the ball on the left, the scorer on the
                    // right, no crests and no score (those are the normal pill's).
                    putExtra("oi_left_icon", emojiIcon(match.emoji ?: "⚽"))
                    putExtra("oi_left_content", "")
                    putExtra("oi_right_template", OriginIslandConstants.TEMPLATE_RIGHT_ISLAND_TEXT_ICON) // 4
                    putExtra("oi_show_right_icon", false)
                    val right = match.scorer.orEmpty()
                    putExtra("oi_right_content", right)
                    putExtra("status_chip_text", right)
                }
                moment != null -> {
                    // A red card: both crests stay where they always are (home left, away right)
                    // and "Red card" goes on that team's side, the score on the other.
                    putExtra("oi_right_template", OriginIslandConstants.TEMPLATE_RIGHT_ISLAND_TEXT_ICON) // 4
                    images.home?.let { putExtra("oi_left_icon", it) }
                    if (images.away != null) {
                        putExtra("oi_right_icon", images.away)
                        putExtra("oi_show_right_icon", true)
                    } else {
                        putExtra("oi_show_right_icon", false)
                    }
                    val (left, right) = when {
                        // The left edge is clipped when crowded: the score, marked, on the right.
                        crowded -> "" to "🟥 $hs-$aws"
                        match.highlightSide == Side.AWAY -> "$hs-$aws" to "Red card"
                        else -> "Red card" to "$hs-$aws"
                    }
                    putExtra("oi_left_content", left)
                    putExtra("oi_right_content", right)
                    putExtra("status_chip_text", right)
                }
                sets -> {
                    // Apple-style tennis: both names stacked, their sets (or games) stacked opposite.
                    putExtra("oi_left_content", "")
                    (images.both ?: images.home)?.let { putExtra("oi_left_icon", it) }
                    putExtra("oi_right_template", OriginIslandConstants.TEMPLATE_RIGHT_ISLAND_DOUBLE_LINE) // 7
                    val games = match.periodScores
                    var lines = if (games.isNotEmpty()) {
                        arrayListOf(games.joinToString(" ") { "${it.first}" }, games.joinToString(" ") { "${it.second}" })
                    } else arrayListOf("$hs", "$aws")
                    if (crowded) {
                        // The left edge is clipped when crowded: the names move next to their scores.
                        lines = arrayListOf("$homeName ${lines[0]}", "$awayName ${lines[1]}")
                    } else {
                        putStringArrayListExtra("oi_left_doubleline", arrayListOf(homeName, awayName))
                    }
                    putStringArrayListExtra("oi_right_doubleline", lines)
                    putExtra("oi_right_content", "")
                    putExtra("status_chip_text", "$hs-$aws")
                    putExtra("oi_show_right_icon", false)
                }
                else -> {
                    putExtra("oi_right_template", OriginIslandConstants.TEMPLATE_RIGHT_ISLAND_TEXT_ICON) // 4
                    images.home?.let { putExtra("oi_left_icon", it) }
                    if (images.away != null) {
                        putExtra("oi_right_icon", images.away)
                        putExtra("oi_show_right_icon", true)
                    } else {
                        putExtra("oi_show_right_icon", false)
                    }
                    // Crowded: vivo clips the left edge when another card sits beside this one,
                    // dropping the home score — so the full score goes on the right, compact.
                    if (crowded) {
                        putExtra("oi_left_content", "")
                        putExtra("oi_right_content", "$hs-$aws")
                        putExtra("status_chip_text", "$hs-$aws")
                    } else {
                        putExtra("oi_left_content", hs.toString())
                        putExtra("oi_right_content", aws.toString())
                        putExtra("status_chip_text", aws.toString())
                    }
                }
            }
        }
        runCatching { context.startService(intent) }.onFailure { Log.w(TAG, "post failed", it) }
    }

    // --- shared from Flashscore --------------------------------------------------------------

    private const val SHARED_KEY = "score_shared_matches"

    /**
     * A match shared from Flashscore ("Show on island" in its share sheet): up on the island right
     * away with the names and score the share had, crests from earlier matches or initials. Its
     * alerts — if the match is followed in Flashscore — update the same card, since the share's id
     * is the one they're tagged with. Remembered, so a restart brings it back.
     */
    fun fromShare(context: Context, shared: FlashscoreShare.Shared) {
        show(context, shared)
        remember(context, shared)
    }

    private fun show(context: Context, shared: FlashscoreShare.Shared) {
        val tag = shared.matchId
        val match = matches.getOrPut(tag) { LiveMatch(tag) }
        match.seed(shared.home, shared.away, shared.homeScore, shared.awayScore)
        val card = cards.getOrPut(tag) {
            Card(FlashscoreImages.from(context, null, match.home, match.away), null, castId(tag))
        }
        if (card.click == null) {
            // Tapping the card opens the match in Flashscore, as the link does.
            card.click = PendingIntent.getActivity(
                context, card.id,
                Intent(Intent.ACTION_VIEW, Uri.parse(shared.link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
        card.updatedAt = System.currentTimeMillis()
        card.dismissed = false
        show(context, tag, null)
    }

    /** Shared matches back on the island after a restart, unless they're done or already up. */
    fun restoreShared(context: Context) {
        val now = System.currentTimeMillis()
        val kept = JSONArray()
        val saved = runCatching {
            JSONArray(context.getSharedPreferences(PREFS_NAME, 0).getString(SHARED_KEY, "[]"))
        }.getOrDefault(JSONArray())
        // Oldest share first, so the latest ones are the ones left up.
        val entries = (0 until saved.length()).mapNotNull { saved.optJSONObject(it) }.sortedBy { it.optLong("at") }
        for (o in entries) {
            if (now - o.optLong("at") > STALE_MS) continue
            kept.put(o)
            val id = o.optString("id")
            if (cards.containsKey(id) || matches[id]?.finished == true) continue
            show(
                context,
                FlashscoreShare.Shared(
                    id, o.optString("home"), o.optString("away"),
                    o.optInt("hs", -1).takeIf { it >= 0 }, o.optInt("as", -1).takeIf { it >= 0 },
                    o.optString("link"),
                ),
            )
        }
        context.getSharedPreferences(PREFS_NAME, 0).edit().putString(SHARED_KEY, kept.toString()).apply()
    }

    private fun remember(context: Context, shared: FlashscoreShare.Shared) {
        editShared(context) { list ->
            list.removeAll { it.optString("id") == shared.matchId }
            list += JSONObject()
                .put("id", shared.matchId).put("home", shared.home).put("away", shared.away)
                .put("hs", shared.homeScore ?: -1).put("as", shared.awayScore ?: -1)
                .put("link", shared.link).put("at", System.currentTimeMillis())
        }
    }

    private fun forget(context: Context, tag: String) {
        editShared(context) { list -> list.removeAll { it.optString("id") == tag } }
    }

    private fun editShared(context: Context, change: (MutableList<JSONObject>) -> Unit) {
        val prefs = context.getSharedPreferences(PREFS_NAME, 0)
        val arr = runCatching { JSONArray(prefs.getString(SHARED_KEY, "[]")) }.getOrDefault(JSONArray())
        val list = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.toMutableList()
        val before = list.size
        change(list)
        if (before == 0 && list.isEmpty()) return
        prefs.edit().putString(SHARED_KEY, JSONArray(list).toString()).apply()
    }

    fun clear(context: Context, tag: String) {
        forget(context, tag)
        val card = cards.remove(tag)
        card?.let { handler.removeCallbacksAndMessages(it) }
        handler.removeCallbacksAndMessages(token(tag))
        tokens.remove(tag)
        cancel(context, card?.id ?: castId(tag))
    }

    private fun cancel(context: Context, id: Int) {
        runCatching {
            context.startService(
                Intent(context, PlaygroundService::class.java)
                    .setAction(PlaygroundService.ACTION_CANCEL)
                    .putExtra("id", id)
                    .putExtra("oi_scene", "NAVIGATION"),
            )
        }.onFailure { Log.w(TAG, "cancel failed", it) }
    }

    /** Matches are kept a while after their card goes, so a redelivered notification stays ignored. */
    private fun prune(now: Long) {
        matches.entries.removeAll { (tag, m) -> !cards.containsKey(tag) && now - m.lastPost > 6 * 60 * 60 * 1000L }
    }

    private val emojiIcons = ConcurrentHashMap<String, Icon>()

    /** An emoji as a pill icon, so it shows even where vivo hides the pill's left text. */
    private fun emojiIcon(emoji: String): Icon = emojiIcons.getOrPut(emoji) {
        val size = 96
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size * 0.8f
            textAlign = android.graphics.Paint.Align.CENTER
        }
        android.graphics.Canvas(b).drawText(emoji, size / 2f, size / 2f - (p.descent() + p.ascent()) / 2, p)
        Icon.createWithBitmap(b)
    }

    private fun pictureOf(sbn: StatusBarNotification): Icon? =
        sbn.notification.getLargeIcon()
            ?: when (val large = sbn.notification.extras.get(NotificationCompat.EXTRA_LARGE_ICON)) {
                is Icon -> large
                is Bitmap -> Icon.createWithBitmap(large)
                else -> null
            }

    private fun sportName(m: LiveMatch): String = when {
        m.emoji == "⚽" || m.isFootball -> "Football"
        m.emoji == "🏒" || m.periodWord == "period" -> "Hockey"
        m.emoji == "🏀" || m.periodWord == "quarter" -> "Basketball"
        m.emoji == "🎾" -> "Tennis"
        m.emoji == "🏐" -> "Volleyball"
        m.emoji == "⚾" || m.periodWord == "inning" -> "Baseball"
        m.emoji == "🏈" -> "American football"
        m.emoji == "🤾" -> "Handball"
        m.emoji == "🏉" -> "Rugby"
        m.format == Format.SETS && m.home != FlashscoreParser.shortName(m.home) -> "Tennis"
        m.format == Format.SETS -> "Volleyball"
        else -> "Live"
    }

    /**
     * Is the island crowded around this card — would its left edge get clipped? True when another
     * of our cards is up, or media is playing (its capsule sits beside ours).
     */
    private fun isCrowded(context: Context, currentId: Int): Boolean {
        if (PlaygroundService.activeCardIds.any { it != currentId }) return true
        return try {
            val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val listener = ComponentName(context, NotificationCastListener::class.java)
            msm.getActiveSessions(listener).any { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        } catch (e: Exception) {
            false
        }
    }

    private fun openApp(context: Context, id: Int): PendingIntent = PendingIntent.getActivity(
        context, id, Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    // --- samples ----------------------------------------------------------------------------

    /**
     * A made-up match for the Home samples: the [lines] are fed through exactly as if Flashscore
     * had posted them, one minute apart and ending now, so the sample shows the real card.
     */
    fun demo(context: Context, matchId: String, id: Int, title: String, lines: List<String>, images: FlashscoreImages.Images) {
        val now = System.currentTimeMillis()
        val match = LiveMatch(matchId)
        var moment: LiveMatch.Moment? = null
        lines.forEachIndexed { i, line ->
            val e = FlashscoreParser.parse(title, line) ?: return@forEachIndexed
            moment = match.apply(e, now - (lines.size - 1 - i) * 60_000L)
        }
        matches[matchId] = match
        cards[matchId] = Card(images, null, id).apply { updatedAt = now }
        show(context, matchId, moment)
    }
}
