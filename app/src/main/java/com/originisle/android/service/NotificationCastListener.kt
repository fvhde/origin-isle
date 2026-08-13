package com.originisle.android.service

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.Icon
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.NotificationListenerService.Ranking
import android.service.notification.NotificationListenerService.RankingMap
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.originisle.android.cards.GenericCard
import com.originisle.android.cards.MediaCard
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

    companion object {
        /** The bound listener instance, or null if the service isn't connected. */
        @Volatile
        var instance: NotificationCastListener? = null

        /** Health signals for the in-app status line. 0 = never. */
        @Volatile
        var connectedAt: Long = 0L
        @Volatile
        var lastEventAt: Long = 0L

        private const val PREFS = "experimental_prefs"
        private const val TAG = "NotificationCast"

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
        val app = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        }.getOrDefault(sbn.packageName)
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

    /**
     * Check if a notification is silent (low/min importance, ambient, or no sound/alert).
     * Alerting (non-silent) notifications have importance >= IMPORTANCE_DEFAULT.
     */
    private fun isSilent(sbn: StatusBarNotification, rankingMap: RankingMap? = null): Boolean {
        val ranking = Ranking()
        val map = rankingMap ?: currentRanking
        if (map != null && map.getRanking(sbn.key, ranking)) {
            val importance = ranking.importance
            if (importance != NotificationManager.IMPORTANCE_UNSPECIFIED) {
                return importance < NotificationManager.IMPORTANCE_DEFAULT
            }
            return ranking.isAmbient
        }
        return false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        onNotificationPosted(sbn, null)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap?) {
        if (sbn.packageName == packageName) return
        // Skip framework/system notifications (USB-debug banner, system UI, etc.) — noise on the island.
        if (sbn.packageName in SYSTEM_PKGS) return
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

        // Skip silent notifications if enabled — only alerting / non-silent notifications belong on the island.
        val ignoreSilent = prefs.getBoolean("cast_ignore_silent", true)
        if (ignoreSilent && isSilent(sbn, rankingMap)) {
            log(sbn, "skipped — silent notification", false); return
        }

        // Score apps: if the notification parses as a match, post a football card with crests.
        // If it doesn't (Google also posts news/weather/etc.), fall through to the normal path.
        if (sbn.packageName in SCORE_APPS && handleScore(sbn)) { log(sbn, "cast — live score", true); return }

        // Payments (Wallet, Revolut, PayPal, banks, …) -> Apple-Pay-style success card. This runs
        // BEFORE the "plain message" filter below, because a payment receipt isn't ongoing and has
        // no progress bar, so it would otherwise be dropped.
        detectPayment(sbn)?.let { castPayment(sbn, it); log(sbn, "cast — payment", true); return }

        // Only "live" notifications belong on the island: navigation, calls, chronometers,
        // or progress bars. Plain chat messages (e.g. WhatsApp texts) are skipped unless the user
        // opts them in. Static background services (VPNs, foreground service daemons) are skipped.
        val includeMessages = prefs.getBoolean("cast_include_messages", false)
        val isOngoing = (n.flags and Notification.FLAG_ONGOING_EVENT) != 0
        val hasProgress = extras.getInt(NotificationCompat.EXTRA_PROGRESS_MAX, 0) > 0
        val showChrono = extras.getBoolean(NotificationCompat.EXTRA_SHOW_CHRONOMETER, false)
        val isNav = n.category == NotificationCompat.CATEGORY_NAVIGATION ||
            sbn.packageName == "com.google.android.apps.maps" ||
            sbn.packageName == "com.autonavi.minimap" ||
            sbn.packageName == "com.baidu.BaiduMap" ||
            sbn.packageName == "com.waze"
        val isLiveOngoing = isCall || isNav || hasProgress || (showChrono && n.`when` > 0)
        val isService = n.category == Notification.CATEGORY_SERVICE ||
            n.category == Notification.CATEGORY_SYSTEM ||
            n.category == Notification.CATEGORY_STATUS

        // Skip static background services that are not active live activities
        if (isService && !isLiveOngoing) {
            log(sbn, "skipped — background service", false); return
        }

        if (!includeMessages && !isOngoing && !isLiveOngoing) {
            log(sbn, "skipped — plain message (not a live card)", false); return
        }

        val kind = when {
            isCall -> "call"
            isNav -> "navigation"
            hasProgress -> "progress"
            showChrono -> "timer"
            isOngoing -> "ongoing"
            else -> "message"
        }
        log(sbn, "cast — $kind", true)
        GenericCard.post(applicationContext, sbn)
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
        val id = IconCache.castIdFor(sbn)
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
        val appLabel = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        }.getOrDefault(sbn.packageName)
        val click = sbn.notification.contentIntent ?: fallbackClickIntent(applicationContext, sbn.packageName)
        PaymentCard.post(applicationContext, id, appIcon, info.amount, info.merchant, appLabel, click)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        lastEventAt = System.currentTimeMillis()
        lastOutcome.remove(sbn.key)
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
