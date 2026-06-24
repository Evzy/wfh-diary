namespace WfhDiary.Api.Data;

public sealed class DayEntryEntity
{
    public Guid Id { get; set; }
    public Guid DiaryId { get; set; }
    public DateOnly Date { get; set; }
    public int? StartTimeMinutes { get; set; }
    public int? FinishTimeMinutes { get; set; }
    public decimal? BreakHours { get; set; }
    public string? Description { get; set; }
    public string? Location { get; set; }
    public DiaryEntity? Diary { get; set; }
}
