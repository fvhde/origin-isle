package com.originisle.android

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.originisle.android.cards.SportsCard
import com.originisle.android.island.OriginIslandBuilder
import com.originisle.android.island.PlaygroundService
import com.originisle.android.service.NotificationCastListener
import com.originisle.android.ui.ActivityScreen
import com.originisle.android.ui.AppsScreen
import com.originisle.android.ui.HomeScreen
import com.originisle.android.ui.IsleColors
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

private enum class Page { HOME, APPS, ACTIVITY }

@Composable
private fun Screen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    remember { OriginIslandBuilder.grantScenes(context) } // whitelist scenes once on entry
    var onboardingDone by remember { mutableStateOf(prefs.getBoolean("onboarding_done", false)) }
    var page by rememberSaveable { mutableStateOf(Page.HOME) }

    // Apps and Activity are pushed on top of Home; system back returns to it.
    BackHandler(enabled = onboardingDone && page != Page.HOME) { page = Page.HOME }

    Scaffold(containerColor = IsleColors.Background) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            if (!onboardingDone) {
                OnboardingScreen(context, prefs) { onboardingDone = true }
                return@Box
            }
            when (page) {
                Page.HOME -> HomeScreen(
                    context, prefs,
                    onRedoSetup = { onboardingDone = false },
                    onOpenApps = { page = Page.APPS },
                    onOpenActivity = { page = Page.ACTIVITY },
                )
                Page.APPS -> AppsScreen(context, prefs, onBack = { page = Page.HOME })
                Page.ACTIVITY -> ActivityScreen(context, onBack = { page = Page.HOME })
            }
        }
    }
}
