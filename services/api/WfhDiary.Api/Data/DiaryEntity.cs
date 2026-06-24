namespace WfhDiary.Api.Data;

public sealed class DiaryEntity
{
    public Guid Id { get; set; }
    public string OwnerUserId { get; set; } = "";
    public int FyYear { get; set; }
    public DateOnly FyStartDate { get; set; }
    public DateOnly FyEndDate { get; set; }
    public DateTimeOffset CreatedAt { get; set; }
    public List<DayEntryEntity> DayEntries { get; set; } = [];
}
