namespace WfhDiary.Api.Auth;

public sealed class FirebaseOptions
{
    public const string SectionName = "Firebase";

    public string? ServiceAccountJson { get; set; }
    public string? ServiceAccountJsonPath { get; set; }
    public bool RequireConfigured { get; set; }
}
