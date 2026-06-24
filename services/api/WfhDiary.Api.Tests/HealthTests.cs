namespace WfhDiary.Api.Tests;

public class HealthTests
{
    [Fact]
    public async Task Health_returns_ok()
    {
        await using var factory = new WfhDiaryApiFactory();
        var client = factory.CreateClient();
        var response = await client.GetAsync("/health");
        response.EnsureSuccessStatusCode();
    }
}
