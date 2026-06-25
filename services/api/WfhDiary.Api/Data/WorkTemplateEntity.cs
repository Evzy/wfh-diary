namespace WfhDiary.Api.Data;

public sealed class WorkTemplateEntity
{
    public Guid Id { get; set; }
    public string OwnerUserId { get; set; } = "";
    public string Label { get; set; } = "";
    public int SortOrder { get; set; }
    public int? StartTimeMinutes { get; set; }
    public int? FinishTimeMinutes { get; set; }
    public decimal? BreakHours { get; set; }
    public string? Location { get; set; }
    public string? Description { get; set; }
    public bool IsDefault { get; set; }
    public double? GeofenceLatitude { get; set; }
    public double? GeofenceLongitude { get; set; }
    public int? GeofenceRadiusMeters { get; set; }
}
