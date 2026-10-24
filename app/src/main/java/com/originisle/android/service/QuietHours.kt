package com.originisle.android.service

import android.app.NotificationManager
import android.content.SharedPreferences
import java.util.Calendar

/**
 * Quiet hours: while active, only the cards someone is actively waiting on still reach the island
 * (calls, navigation, timers/alarms, login codes, media, charging) — see the listener's gate.
 *
 * Two independent triggers, either one is enough:
 *  - "Follow Do Not Disturb" — the system's DND is on (any filter stricter than "all").
 *  - A daily schedule, minutes since midnight, which may wrap past midnight (22:00 → 07:00).
 */
object QuietHours {
    const val DEFAULT_START = 22 * 60
    const val DEFAULT_END = 7 * 60

    fun isActive(prefs: SharedPreferences, interruptionFilter: Int, now: Calendar = Calendar.getInstance()): Boolean {
        val dnd = prefs.getBoolean("cast_quiet_dnd", false) &&
            interruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL
        if (dnd) return true
        if (!prefs.getBoolean("cast_quiet_schedule", false)) return false
        val nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        return inWindow(
            nowMin,
            prefs.getInt("cast_quiet_start", DEFAULT_START),
            prefs.getInt("cast_quiet_end", DEFAULT_END),
        )
    }

    /** Whether [nowMin] falls in [startMin, endMin); wraps past midnight when start > end. Equal = never. */
    fun inWindow(nowMin: Int, startMin: Int, endMin: Int): Boolean = when {
        startMin == endMin -> false
        startMin < endMin -> nowMin in startMin until endMin
        else -> nowMin >= startMin || nowMin < endMin
    }

    /** "22:00" */
    fun format(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)
}
