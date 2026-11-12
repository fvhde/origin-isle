package com.originisle.android.sports

/**
 * Reads a Flashscore match notification. The whole Livesport family (Flashscore and its regional
 * apps, LiveSport.cz, Diretta…) posts the same shape, one notification per match, tagged with the
 * match id and replaced on every event:
 *
 *  - title: `Home - Away`, or `Home - Away 81 - 105` once it's finished
 *  - text:  `⚽ Goal! [1] - 1`, `End of 2nd quarter. 42 - 36`, `Finished. [2] - 1` — the side in
 *    brackets is the one that just scored (or won). With Flashscore's "Goalscorer" alert on, a
 *    goal carries the minute and the scorer too: `⚽ 18' Goal! [3] - 2 Marcos MV`.
 *
 * The numbers and brackets don't depend on the app's language; the words around them do, so an
 * event this doesn't recognise still updates the score, it just isn't named.
 */
object FlashscoreParser {

    enum class Side { HOME, AWAY }

    enum class Kind { START, GOAL, GOALSCORER, RED_CARD, HALF_TIME, PERIOD_START, PERIOD_END, FINISHED, SCORE }

    /** How the sport keeps score — that, not the sport itself, decides the card. */
    enum class Format { GOALS, POINTS, SETS }

    data class Event(
        val home: String,
        val away: String,
        val homeScore: Int?,
        val awayScore: Int?,
        /** The bracketed side: who just scored, or who won. */
        val side: Side?,
        val kind: Kind,
        /** 2 in "End of 2nd quarter", "Start of 2nd half". */
        val period: Int?,
        /** "quarter", "set", "period", "half", "inning" — the word Flashscore used, if any. */
        val periodWord: String?,
        /** Null when this notification alone doesn't say. */
        val format: Format?,
        /** The sport emoji Flashscore leads with on goals, if any. */
        val emoji: String?,
        /** Goal scorer or sent-off player. */
        val player: String?,
        /** "67'" or "90+2'". */
        val minute: String?,
    )

    private val TITLE = Regex("""^(.+?) - (.+?)(?: (\d{1,3}) - (\d{1,3}))?$""")
    private val SCORE = Regex("""(\[)?(\d{1,3})(])?\s*-\s*(\[)?(\d{1,3})(])?""")
    private val MINUTE = Regex("""(\d{1,3}(?:\+\d{1,2})?)\s*['’]""")
    private val PERIOD = Regex(
        """(\d)\s*(?:st|nd|rd|th|er|re|e|ème|eme)?\.?\s+(quarter|period|set|half|inning|quart-temps|quart|période|periode|manche|mi-temps)""" +
            """|(quarter|period|set|inning|quart-temps|période|manche)\s+(\d)""",
        RegexOption.IGNORE_CASE,
    )

    private val EMOJI_FORMAT = linkedMapOf(
        "⚽" to Format.GOALS, "🏒" to Format.GOALS, "🤾" to Format.GOALS, "🏑" to Format.GOALS, "🤽" to Format.GOALS,
        "🏀" to Format.POINTS, "🏈" to Format.POINTS, "⚾" to Format.POINTS, "🏉" to Format.POINTS, "🏏" to Format.POINTS,
        "🎾" to Format.SETS, "🏐" to Format.SETS, "🏓" to Format.SETS, "🏸" to Format.SETS, "🎯" to Format.SETS,
    )

    /** "Etcheverry T. M.", "Dahlin M." — a tennis-style player name: surname then initials. */
    private val PLAYER_NAME = Regex("""^\S.*\s(?:\p{Lu}\.\s?)+$""")

