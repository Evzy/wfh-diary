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
        await EnsureNotificationWorkingDaysMaskColumnAsync(db);
    }

    private static async Task EnsureNotificationWorkingDaysMaskColumnAsync(WfhDiaryDbContext db)
    {
        if (!db.Database.IsSqlite()) return;

        try
        {
            await db.Database.ExecuteSqlRawAsync(
                "ALTER TABLE user_settings ADD COLUMN NotificationWorkingDaysMask INTEGER NOT NULL DEFAULT 31");
        }
        catch
        {
            // Column already exists on databases created after the field was added.
        }
    }
}
