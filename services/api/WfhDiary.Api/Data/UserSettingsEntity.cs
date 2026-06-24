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
}
