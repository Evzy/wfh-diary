using System.Text;
using FirebaseAdmin;
using Google.Apis.Auth.OAuth2;
using Microsoft.EntityFrameworkCore;
using WfhDiary.Api;
using WfhDiary.Api.Auth;
using WfhDiary.Api.Data;
using WfhDiary.Api.Endpoints;
using WfhDiary.Api.Services;

var builder = WebApplication.CreateBuilder(args);

var connectionString = builder.Configuration["WfhDiary:ConnectionString"]?.Trim();
if (string.IsNullOrEmpty(connectionString))
{
    var dbPath = Path.Combine(builder.Environment.ContentRootPath, "wfh_diary_dev.db");
    connectionString = $"Data Source={dbPath}";
}

if (connectionString.Contains("Host=", StringComparison.OrdinalIgnoreCase) ||
    connectionString.Contains("Server=", StringComparison.OrdinalIgnoreCase))
{
    builder.Services.AddDbContextFactory<WfhDiaryDbContext>(o => o.UseNpgsql(connectionString));
}
else
{
    builder.Services.AddDbContextFactory<WfhDiaryDbContext>(o => o.UseSqlite(connectionString));
}

builder.Services.AddScoped<DiaryService>();
builder.Services.AddSingleton<XlsxExportService>();
builder.Services.AddCors(o => o.AddDefaultPolicy(p => p
    .AllowAnyHeader()
    .AllowAnyMethod()
    .SetIsOriginAllowed(_ => true)));

var firebaseSection = builder.Configuration.GetSection(FirebaseOptions.SectionName);
builder.Services.Configure<FirebaseOptions>(firebaseSection);
var firebaseOpts = firebaseSection.Get<FirebaseOptions>() ?? new FirebaseOptions();
var verifyFirebaseTokens = false;

GoogleCredential? firebaseCredential = null;
var saJson = firebaseOpts.ServiceAccountJson?.Trim();
if (!string.IsNullOrEmpty(saJson))
{
    firebaseCredential = GoogleCredential.FromStream(
        new MemoryStream(Encoding.UTF8.GetBytes(saJson), writable: false));
}

if (firebaseCredential is null)
{
    var saPath = firebaseOpts.ServiceAccountJsonPath?.Trim();
    if (!string.IsNullOrEmpty(saPath))
    {
        var resolved = Path.IsPathRooted(saPath) ? saPath : Path.Combine(builder.Environment.ContentRootPath, saPath);
        if (File.Exists(resolved))
        {
            firebaseCredential = GoogleCredential.FromFile(resolved);
        }
        else if (firebaseOpts.RequireConfigured)
        {
            throw new InvalidOperationException($"Firebase service account JSON not found at '{resolved}'.");
        }
    }
}

if (firebaseCredential is not null)
{
    try
    {
        FirebaseApp.Create(new AppOptions { Credential = firebaseCredential });
    }
    catch (ArgumentException ex) when (ex.Message.Contains("already exists", StringComparison.Ordinal))
    {
    }

    verifyFirebaseTokens = true;
}
else if (firebaseOpts.RequireConfigured)
{
    throw new InvalidOperationException("Firebase credentials required but not configured.");
}

builder.Services.AddSingleton(new FirebaseAuthGate(verifyFirebaseTokens));

var app = builder.Build();

await DatabaseBootstrap.ApplyAsync(app);

app.UseCors();
app.UseMiddleware<FirebaseBearerMiddleware>();
if (!app.Environment.IsDevelopment())
{
    app.UseHttpsRedirection();
}

app.MapHealthEndpoints();
app.MapVersionEndpoints();
app.MapSettingsEndpoints();
app.MapDiaryEndpoints();

app.Run();

public partial class Program;
