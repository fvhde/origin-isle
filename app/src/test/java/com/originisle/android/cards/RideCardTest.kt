package com.originisle.android.cards

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RideCardTest {

    @Test fun etaFromTitle() {
        assertEquals("23 min", RideCard.eta("Dropoff in 23 min"))
        assertEquals("3 min", RideCard.eta("Driver arriving in 3 min"))
        assertEquals("12 min", RideCard.eta("Arrivée dans 12 min"))
        assertEquals("1 h 5 min", RideCard.eta("Dropoff in 1 h 5 min"))
        assertEquals("5min", RideCard.eta("Pickup in 5min"))
        assertEquals("03:52", RideCard.eta("Arriving at 03:52"))
    }

    @Test fun noEtaInAddresses() {
        assertNull(RideCard.eta("Heading to 11 Avenue Léon Marchand, Thiais 94320"))
        assertNull(RideCard.eta("Your driver has arrived"))
    }

    @Test fun statusIsTheLineWithoutItsTime() {
        assertEquals("Dropoff", RideCard.status("Dropoff in 23 min", "23 min"))
        assertEquals("Driver arriving", RideCard.status("Driver arriving in 3 min", "3 min"))
        assertEquals("Arrivée", RideCard.status("Arrivée dans 12 min", "12 min"))
        assertEquals("Arriving", RideCard.status("Arriving at 03:52", "03:52"))
        assertEquals("to pickup", RideCard.status("23 min to pickup", "23 min"))
        assertEquals("Your driver has arrived", RideCard.status("Your driver has arrived", ""))
    }
}
