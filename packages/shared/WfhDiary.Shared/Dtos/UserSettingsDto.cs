namespace WfhDiary.Shared.Dtos;

public sealed record UserSettingsDto(
    string DisplayName,
    string NotificationTime,
    int NotificationWorkingDaysMask,
    string MissedNotificationDefault,
    string MissedNotificationApplyAt,
    string MissedNotificationAwayDefault,
    bool GeolocationSkipNotificationAtHome,
    bool GeolocationSkipNotificationAtOffice,
    string DefaultHomeStart,
    string DefaultHomeFinish,
    decimal DefaultHomeBreakHours,
    string DefaultOfficeStart,
    string DefaultOfficeFinish,
    decimal DefaultOfficeBreakHours,
    double? HomeGeofenceLatitude,
    double? HomeGeofenceLongitude,
    int? HomeGeofenceRadiusMeters,
    double? OfficeGeofenceLatitude,
    double? OfficeGeofenceLongitude,
    int? OfficeGeofenceRadiusMeters);
