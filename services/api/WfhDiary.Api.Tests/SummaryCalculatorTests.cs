using WfhDiary.Api.Data;
using WfhDiary.Api.Services;

namespace WfhDiary.Api.Tests;

public class SummaryCalculatorTests
{
    [Fact]
    public void Compute_home_hours_and_claimable_amount()
    {
        var days = new List<DayEntryEntity>
        {
            new()
            {
                StartTimeMinutes = 8 * 60,
                FinishTimeMinutes = 17 * 60,
                BreakHours = 1m,
                Location = "Home",
            },
        };

        var summary = SummaryCalculator.Compute(days);
        Assert.Equal(8m, summary.HomeHours);
        Assert.Equal(5.36m, summary.ClaimableWfhAmount);
        Assert.True(summary.LogicCheckPassed);
    }

    [Fact]
    public void Compute_logic_check_fails_when_unlocated_hours()
    {
        var days = new List<DayEntryEntity>
        {
            new()
            {
                StartTimeMinutes = 8 * 60,
                FinishTimeMinutes = 17 * 60,
                BreakHours = 1m,
                Location = null,
            },
        };

        var summary = SummaryCalculator.Compute(days);
        Assert.Equal(8m, summary.TotalHours);
        Assert.Equal(0m, summary.HomeHours + summary.OfficeHours);
        Assert.False(summary.LogicCheckPassed);
    }
}
