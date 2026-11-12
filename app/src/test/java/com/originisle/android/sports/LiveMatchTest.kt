package com.originisle.android.sports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveMatchTest {

    private val min = 60_000L

    private fun LiveMatch.on(title: String, text: String, at: Long) =
        apply(FlashscoreParser.parse(title, text)!!, at)

    @Test fun footballClockFromKickoffAndSecondHalf() {
        val m = LiveMatch("x")
        m.on("Arsenal - Chelsea", "⚽ Match started. 0 - 0", 0)
        assertEquals("1'", m.status(0))
        assertEquals("23'", m.status(22 * min + 5_000))
        assertEquals("45+2'", m.status(46 * min))
        m.on("Arsenal - Chelsea", "Half-time. 0 - 0", 48 * min)
        assertEquals("HT", m.status(50 * min))
        m.on("Arsenal - Chelsea", "Start of 2nd half. 0 - 0", 63 * min)
        assertEquals("46'", m.status(63 * min))
        assertEquals("90+1'", m.status(108 * min))
    }

    @Test fun goalThenScorer() {
        val m = LiveMatch("x")
        // No scorer yet: no moment (the card just updates its score).
        assertEquals(null, m.on("Arsenal - Chelsea", "⚽ Goal! [1] - 0", 0))
        assertEquals("⚽ Arsenal", m.highlight)
        assertEquals(1, m.homeScore)
        assertEquals(LiveMatch.Moment.GOAL, m.on("Arsenal - Chelsea", "⚽ Goalscorer: Gabriel Magalhães (63') [1] - 0", 30_000))
        assertEquals("⚽ Magalhães 63'", m.highlight)
        assertEquals("64'", m.status(30_000 + min))
    }

    @Test fun goalCarriesScorerAndMinute() {
        val m = LiveMatch("x")
        assertEquals(LiveMatch.Moment.GOAL, m.on("Nyvelados - Dendele", "⚽ 18' Goal! [3] - 2 Marcos MV", 0))
        assertEquals("⚽ Marcos MV 18'", m.highlight)
        assertEquals("20'", m.status(2 * min))
    }

    @Test fun flashscorePostsEachEventMoreThanOnce() {
        val m = LiveMatch("x")
        // Seen live: the bare goal, then the same goal with its scorer, then a red card twice.
        // The bare goal: no scorer, no moment. The same goal with its scorer: the moment.
        assertEquals(null, m.on("Nyvelados - Dendele", "⚽ Goal! 3 - [3]", 0))
        assertEquals(LiveMatch.Moment.GOAL, m.on("Nyvelados - Dendele", "⚽ 23' Goal! 3 - [3] Lucas Guilherme", 90_000))
        assertEquals(true, m.isRepeat)
        assertEquals("Guilherme", m.scorer) // long names are cut to fit
        assertEquals("⚽ Guilherme 23'", m.highlight)
        // Posted again with the same scorer: nothing new.
        assertEquals(null, m.on("Nyvelados - Dendele", "⚽ 23' Goal! 3 - [3] Lucas Guilherme", 91_000))
        // A scorer named long after the goal updates the card without popping.
        assertEquals(null, m.on("Nyvelados - Dendele", "⚽ Goal! 3 - [4]", 400_000))
        assertEquals(null, m.on("Nyvelados - Dendele", "⚽ 30' Goal! 3 - [4] Marcos MV", 400_000 + 4 * 60_000))
        assertEquals(LiveMatch.Moment.RED_CARD, m.on("Nyvelados - Dendele", "25' Red card - Nyvelados", 450_000))
        assertEquals(FlashscoreParser.Side.HOME, m.highlightSide)
        assertEquals("🟥 Nyvelados 25'", m.highlight)
        assertEquals(null, m.on("Nyvelados - Dendele", "25' Red card - Nyvelados", 461_000))
    }

    @Test fun basketballQuarters() {
        val m = LiveMatch("x")
        m.on("Virtus Bologna - Verona", "End of 1st quarter. 20 - 18", 0)
        m.on("Virtus Bologna - Verona", "End of 2nd quarter. 42 - 36", 1)
        assertEquals("Half-time", m.status(2))
        assertEquals("20-18 · 22-18", m.topLine())
        m.on("Virtus Bologna - Verona", "Start of 3rd quarter. 42 - 36", 3)
        assertEquals("Q3", m.status(4))
        m.on("Virtus Bologna - Verona 81 - 75", "Finished. 81 - 75", 5)
        assertEquals("Final", m.status(6))
    }

    @Test fun tennisSets() {
        val m = LiveMatch("x")
        m.on("Zidansek T. - Grabher J.", "Match started.", 0)
        assertEquals("Set 1", m.status(0))
        m.on("Zidansek T. - Grabher J.", "End of 1st set. [1] - 0", 1)
        assertEquals("Set 2", m.status(1)) // the next set starts right away
        m.on("Zidansek T. - Grabher J.", "Start of 2nd set. [1] - 0", 2)
        assertEquals("Set 2", m.status(2))
        assertEquals(1 to 0, m.sets())
    }

    @Test fun tennisSetsWithGames() {
        val m = LiveMatch("x")
        m.on("Zidansek T. - Grabher J.", "End of 1st set. [6] - 4", 0)
        m.on("Zidansek T. - Grabher J.", "End of 2nd set. 3 - [6]", 1)
        assertEquals("6-4  3-6", m.topLine())
        assertEquals(1 to 1, m.sets())
    }

    @Test fun hockeyPeriods() {
        val m = LiveMatch("x")
        m.on("Austria W - USA W", "🏒 Goal! 0 - [8]", 0)
        assertNull(m.minute(0))
        m.on("Austria W - USA W", "End of 2nd period. 0 - 8", 1)
        assertEquals("End P2", m.status(1))
    }

    @Test fun minutes() {
        assertEquals(63 to 2, LiveMatch.parseMinute("63'"))
        assertEquals(23 to 1, LiveMatch.parseMinute("23'"))
        assertEquals(47 to 1, LiveMatch.parseMinute("45+2'"))
        assertEquals(93 to 2, LiveMatch.parseMinute("90+3'"))
    }
}
