namespace WfhDiary.Api.Services;

public static class WorkingDaysMask
{
    public const int Default = 31; // Mon–Fri

    public static bool IsWorkingDay(int mask, DayOfWeek dayOfWeek)
    {
        var bit = 1 << ((int)dayOfWeek - 1);
        return (mask & bit) != 0;
    }
}
