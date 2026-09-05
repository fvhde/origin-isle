package com.originisle.android.sports

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.Icon
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap

/**
 * Fetches real club crests from TheSportsDB for the football score card.
 *
 * TheSportsDB free tier (key "3", no signup): `searchteams.php?t=<team>` returns `strBadge`, a PNG
 * we download, scale and cache. Provides the two side badges (island pill) plus a composited
 * home-vs-away image for the expanded card centre (vivo's scoreboard template only has one centre
 * icon slot, so we combine the crests into one image).
 */
object SportsFeed {

    private const val SEARCH = "https://www.thesportsdb.com/api/v1/json/3/searchteams.php?t="
    private const val CREST_PX = 96
    private val crestCache = ConcurrentHashMap<String, Bitmap>()

    /**
     * Short forms, nicknames and codes a score app might show instead of a club's full name, mapped
     * to a search string that's been verified LIVE against `searchteams.php` to come back as exactly
     * one Soccer result for the intended club (see PR/commit history for the verification run — the
     * free API's fuzzy match otherwise just as happily returns an unrelated club, or the wrong sport,
     * for a short/generic word: "Inter" alone resolves to a 5th-tier Spanish side "Intercity", "PSG"
     * alone resolves to an esports team, "Wolves" alone to a Bermudian club).
     *
     * A few entries map to a value that ISN'T the most "obvious" full name because that obvious name
     * is itself wrong on this API: "Olympique de Marseille" resolves to Marseille's Youth team (use
     * "Marseille"); "Al Hilal" alone resolves to a Sudanese club (use "Al Hilal Saudi"); "Hertha
     * Berlin" resolves to the women's team (use "Hertha BSC"); "Union Saint-Gilloise" (hyphenated) and
     * "Union SG" both miss (use "Union Saint Gilloise", spaced).
     */
    private val ALIASES = mapOf(
        // England
        "man utd" to "Manchester United", "man u" to "Manchester United", "man united" to "Manchester United",
        "mufc" to "Manchester United", "man city" to "Manchester City", "mcfc" to "Manchester City",
        "spurs" to "Tottenham Hotspur", "tottenham" to "Tottenham Hotspur",
        "wolves" to "Wolverhampton Wanderers", "newcastle" to "Newcastle United", "nufc" to "Newcastle United",
        "toon" to "Newcastle United", "west ham" to "West Ham United", "whu" to "West Ham United",
        "forest" to "Nottingham Forest", "nffc" to "Nottingham Forest", "palace" to "Crystal Palace",
        "cpfc" to "Crystal Palace", "villa" to "Aston Villa", "avfc" to "Aston Villa",
        "brighton" to "Brighton & Hove Albion", "leicester" to "Leicester City",
        "leeds" to "Leeds United", "lufc" to "Leeds United",
        "sheffield utd" to "Sheffield United", "sheff utd" to "Sheffield United",
        "sheffield wed" to "Sheffield Wednesday", "sheff wed" to "Sheffield Wednesday",
        "west brom" to "West Bromwich Albion", "wba" to "West Bromwich Albion",
        "qpr" to "Queens Park Rangers", "norwich" to "Norwich City",
        "preston" to "Preston North End", "pne" to "Preston North End",
        "stoke" to "Stoke City", "boro" to "Middlesbrough", "coventry" to "Coventry City", "hull" to "Hull City",
        // Spain
        "real madrid" to "Real Madrid", "madrid" to "Real Madrid", "rmcf" to "Real Madrid",
        "barca" to "Barcelona", "barça" to "Barcelona", "fcb" to "Barcelona",
        "atletico" to "Atletico Madrid", "atlético" to "Atletico Madrid", "atleti" to "Atletico Madrid",
        "atm" to "Atletico Madrid", "real sociedad" to "Real Sociedad", "la real" to "Real Sociedad",
        "athletic bilbao" to "Athletic Bilbao", "athletic club" to "Athletic Bilbao", "bilbao" to "Athletic Bilbao",
        "athletic" to "Athletic Bilbao",
        "betis" to "Real Betis", "sevilla" to "Sevilla FC", "valencia" to "Valencia CF", "celta" to "Celta Vigo",
        "villarreal" to "Villarreal CF", "osasuna" to "Osasuna", "girona" to "Girona FC", "getafe" to "Getafe CF",
        "rayo" to "Rayo Vallecano", "mallorca" to "RCD Mallorca", "espanyol" to "RCD Espanyol",
        "las palmas" to "UD Las Palmas", "alaves" to "Deportivo Alaves", "leganes" to "CD Leganes",
        // Germany
        "bayern" to "Bayern Munich", "fc bayern" to "Bayern Munich", "dortmund" to "Borussia Dortmund",
        "bvb" to "Borussia Dortmund", "leverkusen" to "Bayer Leverkusen", "b04" to "Bayer Leverkusen",
        "frankfurt" to "Eintracht Frankfurt", "sge" to "Eintracht Frankfurt",
        "gladbach" to "Borussia Monchengladbach", "bmg" to "Borussia Monchengladbach",
        "monchengladbach" to "Borussia Monchengladbach", "leipzig" to "RB Leipzig", "rbl" to "RB Leipzig",
        "stuttgart" to "VfB Stuttgart", "schalke" to "Schalke 04", "s04" to "Schalke 04",
        "hamburg" to "Hamburger SV", "hsv" to "Hamburger SV", "bremen" to "Werder Bremen",
        "werder" to "Werder Bremen", "union berlin" to "Union Berlin", "wolfsburg" to "VfL Wolfsburg",
        "hoffenheim" to "TSG Hoffenheim", "freiburg" to "SC Freiburg", "koln" to "FC Koln",
        "cologne" to "FC Koln", "mainz" to "Mainz 05", "augsburg" to "FC Augsburg",
        "hertha" to "Hertha BSC", "hertha berlin" to "Hertha BSC", "bochum" to "VfL Bochum",
        "st pauli" to "St Pauli", "st. pauli" to "St Pauli",
        // Italy
        "inter" to "Inter Milan", "internazionale" to "Inter Milan", "milan" to "AC Milan", "juve" to "Juventus",
        "roma" to "AS Roma", "lazio" to "SS Lazio", "napoli" to "Napoli", "atalanta" to "Atalanta",
        "fiorentina" to "Fiorentina", "viola" to "Fiorentina", "torino" to "Torino", "bologna" to "Bologna",
        "udinese" to "Udinese", "genoa" to "Genoa", "sampdoria" to "Sampdoria", "cagliari" to "Cagliari",
        "verona" to "Hellas Verona", "parma" to "Parma", "lecce" to "Lecce", "empoli" to "Empoli", "monza" to "Monza",
        // France
        "psg" to "Paris Saint Germain", "paris" to "Paris Saint Germain",
        "om" to "Marseille", "marseille" to "Marseille",
        "ol" to "Olympique Lyonnais", "lyon" to "Olympique Lyonnais", "monaco" to "AS Monaco",
        "lille" to "Lille", "losc" to "Lille", "rennes" to "Stade Rennais", "lens" to "RC Lens",
        "reims" to "Stade Reims", "toulouse" to "Toulouse FC", "nice" to "OGC Nice", "nantes" to "FC Nantes",
        "montpellier" to "Montpellier HSC", "strasbourg" to "RC Strasbourg", "brest" to "Stade Brestois",
        "le havre" to "Havre AC",
        // Netherlands
        "ajax" to "Ajax", "psv" to "PSV Eindhoven", "feyenoord" to "Feyenoord",
        "az alkmaar" to "AZ Alkmaar", "twente" to "FC Twente", "utrecht" to "FC Utrecht",
        // Portugal
        "benfica" to "Benfica", "porto" to "Porto", "sporting" to "Sporting CP",
        "sporting lisbon" to "Sporting CP", "braga" to "SC Braga",
        // Belgium
        "club brugge" to "Club Brugge", "anderlecht" to "Anderlecht",
        "union sg" to "Union Saint Gilloise", "union" to "Union Saint Gilloise",
        "genk" to "KRC Genk", "standard" to "Standard Liege", "standard liege" to "Standard Liege",
        "gent" to "KAA Gent",
        // Scotland
        "celtic" to "Celtic", "rangers" to "Rangers", "gers" to "Rangers", "aberdeen" to "Aberdeen",
        "hearts" to "Heart of Midlothian", "hibs" to "Hibernian",
        // Turkey
        "gala" to "Galatasaray", "fener" to "Fenerbahce", "besiktas" to "Besiktas", "bjk" to "Besiktas",
        "trabzon" to "Trabzonspor",
        // Saudi Arabia
        "al nassr" to "Al-Nassr", "al hilal" to "Al Hilal Saudi", "al ahli" to "Al Ahli Saudi",
    )

