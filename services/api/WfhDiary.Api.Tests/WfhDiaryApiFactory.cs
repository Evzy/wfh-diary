using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using WfhDiary.Api.Data;

namespace WfhDiary.Api.Tests;

public sealed class WfhDiaryApiFactory : WebApplicationFactory<Program>
{
    private readonly string _dbPath = Path.Combine(Path.GetTempPath(), $"wfh_diary_test_{Guid.NewGuid():N}.db");

    protected override void ConfigureWebHost(IWebHostBuilder builder)
    {
        builder.ConfigureServices(services =>
        {
            var descriptors = services.Where(d =>
                d.ServiceType == typeof(DbContextOptions<WfhDiaryDbContext>) ||
                d.ServiceType == typeof(IDbContextFactory<WfhDiaryDbContext>)).ToList();
            foreach (var d in descriptors) services.Remove(d);

            services.AddDbContextFactory<WfhDiaryDbContext>(o => o.UseSqlite($"Data Source={_dbPath}"));
        });
    }
}
