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
        if (req.MissedNotificationDefault is not null)
        {
            var d = req.MissedNotificationDefault.Trim();
            settings.MissedNotificationDefault = d is "Home" or "Office" or "Leave" or "None" ? d : "None";
        }
        if (req.MissedNotificationApplyAt is not null &&
            TimeHelpers.ParseTimeString(req.MissedNotificationApplyAt) is int applyAt)
        {
            settings.MissedNotificationApplyAtMinutes = applyAt;
        }

        if (req.ClearHomeGeofence == true)
        {
            settings.HomeGeofenceLatitude = null;
            settings.HomeGeofenceLongitude = null;
            settings.HomeGeofenceRadiusMeters = null;
        }
        else if (req.HomeGeofenceLatitude is not null &&
                 req.HomeGeofenceLongitude is not null &&
                 req.HomeGeofenceRadiusMeters is int homeRadius)
        {
            settings.HomeGeofenceLatitude = req.HomeGeofenceLatitude;
            settings.HomeGeofenceLongitude = req.HomeGeofenceLongitude;
            settings.HomeGeofenceRadiusMeters = homeRadius;
        }

        if (req.ClearOfficeGeofence == true)
        {
            settings.OfficeGeofenceLatitude = null;
            settings.OfficeGeofenceLongitude = null;
            settings.OfficeGeofenceRadiusMeters = null;
        }
        else if (req.OfficeGeofenceLatitude is not null &&
                 req.OfficeGeofenceLongitude is not null &&
                 req.OfficeGeofenceRadiusMeters is int officeRadius)
        {
            settings.OfficeGeofenceLatitude = req.OfficeGeofenceLatitude;
            settings.OfficeGeofenceLongitude = req.OfficeGeofenceLongitude;
            settings.OfficeGeofenceRadiusMeters = officeRadius;
        }

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

    public async Task<bool> DeleteLeaveAsync(string userId, Guid leaveId, CancellationToken ct)
    {
        await using var db = await dbFactory.CreateDbContextAsync(ct);
        var leave = await db.LeavePeriods
            .FirstOrDefaultAsync(l => l.Id == leaveId && l.OwnerUserId == userId, ct);
        if (leave is null) return false;

        var otherLeaves = await db.LeavePeriods
            .Where(l => l.OwnerUserId == userId && l.Id != leaveId)
            .ToListAsync(ct);

        db.LeavePeriods.Remove(leave);

        var diaryIds = await db.Diaries
            .Where(d => d.OwnerUserId == userId)
            .Select(d => d.Id)
            .ToListAsync(ct);

        foreach (var date in FinancialYearHelper.EachDay(leave.StartDate, leave.EndDate))
        {
            if (otherLeaves.Any(l => date >= l.StartDate && date <= l.EndDate)) continue;

            var entries = await db.DayEntries
                .Where(e => diaryIds.Contains(e.DiaryId) && e.Date == date)
                .ToListAsync(ct);
            foreach (var entry in entries)
            {
                if (!string.Equals(entry.Description, "LEAVE", StringComparison.OrdinalIgnoreCase)) continue;
                entry.StartTimeMinutes = null;
                entry.FinishTimeMinutes = null;
                entry.BreakHours = null;
                entry.Description = null;
                entry.Location = null;
            }
        }

        await db.SaveChangesAsync(ct);
        return true;
    }

    public async Task<List<WorkTemplateEntity>> GetWorkTemplatesAsync(string userId, CancellationToken ct)
    {
        await using var db = await dbFactory.CreateDbContextAsync(ct);
        return await db.WorkTemplates
            .Where(t => t.OwnerUserId == userId)
            .OrderBy(t => t.SortOrder)
            .ThenBy(t => t.Label)
            .ToListAsync(ct);
    }

    public async Task<WorkTemplateEntity> CreateWorkTemplateAsync(string userId, CreateWorkTemplateRequest req, CancellationToken ct)
    {
        await using var db = await dbFactory.CreateDbContextAsync(ct);
        if (req.IsDefault)
        {
            await ClearDefaultWorkTemplateAsync(db, userId, ct);
        }

        var maxSort = await db.WorkTemplates
            .Where(t => t.OwnerUserId == userId)
            .Select(t => (int?)t.SortOrder)
            .MaxAsync(ct) ?? -1;

        var template = MapCreateWorkTemplate(userId, req, maxSort + 1);
        db.WorkTemplates.Add(template);
        await db.SaveChangesAsync(ct);
        return template;
    }

    public async Task<WorkTemplateEntity?> UpdateWorkTemplateAsync(string userId, Guid id, UpdateWorkTemplateRequest req, CancellationToken ct)
    {
        await using var db = await dbFactory.CreateDbContextAsync(ct);
        var template = await db.WorkTemplates.FirstOrDefaultAsync(t => t.Id == id && t.OwnerUserId == userId, ct);
        if (template is null) return null;

        if (req.IsDefault == true)
        {
            await ClearDefaultWorkTemplateAsync(db, userId, ct, exceptId: id);
            template.IsDefault = true;
        }
        else if (req.IsDefault == false)
        {
            template.IsDefault = false;
        }

        if (req.Label is not null) template.Label = req.Label.Trim();
        if (req.SortOrder is int sort) template.SortOrder = sort;
        if (req.StartTime is not null) template.StartTimeMinutes = TimeHelpers.ParseTimeString(req.StartTime);
        if (req.FinishTime is not null) template.FinishTimeMinutes = TimeHelpers.ParseTimeString(req.FinishTime);
        if (req.BreakHours is not null) template.BreakHours = req.BreakHours;
        if (req.Location is not null) template.Location = string.IsNullOrWhiteSpace(req.Location) ? null : req.Location;
        if (req.Description is not null) template.Description = string.IsNullOrWhiteSpace(req.Description) ? null : req.Description;
        if (req.GeofenceLatitude is not null) template.GeofenceLatitude = req.GeofenceLatitude;
        if (req.GeofenceLongitude is not null) template.GeofenceLongitude = req.GeofenceLongitude;
        if (req.GeofenceRadiusMeters is not null) template.GeofenceRadiusMeters = req.GeofenceRadiusMeters;

        await db.SaveChangesAsync(ct);
        return template;
    }

    public async Task<bool> DeleteWorkTemplateAsync(string userId, Guid id, CancellationToken ct)
    {
        await using var db = await dbFactory.CreateDbContextAsync(ct);
        var template = await db.WorkTemplates.FirstOrDefaultAsync(t => t.Id == id && t.OwnerUserId == userId, ct);
        if (template is null) return false;
        db.WorkTemplates.Remove(template);
        await db.SaveChangesAsync(ct);
        return true;
    }

    private static WorkTemplateEntity MapCreateWorkTemplate(string userId, CreateWorkTemplateRequest req, int sortOrder) =>
        new()
        {
            Id = Guid.NewGuid(),
            OwnerUserId = userId,
            Label = req.Label.Trim(),
            SortOrder = req.SortOrder ?? sortOrder,
            StartTimeMinutes = req.StartTime is not null ? TimeHelpers.ParseTimeString(req.StartTime) : null,
            FinishTimeMinutes = req.FinishTime is not null ? TimeHelpers.ParseTimeString(req.FinishTime) : null,
            BreakHours = req.BreakHours,
            Location = string.IsNullOrWhiteSpace(req.Location) ? null : req.Location,
            Description = string.IsNullOrWhiteSpace(req.Description) ? null : req.Description,
            IsDefault = req.IsDefault,
            GeofenceLatitude = req.GeofenceLatitude,
            GeofenceLongitude = req.GeofenceLongitude,
            GeofenceRadiusMeters = req.GeofenceRadiusMeters,
        };

    private static async Task ClearDefaultWorkTemplateAsync(
        WfhDiaryDbContext db,
        string userId,
        CancellationToken ct,
        Guid? exceptId = null)
    {
        var defaults = await db.WorkTemplates
            .Where(t => t.OwnerUserId == userId && t.IsDefault && (exceptId == null || t.Id != exceptId))
            .ToListAsync(ct);
        foreach (var item in defaults)
        {
            item.IsDefault = false;
        }
    }

    public static UserSettingsDto ToDto(UserSettingsEntity s) => new(
        s.DisplayName,
        TimeHelpers.MinutesToTimeString(s.NotificationTimeMinutes),
        s.NotificationWorkingDaysMask,
        s.MissedNotificationDefault,
        TimeHelpers.MinutesToTimeString(s.MissedNotificationApplyAtMinutes),
        TimeHelpers.MinutesToTimeString(s.DefaultHomeStartMinutes),
        TimeHelpers.MinutesToTimeString(s.DefaultHomeFinishMinutes),
        s.DefaultHomeBreakHours,
        TimeHelpers.MinutesToTimeString(s.DefaultOfficeStartMinutes),
        TimeHelpers.MinutesToTimeString(s.DefaultOfficeFinishMinutes),
        s.DefaultOfficeBreakHours,
        s.HomeGeofenceLatitude,
        s.HomeGeofenceLongitude,
        s.HomeGeofenceRadiusMeters,
        s.OfficeGeofenceLatitude,
        s.OfficeGeofenceLongitude,
        s.OfficeGeofenceRadiusMeters);

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

    public static WorkTemplateDto ToDto(WorkTemplateEntity t) => new(
        t.Id,
        t.Label,
        t.SortOrder,
        t.StartTimeMinutes is int sm ? TimeHelpers.MinutesToTimeString(sm) : null,
        t.FinishTimeMinutes is int fm ? TimeHelpers.MinutesToTimeString(fm) : null,
        t.BreakHours,
        t.Location,
        t.Description,
        t.IsDefault,
        t.GeofenceLatitude,
        t.GeofenceLongitude,
        t.GeofenceRadiusMeters);
}
