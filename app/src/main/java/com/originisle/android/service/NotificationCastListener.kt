package com.originisle.android.service

import android.app.Notification
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.NotificationListenerService.Ranking
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.originisle.android.cards.ChargingCard
import com.originisle.android.cards.EarbudsCard
import com.originisle.android.cards.FlashlightCard
import com.originisle.android.cards.GenericCard
import com.originisle.android.cards.MediaCard
import com.originisle.android.cards.MessageCard
import com.originisle.android.cards.NavigationCard
import com.originisle.android.cards.OtpCard
import com.originisle.android.cards.PaymentCard
import com.originisle.android.cards.PrivacyCard
import com.originisle.android.cards.RideCard
import com.originisle.android.cards.ScoreCard
import com.originisle.android.cards.fallbackClickIntent
import com.originisle.android.island.OriginIslandConstants
import com.originisle.android.island.PlaygroundService
import com.originisle.android.log.CastLog
import com.originisle.android.otp.OtpDetector
import java.util.concurrent.ConcurrentHashMap

/**
 * Listens to every notification on the device and re-casts eligible ones into the OriginIsland.
 *
 * This class owns only the CAST/SKIP DECISION for each notification — which kind of card (if any)
 * it becomes, and why one wasn't posted. The actual card is built and posted by the type-specific
 * object in `cards/` ([GenericCard], [MediaCard], [PaymentCard], [ScoreCard]); [IconCache] is the
 * state they + [MediaControlReceiver] share.
 */
class NotificationCastListener : NotificationListenerService() {

    /** Outcome of [forceRebind], so callers can tell the user something truthful. */
    enum class RebindResult { REBINDING, ALREADY_CONNECTED, NO_ACCESS, FAILED }

