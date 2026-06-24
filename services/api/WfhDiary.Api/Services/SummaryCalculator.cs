using WfhDiary.Api.Data;
using WfhDiary.Shared.Dtos;

namespace WfhDiary.Api.Services;

public static class SummaryCalculator
{
    private const decimal WfhRate = 0.67m;

    public static DiarySummaryDto Compute(IEnumerable<DayEntryEntity> days)
    {
        var list = days.ToList();
        decimal total = 0, home = 0, office = 0;
        var daysHome = 0;
        var daysOffice = 0;

        foreach (var d in list)
        {
            var hours = TimeHelpers.ComputeTotalHours(d.StartTimeMinutes, d.FinishTimeMinutes, d.BreakHours);
            total += hours;
            if (string.Equals(d.Location, "Home", StringComparison.OrdinalIgnoreCase))
            {
                home += hours;
                if (hours > 0) daysHome++;
            }
            else if (string.Equals(d.Location, "Office", StringComparison.OrdinalIgnoreCase))
            {
                office += hours;
                if (hours > 0) daysOffice++;
            }
        }

        return new DiarySummaryDto(
            total,
            home,
            office,
            Math.Round(home * WfhRate, 2),
            daysHome,
            daysOffice,
            total == home + office);
    }
}
