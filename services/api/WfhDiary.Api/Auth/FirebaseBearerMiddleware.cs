using System.Security.Claims;
using FirebaseAdmin.Auth;

namespace WfhDiary.Api.Auth;

internal sealed class FirebaseBearerMiddleware(RequestDelegate next, ILogger<FirebaseBearerMiddleware> logger)
{
    public async Task InvokeAsync(HttpContext context, FirebaseAuthGate gate)
    {
        if (!gate.VerifyFirebaseTokens)
        {
            await next(context);
            return;
        }

        var path = context.Request.Path.Value ?? "";
        if (!path.StartsWith("/v1/", StringComparison.OrdinalIgnoreCase))
        {
            await next(context);
            return;
        }

        var authHeader = context.Request.Headers.Authorization.ToString();
        const string prefix = "Bearer ";
        if (string.IsNullOrEmpty(authHeader) ||
            !authHeader.StartsWith(prefix, StringComparison.OrdinalIgnoreCase))
        {
            await RejectAsync(context, "Missing or invalid Authorization header.");
            return;
        }

        var token = authHeader[prefix.Length..].Trim();
        if (token.Length == 0)
        {
            await RejectAsync(context, "Empty bearer token.");
            return;
        }

        try
        {
            var decoded = await FirebaseAuth.DefaultInstance.VerifyIdTokenAsync(token, context.RequestAborted);
            var identity = new ClaimsIdentity(
                [new Claim(ClaimTypes.NameIdentifier, decoded.Uid)],
                authenticationType: "Firebase");
            context.User = new ClaimsPrincipal(identity);
        }
        catch (FirebaseAuthException ex)
        {
            logger.LogDebug(ex, "Firebase ID token verification failed.");
            await RejectAsync(context, "Invalid or expired token.");
            return;
        }

        await next(context);
    }

    private static async Task RejectAsync(HttpContext context, string message)
    {
        context.Response.StatusCode = StatusCodes.Status401Unauthorized;
        context.Response.ContentType = "application/json";
        await context.Response.WriteAsJsonAsync(new { error = message });
    }
}
