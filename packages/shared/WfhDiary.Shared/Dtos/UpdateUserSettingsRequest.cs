namespace WfhDiary.Shared.Dtos;

public sealed record UpdateUserSettingsRequest(
    string? DisplayName,
    string? NotificationTime,
    int? NotificationWorkingDaysMask,
    string? DefaultHomeStart,
    string? DefaultHomeFinish,
    decimal? DefaultHomeBreakHours,
    string? DefaultOfficeStart,
    string? DefaultOfficeFinish,
    decimal? DefaultOfficeBreakHours);
