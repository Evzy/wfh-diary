namespace WfhDiary.Shared.Dtos;

public sealed record CreateLeaveRequest(
    string StartDate,
    string EndDate,
    string? Note);
