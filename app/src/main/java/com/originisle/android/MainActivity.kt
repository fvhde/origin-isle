package com.originisle.android

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.originisle.android.cards.SportsCard
import com.originisle.android.island.OriginIslandBuilder
import com.originisle.android.island.PlaygroundService
import com.originisle.android.service.NotificationCastListener
import com.originisle.android.ui.AppsTab
import com.originisle.android.ui.CastTab
import com.originisle.android.ui.LogTab
import com.originisle.android.ui.OnboardingScreen
import com.originisle.android.ui.OriginIsleTheme
import com.originisle.android.ui.PREFS_NAME
import com.originisle.android.ui.samples.IslandSamples

/**
 * Driver UI: onboard the required permissions, grant access, toggle casting, fire sample cards, and
 * pick which installed apps are allowed on the island.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (intent?.getStringExtra("autofire")) {
            "football" -> SportsCard.post(this, "ARS", 1, "MAN", 1, "65'")
        }
        intent?.getIntExtra("autofire_sample", -1)?.takeIf { it >= 0 }?.let {
            IslandSamples.all.getOrNull(it)?.post(this)
        }
        setContent { OriginIsleTheme { Screen() } }
    }

    /**
     * Heal a killed listener on every foregrounding, not just a cold start — resuming an
     * already-running task would otherwise skip the recovery. Cheap: [forceRebind] no-ops while the
     * listener is connected.
     */
    override fun onResume() {
        super.onResume()
        if (getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean("cast_notifications", false)) {
            PlaygroundService.keepAlive(this)
            NotificationCastListener.forceRebind(this)
        }
    }
}

@Composable
private fun Screen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    remember { OriginIslandBuilder.grantScenes(context) } // whitelist scenes once on entry
    var onboardingDone by remember { mutableStateOf(prefs.getBoolean("onboarding_done", false)) }
    var tab by remember { mutableStateOf(0) }

    if (!onboardingDone) {
        OnboardingScreen(context, prefs) { onboardingDone = true }
        return
    }

    Scaffold { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Cast") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Apps") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Log") })
            }
            when (tab) {
                0 -> CastTab(context, prefs, onRedoSetup = { onboardingDone = false })
                1 -> AppsTab(context, prefs)
                2 -> LogTab(context)
            }
        }
    }
}
