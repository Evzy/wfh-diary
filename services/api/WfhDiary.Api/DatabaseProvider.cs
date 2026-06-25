using Microsoft.EntityFrameworkCore;
using WfhDiary.Api.Data;

namespace WfhDiary.Api;

public enum DatabaseKind
{
    Sqlite,
    SqlServer,
    PostgreSql,
}

public static class DatabaseProvider
{
    public static DatabaseKind Detect(string connectionString)
    {
        if (connectionString.Contains("Host=", StringComparison.OrdinalIgnoreCase))
        {
            return DatabaseKind.PostgreSql;
        }

        if (connectionString.Contains("Server=", StringComparison.OrdinalIgnoreCase) ||
            connectionString.Contains("Initial Catalog=", StringComparison.OrdinalIgnoreCase))
        {
            return DatabaseKind.SqlServer;
        }

        return DatabaseKind.Sqlite;
    }

    public static IServiceCollection AddWfhDiaryDatabase(this IServiceCollection services, string connectionString)
    {
        var kind = Detect(connectionString);
        services.AddDbContextFactory<WfhDiaryDbContext>(options =>
        {
            switch (kind)
            {
                case DatabaseKind.PostgreSql:
                    options.UseNpgsql(connectionString);
                    break;
                case DatabaseKind.SqlServer:
                    options.UseSqlServer(connectionString);
                    break;
                default:
                    options.UseSqlite(connectionString);
                    break;
            }
        });

        return services;
    }
}
