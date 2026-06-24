using WfhDiary.Api.Services;

namespace WfhDiary.Api.Tests;

public class TimeHelpersTests
{
    [Theory]
    [InlineData("08:00", 480)]
    [InlineData("8:00", 480)]
    [InlineData("17:30", 17 * 60 + 30)]
    public void ParseTimeString_valid(string input, int expectedMinutes)
    {
        Assert.Equal(expectedMinutes, TimeHelpers.ParseTimeString(input));
    }

    [Fact]
    public void ComputeTotalHours_subtracts_break()
    {
        var total = TimeHelpers.ComputeTotalHours(8 * 60, 17 * 60, 1m);
        Assert.Equal(8m, total);
    }
}
