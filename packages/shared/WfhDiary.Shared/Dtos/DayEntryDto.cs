namespace WfhDiary.Shared.Dtos;

public sealed record DayEntryDto(
    string Date,
    string DayName,
    string? StartTime,
    string? FinishTime,
    decimal? BreakHours,
    decimal TotalHours,
    string? Description,
    string? Location);
