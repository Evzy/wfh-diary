using Microsoft.EntityFrameworkCore;
using WfhDiary.Api.Data;

namespace WfhDiary.Api;

public static class DatabaseBootstrap
{
    public static async Task ApplyAsync(WebApplication app)
    {
        await using var scope = app.Services.CreateAsyncScope();
        var factory = scope.ServiceProvider.GetRequiredService<IDbContextFactory<WfhDiaryDbContext>>();
        await using var db = await factory.CreateDbContextAsync();

        if (db.Database.IsSqlite())
        {
            await BootstrapSqliteAsync(db);
            return;
        }

        await db.Database.MigrateAsync();
    }

    private static async Task BootstrapSqliteAsync(WfhDiaryDbContext db)
    {
        await db.Database.EnsureCreatedAsync();
        await EnsureSqliteColumnAsync(db, "NotificationWorkingDaysMask", "INTEGER NOT NULL DEFAULT 31");
        await EnsureSqliteColumnAsync(db, "MissedNotificationDefault", "TEXT NOT NULL DEFAULT 'None'");
        await EnsureSqliteColumnAsync(db, "MissedNotificationApplyAtMinutes", "INTEGER NOT NULL DEFAULT 720");
        await EnsureSqliteColumnAsync(db, "HomeGeofenceLatitude", "REAL NULL");
        await EnsureSqliteColumnAsync(db, "HomeGeofenceLongitude", "REAL NULL");
        await EnsureSqliteColumnAsync(db, "HomeGeofenceRadiusMeters", "INTEGER NULL");
        await EnsureSqliteColumnAsync(db, "OfficeGeofenceLatitude", "REAL NULL");
        await EnsureSqliteColumnAsync(db, "OfficeGeofenceLongitude", "REAL NULL");
        await EnsureSqliteColumnAsync(db, "OfficeGeofenceRadiusMeters", "INTEGER NULL");
        await EnsureSqliteColumnAsync(db, "MissedNotificationAwayDefault", "TEXT NOT NULL DEFAULT 'Office'");
        await EnsureSqliteColumnAsync(db, "GeolocationSkipNotificationAtHome", "INTEGER NOT NULL DEFAULT 0");
        await EnsureSqliteColumnAsync(db, "GeolocationSkipNotificationAtOffice", "INTEGER NOT NULL DEFAULT 0");
        await EnsureSqliteColumnAsync(db, "DefaultHomeDescription", "TEXT NULL");
        await EnsureSqliteColumnAsync(db, "DefaultOfficeDescription", "TEXT NULL");
        await EnsureSqliteWorkTemplatesTableAsync(db);
    }

    private static async Task EnsureSqliteWorkTemplatesTableAsync(WfhDiaryDbContext db)
    {
        const string sql = """
            CREATE TABLE IF NOT EXISTS work_templates (
                Id TEXT NOT NULL PRIMARY KEY,
                OwnerUserId TEXT NOT NULL,
                Label TEXT NOT NULL,
                SortOrder INTEGER NOT NULL DEFAULT 0,
                StartTimeMinutes INTEGER NULL,
                FinishTimeMinutes INTEGER NULL,
                BreakHours REAL NULL,
                Location TEXT NULL,
                Description TEXT NULL,
                IsDefault INTEGER NOT NULL DEFAULT 0,
                GeofenceLatitude REAL NULL,
                GeofenceLongitude REAL NULL,
                GeofenceRadiusMeters INTEGER NULL
            )
            """;
        await db.Database.ExecuteSqlRawAsync(sql);
    }

    private static async Task EnsureSqliteColumnAsync(WfhDiaryDbContext db, string column, string definition)
    {
        try
        {
            await db.Database.ExecuteSqlRawAsync(
                $"ALTER TABLE user_settings ADD COLUMN {column} {definition}");
        }
        catch
        {
            // Column already exists on databases created after the field was added.
        }
    }
}