    // When a search returns several teams, prefer one of these leagues over amateur/minor divisions.
    private val MAJOR_LEAGUES = setOf(
        "English Premier League", "Spanish La Liga", "German Bundesliga", "Italian Serie A",
        "French Ligue 1", "UEFA Champions League", "UEFA Europa League", "English League Championship",
        "Portuguese Primeira Liga", "Dutch Eredivisie", "Scottish Premiership", "Turkish Super Lig",
        "German 2. Bundesliga", "Spanish La Liga 2", "Belgian Pro League", "Belgian First Division A",
        "Saudi Pro League",
    )

    /** Icons for a match: home badge, away badge, and a composited centre image (nulls if missing). */
    data class MatchIcons(val home: Icon?, val away: Icon?, val center: Icon?)

    /**
     * @param competition the match's competition/league name, if known (e.g. from the notification's
     *   sub-text) — used to pick the right club when a search returns several teams sharing a name
     *   (more accurate than the generic [MAJOR_LEAGUES] preference alone, and it also helps for
     *   leagues that aren't in that hardcoded set).
     */
    private suspend fun crestBitmap(team: String, competition: String = ""): Bitmap? = withContext(Dispatchers.IO) {
        val key = team.trim().lowercase()
        if (key.isEmpty()) return@withContext null
        crestCache[key]?.let { return@withContext it }
        try {
            val query = ALIASES[key] ?: team
            var teams = fetchTeams(query)
            if (teams == null || teams.length() == 0) {
                val normalized = normalizeForSearch(query)
                if (normalized.isNotEmpty() && !normalized.equals(query, ignoreCase = true)) {
                    teams = fetchTeams(normalized)
                }
            }
            if (teams == null || teams.length() == 0) return@withContext null
            val comp = competition.trim().lowercase()
            var chosen: JSONObject? = null
            var chosenIsMajorLeague = false
            for (i in 0 until teams.length()) {
                val t = teams.optJSONObject(i) ?: continue
                if (!t.optString("strSport").equals("Soccer", ignoreCase = true)) continue
                val league = t.optString("strLeague")
                if (chosen == null) chosen = t
                if (comp.isNotEmpty() && league.isNotBlank() &&
                    (league.lowercase().contains(comp) || comp.contains(league.lowercase()))
                ) {
                    // An exact competition match is unambiguous — take it immediately.
                    chosen = t
                    break
                }
                // Otherwise prefer the FIRST major-league result (deterministic), not whichever
                // happens to be scanned last.
                if (!chosenIsMajorLeague && league in MAJOR_LEAGUES) {
                    chosen = t
                    chosenIsMajorLeague = true
                }
            }
            val badge = (chosen ?: teams.optJSONObject(0))?.optString("strBadge")
            if (badge.isNullOrBlank()) return@withContext null
            val raw = URL(badge).openStream().use { BitmapFactory.decodeStream(it) } ?: return@withContext null
            Bitmap.createScaledBitmap(raw, CREST_PX, CREST_PX, true).also { crestCache[key] = it }
        } catch (e: Exception) {
            Log.w("SportsFeed", "crest fetch failed for '$team': ${e.message}")
            null
        }
    }

