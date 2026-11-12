package com.originisle.android.sports

import com.originisle.android.sports.FlashscoreParser.Format
import com.originisle.android.sports.FlashscoreParser.Kind
import com.originisle.android.sports.FlashscoreParser.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Every title/text pair here was posted by Flashscore 26.11.4 on a real phone. */
class FlashscoreParserTest {

    private fun p(title: String, text: String) = FlashscoreParser.parse(title, text)!!

    @Test fun footballGoalHome() {
        val e = p("Palmeiras U20 - Santos U20", "⚽ Goal! [1] - 1")
        assertEquals("Palmeiras U20", e.home); assertEquals("Santos U20", e.away)
        assertEquals(1, e.homeScore); assertEquals(1, e.awayScore)
        assertEquals(Side.HOME, e.side); assertEquals(Kind.GOAL, e.kind)
        assertEquals(Format.GOALS, e.format); assertEquals("⚽", e.emoji)
    }

    @Test fun footballGoalAway() {
        val e = p("Palmeiras U20 - Santos U20", "⚽ Goal! 1 - [2]")
        assertEquals(1, e.homeScore); assertEquals(2, e.awayScore); assertEquals(Side.AWAY, e.side)
    }

    @Test fun goalWithScorer() {
        val e = p("Nyvelados - Dendele", "⚽ 18' Goal! [3] - 2 Marcos MV")
        assertEquals(Kind.GOAL, e.kind); assertEquals(Side.HOME, e.side)
        assertEquals(3, e.homeScore); assertEquals(2, e.awayScore)
        assertEquals("18'", e.minute); assertEquals("Marcos MV", e.player)
    }

    @Test fun scorerWithInitial() {
        val e = p("Chelmsford - Ebbsfleet", "⚽ 35' Goal! [1] - 1 Clarke A.")
        assertEquals("Clarke A.", e.player); assertEquals("35'", e.minute); assertEquals(Side.HOME, e.side)
        assertEquals("Clarke", LiveMatch.shortPlayer(e.player!!))
    }

    @Test fun moreRealLines() {
        val half = p("Plymouth Parkway - Berkhamsted", "Start of 2nd half. 0 - 0")
        assertEquals(Kind.PERIOD_START, half.kind); assertEquals(2, half.period)
        val q3 = p("Jena - Hamburg", "End of 3rd quarter. 68 - 75")
        assertEquals(Kind.PERIOD_END, q3.kind); assertEquals(3, q3.period); assertEquals(75, q3.awayScore)
        val tennis = p("Zidansek T. - Grabher J. 0 - 2", "Finished. 0 - [2]")
        assertEquals(Kind.FINISHED, tennis.kind); assertEquals(Side.AWAY, tennis.side); assertEquals(Format.SETS, tennis.format)
    }

    @Test fun setEndWithoutNumber() {
        val e = p("Sabalenka A. - Muchova K.", "End of set. [1] - 0")
        assertEquals(Kind.PERIOD_END, e.kind); assertEquals(Format.SETS, e.format)
        assertEquals(1, e.homeScore); assertEquals(Side.HOME, e.side)
    }

    @Test fun hockeyGoal() {
        val e = p("Austria W - USA W", "🏒 Goal! 0 - [8]")
        assertEquals(0, e.homeScore); assertEquals(8, e.awayScore)
        assertEquals(Side.AWAY, e.side); assertEquals(Format.GOALS, e.format); assertEquals("🏒", e.emoji)
    }

    @Test fun footballFinal() {
        val e = p("Palmeiras U20 - Santos U20 1 - 2", "Finished. 1 - 2")
        assertEquals("Santos U20", e.away); assertEquals(Kind.FINISHED, e.kind)
        assertEquals(1, e.homeScore); assertEquals(2, e.awayScore); assertNull(e.side)
    }

    @Test fun basketballQuarter() {
        val e = p("Virtus Bologna - Verona", "End of 2nd quarter. 42 - 36")
        assertEquals(Kind.PERIOD_END, e.kind); assertEquals(2, e.period); assertEquals("quarter", e.periodWord)
        assertEquals(42, e.homeScore); assertEquals(36, e.awayScore); assertEquals(Format.POINTS, e.format)
    }

    @Test fun basketballFinal() {
        val e = p("Partizani Tirana - Skenderbeu 81 - 105", "Finished. 81 - 105")
        assertEquals("Skenderbeu", e.away); assertEquals(105, e.awayScore); assertEquals(Kind.FINISHED, e.kind)
    }

    @Test fun tennisFinal() {
        val e = p("Etcheverry T. M. - Dahlin M. 2 - 1", "Finished. [2] - 1")
        assertEquals("Etcheverry T. M.", e.home); assertEquals("Dahlin M.", e.away)
        assertEquals(Side.HOME, e.side); assertEquals(Format.SETS, e.format)
        assertEquals("Etcheverry", FlashscoreParser.shortName(e.home))
        assertEquals("Dahlin", FlashscoreParser.shortName(e.away))
    }

    @Test fun volleyballFinal() {
        val e = p("AO Kalamata - PAOK 1 - 3", "Finished. 1 - [3]")
        assertEquals("PAOK", e.away); assertEquals(3, e.awayScore); assertEquals(Side.AWAY, e.side)
    }

    @Test fun teamNamesKeepTheirDigits() {
        val e = p("Bayern - Schalke 04", "⚽ Goal! 1 - [1]")
        assertEquals("Schalke 04", e.away); assertEquals(1, e.awayScore)
        assertEquals("Schalke 04", FlashscoreParser.shortName("Schalke 04"))
    }

    @Test fun periodWords() {
        assertEquals(Kind.HALF_TIME, p("A - B", "Half-time. 1 - 0").kind)
        val second = p("A - B", "Start of 2nd half. 1 - 0")
        assertEquals(Kind.PERIOD_START, second.kind); assertEquals(2, second.period)
        val set = p("Zidansek T. - Grabher J.", "End of 1st set. [1] - 0")
        assertEquals(Kind.PERIOD_END, set.kind); assertEquals("set", set.periodWord); assertEquals(Format.SETS, set.format)
    }

    @Test fun halfTimeWithoutEmoji() {
        val e = p("England - Croatia", "Half-time. 1 - 0")
        assertEquals(Kind.HALF_TIME, e.kind); assertEquals("half", e.periodWord); assertEquals(Format.GOALS, e.format)
    }

    @Test fun volleyballSetWithoutNumber() {
        val e = p("ACH Ljubljana - Orion Stars", "End of set. [2] - 1")
        assertEquals(Kind.PERIOD_END, e.kind); assertEquals("set", e.periodWord); assertEquals(Format.SETS, e.format)
        assertEquals(2, e.homeScore); assertEquals(1, e.awayScore)
    }

    @Test fun scorerLine() {
        val e = p("Arsenal - Chelsea", "⚽ Goalscorer: Gabriel Magalhães (63') [1] - 0")
        assertEquals(Kind.GOALSCORER, e.kind); assertEquals("63'", e.minute); assertEquals("Gabriel Magalhães", e.player)
    }

    @Test fun notAMatch() {
        assertNull(FlashscoreParser.parse("Breaking news", "Mbappé signs a new deal"))
    }
}
