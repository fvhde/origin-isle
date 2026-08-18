package com.originisle.android

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.originisle.android.island.OriginIslandBuilder
import com.originisle.android.service.NotificationCastListener

class OriginIsleApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        OriginIslandBuilder.grantScenes(this)
        // Every process start, not just the UI's: a service restart is the likeliest way back in
        // after the kill that could have left the component half-toggled.
        NotificationCastListener.ensureEnabled(this)
    }
}
