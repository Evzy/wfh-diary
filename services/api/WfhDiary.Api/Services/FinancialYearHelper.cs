namespace WfhDiary.Api.Services;

public static class FinancialYearHelper
{
    public static int CurrentFyYear(DateOnly today) =>
        today.Month >= 7 ? today.Year : today.Year - 1;

    public static (DateOnly Start, DateOnly End) FyRange(int fyYear) =>
        (new DateOnly(fyYear, 7, 1), new DateOnly(fyYear + 1, 6, 30));

    public static IEnumerable<DateOnly> EachDay(DateOnly start, DateOnly end)
    {
        for (var d = start; d <= end; d = d.AddDays(1))
        {
            yield return d;
        }
    }
}
