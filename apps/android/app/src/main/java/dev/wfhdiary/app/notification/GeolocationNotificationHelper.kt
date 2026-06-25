package dev.wfhdiary.app.notification

import dev.wfhdiary.app.WfhDiaryApplication
import dev.wfhdiary.app.data.remote.UserSettingsDto
import dev.wfhdiary.app.util.GeofenceHelper
import dev.wfhdiary.app.util.LocationHelper

object GeolocationNotificationHelper {
    suspend fun matchingPlace(
        app: WfhDiaryApplication,
        settings: UserSettingsDto,
    ): GeofenceHelper.Place? {
        if (!hasGeofencesConfigured(settings)) return null
        val location = LocationHelper.getCurrentLocation(app) ?: return null
        return GeofenceHelper.matchingPlace(
            homeLatitude = settings.homeGeofenceLatitude,
            homeLongitude = settings.homeGeofenceLongitude,
            homeRadiusMeters = settings.homeGeofenceRadiusMeters,
            officeLatitude = settings.officeGeofenceLatitude,
            officeLongitude = settings.officeGeofenceLongitude,
            officeRadiusMeters = settings.officeGeofenceRadiusMeters,
            latitude = location.latitude,
            longitude = location.longitude,
        )
    }

    fun hasGeofencesConfigured(settings: UserSettingsDto): Boolean =
        (settings.homeGeofenceLatitude != null &&
            settings.homeGeofenceLongitude != null &&
            settings.homeGeofenceRadiusMeters != null) ||
            (settings.officeGeofenceLatitude != null &&
                settings.officeGeofenceLongitude != null &&
                settings.officeGeofenceRadiusMeters != null)

    fun shouldSkipNotification(
        settings: UserSettingsDto,
        place: GeofenceHelper.Place,
    ): Boolean =
        when (place) {
            GeofenceHelper.Place.HOME -> settings.geolocationSkipNotificationAtHome
            GeofenceHelper.Place.OFFICE -> settings.geolocationSkipNotificationAtOffice
        }

    suspend fun applyPlace(
        app: WfhDiaryApplication,
        date: String,
        settings: UserSettingsDto,
        place: GeofenceHelper.Place,
    ) {
        val action =
            when (place) {
                GeofenceHelper.Place.HOME -> MissedNotificationDefault.HOME
                GeofenceHelper.Place.OFFICE -> MissedNotificationDefault.OFFICE
            }
        NotificationDayActions.apply(app, date, action, settings)
    }

    suspend fun applyAwayFallback(
        app: WfhDiaryApplication,
        date: String,
        settings: UserSettingsDto,
    ) {
        NotificationDayActions.apply(app, date, settings.missedNotificationAwayDefault, settings)
    }
}
