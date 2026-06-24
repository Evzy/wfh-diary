namespace WfhDiary.Shared.Dtos;

public sealed record DiarySummaryDto(
    decimal TotalHours,
    decimal HomeHours,
    decimal OfficeHours,
    decimal ClaimableWfhAmount,
    int DaysAtHome,
    int DaysInOffice,
    bool LogicCheckPassed);
