using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Design;

namespace WfhDiary.Api.Data;

/// <summary>Design-time factory for EF Core migrations (targets SQL Server).</summary>
public sealed class WfhDiaryDbContextFactory : IDesignTimeDbContextFactory<WfhDiaryDbContext>
{
    public WfhDiaryDbContext CreateDbContext(string[] args)
    {
        var options =
            new DbContextOptionsBuilder<WfhDiaryDbContext>()
                .UseSqlServer(
                    "Server=localhost;Database=wfh_diary_design;Trusted_Connection=True;TrustServerCertificate=True")
                .Options;
        return new WfhDiaryDbContext(options);
    }
}
