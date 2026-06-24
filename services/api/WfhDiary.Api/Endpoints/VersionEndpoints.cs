namespace WfhDiary.Api.Endpoints;

public static class VersionEndpoints
{
    public static void MapVersionEndpoints(this WebApplication app)
    {
        app.MapGet("/v1/version", () => Results.Json(new
        {
            version = AppVersion.VersionName,
            versionCode = AppVersion.VersionCode,
            service = "WfhDiary.Api",
        }));
    }
}
