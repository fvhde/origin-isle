package com.originisle.android.otp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OtpDetectorTest {

    @Test fun plainCode() = assertEquals("482913", OtpDetector.find("Your verification code is 482913. Don't share it."))
    @Test fun codeBeforeKeyword() = assertEquals("482913", OtpDetector.find("482913 is your login code"))
    @Test fun googlePrefix() = assertEquals("123456", OtpDetector.find("G-123456 is your Google verification code."))
    @Test fun splitWithSpace() = assertEquals("123456", OtpDetector.find("Use code 123 456 to sign in"))
    @Test fun splitWithDash() = assertEquals("123456", OtpDetector.find("Your WhatsApp code: 123-456"))
    @Test fun fourDigits() = assertEquals("4821", OtpDetector.find("Uber code: 4821"))
    @Test fun french() = assertEquals("5531", OtpDetector.find("Code de vérification : 5531"))
    @Test fun spanish() = assertEquals("774120", OtpDetector.find("Tu código es 774120"))
    @Test fun german() = assertEquals("90817", OtpDetector.find("Ihr Bestätigungscode lautet 90817"))
    @Test fun chinese() = assertEquals("605218", OtpDetector.find("您的验证码为605218，5分钟内有效"))

    @Test fun noKeyword() = assertNull(OtpDetector.find("Meet me at 1830 near gate 4"))
    @Test fun chatNumber() = assertNull(OtpDetector.find("I paid 1500 for the tickets"))
    @Test fun price() = assertNull(OtpDetector.find("Confirm your payment of €1299"))
    @Test fun priceAfter() = assertNull(OtpDetector.find("Confirm your payment of 1299 €"))
    @Test fun decimal() = assertNull(OtpDetector.find("Confirm 1299.50 to verify"))
    @Test fun time() = assertNull(OtpDetector.find("Log in before 12:30"))
    @Test fun year() = assertNull(OtpDetector.find("Code valid until 2026"))
    @Test fun phone() = assertNull(OtpDetector.find("Verify by calling +33 612 345 678"))
    @Test fun keywordTooFar() = assertNull(
        OtpDetector.find("Your code was sent. " + "x".repeat(80) + " Order 88123 shipped"),
    )
}
