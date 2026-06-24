namespace WfhDiary.Api.Data;

public sealed class LeavePeriodEntity
{
    public Guid Id { get; set; }
    public string OwnerUserId { get; set; } = "";
    public DateOnly StartDate { get; set; }
    public DateOnly EndDate { get; set; }
    public string? Note { get; set; }
}
