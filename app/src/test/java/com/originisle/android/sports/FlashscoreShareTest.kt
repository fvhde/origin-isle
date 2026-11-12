package com.originisle.android.sports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FlashscoreShareTest {

    @Test fun realShare() {
        // Shared from Flashscore 26.11.4 on a live match; its notifications were tagged W6twRmA4.
        val s = FlashscoreShare.parse("Georgia U19 - Denmark U19 1:4\n\nMore info: https://www.flashscore.com/r/?t=1&id=W6twRmA4")!!
        assertEquals("W6twRmA4", s.matchId)
        assertEquals("Georgia U19", s.home)
        assertEquals("Denmark U19", s.away)
        assertEquals(1, s.homeScore)
        assertEquals(4, s.awayScore)
        assertEquals("https://www.flashscore.com/r/?t=1&id=W6twRmA4", s.link)
    }

    @Test fun oneLine() {
        val s = FlashscoreShare.parse("Croatia U19 - Hungary U19 0:1 More info: https://www.flashscore.com/r/?t=1&id=fBnUwN7E")!!
        assertEquals("fBnUwN7E", s.matchId)
        assertEquals("Hungary U19", s.away)
        assertEquals(1, s.awayScore)
    }

    @Test fun notStartedYet() {
        val s = FlashscoreShare.parse("Arsenal - Chelsea\n\nMore info: https://www.flashscore.fr/r/?t=1&id=Ab3dEf7H")!!
        assertEquals("Ab3dEf7H", s.matchId)
        assertNull(s.homeScore)
    }

    @Test fun tennisAndFullLinks() {
        val s = FlashscoreShare.parse("Sabalenka A. - Muchova K. 1:0\nhttps://www.flashscore.com/match/hAIeAn5E/#/match-summary")!!
        assertEquals("hAIeAn5E", s.matchId)
        assertEquals("Sabalenka A.", s.home)
        assertEquals(1, s.homeScore)
    }

    @Test fun notAMatch() {
        assertNull(FlashscoreShare.parse("Look at this https://example.com/r/?id=W6twRmA4"))
        assertNull(FlashscoreShare.parse("Mbappé signs! https://www.flashscore.com/news/abc/"))
    }
}
