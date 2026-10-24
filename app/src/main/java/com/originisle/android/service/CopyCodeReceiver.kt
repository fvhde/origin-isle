package com.originisle.android.service

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.PersistableBundle
import android.widget.Toast
import com.originisle.android.cards.OtpCard

/** Copies a login code from a tapped [OtpCard] to the clipboard, then takes the card down. */
class CopyCodeReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_CODE = "otp_code"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val code = intent.getStringExtra(EXTRA_CODE) ?: return
        val clip = ClipData.newPlainText("Login code", code).apply {
            // Keeps the code out of the clipboard preview / keyboard clipboard history.
            description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
        Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()

        val id = intent.getIntExtra("cast_id", -1)
        if (id != -1) OtpCard.cancel(context, id)
    }
}
