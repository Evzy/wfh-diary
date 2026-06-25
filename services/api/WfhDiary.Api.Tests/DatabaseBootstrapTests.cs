using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using WfhDiary.Api.Data;

namespace WfhDiary.Api.Tests;

public class DatabaseBootstrapTests
{
    [Fact]
    public async Task Startup_bootstrap_adds_missed_notification_columns()
    {
        await using var factory = new WfhDiaryApiFactory();
        _ = factory.CreateClient();

        await using var scope = factory.Services.CreateAsyncScope();
        var dbFactory = scope.ServiceProvider.GetRequiredService<IDbContextFactory<WfhDiaryDbContext>>();
        await using var db = await dbFactory.CreateDbContextAsync();

        var columns = new List<string>();
        await using var command = db.Database.GetDbConnection().CreateCommand();
        if (command.Connection!.State != System.Data.ConnectionState.Open)
        {
            await command.Connection.OpenAsync();
        }

        command.CommandText = "SELECT name FROM pragma_table_info('user_settings')";
        await using var reader = await command.ExecuteReaderAsync();
        while (await reader.ReadAsync())
        {
            columns.Add(reader.GetString(0));
        }

        Assert.Contains("MissedNotificationDefault", columns);
        Assert.Contains("MissedNotificationApplyAtMinutes", columns);
        Assert.Contains("NotificationWorkingDaysMask", columns);
    }
}
