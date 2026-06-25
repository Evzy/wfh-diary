namespace WfhDiary.Api.Tests;

internal static class ContractTestPaths
{
    public static string RepoRoot()
    {
        var dir = new DirectoryInfo(AppContext.BaseDirectory);
        while (dir is not null && !File.Exists(Path.Combine(dir.FullName, "version.json")))
        {
            dir = dir.Parent;
        }

        return dir?.FullName
            ?? throw new InvalidOperationException("Could not locate repo root (version.json).");
    }

    public static string ContractFile(string name) =>
        Path.Combine(RepoRoot(), "packages", "shared", "contracts", name);
}
