using WfhDiary.Api.Services;

namespace WfhDiary.Api.Data;

public sealed class UserSettingsEntity
{
    public string OwnerUserId { get; set; } = "";
    public string DisplayName { get; set; } = "YOUR NAME HERE";
    public int NotificationTimeMinutes { get; set; } = 8 * 60;
    public int DefaultHomeStartMinutes { get; set; } = 8 * 60;
    public int DefaultHomeFinishMinutes { get; set; } = 17 * 60;
    public decimal DefaultHomeBreakHours { get; set; } = 1m;
    public int DefaultOfficeStartMinutes { get; set; } = 8 * 60;
    public int DefaultOfficeFinishMinutes { get; set; } = 17 * 60;
    public decimal DefaultOfficeBreakHours { get; set; } = 1m;
    /// <summary>Bitmask of ISO weekdays (Mon=bit0 … Sun=bit6) when morning reminders are sent.</summary>
    public int NotificationWorkingDaysMask { get; set; } = WorkingDaysMask.Default;
    /// <summary>None, Home, Office, or Leave — applied if the user ignores the morning reminder.</summary>
    public string MissedNotificationDefault { get; set; } = "None";
    public int MissedNotificationApplyAtMinutes { get; set; } = 12 * 60;
    public double? HomeGeofenceLatitude { get; set; }
    public double? HomeGeofenceLongitude { get; set; }
    public int? HomeGeofenceRadiusMeters { get; set; }
    public double? OfficeGeofenceLatitude { get; set; }
    public double? OfficeGeofenceLongitude { get; set; }
    public int? OfficeGeofenceRadiusMeters { get; set; }
}
