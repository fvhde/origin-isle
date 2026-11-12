package com.originisle.android.sports

import com.originisle.android.sports.FlashscoreParser.Event
import com.originisle.android.sports.FlashscoreParser.Format
import com.originisle.android.sports.FlashscoreParser.Kind
import com.originisle.android.sports.FlashscoreParser.Side

/**
 * One match as the island shows it, built up from Flashscore's notifications. Flashscore only
 * says what just happened ("End of 2nd quarter. 42 - 36"), so everything that lasts — the period
 * we're in, quarter-by-quarter scores, the last scorer, where the match clock was — is kept here.
 */
class LiveMatch(val id: String) {

    enum class Moment { GOAL, RED_CARD }

    var home = ""
    var away = ""
    var homeScore = 0
    var awayScore = 0
    var format: Format? = null
    /** The sport emoji Flashscore used on goals (⚽ 🏒 …), once seen. */
    var emoji: String? = null
    /** "half", "quarter", "period", "set", "inning". */
    var periodWord: String? = null
    var period = 0
    var inBreak = false
    var started = false
    var finished = false
    /** Points (or games) per finished period, as reported at each period's end. */
    val periodScores = mutableListOf<Pair<Int, Int>>()
    /** The last thing worth a line on the card: "⚽ Magalhães 63'", "🟥 Ramos". */
    var highlight: String? = null
    /** Which side the last goal / red card was for. */
    var highlightSide: Side? = null
    /** Who scored the last goal ("Magalhães"), once Flashscore has said. */
    var scorer: String? = null
    /** Post time of the last notification applied (redeliveries carry the same one). */
    var lastPost = 0L

    /**
     * Whether the last notification applied was the same event as the one before. Flashscore
     * posts every event twice or three times: first the bare event, then again once the crests
     * have downloaded, and for a goal once more when the scorer is known.
     */
    var isRepeat = false
        private set
    private var lastEventKey = ""
    /** When the last goal came in, so a scorer named much later doesn't pop it again. */
    private var goalAt = 0L

    // Match clock (football): the minute shown at [clockAt], and which half that was in.
    private var clockAt = 0L
    private var clockMinute = 0
    private var clockHalf = 0

    val isFootball: Boolean
        get() = emoji == "⚽" || (format == Format.GOALS && periodWord in setOf(null, "half") && emoji == null)

    /**
     * A match shared from Flashscore, before (or without) any of its alerts: names and, if the
     * share had it, the score. Alerts that have already come in win over the share.
     */
    fun seed(home: String, away: String, homeScore: Int?, awayScore: Int?) {
        if (this.home.isEmpty()) this.home = home
        if (this.away.isEmpty()) this.away = away
        started = true
        if (lastPost == 0L && homeScore != null && awayScore != null) {
            this.homeScore = homeScore
            this.awayScore = awayScore
        }
        if (format == null && FlashscoreParser.looksLikePlayers(home, away)) format = Format.SETS
    }

    /** Apply one notification. Returns a moment worth popping the island for, if any. */
    fun apply(e: Event, at: Long): Moment? {
        if (home.isEmpty()) home = e.home
        if (away.isEmpty()) away = e.away
        format = e.format ?: format
        emoji = e.emoji ?: emoji
        periodWord = e.periodWord ?: periodWord
        if (e.homeScore != null && e.awayScore != null) {
            homeScore = e.homeScore
            awayScore = e.awayScore
        }
        lastPost = at
        val key = "${e.kind}|${e.homeScore}|${e.awayScore}|${e.period}"
        isRepeat = key == lastEventKey
        lastEventKey = key
        var moment: Moment? = null
        // A goal's moment shows the scorer, so it waits for Flashscore to name them: on the goal
        // itself, or on the copy it posts a little later with the name.
        var scorerLearned = false
        when (e.kind) {
            Kind.START -> {
                started = true; inBreak = false; period = maxOf(period, 1)
                setClock(at, 1, 1)
            }
            Kind.GOAL -> {
                started = true; inBreak = false
                highlightSide = e.side
                if (!isRepeat) goalAt = at
                val named = e.player?.let(::shortPlayer)
                scorerLearned = named != null && (!isRepeat || scorer == null) && at - goalAt < SCORER_WAIT_MS
                if (!isRepeat || named != null) scorer = named
                val who = named ?: sideName(e.side)
                highlight = listOfNotNull(emoji ?: "⚽", who.ifBlank { null }, e.minute?.takeIf { e.player != null })
                    .joinToString(" ")
                e.minute?.let { m -> parseMinute(m)?.let { (min, half) -> setClock(at, min, half) } }
                if (scorerLearned) moment = Moment.GOAL
            }
            Kind.GOALSCORER -> {
                started = true
                e.side?.let { highlightSide = it }
                e.player?.let { scorerLearned = scorer == null; scorer = shortPlayer(it) }
                highlight = listOfNotNull(emoji ?: "⚽", e.player?.let(::shortPlayer), e.minute).joinToString(" ")
                e.minute?.let { m -> parseMinute(m)?.let { (min, half) -> setClock(at, min, half) } }
                if (scorerLearned) moment = Moment.GOAL
            }
            Kind.RED_CARD -> {
                // "25' Red card - Nyvelados": Flashscore names the team, not the player.
                highlightSide = e.side ?: when (e.player) {
                    home -> Side.HOME
                    away -> Side.AWAY
                    else -> null
                }
                highlight = listOfNotNull("🟥", e.player?.let(::shortPlayer) ?: sideName(e.side), e.minute).joinToString(" ")
                moment = Moment.RED_CARD
            }
            Kind.HALF_TIME -> { started = true; inBreak = true; period = maxOf(period, 1) }
            Kind.PERIOD_END -> {
                started = true; inBreak = true
                val n = e.period ?: (period.coerceAtLeast(1))
                period = n
                recordPeriod(n)
            }
            Kind.PERIOD_START -> {
                started = true; inBreak = false
                period = e.period ?: (period + 1)
                if (isFootball && period == 2) setClock(at, 46, 2)
            }
            Kind.FINISHED -> { finished = true; inBreak = false }
            Kind.SCORE -> started = true
        }
        // The same event again (now with its crests, or its scorer): an update, not a new moment.
        return moment.takeUnless { isRepeat && !scorerLearned }
    }

