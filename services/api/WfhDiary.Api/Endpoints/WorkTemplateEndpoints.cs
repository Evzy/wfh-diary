using WfhDiary.Api.Services;
using WfhDiary.Shared.Dtos;

namespace WfhDiary.Api.Endpoints;

public static class WorkTemplateEndpoints
{
    public static void MapWorkTemplateEndpoints(this WebApplication app)
    {
        app.MapGet("/v1/work-templates", async (DiaryService svc, HttpContext ctx, CancellationToken ct) =>
        {
            var userId = svc.RequireUserId(ctx);
            var templates = await svc.GetWorkTemplatesAsync(userId, ct);
            return Results.Ok(templates.Select(DiaryService.ToDto));
        });

        app.MapPost("/v1/work-templates", async (CreateWorkTemplateRequest req, DiaryService svc, HttpContext ctx, CancellationToken ct) =>
        {
            if (string.IsNullOrWhiteSpace(req.Label))
            {
                return Results.BadRequest(new { error = "Label is required." });
            }

            var userId = svc.RequireUserId(ctx);
            var created = await svc.CreateWorkTemplateAsync(userId, req, ct);
            return Results.Ok(DiaryService.ToDto(created));
        });

        app.MapPut("/v1/work-templates/{id:guid}", async (Guid id, UpdateWorkTemplateRequest req, DiaryService svc, HttpContext ctx, CancellationToken ct) =>
        {
            var userId = svc.RequireUserId(ctx);
            var updated = await svc.UpdateWorkTemplateAsync(userId, id, req, ct);
            return updated is null ? Results.NotFound() : Results.Ok(DiaryService.ToDto(updated));
        });

        app.MapDelete("/v1/work-templates/{id:guid}", async (Guid id, DiaryService svc, HttpContext ctx, CancellationToken ct) =>
        {
            var userId = svc.RequireUserId(ctx);
            var deleted = await svc.DeleteWorkTemplateAsync(userId, id, ct);
            return deleted ? Results.NoContent() : Results.NotFound();
        });
    }
}
