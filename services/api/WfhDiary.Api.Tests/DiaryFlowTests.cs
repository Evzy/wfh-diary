using System.Net.Http.Json;
using WfhDiary.Api.Services;
using WfhDiary.Shared.Dtos;

namespace WfhDiary.Api.Tests;

public class DiaryFlowTests
{
    private static HttpClient CreateClient(WfhDiaryApiFactory factory)
    {
        var client = factory.CreateClient();
        client.DefaultRequestHeaders.Add("X-Debug-User-Id", "test-user");
        return client;
    }

    [Fact]
    public async Task Upsert_day_and_summary_work()
    {
        await using var factory = new WfhDiaryApiFactory();
        var client = CreateClient(factory);
        var fy = FinancialYearHelper.CurrentFyYear(DateOnly.FromDateTime(DateTime.UtcNow));
        var diary = await client.GetFromJsonAsync<DiaryDto>($"/v1/diaries/{fy}");
        Assert.NotNull(diary);

        var put = await client.PutAsJsonAsync($"/v1/diaries/{fy}/days/{diary!.FyStartDate}", new
        {
            startTime = "08:00",
            finishTime = "17:00",
            breakHours = 1m,
            description = "test work",
            location = "Home",
        });
        put.EnsureSuccessStatusCode();

        var summary = await client.GetFromJsonAsync<DiarySummaryDto>($"/v1/diaries/{fy}/summary");
        Assert.NotNull(summary);
        Assert.True(summary.HomeHours >= 8m);
        Assert.True(summary.ClaimableWfhAmount > 0);
    }

    [Fact]
    public async Task Export_returns_xlsx()
    {
        await using var factory = new WfhDiaryApiFactory();
        var client = CreateClient(factory);
        var fy = FinancialYearHelper.CurrentFyYear(DateOnly.FromDateTime(DateTime.UtcNow));
        await client.GetAsync($"/v1/diaries/{fy}");

        var response = await client.GetAsync($"/v1/diaries/{fy}/export.xlsx");
        response.EnsureSuccessStatusCode();
        var bytes = await response.Content.ReadAsByteArrayAsync();
        Assert.True(bytes.Length > 1000);
        Assert.Equal("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            response.Content.Headers.ContentType?.MediaType);
    }
}