    fun parse(title: String, text: String): Event? {
        val t = TITLE.find(title.trim()) ?: return null
        val home = t.groupValues[1].trim()
        val away = t.groupValues[2].trim()
        if (home.isEmpty() || away.isEmpty()) return null

        val body = text.trim()
        val lower = body.lowercase()
        // The last "a - b" in the text is the score; the title carries it too once it's over.
        val s = SCORE.findAll(body).lastOrNull()
        val homeScore = s?.groupValues?.get(2)?.toIntOrNull() ?: t.groupValues[3].toIntOrNull()
        val awayScore = s?.groupValues?.get(5)?.toIntOrNull() ?: t.groupValues[4].toIntOrNull()
        val side = when {
            s == null -> null
            s.groupValues[1].isNotEmpty() || s.groupValues[3].isNotEmpty() -> Side.HOME
            s.groupValues[4].isNotEmpty() || s.groupValues[6].isNotEmpty() -> Side.AWAY
            else -> null
        }

        val p = PERIOD.find(body)
        val period = p?.let { (it.groupValues[1].ifEmpty { it.groupValues[4] }).toIntOrNull() }
        // Volleyball's "End of set." has no number, and no emoji either: the word alone says sets.
        val periodWord = p?.let { normalizeWord(it.groupValues[2].ifEmpty { it.groupValues[3] }) }
            ?: BARE_SET.find(body)?.let { normalizeWord(it.groupValues[1]) }

        val minute = MINUTE.find(body)?.groupValues?.get(1)?.let { "$it'" }
        val emoji = EMOJI_FORMAT.keys.firstOrNull { body.contains(it) }

        val kind = when {
            listOf("finished", "final result", "terminé", "termine", "fin du match", "after penalties", "after extra time")
                .any { lower.contains(it) } -> Kind.FINISHED
            lower.contains("red card") || lower.contains("carton rouge") || body.contains("🟥") -> Kind.RED_CARD
            listOf("goalscorer", "goal scorer", "buteur", "scorer").any { lower.contains(it) } -> Kind.GOALSCORER
            listOf("half-time", "half time", "halftime", "mi-temps").any { lower.contains(it) } &&
                !lower.contains("start") && !lower.contains("début") && !lower.contains("2nd half") -> Kind.HALF_TIME
            lower.contains("end of") || lower.startsWith("fin de") || lower.startsWith("fin du") -> Kind.PERIOD_END
            lower.contains("start of") || lower.contains("début de") || lower.contains("début du") ||
                (period != null && lower.contains("start")) -> Kind.PERIOD_START
            listOf("match started", "has started", "kick-off", "kick off", "coup d'envoi", "début du match", "started")
                .any { lower.contains(it) } -> Kind.START
            Regex("""\b(goal|but|touchdown|try)\b""").containsMatchIn(lower) -> Kind.GOAL
            // "⚽ Mbappé (67')": a name and a minute with no other word is the scorer line.
            minute != null && emoji != null -> Kind.GOALSCORER
            else -> Kind.SCORE
        }

        // Whatever follows the score is the player ("… [3] - 2 Marcos MV").
        val tail = s?.let { body.substring(it.range.last + 1) }
            ?.replace(Regex("""[^\p{L}\p{M} .'’-]"""), " ")?.replace(Regex("""\s+"""), " ")?.trim(' ', '-')
            ?.takeIf { it.length >= 2 && it.any(Char::isLetter) }
        val player = when (kind) {
            Kind.GOAL -> tail
            Kind.GOALSCORER, Kind.RED_CARD -> tail ?: playerIn(body)
            else -> null
        }

        // "Half-time. 1 - 0" names no half, but only sports played in halves have one.
        val word = periodWord ?: if (kind == Kind.HALF_TIME) "half" else null
        val format = emoji?.let { EMOJI_FORMAT[it] } ?: when (word) {
            "quarter", "inning" -> Format.POINTS
            "set" -> Format.SETS
            "period", "half" -> Format.GOALS
            else -> null
        } ?: if (PLAYER_NAME.matches(home) && PLAYER_NAME.matches(away)) Format.SETS else null

        return Event(home, away, homeScore, awayScore, side, kind, period, word, format, emoji, player, minute)
    }

    private val BARE_SET = Regex("""\b(set|manche)\b""", RegexOption.IGNORE_CASE)

    private fun normalizeWord(w: String): String = when (w.lowercase()) {
        "quart", "quart-temps" -> "quarter"
        "période", "periode" -> "period"
        "manche" -> "set"
        "mi-temps" -> "half"
        else -> w.lowercase()
    }

    /**
     * The player's name out of a scorer / red-card line: what's left once the emoji, the label,
     * the minute, the score and any brackets are taken out.
     */
    private fun playerIn(body: String): String? =
        body.replace(SCORE, " ")
            .replace(MINUTE, " ")
            .replace(Regex("""(?i)\b(goalscorer|goal scorer|scorer|buteur|red card|carton rouge|goal|but)\b"""), " ")
            .replace(Regex("""[^\p{L}\p{M} .'’-]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', '.', '-', ':')
            .takeIf { it.length >= 2 }

    /** Both names look like players ("Sabalenka A.") rather than teams: tennis and the like. */
    fun looksLikePlayers(home: String, away: String): Boolean =
        PLAYER_NAME.matches(home) && PLAYER_NAME.matches(away)

    /** "Etcheverry T. M." -> "Etcheverry"; team names pass through. */
    fun shortName(name: String): String =
        if (PLAYER_NAME.matches(name)) name.replace(Regex("""(\s+\p{Lu}\.)+\s*$"""), "").trim() else name
}
