namespace WfhDiary.Shared.Dtos;

public sealed record UpdateWorkTemplateRequest(
    string? Label,
    int? SortOrder,
    string? StartTime,
    string? FinishTime,
    decimal? BreakHours,
    string? Location,
    string? Description,
    bool? IsDefault,
    double? GeofenceLatitude,
    double? GeofenceLongitude,
    int? GeofenceRadiusMeters);
