package dev.wfhdiary.app.ui.settings

data class GeolocationSettingsDraft(
    val missedNotificationAwayDefault: String = "Office",
    val skipNotificationAtHome: Boolean = false,
    val skipNotificationAtOffice: Boolean = false,
    val homeGeofenceLatitude: Double? = null,
    val homeGeofenceLongitude: Double? = null,
    val homeGeofenceRadiusMeters: Int? = null,
    val officeGeofenceLatitude: Double? = null,
    val officeGeofenceLongitude: Double? = null,
    val officeGeofenceRadiusMeters: Int? = null,
    val clearHomeGeofence: Boolean = false,
    val clearOfficeGeofence: Boolean = false,
)

fun SettingsFormState.toGeolocationDraft() =
    GeolocationSettingsDraft(
        missedNotificationAwayDefault = missedNotificationAwayDefault,
        skipNotificationAtHome = geolocationSkipNotificationAtHome,
        skipNotificationAtOffice = geolocationSkipNotificationAtOffice,
        homeGeofenceLatitude = homeGeofenceLatitude,
        homeGeofenceLongitude = homeGeofenceLongitude,
        homeGeofenceRadiusMeters = homeGeofenceRadiusMeters,
        officeGeofenceLatitude = officeGeofenceLatitude,
        officeGeofenceLongitude = officeGeofenceLongitude,
        officeGeofenceRadiusMeters = officeGeofenceRadiusMeters,
    )

fun GeolocationSettingsDraft.applyTo(form: SettingsFormState): SettingsFormState =
    form.copy(
        missedNotificationAwayDefault = missedNotificationAwayDefault,
        geolocationSkipNotificationAtHome = skipNotificationAtHome,
        geolocationSkipNotificationAtOffice = skipNotificationAtOffice,
        homeGeofenceLatitude = if (clearHomeGeofence) null else homeGeofenceLatitude,
        homeGeofenceLongitude = if (clearHomeGeofence) null else homeGeofenceLongitude,
        homeGeofenceRadiusMeters = if (clearHomeGeofence) null else homeGeofenceRadiusMeters,
        officeGeofenceLatitude = if (clearOfficeGeofence) null else officeGeofenceLatitude,
        officeGeofenceLongitude = if (clearOfficeGeofence) null else officeGeofenceLongitude,
        officeGeofenceRadiusMeters = if (clearOfficeGeofence) null else officeGeofenceRadiusMeters,
    )
