using WfhDiary.Api.Services;

namespace WfhDiary.Api.Tests;

public class FinancialYearHelperTests
{
    [Theory]
    [InlineData(2025, 6, 30, 2024)]
    [InlineData(2025, 7, 1, 2025)]
    [InlineData(2026, 1, 15, 2025)]
    public void CurrentFyYear_july_start(int year, int month, int day, int expectedFy)
    {
        var today = new DateOnly(year, month, day);
        Assert.Equal(expectedFy, FinancialYearHelper.CurrentFyYear(today));
    }

    [Fact]
    public void FyRange_spans_july_to_june()
    {
        var (start, end) = FinancialYearHelper.FyRange(2025);
        Assert.Equal(new DateOnly(2025, 7, 1), start);
        Assert.Equal(new DateOnly(2026, 6, 30), end);
    }

    [Fact]
    public void EachDay_includes_end_date()
    {
        var start = new DateOnly(2025, 7, 1);
        var end = new DateOnly(2025, 7, 3);
        var days = FinancialYearHelper.EachDay(start, end).ToList();
        Assert.Equal(3, days.Count);
        Assert.Equal(end, days[^1]);
    }
}
