package dev.wfhdiary.app.util

import dev.wfhdiary.app.data.remote.WorkTemplateDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    fun matchingTemplate_prefersDefaultWhenMultipleMatch() {
        val templates =
            listOf(
                template("a", isDefault = false, lat = -33.8688, lng = 151.2093),
                template("b", isDefault = true, lat = -33.8688, lng = 151.2093),
            )
        val match = GeofenceHelper.matchingTemplate(templates, -33.8688, 151.2093)
        assertNotNull(match)
        assertEquals("b", match!!.label)
    }

    @Test
    fun matchingTemplate_returnsNullWhenNoGeofence() {
        val templates = listOf(template("plain", isDefault = true))
        assertNull(GeofenceHelper.matchingTemplate(templates, -33.8688, 151.2093))
    }

    @Test
    fun matchingTemplate_returnsNullWhenOutsideRadius() {
        val templates = listOf(template("office", lat = -33.8688, lng = 151.2093))
        assertNull(GeofenceHelper.matchingTemplate(templates, -37.8136, 144.9631))
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

    private fun template(
        label: String,
        isDefault: Boolean = false,
        lat: Double? = null,
        lng: Double? = null,
    ) = WorkTemplateDto(
        id = label,
        label = label,
        isDefault = isDefault,
        geofenceLatitude = lat,
        geofenceLongitude = lng,
        geofenceRadiusMeters = if (lat != null && lng != null) 200 else null,
    )
}
