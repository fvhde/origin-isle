package com.originisle.android.otp

/**
 * Pulls a one-time login / verification code out of a notification's text ("Your code is 482913",
 * "G-123456 is your Google verification code", "Code de vérification : 1234").
 *
 * A code only counts when a code-ish keyword sits near it — a bare number in a chat message, an
 * order number or a price must never turn into a login-code card.
 */
object OtpDetector {

    /** How far (in chars) a candidate may sit from the nearest keyword and still count. */
    private const val MAX_DISTANCE = 60

    private val KEYWORD = Regex(
        """\b(?:code|codes|otp|passcode|pin|verif\w*|one[- ]time|2fa|mfa|login|log[- ]in|sign[- ]in|""" +
            """authenticat\w*|confirm\w*|c[oó]digo|codice|kod|bestätigung\w*|v[ée]rification|""" +
            """verificaci[oó]n|verifica|password)\b|验证码|認証|인증""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * 4–8 digits, or two groups of 3–4 split by a space/dash ("123 456", "123-456"), optionally
     * with Google's "G-" prefix. Not part of a longer number, a decimal, a time, a price, a
     * percentage or a phone number.
     */
    private val CANDIDATE = Regex(
        """(?<![\d.,:/#+€$£¥-])(?<!\d[ -])(?:G-)?(\d{3,4}[ -]\d{3,4}|\d{4,8})""" +
            """(?!\d|[.,:]\d|[ -]\d|\s?[%€$£¥]|\s?(?:EUR|USD|GBP|CHF)\b)""",
        RegexOption.IGNORE_CASE,
    )

    /** The code (digits only) in [text], or null if it doesn't read like a login code. */
    fun find(text: String): String? {
        val keywords = KEYWORD.findAll(text).map { it.range }.toList()
        if (keywords.isEmpty()) return null
        return CANDIDATE.findAll(text)
            .map { m -> m to keywords.minOf { k -> distance(m.range, k) } }
            .filter { (m, d) -> d <= MAX_DISTANCE && !looksLikeYear(m.groupValues[1]) }
            .minByOrNull { (_, d) -> d }
            ?.first?.groupValues?.get(1)
            ?.filter(Char::isDigit)
    }

    /** Gap between two ranges, 0 if they touch or overlap. */
    private fun distance(a: IntRange, b: IntRange): Int = when {
        a.last < b.first -> b.first - a.last
        b.last < a.first -> a.first - b.last
        else -> 0
    }

    /** "2026" next to "code" is far more often a date ("code valid until 2026") than a code. */
    private fun looksLikeYear(s: String): Boolean =
        s.length == 4 && s.toIntOrNull()?.let { it in 1990..2099 } == true
}