    /** The current period's score at its end, as a difference from the ones before it. */
    private fun recordPeriod(n: Int) {
        if (periodScores.size != n - 1) return
        if (format == Format.SETS) {
            // A set's end reports games (6 - 4) or the sets count (1 - 0); keep only games.
            if (maxOf(homeScore, awayScore) > 3) periodScores += homeScore to awayScore
            return
        }
        val h = homeScore - periodScores.sumOf { it.first }
        val a = awayScore - periodScores.sumOf { it.second }
        if (h >= 0 && a >= 0) periodScores += h to a
    }

    private fun setClock(at: Long, minute: Int, half: Int) {
        clockAt = at; clockMinute = minute; clockHalf = half
    }

    private fun sideName(side: Side?): String = when (side) {
        Side.HOME -> home
        Side.AWAY -> away
        null -> ""
    }

    /** "67'", "45+2'", or null when the clock isn't known (or is stopped). */
    fun minute(now: Long): String? {
        if (!isFootball || clockHalf == 0 || inBreak || finished) return null
        val m = clockMinute + ((now - clockAt).coerceAtLeast(0) / 60_000).toInt()
        val cap = if (clockHalf == 1) 45 else 90
        return if (m > cap) "$cap+${m - cap}'" else "$m'"
    }

    /** Sets won, for set sports: from the reported sets, or counted from the games. */
    fun sets(): Pair<Int, Int> =
        if (format == Format.SETS && periodScores.isNotEmpty() && maxOf(homeScore, awayScore) > 3) {
            periodScores.count { it.first > it.second } to periodScores.count { it.second > it.first }
        } else homeScore to awayScore

    /** Short status for the card's middle line and the pill. */
    fun status(now: Long): String {
        if (finished) return if (isFootball) "FT" else "Final"
        val word = periodWord
        return when {
            format == Format.SETS -> {
                // Flashscore only says when a set ends, and the next one starts right away.
                val (h, a) = sets()
                "Set ${h + a + 1}"
            }
            word == "quarter" -> when {
                period == 0 -> "Live"
                inBreak && period == 2 -> "Half-time"
                inBreak -> "End Q$period"
                else -> "Q$period"
            }
            word == "period" -> if (period == 0) "Live" else if (inBreak) "End P$period" else "P$period"
            word == "inning" -> if (period == 0) "Live" else "Inning $period"
            isFootball -> when {
                inBreak && period <= 1 -> "HT"
                else -> minute(now) ?: if (period >= 2) "2nd half" else "Live"
            }
            else -> if (inBreak) "Break" else "Live"
        }
    }

    /** The line above the middle: per-period scores for points and sets, else the last highlight. */
    fun topLine(): String? = when {
        format == Format.SETS && periodScores.isNotEmpty() ->
            periodScores.joinToString("  ") { "${it.first}-${it.second}" }
        format == Format.POINTS && periodScores.isNotEmpty() ->
            periodScores.joinToString(" · ") { "${it.first}-${it.second}" }
        else -> highlight
    }

    companion object {
        /** A scorer named later than this after the goal updates the card without popping it. */
        private const val SCORER_WAIT_MS = 3 * 60_000L

        /** "Gabriel Magalhães" -> "Magalhães", "Mbappé K." -> "Mbappé": the line above the score is narrow. */
        fun shortPlayer(name: String): String {
            val n = FlashscoreParser.shortName(name.trim())
            return if (n.length > 12 && n.contains(' ')) n.substringAfterLast(' ') else n
        }

        /** "63'" -> (63, 1); "45+2'" -> (47, 1); "90+3'" -> (93, 2); "78'" -> (78, 2). */
        fun parseMinute(s: String): Pair<Int, Int>? {
            val m = Regex("""(\d{1,3})(?:\+(\d{1,2}))?""").find(s) ?: return null
            val base = m.groupValues[1].toInt()
            val extra = m.groupValues[2].toIntOrNull() ?: 0
            val half = if (base <= 45) 1 else 2
            return (base + extra) to half
        }
    }
}