    companion object {
        /** The bound listener instance, or null if the service isn't connected. */
        @Volatile
        var instance: NotificationCastListener? = null

        /** Health signals for the in-app status line. 0 = never. */
        @Volatile
        var connectedAt: Long = 0L
        @Volatile
        var lastEventAt: Long = 0L

        /**
         * Force the system to re-bind this listener after the process was killed (OriginOS kills it
         * on swipe-away), and report whether a rebind could even be attempted.
         *
         * [NotificationListenerService.requestRebind] alone is NOT enough — the whole reason the
         * Reconnect button did nothing. It early-returns unless the component was explicitly
         * snoozed by [requestUnbind], and a process kill never snoozes it, so the system still
         * believes we're bound. Toggling the component's enabled state instead fires
         * ACTION_PACKAGE_CHANGED, which makes NotificationManagerService rebind every
         * approved-but-unbound listener. The grant lives in Settings.Secure keyed by component and
         * survives the toggle; DONT_KILL_APP keeps our own process up across it.
         */
        fun forceRebind(context: Context): RebindResult {
            val component = ComponentName(context.applicationContext, NotificationCastListener::class.java)
            if (!NotificationManagerCompat.getEnabledListenerPackages(context)
                    .contains(context.packageName)
            ) {
                return RebindResult.NO_ACCESS
            }
            if (instance != null) return RebindResult.ALREADY_CONNECTED
            // The rebind is asynchronous (it waits on a package broadcast), so a burst of callers —
            // onResume firing repeatedly while the listener is still down — must not keep tearing
            // down a binding that is already on its way up.
            val now = System.currentTimeMillis()
            if (now - lastRebindAt < REBIND_THROTTLE_MS) return RebindResult.REBINDING

            // Separate runCatching per step: the re-enable must be attempted even if the disable
            // threw. See [ensureEnabled] for why being left disabled is unrecoverable.
            val pm = context.applicationContext.packageManager
            val disabled = runCatching {
                pm.setComponentEnabledSetting(
                    component,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }.isSuccess
            val enabled = runCatching {
                pm.setComponentEnabledSetting(
                    component,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }.isSuccess
            // Only arm the throttle once the toggle actually landed, so a failure doesn't leave the
            // next 10s of taps reporting "rebinding" when nothing is.
            if (!disabled || !enabled) return RebindResult.FAILED
            lastRebindAt = now
            // After the toggle the component IS considered unbound, so this call is no longer a
            // no-op and can shortcut the wait for the package broadcast to land.
            runCatching { requestRebind(component) }
            return RebindResult.REBINDING
        }

        /**
         * Undo a [forceRebind] that only got halfway — the process can die between its two calls,
         * and DONT_KILL_APP only stops PackageManager from killing us, not OriginOS. A component
         * left DISABLED persists across reboots and never binds again, while the grant it's checked
         * against lives in Settings.Secure and still reads as granted, so the UI would show a
         * healthy listener forever. Called on every process start.
         */
        fun ensureEnabled(context: Context) {
            val pm = context.applicationContext.packageManager
            val component = ComponentName(context.applicationContext, NotificationCastListener::class.java)
            runCatching {
                if (pm.getComponentEnabledSetting(component) ==
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                ) {
                    Log.w(TAG, "listener component was left disabled by a partial rebind — re-enabling")
                    pm.setComponentEnabledSetting(
                        component,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP,
                    )
                }
            }
        }

        private const val PREFS = "experimental_prefs"
        private const val TAG = "NotificationCast"

        /** Minimum gap between component toggles in [forceRebind]. */
        private const val REBIND_THROTTLE_MS = 10_000L

        @Volatile
        private var lastRebindAt = 0L

        /** Framework/system packages whose notifications are noise on the island. */
        private val SYSTEM_PKGS = setOf(
            "android",
            "com.android.systemui",
            "com.android.shell",
            "com.vivo.upslide",
            "com.vivo.smartmultiwindow",
        )

        /**
         * Live scores come from Flashscore and the rest of the Livesport family (its regional apps,
         * LiveSport.cz, Diretta…), which all post the same match notifications. See [ScoreCard].
         */
        private const val SCORE_PKG_PREFIX = "eu.livesport."

        /** On a reconnect, a one-off notification older than this isn't shown again. */
        private const val RELOAD_FRESH_MS = 60_000L

        /**
         * Turn-by-turn navigation apps. Most set [Notification.CATEGORY_NAVIGATION] and are matched
         * on that alone; Maps is listed by package because it's worth casting even on a build that
         * doesn't set the category.
         *
         * Waze is deliberately NOT here. Its only notification is a `CLOSE_WAZE_CHANNEL`
         * foreground-service notice — title "Waze", text "Running. Tap to open.", one "Switch off"
         * action — with no subText, no large icon, no `shortCriticalText` and all three RemoteViews
         * slots null (verified against `dumpsys notification --noredact`). It carries no turn data
         * to build a card from, and it is byte-identical whether Waze is navigating or merely open,
         * so there's nothing to key a "navigating" card off either. Waze renders its turn banner
         * inside its own app and publishes none of it to the notification system.
         *
         * This only steers Waze away from [NavigationCard] — it still falls through to the generic
         * ongoing card below, same as any other app's plain foreground-service notice. No special
         * filtering for that pattern: a user who doesn't want it can turn Waze off in the Apps tab,
         * the same way they'd opt out of any other app's notifications.
         */
        private val NAV_APPS = setOf(
            "com.google.android.apps.maps", // Google Maps
        )

        /** Wallet / bank / money apps whose notifications are treated as payments (Apple-Pay card). */
        private val PAYMENT_APPS = setOf(
            "com.google.android.apps.walletnfcrel",     // Google Wallet
            "com.google.android.apps.nbu.paisa.user",   // Google Pay
            "com.revolut.revolut",                      // Revolut
            "com.paypal.android.p2pmobile",             // PayPal
            "com.wise.android", "com.transferwise.android", // Wise
            "de.number26.android", "com.n26.n26",       // N26
            "com.monzo.emma",                           // Monzo
            "com.starlingbank.android",                 // Starling
            "fr.lydia.android",                         // Lydia
            "com.getcurve.mobile",                      // Curve
            "com.klarna.mobile",                        // Klarna
            "co.mona.android",                          // Crypto.com
        )

        /** Payment verbs — any app whose notification pairs one of these with an amount is a payment. */
        private val PAYMENT_WORDS = listOf(
            "paid", "payment", "you sent", "you received", "charged", "purchase",
            "transaction", "debited", "credited", "receipt", "spent", "approved",
            "bezahlt", "payé", "reçu", "pago", "pagamento", "betaling",
        )

        /** "€12.90", "12,90 €", "$5", "£3.20", "EUR 40" … first match is used as the amount. */
        private val AMOUNT = Regex(
            """(?:[€$£¥]|\b(?:EUR|USD|GBP|CHF)\b)\s?\d[\d.,]*|\d[\d.,]*\s?(?:[€$£¥]|\b(?:EUR|USD|GBP|CHF)\b)""",
            RegexOption.IGNORE_CASE,
        )
    }

    private val pollHandler = Handler(Looper.getMainLooper())

    /** Last-logged outcome per notification key, so repeated updates don't flood the log. */
    private val lastOutcome = ConcurrentHashMap<String, String>()

    /**
     * Last-seen [StatusBarNotification.getPostTime] per score-app notification key. onNotificationPosted
     * is meant to fire on every update, but OriginOS can visibly delay/batch that delivery for a
     * specific app — the score card was observed sitting stale until some UNRELATED notification came
     * in and (presumably) flushed the batch. Polled the same way media/chronometer already are, but
     * gated on postTime actually changing so this doesn't reprocess (and repost) unchanged content
     * every second — a live score's own post frequency is way under 1/sec anyway.
     */
    private val lastScorePostTime = ConcurrentHashMap<String, Long>()

    /**
     * Last [StatusBarNotification.getPostTime] actually cast, per notification key — for one-shot
     * "message"/payment casts only (see the two call sites). OriginOS (verified on vivo) redelivers
     * onNotificationPosted for an app's OTHER already-cast notifications whenever any one of that
     * app's notifications changes — WhatsApp and Instagram threads, and wallet payment apps, all
     * exhibit this: a brand new message elsewhere recasts every older, untouched thread's card back
     * onto the island. A genuine update always carries a fresh postTime from the source app's own
     * notify() call, so gating on postTime being unchanged catches only the redelivery, never real
     * content. Ongoing/call/progress cards don't use this — their own ticking/polling paths
     * deliberately re-cast with an unchanged postTime (see [pollRunnable]).
     */
    private val lastCastPostTime = ConcurrentHashMap<String, Long>()

    private val pollRunnable = object : Runnable {
        override fun run() {
            try {
                val prefs = getSharedPreferences(PREFS, 0)
                val castNotifs = prefs.getBoolean("cast_notifications", false)
                val castMedia = prefs.getBoolean("cast_media_sessions", false)
                activeNotifications?.forEach { sbn ->
                    // A live score still showing initials: its picture may have arrived since.
                    if (castNotifs && sbn.packageName.startsWith(SCORE_PKG_PREFIX)) {
                        ScoreCard.refreshPicture(applicationContext, sbn)
                    }
                    val extras = sbn.notification.extras
                    val token = extras.getParcelable(
                        NotificationCompat.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java,
                    )
                    if (token != null) {
                        if (castMedia && isAppEnabled(prefs, sbn.packageName)) {
                            MediaCard.post(
                                applicationContext, sbn,
                                MediaController(this@NotificationCastListener, token),
                            )
                        }
                    } else if (castNotifs && sbn.isOngoing &&
                        extras.getBoolean(NotificationCompat.EXTRA_SHOW_CHRONOMETER, false)
                    ) {
                        onNotificationPosted(sbn) // refresh ticking chronometer
                    } else if (castNotifs && sbn.packageName.startsWith(SCORE_PKG_PREFIX) &&
                        isAppEnabled(prefs, sbn.packageName) &&
                        lastScorePostTime.put(sbn.key, sbn.postTime) != sbn.postTime
                    ) {
                        onNotificationPosted(sbn) // catch a score update onNotificationPosted was slow to deliver
                    }
                }
                ScoreCard.tick(applicationContext)
            } catch (e: Exception) {
                Log.e(TAG, "poll error", e)
            }
            pollHandler.postDelayed(this, 1000L)
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        connectedAt = System.currentTimeMillis()
        if (getSharedPreferences(PREFS, 0).getBoolean("cast_notifications", false)) {
            runCatching { PlaygroundService.keepAlive(this) }
        }
        reload()
        // Matches shared from Flashscore that no notification brings back by itself.
        runCatching { ScoreCard.restoreShared(applicationContext) }
        pollHandler.post(pollRunnable)
        registerBatteryReceiver()
        startSystemCards()
    }

    /** Cards fed by the system rather than by notifications: camera/mic, flashlight, earbuds. */
    private fun startSystemCards() {
        runCatching { FlashlightCard.start(this) }.onFailure { Log.e(TAG, "flashlight card failed", it) }
        runCatching { PrivacyCard.start(this) }.onFailure { Log.e(TAG, "privacy card failed", it) }
        runCatching { EarbudsCard.start(this) }.onFailure { Log.e(TAG, "earbuds card failed", it) }
    }

    private fun stopSystemCards() {
        runCatching { PrivacyCard.stop() }
        runCatching { FlashlightCard.stop() }
        runCatching { EarbudsCard.stop() }
    }

    /** Feeds [ChargingCard]. Registered while bound — the bound listener is what keeps us alive anyway. */
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            runCatching { ChargingCard.onBatteryChanged(context, intent) }
                .onFailure { Log.e(TAG, "charging card failed", it) }
        }
    }
    private var batteryReceiverRegistered = false

    private fun registerBatteryReceiver() {
        if (batteryReceiverRegistered) return
        runCatching {
            registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), RECEIVER_NOT_EXPORTED)
            batteryReceiverRegistered = true
        }
    }

    private fun unregisterBatteryReceiver() {
        if (!batteryReceiverRegistered) return
        runCatching { unregisterReceiver(batteryReceiver) }
        batteryReceiverRegistered = false
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        unregisterBatteryReceiver()
        stopSystemCards()
        if (instance === this) instance = null
        connectedAt = 0L
        pollHandler.removeCallbacks(pollRunnable)
        // A system-initiated unbind DOES snooze the component, so unlike the process-kill case a
        // plain requestRebind works here (see [forceRebind]). Self-heals transient disconnects.
        runCatching { requestRebind(ComponentName(this, NotificationCastListener::class.java)) }
    }

    /**
     * The Apps tab's per-app switch is a single master kill switch — it must gate every card type
     * (media sessions included), not just plain notifications. Checked once, up front, in
     * [onNotificationPosted] and [pollRunnable] so a disabled app never casts anything.
     */
    private fun isAppEnabled(prefs: SharedPreferences, pkg: String): Boolean {
        val ignored = prefs.getStringSet("cast_ignored_apps", emptySet()).orEmpty()
        if (pkg in ignored) return false
        val enabledApps = prefs.getStringSet("cast_enabled_apps", null)
        return enabledApps == null || pkg in enabledApps
    }

    /** Record a decision for the in-app Log screen (deduped: only when the outcome changes). */
    private fun log(sbn: StatusBarNotification, outcome: String, cast: Boolean) {
        lastEventAt = System.currentTimeMillis()
        if (lastOutcome.put(sbn.key, outcome) == outcome) return
        val app = appLabel(sbn.packageName)
        val title = sbn.notification.extras.getCharSequence(NotificationCompat.EXTRA_TITLE)?.toString().orEmpty()
        CastLog.add(app, title, outcome, cast)
    }

    /**
     * Replay every notification currently on the device through the normal cast path, so anything
     * eligible (downloads, navigation, calls, media, live scores) is (re)cast to the island at once.
     * Backs the "Recast all" button. Returns the number of notifications examined.
     */
    fun recastAll(): Int {
        val notifs = activeNotifications ?: return 0
        notifs.forEach { sbn ->
            // Force it past the [lastCastPostTime] redelivery guard: this is a deliberate replay, not
            // a redelivery, and it's the one way to bring back a message/payment card the user
            // dismissed from the island while its source notification is still on the device.
            lastCastPostTime.remove(sbn.key)
            if (sbn.packageName != packageName) runCatching { onNotificationPosted(sbn) }
        }
        return notifs.size
    }

    private fun reload() {
        val prefs = getSharedPreferences(PREFS, 0)
        val enabled = prefs.getBoolean("cast_notifications", false)
        val apps = prefs.getStringSet("cast_enabled_apps", null)
        val now = System.currentTimeMillis()
        // Oldest first: the island puts the last card posted on top, and that should be the newest.
        activeNotifications?.sortedBy { it.postTime }?.forEach { sbn ->
            val eligible = apps == null || sbn.packageName in apps
            if (!enabled || sbn.packageName == packageName || !eligible) return@forEach
            // A reconnect (app update, the system rebinding us) replays everything in the shade.
            // Live notifications should come back; an old message shouldn't pop up again as if new,
            // so it's only marked as already shown.
            val n = sbn.notification
            val live = sbn.isOngoing || n.extras.getInt(NotificationCompat.EXTRA_PROGRESS_MAX, 0) > 0 ||
                n.category == NotificationCompat.CATEGORY_CALL
            if (!live && !sbn.packageName.startsWith(SCORE_PKG_PREFIX) && now - sbn.postTime > RELOAD_FRESH_MS) {
                lastCastPostTime[sbn.key] = sbn.postTime
                return@forEach
            }
            onNotificationPosted(sbn)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) {
            cleanUpRejectedSuperX(sbn)
            return
        }
        // Skip framework/system notifications (USB-debug banner, system UI, etc.) — noise on the island.
        if (sbn.packageName in SYSTEM_PKGS) return
        // A group summary ("3 new messages") stays active alongside the individual notifications it
        // summarizes — casting both means the same event shows up on the island twice (Gmail is the
        // common case). The individual notifications carry the actual content, so skip the summary.
        if ((sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) {
            log(sbn, "skipped — group summary", false); return
        }
        val prefs = getSharedPreferences(PREFS, 0)

        // Per-app exclusion: never cast apps the user explicitly turned off in the Apps tab, for
        // ANY card type — this must run before the media-session branch below, not just the plain
        // one, or a disabled app's media sessions (e.g. YouTube video playback) still slip through.
        if (!isAppEnabled(prefs, sbn.packageName)) {
            log(sbn, "skipped — app turned off in Apps tab", false); return
        }

        val castNotifs = prefs.getBoolean("cast_notifications", false)
        val castMedia = prefs.getBoolean("cast_media_sessions", false)
        if (!castNotifs && !castMedia) { log(sbn, "skipped — casting is off", false); return }

        val n = sbn.notification
        val extras = n.extras
        val token = extras.getParcelable(NotificationCompat.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)
        val template = extras.getString(NotificationCompat.EXTRA_TEMPLATE)
        val isCall = n.category == NotificationCompat.CATEGORY_CALL ||
            template == "android.app.Notification\$CallStyle"

        // Media sessions get the dedicated media path. If the notification has no session token
        // (some players, e.g. Firefox playing a video, omit it), fall back to matching an active
        // MediaSessionManager session by package name — ongoing only, so an app's ordinary
        // notifications (e.g. an X DM while a video plays) aren't mistaken for its player.
        // Resolved even with media casting off: otherwise the player notification falls through
        // below and gets cast as a generic "ongoing" card anyway.
        if (!isCall) {
            val isOngoingNotif = (n.flags and Notification.FLAG_ONGOING_EVENT) != 0
            val controller = token?.let { MediaController(this, it) }
                ?: if (isOngoingNotif) findMediaControllerFallback(sbn.packageName) else null
            if (controller != null) {
                if (!castMedia) { log(sbn, "skipped — media, media casting off", false); return }
                log(sbn, "cast — media player", true)
                MediaCard.post(applicationContext, sbn, controller)
                return
            }
        }
        if (!castNotifs) { log(sbn, "skipped — media only, notifications off", false); return }

        // A ride or delivery on its way: ahead of login codes (a trip line can carry a postcode) and
        // of quiet hours, since it's exactly what someone is waiting on.
        if (!isCall && RideCard.isTrip(sbn)) {
            log(sbn, "cast — ride", true)
            RideCard.post(applicationContext, sbn, appLabel(sbn.packageName))
            return
        }

        // Login / verification codes: ahead of payments (a bank's "code to confirm your €12 payment"
        // is a code, not a receipt) and of the plain-message filter, which would otherwise drop it.
        if (!isCall && prefs.getBoolean("cast_otp", true)) {
            OtpDetector.find(notificationText(sbn))?.let { code ->
                if (lastCastPostTime.put(sbn.key, sbn.postTime) == sbn.postTime) {
                    log(sbn, "skipped — duplicate delivery, unchanged", false); return
                }
                OtpCard.post(applicationContext, sbn, code, appLabel(sbn.packageName))
                log(sbn, "cast — login code", true)
                return
            }
        }

        // Quiet hours / Do Not Disturb: only what someone is actively waiting on gets through —
        // calls, navigation and timers/alarms here; media and login codes were handled above.
        val isOngoingNow = (n.flags and Notification.FLAG_ONGOING_EVENT) != 0
        if (QuietHours.isActive(prefs, currentInterruptionFilter) &&
            !isCall && !isNavigation(sbn, isOngoingNow) && !isTimer(sbn)
        ) {
            log(sbn, "skipped — quiet hours", false); return
        }

        // Flashscore: a match notification becomes (or updates) its score card. Anything else it
        // posts — news, transfers — goes the normal way.
        if (sbn.packageName.startsWith(SCORE_PKG_PREFIX)) {
            when (ScoreCard.onNotification(applicationContext, sbn)) {
                ScoreCard.Result.CAST -> {
                    // With the event in it, so a goal and its scorer arriving after are two lines,
                    // while Flashscore posting the same text again stays one.
                    val what = n.extras.getCharSequence(NotificationCompat.EXTRA_TEXT)?.toString()?.trim().orEmpty()
                    log(sbn, "cast — live score · $what".trimEnd(' ', '·'), true); return
                }
                // Already handled (redelivered, or left over from earlier): not news, not logged.
                ScoreCard.Result.OLD -> return
                ScoreCard.Result.NOT_A_MATCH -> Unit
            }
        }

        // Payments (Wallet, Revolut, PayPal, banks, …) -> Apple-Pay-style success card. This runs
        // BEFORE the "plain message" filter below, because a payment receipt isn't ongoing and has
        // no progress bar, so it would otherwise be dropped.
        detectPayment(sbn)?.let {
            if (lastCastPostTime.put(sbn.key, sbn.postTime) == sbn.postTime) {
                log(sbn, "skipped — duplicate delivery, unchanged", false); return
            }
            castPayment(sbn, it); log(sbn, "cast — payment", true); return
        }

        val isOngoing = (n.flags and Notification.FLAG_ONGOING_EVENT) != 0
        val hasProgress = extras.getInt(NotificationCompat.EXTRA_PROGRESS_MAX, 0) > 0
        val isLive = isOngoing || isCall || hasProgress

        // Turn-by-turn navigation -> vivo's own driving card (template 9) instead of the generic one.
        if (isNavigation(sbn, isOngoing)) {
            log(sbn, "cast — navigation", true)
            NavigationCard.post(applicationContext, sbn)
            return
        }

        // A download finishing is usually an UPDATE to the same notification (progress/ongoing flags
        // just get cleared), not a cancel+repost — browsers do this. If we already have a live card up
        // for this id, this update must still go through even though it now looks like "just a plain
        // message", or the card is stuck showing its last progress forever with nothing to ever tell
        // it otherwise. Only apply the message/silent filters to notifications we haven't already cast.
        val wasLive = IconCache.castIdFor(sbn) in PlaygroundService.activeCardIds

        // Background notices ("Checking for new messages", backups, VPN): ongoing, but on a silenced
        // channel with nothing to follow. As live cards they never hid and sat on the pill for as
        // long as the app kept them. WhatsApp posts its own on channels like these.
        if (isOngoing && !isCall && !hasProgress && isSilent(sbn) &&
            !extras.getBoolean(NotificationCompat.EXTRA_SHOW_CHRONOMETER, false) &&
            prefs.getBoolean("cast_ignore_silent", true)
        ) {
            // A download or upload that just lost its bar: take its card down rather than freeze it.
            if (wasLive) {
                startService(
                    Intent(this, PlaygroundService::class.java)
                        .setAction(PlaygroundService.ACTION_CANCEL)
                        .putExtra("id", IconCache.castIdFor(sbn)),
                )
            }
            log(sbn, "skipped — silent background notice", false); return
        }

        if (!isLive && !wasLive) {
            val includeMessages = prefs.getBoolean("cast_include_messages", false)
            if (!includeMessages) { log(sbn, "skipped — plain message (not a live card)", false); return }

            val ignoreSilent = prefs.getBoolean("cast_ignore_silent", true)
            if (ignoreSilent && isSilent(sbn)) { log(sbn, "skipped — silent notification", false); return }
        }

        val kind = when {
            isCall -> "call"
            hasProgress -> "progress"
            isOngoing -> "ongoing"
            else -> "message"
        }
        if (kind == "message" && lastCastPostTime.put(sbn.key, sbn.postTime) == sbn.postTime) {
            log(sbn, "skipped — duplicate delivery, unchanged", false); return
        }
        log(sbn, "cast — $kind", true)
        if (kind == "message") {
            // A message card that can't be built must never take the listener down with it (a
            // crash here repeats on every reconnect): fall back to the plain generic card.
            runCatching { MessageCard.post(applicationContext, sbn, appLabel(sbn.packageName)) }
                .onFailure {
                    Log.e(TAG, "message card failed, falling back", it)
                    GenericCard.post(applicationContext, sbn, isLive)
                }
        } else {
            GenericCard.post(applicationContext, sbn, isLive)
        }
    }

    /** The user-visible name of [pkg], or the package name if it can't be resolved. */
    private fun appLabel(pkg: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    /**
     * Whether [sbn] is an active turn-by-turn navigation notification, i.e. one [NavigationCard]
     * can build a driving card out of.
     *
     * The category is the reliable signal and picks up Organic Maps, Sygic and the rest for free.
     * The package check is the fallback for builds that don't set it — but it additionally demands
     * real text, because Maps also keeps a bare ongoing "Maps is running" notification alive with
     * nothing in it, and a driving card built from that is an empty card.
     */
    private fun isNavigation(sbn: StatusBarNotification, isOngoing: Boolean): Boolean {
        if (!isOngoing) return false
        val extras = sbn.notification.extras
        if (sbn.notification.category == Notification.CATEGORY_NAVIGATION) return true
        if (sbn.packageName !in NAV_APPS) return false
        val text = extras.getCharSequence(NotificationCompat.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        val subText = extras.getCharSequence(NotificationCompat.EXTRA_SUB_TEXT)?.toString()?.trim().orEmpty()
        return text.isNotBlank() || subText.isNotBlank()
    }

    /** A timer, stopwatch or alarm — by category, or an ongoing countdown chronometer. */
    private fun isTimer(sbn: StatusBarNotification): Boolean {
        val n = sbn.notification
        if (n.category == Notification.CATEGORY_ALARM || n.category == Notification.CATEGORY_STOPWATCH) return true
        val extras = n.extras
        return sbn.isOngoing &&
            extras.getBoolean(NotificationCompat.EXTRA_SHOW_CHRONOMETER, false) &&
            extras.getBoolean(NotificationCompat.EXTRA_CHRONOMETER_COUNT_DOWN, false)
    }

    /** Title + text + big text, as one string to search. */
    private fun notificationText(sbn: StatusBarNotification): String {
        val extras = sbn.notification.extras
        return listOf(
            NotificationCompat.EXTRA_TITLE, NotificationCompat.EXTRA_TEXT, NotificationCompat.EXTRA_BIG_TEXT,
        ).joinToString(" \n ") { extras.getCharSequence(it)?.toString().orEmpty() }
    }

    /**
     * Whether [sbn]'s notification channel is currently below default importance — muted, or set to
     * "silent"/"minimal" by the user. Used to keep muted chat threads off the island.
     */
    private fun isSilent(sbn: StatusBarNotification): Boolean {
        val ranking = Ranking()
        if (currentRanking?.getRanking(sbn.key, ranking) != true) return false
        return ranking.importance < NotificationManager.IMPORTANCE_DEFAULT
    }

    /**
     * Recover a [MediaController] for a media notification that has no session token attached
     * (some browsers, e.g. Firefox playing a video, omit it) by matching the package name against
     * the system's active media sessions. Requires notification-listener access, which this class
     * has by construction.
     */
    private fun findMediaControllerFallback(pkg: String): MediaController? = try {
        val msm = getSystemService(MediaSessionManager::class.java)
        val listenerComponent = ComponentName(this, NotificationCastListener::class.java)
        msm?.getActiveSessions(listenerComponent)?.firstOrNull { it.packageName == pkg }
    } catch (e: SecurityException) {
        null
    } catch (e: Exception) {
        null
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterBatteryReceiver()
        stopSystemCards()
        if (instance === this) instance = null
    }

    /**
     * vivo's OriginOS validates every SuperX card we post (e.g. it can only stack so many live
     * cards on the island at once — confirmed live via `dumpsys notification` while several Play
     * Store downloads were casting concurrently). When it rejects one, it doesn't drop it — it
     * rewrites our own notification in place: the [OriginIslandConstants.SUPERX_TAG] tag is
     * stripped to null, every `notification.superx.*` bundle we built is gone, and the OS stamps
     * its own "SUPERX_VALUE"="false" marker on what's left, then shows THAT as a plain, visible
     * shade notification. Left alone, a card the island couldn't take shows up as a stray
     * notification instead of nothing. Since this is our own repost of our own failed card, cancel
     * it outright the moment it appears — a rejected island should show nothing, not a plain copy.
     */
    private fun cleanUpRejectedSuperX(sbn: StatusBarNotification) {
        if (sbn.tag != null) return // still tagged VIVO_SUPERX_TAG -> a real island, leave it alone
        if (sbn.notification.channelId != PlaygroundService.ORIGIN_CHANNEL_ID) return
        if (sbn.notification.extras.get("SUPERX_VALUE")?.toString() != "false") return
        Log.w(TAG, "vivo rejected a SuperX card (id=${sbn.id}) — cancelling its shade fallback")
        getSystemService(NotificationManager::class.java)?.cancel(sbn.id)
        // Some builds refuse buttons or the driving card: let the service retry it plainer.
        runCatching {
            startService(
                Intent(this, PlaygroundService::class.java)
                    .setAction(PlaygroundService.ACTION_REJECTED)
                    .putExtra("id", sbn.id),
            )
        }
    }

    // --- payments -----------------------------------------------------------------

    private data class PaymentInfo(val amount: String, val merchant: String)

    /**
     * Decide whether a notification is a payment, and pull the amount + merchant out of it.
     * A payment is either a known wallet/bank app carrying an amount or a payment verb, or *any*
     * app whose text pairs a payment verb with a currency amount.
     */
    private fun detectPayment(sbn: StatusBarNotification): PaymentInfo? {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(NotificationCompat.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(NotificationCompat.EXTRA_TEXT)?.toString().orEmpty()
        val big = extras.getCharSequence(NotificationCompat.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val hay = "$title $text $big"

        val amount = AMOUNT.find(hay)?.value?.trim()
        val known = sbn.packageName in PAYMENT_APPS
        val hasVerb = PAYMENT_WORDS.any { hay.contains(it, ignoreCase = true) }
        val isPayment = (known && (amount != null || hasVerb)) || (hasVerb && amount != null)
        if (!isPayment) return null

        // Merchant: if the title itself is the amount (or empty), the payee is usually in the body.
        val titleIsAmount = amount != null && title.contains(amount)
        val merchant = (if (titleIsAmount || title.isBlank()) text else title)
            .trim().ifBlank { "Payment" }.take(40)
        return PaymentInfo(amount.orEmpty(), merchant)
    }

    private fun castPayment(sbn: StatusBarNotification, info: PaymentInfo) {
        val id = IconCache.castIdFor(sbn)
        val appIcon = runCatching {
            val ai = packageManager.getApplicationInfo(sbn.packageName, 0)
            Icon.createWithResource(sbn.packageName, ai.icon)
        }.getOrNull()
        val click = sbn.notification.contentIntent ?: fallbackClickIntent(applicationContext, sbn.packageName)
        PaymentCard.post(applicationContext, id, appIcon, info.amount, info.merchant, appLabel(sbn.packageName), click)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // One of our own island cards went away. The generic cleanup below would cancel the card
        // whose id our notification's key happens to hash to — someone else's. Just tell the service.
        if (sbn.packageName == packageName) {
            if (sbn.tag == OriginIslandConstants.SUPERX_TAG) {
                runCatching {
                    startService(
                        Intent(this, PlaygroundService::class.java)
                            .setAction(PlaygroundService.ACTION_GONE)
                            .putExtra("id", sbn.id),
                    )
                }
            }
            return
        }
        lastEventAt = System.currentTimeMillis()
        lastOutcome.remove(sbn.key)
        lastScorePostTime.remove(sbn.key)
        lastCastPostTime.remove(sbn.key)

        // Score cards are keyed by the match (the notification's tag), not by IconCache.castIdFor.
        if (sbn.packageName.startsWith(SCORE_PKG_PREFIX)) ScoreCard.onRemoved(applicationContext, sbn)

        val id = IconCache.castIdFor(sbn)
        IconCache.clear(id)
        startService(
            Intent(this, PlaygroundService::class.java)
                .setAction(PlaygroundService.ACTION_CANCEL)
                .putExtra("id", id),
        )
    }
}
