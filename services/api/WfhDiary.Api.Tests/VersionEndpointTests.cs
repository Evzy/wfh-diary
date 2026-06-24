using WfhDiary.Api;

namespace WfhDiary.Api.Tests;

public class VersionEndpointTests
{
    [Fact]
    public async Task Version_returns_name_and_code()
    {
        await using var factory = new WfhDiaryApiFactory();
        var client = factory.CreateClient();
        var response = await client.GetAsync("/v1/version");
        response.EnsureSuccessStatusCode();
        var body = await response.Content.ReadAsStringAsync();
        Assert.Contains(AppVersion.VersionName, body);
        Assert.Contains("versionCode", body);
    }
}
