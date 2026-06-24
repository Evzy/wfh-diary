namespace WfhDiary.Shared.Dtos;

public sealed record LeavePeriodDto(
    Guid Id,
    string StartDate,
    string EndDate,
    string? Note);
