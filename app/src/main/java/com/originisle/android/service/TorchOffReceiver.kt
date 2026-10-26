package com.originisle.android.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.originisle.android.cards.FlashlightCard

/** "Turn off" on the [FlashlightCard]. */
class TorchOffReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        FlashlightCard.turnOff(context)
    }
}
