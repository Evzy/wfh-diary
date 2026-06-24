namespace WfhDiary.Shared.Dtos;

public sealed record UserSettingsDto(
    string DisplayName,
    string NotificationTime,
    string DefaultHomeStart,
    string DefaultHomeFinish,
    decimal DefaultHomeBreakHours,
    string DefaultOfficeStart,
    string DefaultOfficeFinish,
    decimal DefaultOfficeBreakHours);
