package com.originisle.android.service

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
import com.originisle.android.cards.GenericCard
import com.originisle.android.cards.MediaCard
import com.originisle.android.cards.NavigationCard
import com.originisle.android.cards.PaymentCard
import com.originisle.android.cards.SportsCard
import com.originisle.android.cards.fallbackClickIntent
import com.originisle.android.island.PlaygroundService
import com.originisle.android.log.CastLog
import com.originisle.android.sports.SportsFeed
import com.originisle.android.sports.SportsParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Listens to every notification on the device and re-casts eligible ones into the OriginIsland.
 *
 * This class owns only the CAST/SKIP DECISION for each notification — which kind of card (if any)
 * it becomes, and why one wasn't posted. The actual card is built and posted by the type-specific
 * object in `cards/` ([GenericCard], [MediaCard], [PaymentCard], [SportsCard]); [IconCache] is the
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

        /** Score apps whose live-match notifications get parsed into a football card with crests. */
        private val SCORE_APPS = setOf(
            "com.google.android.googlequicksearchbox", // Google (favourite-team live scores)
            "com.sofascore.results",       // SofaScore
            "com.mobilefootie.fotmobpro",  // FotMob
            "com.mobilefootie.wc2010",     // FotMob (free)
            "de.motain.iliga",             // OneFootball
            "com.scores365",               // 365Scores
            "com.livescore",               // LiveScore
        )

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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Last-logged outcome per notification key, so repeated updates don't flood the log. */
    private val lastOutcome = ConcurrentHashMap<String, String>()

    /**
     * Last successfully-resolved crest icons per match id. A goal update's notification text can
     * name a team slightly differently than the kickoff notification did (or a lookup can just fail
     * transiently) — refetching fresh every time then means a badge that WAS showing can vanish the
     * moment its team scores. Keep the last good icon per side and only replace it when a fresh fetch
     * actually finds one, instead of overwriting a known-good badge with a failed lookup's null.
     */
    private val lastMatchIcons = ConcurrentHashMap<Int, SportsFeed.MatchIcons>()

    /**
     * Last-seen [StatusBarNotification.getPostTime] per score-app notification key. onNotificationPosted
     * is meant to fire on every update, but OriginOS can visibly delay/batch that delivery for a
     * specific app — the score card was observed sitting stale until some UNRELATED notification came
     * in and (presumably) flushed the batch. Polled the same way media/chronometer already are, but
     * gated on postTime actually changing so this doesn't reprocess (and repost) unchanged content
     * every second — a live score's own post frequency is way under 1/sec anyway.
     */
    private val lastScorePostTime = ConcurrentHashMap<String, Long>()

    private val pollRunnable = object : Runnable {
        override fun run() {
            try {
                val prefs = getSharedPreferences(PREFS, 0)
                val castNotifs = prefs.getBoolean("cast_notifications", false)
                val castMedia = prefs.getBoolean("cast_media_sessions", false)
                activeNotifications?.forEach { sbn ->
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
                    } else if (castNotifs && sbn.packageName in SCORE_APPS &&
                        isAppEnabled(prefs, sbn.packageName) &&
                        lastScorePostTime.put(sbn.key, sbn.postTime) != sbn.postTime
                    ) {
                        onNotificationPosted(sbn) // catch a score update onNotificationPosted was slow to deliver
                    }
                }
                SportsCard.refresh(applicationContext)
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
        pollHandler.post(pollRunnable)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
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
            if (sbn.packageName != packageName) runCatching { onNotificationPosted(sbn) }
        }
        return notifs.size
    }

    private fun reload() {
        val prefs = getSharedPreferences(PREFS, 0)
        val enabled = prefs.getBoolean("cast_notifications", false)
        val apps = prefs.getStringSet("cast_enabled_apps", null)
        activeNotifications?.forEach { sbn ->
            val eligible = apps == null || sbn.packageName in apps
            if (enabled && sbn.packageName != packageName && eligible) onNotificationPosted(sbn)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
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
        // MediaSessionManager session by package name.
        if (castMedia && !isCall) {
            val controller = token?.let { MediaController(this, it) }
                ?: findMediaControllerFallback(sbn.packageName)
            if (controller != null) {
                log(sbn, "cast — media player", true)
                MediaCard.post(applicationContext, sbn, controller)
                return
            }
        }
        if (!castNotifs) { log(sbn, "skipped — media only, notifications off", false); return }

        // Score apps: if the notification parses as a match, post a football card with crests.
        // If it doesn't (Google also posts news/weather/etc.), fall through to the normal path.
        if (sbn.packageName in SCORE_APPS && handleScore(sbn)) { log(sbn, "cast — live score", true); return }

        // Payments (Wallet, Revolut, PayPal, banks, …) -> Apple-Pay-style success card. This runs
        // BEFORE the "plain message" filter below, because a payment receipt isn't ongoing and has
        // no progress bar, so it would otherwise be dropped.
        detectPayment(sbn)?.let { castPayment(sbn, it); log(sbn, "cast — payment", true); return }

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
        log(sbn, "cast — $kind", true)
        GenericCard.post(applicationContext, sbn, isLive)
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
        if (instance === this) instance = null
        scope.cancel()
    }

    /**
     * A score app's live-match notification -> parse it, fetch both club crests, and post/refresh
     * the football card. The same cast id is reused so score updates refresh one card, and the card
     * is cancelled when the notification is dismissed. Falls back to a generic cast if unparseable.
     */
    private fun handleScore(sbn: StatusBarNotification): Boolean {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(NotificationCompat.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(NotificationCompat.EXTRA_TEXT)?.toString().orEmpty()
        val big = extras.getCharSequence(NotificationCompat.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val sub = extras.getCharSequence(NotificationCompat.EXTRA_SUB_TEXT)?.toString().orEmpty()

        val match = SportsParser.parse(title, text, big, sub) ?: return false
        // Identity-based on the teams, NOT IconCache.castIdFor(sbn) — a post-match "recap" notification
        // (sent after the user dismisses the live one, or just later) is a genuinely different
        // notification object with a different key, so a notification-identity id would treat it as a
        // brand new match and pop the pill back up. Keying off the teams instead means it always lands
        // on the same card, so an already-cleared match's recap just re-triggers that card's own
        // (short) auto-clear instead of resurrecting it as new.
        val id = matchCastId(match.home, match.away)
        val competition = sub.ifBlank { "Live" }
        val clickResp = sbn.notification.contentIntent ?: fallbackClickIntent(applicationContext, sbn.packageName)
        scope.launch {
            val fresh = SportsFeed.iconsFor(match.home, match.away, competition)
            val prev = lastMatchIcons[id]
            val icons = SportsFeed.MatchIcons(
                home = fresh.home ?: prev?.home,
                away = fresh.away ?: prev?.away,
                center = fresh.center ?: prev?.center,
            )
            lastMatchIcons[id] = icons
            SportsCard.post(
                context = applicationContext,
                home = match.home, homeScore = match.homeScore,
                away = match.away, awayScore = match.awayScore,
                clock = match.minute,
                competition = competition,
                id = id,
                homeLogo = icons.home, awayLogo = icons.away, centerLogo = icons.center,
                clickResp = clickResp,
            )
        }
        return true
    }

    /** Stable per-match cast id (band 30000-34999), independent of which notification reported it. */
    private fun matchCastId(home: String, away: String): Int =
        ("$home|$away".lowercase().hashCode() and Int.MAX_VALUE) % 5000 + 30000

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
        lastEventAt = System.currentTimeMillis()
        lastOutcome.remove(sbn.key)
        lastScorePostTime.remove(sbn.key)

        // Score cards are keyed by team names (matchCastId), not by this notification's own identity
        // (see handleScore) — cancelling by IconCache.castIdFor(sbn) here would target the wrong id and
        // leave the actual card stuck up. Route through SportsCard.clear() too, so its own auto-clear
        // timer/state gets torn down, not just the island post.
        if (sbn.packageName in SCORE_APPS) {
            val extras = sbn.notification.extras
            val title = extras.getCharSequence(NotificationCompat.EXTRA_TITLE)?.toString().orEmpty()
            val text = extras.getCharSequence(NotificationCompat.EXTRA_TEXT)?.toString().orEmpty()
            val big = extras.getCharSequence(NotificationCompat.EXTRA_BIG_TEXT)?.toString().orEmpty()
            val sub = extras.getCharSequence(NotificationCompat.EXTRA_SUB_TEXT)?.toString().orEmpty()
            val match = SportsParser.parse(title, text, big, sub)
            if (match != null) {
                val id = matchCastId(match.home, match.away)
                lastMatchIcons.remove(id)
                SportsCard.clear(applicationContext, id)
                return
            }
        }

        val id = IconCache.castIdFor(sbn)
        IconCache.clear(id)
        lastMatchIcons.remove(id)
        startService(
            Intent(this, PlaygroundService::class.java)
                .setAction(PlaygroundService.ACTION_CANCEL)
                .putExtra("id", id),
        )
    }
}