    // TheSportsDB's free tier sits behind Cloudflare rate limiting that kicks in hard — verified live,
    // as few as ~15 requests within a short window get back a plain-text "error code: 1015" body
    // instead of JSON (which JSONObject() then throws on, caught by crestBitmap's try/catch as a silent
    // missing crest). A goal notification fetches both the home and away crest at once, and several
    // live matches can update within seconds of each other, so bursts are the normal case, not an edge
    // case. Serializing every search through this lock with a minimum gap keeps normal traffic under
    // the limit instead of occasionally blanking out every crest in the app at once.
    private val requestLock = Mutex()
    private var lastRequestAt = 0L
    private const val MIN_REQUEST_GAP_MS = 400L

    private suspend fun fetchTeams(query: String): JSONArray? = requestLock.withLock {
        val wait = MIN_REQUEST_GAP_MS - (System.currentTimeMillis() - lastRequestAt)
        if (wait > 0) delay(wait)
        lastRequestAt = System.currentTimeMillis()
        val json = URL(SEARCH + URLEncoder.encode(query, "UTF-8")).readText()
        JSONObject(json).optJSONArray("teams")
    }

    /** Fold accented/Nordic letters to plain ASCII and punctuation to spaces, for a fallback search. */
    private fun normalizeForSearch(name: String): String {
        val folded = Normalizer.normalize(name, Normalizer.Form.NFD)
            .replace(Regex("""\p{Mn}+"""), "") // strip combining diacritical marks (é -> e, etc.)
            .replace('ø', 'o').replace('Ø', 'O')
            .replace('æ', 'e').replace('Æ', 'E')
            .replace('å', 'a').replace('Å', 'A')
            .replace('ß', 's')
        return folded.replace(Regex("""[/\\_-]"""), " ").replace(Regex("""\s+"""), " ").trim()
    }

    /** Single team crest as an [Icon], or null. */
    suspend fun crest(team: String): Icon? = crestBitmap(team)?.let { Icon.createWithBitmap(it) }

    /** Fetch both crests and build the badge + composite icons for a match. */
    suspend fun iconsFor(home: String, away: String, competition: String = ""): MatchIcons {
        val h = crestBitmap(home, competition)
        val a = crestBitmap(away, competition)
        return MatchIcons(
            home = h?.let { Icon.createWithBitmap(it) },
            away = a?.let { Icon.createWithBitmap(it) },
            center = composite(h, a),
        )
    }

    /** Home and away crest side by side as one image, for the expanded card centre. */
    private fun composite(home: Bitmap?, away: Bitmap?): Icon? {
        if (home == null && away == null) return null
        val gap = CREST_PX / 3
        val out = Bitmap.createBitmap(CREST_PX * 2 + gap, CREST_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        home?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        away?.let { canvas.drawBitmap(it, (CREST_PX + gap).toFloat(), 0f, null) }
        return Icon.createWithBitmap(out)
    }
}
