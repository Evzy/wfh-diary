using System.Net;
using System.Net.Http.Json;

namespace WfhDiary.Api.Tests;

public class AuthGateTests
{
    [Fact]
    public async Task Settings_without_auth_returns_unauthorized_in_dev_mode()
    {
        await using var factory = new WfhDiaryApiFactory();
        var client = factory.CreateClient();
        var response = await client.GetAsync("/v1/settings");
        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
    }

    [Fact]
    public async Task Settings_with_debug_user_header_succeeds_in_dev_mode()
    {
        await using var factory = new WfhDiaryApiFactory();
        var client = factory.CreateClient();
        client.DefaultRequestHeaders.Add("X-Debug-User-Id", "auth-test-user");
        var response = await client.GetAsync("/v1/settings");
        response.EnsureSuccessStatusCode();
    }

    [Fact]
    public async Task Health_does_not_require_auth()
    {
        await using var factory = new WfhDiaryApiFactory();
        var client = factory.CreateClient();
        var response = await client.GetAsync("/health");
        response.EnsureSuccessStatusCode();
    }
}
