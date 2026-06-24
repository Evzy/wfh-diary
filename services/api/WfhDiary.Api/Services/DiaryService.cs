using System.Security.Claims;
using Microsoft.EntityFrameworkCore;
using WfhDiary.Api.Data;
using WfhDiary.Shared.Dtos;

namespace WfhDiary.Api.Services;

public sealed class DiaryService(IDbContextFactory<WfhDiaryDbContext> dbFactory)
{
    public string RequireUserId(HttpContext httpContext)
    {
        var uid = httpContext.User.FindFirstValue(ClaimTypes.NameIdentifier);
        if (string.IsNullOrEmpty(uid))
        {
            uid = httpContext.Request.Headers["X-Debug-User-Id"].FirstOrDefault();
        }

        if (string.IsNullOrEmpty(uid))
        {
            throw new UnauthorizedAccessException("User id required.");
        }

        return uid;
    }

    public async Task<UserSettingsEntity> GetOrCreateSettingsAsync(string userId, CancellationToken ct)
    {
        await using var db = await dbFactory.CreateDbContextAsync(ct);
        var settings = await db.UserSettings.FindAsync([userId], ct);
        if (settings is not null) return settings;

        settings = new UserSettingsEntity { OwnerUserId = userId };
        db.UserSettings.Add(settings);
        await db.SaveChangesAsync(ct);
        return settings;
    }

    public async Task<UserSettingsEntity> UpdateSettingsAsync(string userId, UpdateUserSettingsRequest req, CancellationToken ct)
    {
        var settings = await GetOrCreateSettingsAsync(userId, ct);
        await using var db = await dbFactory.CreateDbContextAsync(ct);
        settings = (await db.UserSettings.FindAsync([userId], ct))!;

        if (req.DisplayName is not null) settings.DisplayName = req.DisplayName;
        if (req.NotificationTime is not null && TimeHelpers.ParseTimeString(req.NotificationTime) is int n)
            settings.NotificationTimeMinutes = n;
        if (req.DefaultHomeStart is not null && TimeHelpers.ParseTimeString(req.DefaultHomeStart) is int hs)
            settings.DefaultHomeStartMinutes = hs;
        if (req.DefaultHomeFinish is not null && TimeHelpers.ParseTimeString(req.DefaultHomeFinish) is int hf)
            settings.DefaultHomeFinishMinutes = hf;
        if (req.DefaultHomeBreakHours is not null) settings.DefaultHomeBreakHours = req.DefaultHomeBreakHours.Value;
        if (req.DefaultOfficeStart is not null && TimeHelpers.ParseTimeString(req.DefaultOfficeStart) is int os)
            settings.DefaultOfficeStartMinutes = os;
        if (req.DefaultOfficeFinish is not null && TimeHelpers.ParseTimeString(req.DefaultOfficeFinish) is int of)
            settings.DefaultOfficeFinishMinutes = of;
        if (req.DefaultOfficeBreakHours is not null) settings.DefaultOfficeBreakHours = req.DefaultOfficeBreakHours.Value;
        if (req.NotificationWorkingDaysMask is int mask) settings.NotificationWorkingDaysMask = mask;

        await db.SaveChangesAsync(ct);
        return settings;
    }

    public async Task<DiaryEntity> GetOrCreateDiaryAsync(string userId, int fyYear, CancellationToken ct)
    {
        await using var db = await dbFactory.CreateDbContextAsync(ct);
        var diary = await db.Diaries
            .Include(d => d.DayEntries)
            .FirstOrDefaultAsync(d => d.OwnerUserId == userId && d.FyYear == fyYear, ct);

        if (diary is not null) return diary;

        var (start, end) = FinancialYearHelper.FyRange(fyYear);
        diary = new DiaryEntity
        {
            Id = Guid.NewGuid(),
            OwnerUserId = userId,
            FyYear = fyYear,
            FyStartDate = start,
            FyEndDate = end,
            CreatedAt = DateTimeOffset.UtcNow,
        };

        foreach (var date in FinancialYearHelper.EachDay(start, end))
        {
            diary.DayEntries.Add(new DayEntryEntity
            {
                Id = Guid.NewGuid(),
                DiaryId = diary.Id,
                Date = date,
            });
        }

        db.Diaries.Add(diary);
        await db.SaveChangesAsync(ct);
        return diary;
    }

    public async Task<List<DayEntryEntity>> GetDaysAsync(string userId, int fyYear, DateOnly? from, DateOnly? to, CancellationToken ct)
    {
        var diary = await GetOrCreateDiaryAsync(userId, fyYear, ct);
        await using var db = await dbFactory.CreateDbContextAsync(ct);
        var query = db.DayEntries.Where(d => d.DiaryId == diary.Id);
        if (from is not null) query = query.Where(d => d.Date >= from);
        if (to is not null) query = query.Where(d => d.Date <= to);
        return await query.OrderBy(d => d.Date).ToListAsync(ct);
    }

