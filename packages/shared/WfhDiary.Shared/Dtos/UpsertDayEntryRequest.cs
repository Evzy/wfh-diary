namespace WfhDiary.Shared.Dtos;

public sealed record UpsertDayEntryRequest(
    string? StartTime,
    string? FinishTime,
    decimal? BreakHours,
    string? Description,
    string? Location);
