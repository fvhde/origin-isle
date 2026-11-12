package com.originisle.android.sports

/**
 * Reads what Flashscore puts in a match share (its Share button on a match page), e.g.
 *
 *     Georgia U19 - Denmark U19 1:4
 *
 *     More info: https://www.flashscore.com/r/?t=1&id=W6twRmA4
 *
 * The id in the link is the one Flashscore tags that match's notifications with, so a card started
 * from a share is the same card its alerts update later. The regional apps and sites (flashscore.fr,
 * diretta.it, livesport.cz, soccer24…) share the same way.
 */
object FlashscoreShare {

    data class Shared(
        val matchId: String,
        val home: String,
        val away: String,
        val homeScore: Int?,
        val awayScore: Int?,
        val link: String,
    )

    private val LINK = Regex(
        """https?://[^\s]*(?:flashscore|livesport|diretta|soccer24|tennis24|\w+24)\.[^\s]*""",
        RegexOption.IGNORE_CASE,
    )
    /** `?id=W6twRmA4` / `&mid=…` in a short link, or `/match/W6twRmA4/` in a full one. */
    private val ID = Regex("""[?&](?:id|mid)=([A-Za-z0-9]{8})(?![A-Za-z0-9])|/match/([A-Za-z0-9]{8})(?:[/?#]|$)""")
    /** "More info:", "Plus d'infos :"… — a label ending in a colon, after the match line. */
    private val LEAD_IN = Regex("""\s+[^\d\s][^:\n]*:\s*$|^[^\d\s][^:\n-]*:\s*$""")
    private val TITLE = Regex("""^(.+?) - (.+?)(?: (\d{1,3}):(\d{1,3}))?$""")

    fun parse(text: String): Shared? {
        val link = LINK.find(text)?.value?.trimEnd('.', ',', ')') ?: return null
        val m = ID.find(link) ?: return null
        val id = m.groupValues[1].ifEmpty { m.groupValues[2] }
        // The match line: what comes before the link, without its "More info:" lead-in (some apps
        // pass the share on as one line).
        val line = text.substringBefore(link).lineSequence()
            .map { it.replace(LEAD_IN, "").trim() }
            .firstOrNull { it.isNotEmpty() && TITLE.matches(it) }
            ?: return null
        val t = TITLE.find(line) ?: return null
        val home = t.groupValues[1].trim()
        val away = t.groupValues[2].trim()
        if (home.isEmpty() || away.isEmpty()) return null
        return Shared(id, home, away, t.groupValues[3].toIntOrNull(), t.groupValues[4].toIntOrNull(), link)
    }
}
