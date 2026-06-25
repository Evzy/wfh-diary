package dev.wfhdiary.app.util

import dev.wfhdiary.app.data.remote.WorkTemplateDto
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object GeofenceHelper {
    const val DEFAULT_RADIUS_METERS = 200
    const val MIN_RADIUS_METERS = 50
    const val MAX_RADIUS_METERS = 500

    enum class Place { HOME, OFFICE }

    fun isWithinRadius(
        latitude: Double,
        longitude: Double,
        centerLatitude: Double,
        centerLongitude: Double,
        radiusMeters: Int,
    ): Boolean = distanceMeters(latitude, longitude, centerLatitude, centerLongitude) <= radiusMeters

    fun distanceMeters(
        latitude: Double,
        longitude: Double,
        centerLatitude: Double,
        centerLongitude: Double,
    ): Double {
        val earthRadius = 6_371_000.0
        val dLat = Math.toRadians(centerLatitude - latitude)
        val dLon = Math.toRadians(centerLongitude - longitude)
        val lat1 = Math.toRadians(latitude)
        val lat2 = Math.toRadians(centerLatitude)
        val a =
            sin(dLat / 2).pow(2.0) +
                cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2.0)
        val c = 2 * asin(sqrt(a))
        return earthRadius * c
    }

    fun matchingTemplate(
        templates: List<WorkTemplateDto>,
        latitude: Double,
        longitude: Double,
    ): WorkTemplateDto? {
        val withGeofence =
            templates.filter { template ->
                val lat = template.geofenceLatitude
                val lng = template.geofenceLongitude
                val radius = template.geofenceRadiusMeters
                lat != null && lng != null && radius != null &&
                    isWithinRadius(latitude, longitude, lat, lng, radius)
            }
        return withGeofence.firstOrNull { it.isDefault }
            ?: withGeofence.firstOrNull()
    }

    fun matchingPlace(
        homeLatitude: Double?,
        homeLongitude: Double?,
        homeRadiusMeters: Int?,
        officeLatitude: Double?,
        officeLongitude: Double?,
        officeRadiusMeters: Int?,
        latitude: Double,
        longitude: Double,
    ): Place? {
        val atHome =
            homeLatitude != null && homeLongitude != null && homeRadiusMeters != null &&
                isWithinRadius(latitude, longitude, homeLatitude, homeLongitude, homeRadiusMeters)
        val atOffice =
            officeLatitude != null && officeLongitude != null && officeRadiusMeters != null &&
                isWithinRadius(latitude, longitude, officeLatitude, officeLongitude, officeRadiusMeters)
        return when {
            atOffice -> Place.OFFICE
            atHome -> Place.HOME
            else -> null
        }
    }
}
