using System.Globalization;

namespace WfhDiary.Api.Services;

public static class TimeHelpers
{
    public static string MinutesToTimeString(int minutes) =>
        TimeOnly.FromTimeSpan(TimeSpan.FromMinutes(minutes)).ToString("HH:mm", CultureInfo.InvariantCulture);

    public static int? ParseTimeString(string? value)
    {
        if (string.IsNullOrWhiteSpace(value)) return null;
        if (TimeOnly.TryParseExact(value.Trim(), ["HH:mm", "H:mm"], CultureInfo.InvariantCulture, DateTimeStyles.None, out var t))
        {
            return t.Hour * 60 + t.Minute;
        }

        return null;
    }

    public static double MinutesToExcelDecimal(int minutes) => minutes / (24.0 * 60.0);

    public static decimal ComputeTotalHours(int? startMinutes, int? finishMinutes, decimal? breakHours)
    {
        if (startMinutes is null || finishMinutes is null) return 0m;
        var spanHours = (finishMinutes.Value - startMinutes.Value) / 60m;
        var breaks = breakHours ?? 0m;
        return Math.Max(0m, spanHours - breaks);
    }
}
