package com.originisle.android.share

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.originisle.android.cards.ScoreCard
import com.originisle.android.sports.FlashscoreShare

/**
 * "Show on island" in the share sheet. Flashscore's Share button on a match sends the match line
 * ("Georgia U19 - Denmark U19 1:4") and a link with the match id, alongside a picture; this puts
 * that match on the island straight away, without waiting for its next alert. No screen of its
 * own: it handles the share and closes.
 */
class ShareToIslandActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = listOfNotNull(
            intent?.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(),
            intent?.getStringExtra(Intent.EXTRA_SUBJECT),
        ).joinToString("\n")
        val shared = FlashscoreShare.parse(text)
        if (shared == null) {
            Toast.makeText(applicationContext, "Only Flashscore matches can go on the island", Toast.LENGTH_SHORT).show()
        } else {
            ScoreCard.fromShare(applicationContext, shared)
            Toast.makeText(
                applicationContext,
                "${shared.home} - ${shared.away} is on the island. Follow it in Flashscore to get its updates.",
                Toast.LENGTH_LONG,
            ).show()
        }
        finish()
    }
}
