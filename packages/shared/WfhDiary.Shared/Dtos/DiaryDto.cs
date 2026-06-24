namespace WfhDiary.Shared.Dtos;

public sealed record DiaryDto(
    int FyYear,
    string FyStartDate,
    string FyEndDate);
