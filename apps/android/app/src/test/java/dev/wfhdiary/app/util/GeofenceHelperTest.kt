package dev.wfhdiary.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeofenceHelperTest {
    @Test
    fun isWithinRadius_samePoint_isTrue() {
        assertTrue(GeofenceHelper.isWithinRadius(-33.8688, 151.2093, -33.8688, 151.2093, 200))
    }

    @Test
    fun isWithinRadius_farPoint_isFalse() {
        assertFalse(GeofenceHelper.isWithinRadius(-33.8688, 151.2093, -37.8136, 144.9631, 200))
    }

    @Test
    fun matchingPlace_prefersOfficeWhenBothMatch() {
        val place =
            GeofenceHelper.matchingPlace(
                homeLatitude = -33.8688,
                homeLongitude = 151.2093,
                homeRadiusMeters = 500,
                officeLatitude = -33.8688,
                officeLongitude = 151.2093,
                officeRadiusMeters = 500,
                latitude = -33.8688,
                longitude = 151.2093,
            )
        assertEquals(GeofenceHelper.Place.OFFICE, place)
    }

    @Test
    fun matchingPlace_returnsHomeWhenOnlyHomeConfigured() {
        val place =
            GeofenceHelper.matchingPlace(
                homeLatitude = -33.8688,
                homeLongitude = 151.2093,
                homeRadiusMeters = 200,
                officeLatitude = null,
                officeLongitude = null,
                officeRadiusMeters = null,
                latitude = -33.8688,
                longitude = 151.2093,
            )
        assertEquals(GeofenceHelper.Place.HOME, place)
    }
}
