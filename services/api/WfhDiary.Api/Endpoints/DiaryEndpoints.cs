using WfhDiary.Api.Services;
using WfhDiary.Shared.Dtos;

namespace WfhDiary.Api.Endpoints;

public static class DiaryEndpoints
{
    public static void MapDiaryEndpoints(this WebApplication app)
    {
        app.MapGet("/v1/diaries/{fyYear:int}", async (int fyYear, DiaryService svc, HttpContext ctx, CancellationToken ct) =>
        {
            var userId = svc.RequireUserId(ctx);
            var diary = await svc.GetOrCreateDiaryAsync(userId, fyYear, ct);
            return Results.Ok(new DiaryDto(fyYear, diary.FyStartDate.ToString("yyyy-MM-dd"), diary.FyEndDate.ToString("yyyy-MM-dd")));
        });

        app.MapGet("/v1/diaries/{fyYear:int}/days", async (int fyYear, string? from, string? to, DiaryService svc, HttpContext ctx, CancellationToken ct) =>
        {
            var userId = svc.RequireUserId(ctx);
            DateOnly? fromDate = DateOnly.TryParse(from, out var f) ? f : null;
            DateOnly? toDate = DateOnly.TryParse(to, out var t) ? t : null;
            var days = await svc.GetDaysAsync(userId, fyYear, fromDate, toDate, ct);
            return Results.Ok(days.Select(DiaryService.ToDto));
        });

        app.MapPut("/v1/diaries/{fyYear:int}/days/{date}", async (int fyYear, string date, UpsertDayEntryRequest req, DiaryService svc, HttpContext ctx, CancellationToken ct) =>
        {
            if (!DateOnly.TryParse(date, out var parsed))
            {
                return Results.BadRequest(new { error = "Invalid date. Use yyyy-MM-dd." });
            }

            var userId = svc.RequireUserId(ctx);
            try
            {
                var entry = await svc.UpsertDayAsync(userId, fyYear, parsed, req, ct);
                return Results.Ok(DiaryService.ToDto(entry));
            }
            catch (ArgumentOutOfRangeException ex)
            {
                return Results.BadRequest(new { error = ex.Message });
            }
        });

        app.MapDelete("/v1/diaries/{fyYear:int}/days/{date}", async (int fyYear, string date, DiaryService svc, HttpContext ctx, CancellationToken ct) =>
        {
            if (!DateOnly.TryParse(date, out var parsed))
            {
                return Results.BadRequest(new { error = "Invalid date. Use yyyy-MM-dd." });
            }

            var userId = svc.RequireUserId(ctx);
            try
            {
                var entry = await svc.ClearDayAsync(userId, fyYear, parsed, ct);
                return entry is null
                    ? Results.NotFound()
                    : Results.Ok(DiaryService.ToDto(entry));
            }
            catch (ArgumentOutOfRangeException ex)
            {
                return Results.BadRequest(new { error = ex.Message });
            }
        });

        app.MapPost("/v1/diaries/{fyYear:int}/leave", async (int fyYear, CreateLeaveRequest req, DiaryService svc, HttpContext ctx, CancellationToken ct) =>
        {
            var userId = svc.RequireUserId(ctx);
            try
            {
                var leave = await svc.CreateLeaveAsync(userId, fyYear, req, ct);
                return Results.Ok(DiaryService.ToDto(leave));
            }
            catch (ArgumentException ex)
            {
                return Results.BadRequest(new { error = ex.Message });
            }
        });

        app.MapGet("/v1/diaries/{fyYear:int}/summary", async (int fyYear, DiaryService svc, HttpContext ctx, CancellationToken ct) =>
        {
            var userId = svc.RequireUserId(ctx);
            var days = await svc.GetDaysAsync(userId, fyYear, null, null, ct);
            return Results.Ok(SummaryCalculator.Compute(days));
        });

        app.MapGet("/v1/diaries/{fyYear:int}/export.xlsx", async (int fyYear, DiaryService svc, XlsxExportService export, HttpContext ctx, CancellationToken ct) =>
        {
            var userId = svc.RequireUserId(ctx);
            var settings = await svc.GetOrCreateSettingsAsync(userId, ct);
            var diary = await svc.GetOrCreateDiaryAsync(userId, fyYear, ct);
            var days = await svc.GetDaysAsync(userId, fyYear, null, null, ct);
            var bytes = export.Export(settings, diary, days);
            return Results.File(bytes, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                $"WFH-Diary-{fyYear}-{fyYear + 1}.xlsx");
        });

        app.MapGet("/v1/leave-periods", async (DiaryService svc, HttpContext ctx, CancellationToken ct) =>
        {
            var userId = svc.RequireUserId(ctx);
            var periods = await svc.GetLeavePeriodsAsync(userId, ct);
            return Results.Ok(periods.Select(DiaryService.ToDto));
        });

        app.MapDelete("/v1/leave-periods/{id:guid}", async (Guid id, DiaryService svc, HttpContext ctx, CancellationToken ct) =>
        {
            var userId = svc.RequireUserId(ctx);
            var deleted = await svc.DeleteLeaveAsync(userId, id, ct);
            return deleted ? Results.NoContent() : Results.NotFound();
        });
    }
}
