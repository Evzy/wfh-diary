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
        await EnsureWorkTemplatesTableAsync(db);
    }

    private static async Task EnsureWorkTemplatesTableAsync(WfhDiaryDbContext db)
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
        if (db.Database.IsSqlite())
        {
            await db.Database.ExecuteSqlRawAsync(sql);
            return;
        }

        if (db.Database.IsNpgsql())
        {
            await db.Database.ExecuteSqlRawAsync("""
                CREATE TABLE IF NOT EXISTS work_templates (
                    "Id" uuid NOT NULL PRIMARY KEY,
                    "OwnerUserId" text NOT NULL,
                    "Label" text NOT NULL,
                    "SortOrder" integer NOT NULL DEFAULT 0,
                    "StartTimeMinutes" integer NULL,
                    "FinishTimeMinutes" integer NULL,
                    "BreakHours" numeric NULL,
                    "Location" text NULL,
                    "Description" text NULL,
                    "IsDefault" boolean NOT NULL DEFAULT false,
                    "GeofenceLatitude" double precision NULL,
                    "GeofenceLongitude" double precision NULL,
                    "GeofenceRadiusMeters" integer NULL
                )
                """);
        }
    }

    private static async Task EnsureSqliteColumnAsync(WfhDiaryDbContext db, string column, string definition)
    {
        if (!db.Database.IsSqlite()) return;

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
