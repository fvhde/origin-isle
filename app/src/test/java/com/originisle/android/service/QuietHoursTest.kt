package com.originisle.android.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuietHoursTest {
    private fun m(h: Int, min: Int = 0) = h * 60 + min

    @Test fun overnightInside() {
        assertTrue(QuietHours.inWindow(m(23), m(22), m(7)))
        assertTrue(QuietHours.inWindow(m(3), m(22), m(7)))
        assertTrue(QuietHours.inWindow(m(22), m(22), m(7)))
    }

    @Test fun overnightOutside() {
        assertFalse(QuietHours.inWindow(m(7), m(22), m(7)))
        assertFalse(QuietHours.inWindow(m(12), m(22), m(7)))
        assertFalse(QuietHours.inWindow(m(21, 59), m(22), m(7)))
    }

    @Test fun sameDay() {
        assertTrue(QuietHours.inWindow(m(13), m(12), m(14)))
        assertFalse(QuietHours.inWindow(m(14), m(12), m(14)))
        assertFalse(QuietHours.inWindow(m(11), m(12), m(14)))
    }

    @Test fun equalIsOff() = assertFalse(QuietHours.inWindow(m(10), m(10), m(10)))
}
