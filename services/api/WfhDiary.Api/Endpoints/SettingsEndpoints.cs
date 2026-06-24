using WfhDiary.Api.Services;
using WfhDiary.Shared.Dtos;

namespace WfhDiary.Api.Endpoints;

public static class SettingsEndpoints
{
    public static void MapSettingsEndpoints(this WebApplication app)
    {
        app.MapGet("/v1/settings", async (DiaryService svc, HttpContext ctx, CancellationToken ct) =>
        {
            var userId = svc.RequireUserId(ctx);
            var settings = await svc.GetOrCreateSettingsAsync(userId, ct);
            return Results.Ok(DiaryService.ToDto(settings));
        });

        app.MapPut("/v1/settings", async (UpdateUserSettingsRequest req, DiaryService svc, HttpContext ctx, CancellationToken ct) =>
        {
            var userId = svc.RequireUserId(ctx);
            var settings = await svc.UpdateSettingsAsync(userId, req, ct);
            return Results.Ok(DiaryService.ToDto(settings));
        });
    }
}