    public async Task<DayEntryEntity> UpsertDayAsync(string userId, int fyYear, DateOnly date, UpsertDayEntryRequest req, CancellationToken ct)
    {
        var diary = await GetOrCreateDiaryAsync(userId, fyYear, ct);
        if (date < diary.FyStartDate || date > diary.FyEndDate)
        {
            throw new ArgumentOutOfRangeException(nameof(date), "Date outside financial year.");
        }

        await using var db = await dbFactory.CreateDbContextAsync(ct);
        var entry = await db.DayEntries.FirstOrDefaultAsync(d => d.DiaryId == diary.Id && d.Date == date, ct);
        if (entry is null)
        {
            entry = new DayEntryEntity { Id = Guid.NewGuid(), DiaryId = diary.Id, Date = date };
            db.DayEntries.Add(entry);
        }

        if (req.StartTime is not null) entry.StartTimeMinutes = TimeHelpers.ParseTimeString(req.StartTime);
        if (req.FinishTime is not null) entry.FinishTimeMinutes = TimeHelpers.ParseTimeString(req.FinishTime);
        if (req.BreakHours is not null) entry.BreakHours = req.BreakHours;
        if (req.Description is not null) entry.Description = req.Description;
        if (req.Location is not null) entry.Location = string.IsNullOrWhiteSpace(req.Location) ? null : req.Location;

        await db.SaveChangesAsync(ct);
        return entry;
    }

    public async Task<DayEntryEntity?> ClearDayAsync(string userId, int fyYear, DateOnly date, CancellationToken ct)
    {
        var diary = await GetOrCreateDiaryAsync(userId, fyYear, ct);
        if (date < diary.FyStartDate || date > diary.FyEndDate)
        {
            throw new ArgumentOutOfRangeException(nameof(date), "Date outside financial year.");
        }

        await using var db = await dbFactory.CreateDbContextAsync(ct);
        var entry = await db.DayEntries.FirstOrDefaultAsync(d => d.DiaryId == diary.Id && d.Date == date, ct);
        if (entry is null) return null;

        entry.StartTimeMinutes = null;
        entry.FinishTimeMinutes = null;
        entry.BreakHours = null;
        entry.Description = null;
        entry.Location = null;
        await db.SaveChangesAsync(ct);
        return entry;
    }

    public async Task<LeavePeriodEntity> CreateLeaveAsync(string userId, int fyYear, CreateLeaveRequest req, CancellationToken ct)
    {
        if (!DateOnly.TryParse(req.StartDate, out var start) || !DateOnly.TryParse(req.EndDate, out var end))
        {
            throw new ArgumentException("Invalid date format. Use yyyy-MM-dd.");
        }

        if (end < start) (start, end) = (end, start);

        var diary = await GetOrCreateDiaryAsync(userId, fyYear, ct);
        await using var db = await dbFactory.CreateDbContextAsync(ct);

        var leave = new LeavePeriodEntity
        {
            Id = Guid.NewGuid(),
            OwnerUserId = userId,
            StartDate = start,
            EndDate = end,
            Note = req.Note,
        };
        db.LeavePeriods.Add(leave);

        foreach (var date in FinancialYearHelper.EachDay(start, end))
        {
            if (date < diary.FyStartDate || date > diary.FyEndDate) continue;
            var entry = await db.DayEntries.FirstOrDefaultAsync(d => d.DiaryId == diary.Id && d.Date == date, ct);
            if (entry is null) continue;
            entry.StartTimeMinutes = null;
            entry.FinishTimeMinutes = null;
            entry.BreakHours = null;
            entry.Description = "LEAVE";
            entry.Location = null;
        }

        await db.SaveChangesAsync(ct);
        return leave;
    }

    public async Task<List<LeavePeriodEntity>> GetLeavePeriodsAsync(string userId, CancellationToken ct)
    {
        await using var db = await dbFactory.CreateDbContextAsync(ct);
        return await db.LeavePeriods
            .Where(l => l.OwnerUserId == userId)
            .OrderBy(l => l.StartDate)
            .ToListAsync(ct);
    }

    public static UserSettingsDto ToDto(UserSettingsEntity s) => new(
        s.DisplayName,
        TimeHelpers.MinutesToTimeString(s.NotificationTimeMinutes),
        s.NotificationWorkingDaysMask,
        TimeHelpers.MinutesToTimeString(s.DefaultHomeStartMinutes),
        TimeHelpers.MinutesToTimeString(s.DefaultHomeFinishMinutes),
        s.DefaultHomeBreakHours,
        TimeHelpers.MinutesToTimeString(s.DefaultOfficeStartMinutes),
        TimeHelpers.MinutesToTimeString(s.DefaultOfficeFinishMinutes),
        s.DefaultOfficeBreakHours);

    public static DayEntryDto ToDto(DayEntryEntity e) => new(
        e.Date.ToString("yyyy-MM-dd"),
        e.Date.ToString("dddd"),
        e.StartTimeMinutes is int sm ? TimeHelpers.MinutesToTimeString(sm) : null,
        e.FinishTimeMinutes is int fm ? TimeHelpers.MinutesToTimeString(fm) : null,
        e.BreakHours,
        TimeHelpers.ComputeTotalHours(e.StartTimeMinutes, e.FinishTimeMinutes, e.BreakHours),
        e.Description,
        e.Location);

    public static LeavePeriodDto ToDto(LeavePeriodEntity l) => new(
        l.Id,
        l.StartDate.ToString("yyyy-MM-dd"),
        l.EndDate.ToString("yyyy-MM-dd"),
        l.Note);
}
