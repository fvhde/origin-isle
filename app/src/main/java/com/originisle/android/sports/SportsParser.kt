package com.originisle.android.sports

/**
 * Best-effort parser that pulls a football match out of a score app's notification text
 * (SofaScore, FotMob, OneFootball, …). Formats vary per app, so this tries the score pattern in
 * the title first, then the body, and cleans team names of emoji / trailing "(65')" noise.
 *
 * Handles e.g. "Arsenal 1 - 1 Manchester United", "Arsenal 1-1 Man Utd  65'", "⚽ ARS 2-0 CHE".
 * The raw notification is logged by the caller so unrecognised formats can be tuned.
 */
object SportsParser {

    data class Match(
        val home: String,
        val homeScore: Int,
        val away: String,
        val awayScore: Int,
        val minute: String,
    )

    // "<home> <hs> - <as> <away>"  (dash / en-dash / colon, spaces optional around the separator)
    private val SCORE = Regex("""(.+?)\s+(\d{1,2})\s*[-–:]\s*(\d{1,2})\s+(.+)""")
    // Extra-time minutes ("90+2'") must be tried before plain minutes, or a bare \d{1,3}['’] match
    // finds just the "2'" part of "90+2'" and drops the "90+" — showing "2'" instead of "90+2'".
    private val MINUTE = Regex(
        """(?:\d{1,3}\+\d{1,2}|\d{1,3})\s*['’]|\bHT\b|\bFT\b|half.?time|full.?time""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Try each notification field on its own (NOT concatenated) — a goal notification often repeats
     * "Home X - Y Away" in both text and bigText, and merging them makes the greedy away-capture
     * swallow the duplicate ("Dortmund Arsenal 0 - 1 Dortmund"), which then fails crest lookup.
     */
    fun parse(vararg sources: String): Match? {
        for (source in sources) {
            val m = SCORE.find(source.trim()) ?: continue
            val home = clean(m.groupValues[1])
            val homeScore = m.groupValues[2].toIntOrNull() ?: continue
            val awayScore = m.groupValues[3].toIntOrNull() ?: continue
            val away = clean(m.groupValues[4])
            if (home.isBlank() || away.isBlank()) continue
            return Match(home, homeScore, away, awayScore, statusOf(sources.joinToString(" ")))
        }
        return null
    }

    /** A short match-status string: a numeric minute if present, else HT/FT/1st half/… */
    private fun statusOf(s: String): String {
        val l0 = s.lowercase()
        // A post-match "recap"/"highlights" notification (e.g. Google's "Watch match recap") is
        // unambiguously finished, even though it carries none of the FT/full-time wording below — it's
        // often just "<team> <score> - <score> <team> · <past date>". Check this FIRST: a recap's score
        // line has no apostrophe-suffixed digits, but treating it as FT here (before the general
        // wording checks) is the reliable signal, not an absence-of-minute-marker guess.
        if (l0.contains("recap") || l0.contains("highlights") || l0.contains("position")) return "FT"
        // Extra-time form ("90+2'") first, or the same "drops the 90+" bug as MINUTE above.
        Regex("""(?:\d{1,3}\+\d{1,2}|\d{1,3})\s*['’]""").find(s)
            ?.let { return it.value.replace("’", "'").replace(" ", "") }
        val l = s.lowercase()
        return when {
            l.contains("full") || Regex("""\bft\b""").containsMatchIn(l) -> "FT"
            l.contains("half-time") || l.contains("half time") || l.contains("halftime") ||
                Regex("""\bht\b""").containsMatchIn(l) -> "HT"
            l.contains("penalt") -> "Pens"
            l.contains("extra time") || Regex("""\bet\b""").containsMatchIn(l) -> when {
                l.contains("second") -> "ET 2nd half"
                l.contains("first") -> "ET 1st half"
                else -> "ET"
            }
            l.contains("second half") -> "2nd half"
            l.contains("first half") -> "1st half"
            else -> ""
        }
    }

    /** Strip a trailing "(65')" / minute / stray symbols and collapse whitespace from a team name. */
    private fun clean(raw: String): String = raw
        .substringBefore(" · ").substringBefore(" ⋅ ").substringBefore(" | ") // drop "· Live", "⋅ First half"
        .replace(Regex("""\(.*?\)"""), " ")                 // "(65')", "(AGG 3-2)"
        .replace(MINUTE, " ")
        .replace(Regex("""[^\p{L}\p{N} .&'/-]"""), " ")     // drop emoji / arrows / etc.
        .replace(Regex("""\s+"""), " ")
        .trim()
}
