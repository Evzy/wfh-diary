using Microsoft.EntityFrameworkCore;

namespace WfhDiary.Api.Data;

public sealed class WfhDiaryDbContext(DbContextOptions<WfhDiaryDbContext> options) : DbContext(options)
{
    public DbSet<UserSettingsEntity> UserSettings => Set<UserSettingsEntity>();
    public DbSet<DiaryEntity> Diaries => Set<DiaryEntity>();
    public DbSet<DayEntryEntity> DayEntries => Set<DayEntryEntity>();
    public DbSet<LeavePeriodEntity> LeavePeriods => Set<LeavePeriodEntity>();
    public DbSet<WorkTemplateEntity> WorkTemplates => Set<WorkTemplateEntity>();

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        modelBuilder.Entity<UserSettingsEntity>(e =>
        {
            e.ToTable("user_settings");
            e.HasKey(x => x.OwnerUserId);
        });

        modelBuilder.Entity<DiaryEntity>(e =>
        {
            e.ToTable("diaries");
            e.HasKey(x => x.Id);
            e.HasIndex(x => new { x.OwnerUserId, x.FyYear }).IsUnique();
            e.HasMany(x => x.DayEntries).WithOne(x => x.Diary).HasForeignKey(x => x.DiaryId);
        });

        modelBuilder.Entity<DayEntryEntity>(e =>
        {
            e.ToTable("day_entries");
            e.HasKey(x => x.Id);
            e.HasIndex(x => new { x.DiaryId, x.Date }).IsUnique();
        });

        modelBuilder.Entity<LeavePeriodEntity>(e =>
        {
            e.ToTable("leave_periods");
            e.HasKey(x => x.Id);
            e.HasIndex(x => new { x.OwnerUserId, x.StartDate, x.EndDate });
        });

        modelBuilder.Entity<WorkTemplateEntity>(e =>
        {
            e.ToTable("work_templates");
            e.HasKey(x => x.Id);
            e.HasIndex(x => new { x.OwnerUserId, x.SortOrder });
        });
    }
}
