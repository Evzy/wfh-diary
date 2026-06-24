using System.Net;
using System.Net.Http.Json;
using WfhDiary.Shared.Dtos;

namespace WfhDiary.Api.Tests;

public class LeaveFlowTests
{
    private const int FyYear = 2025;
    private const string LeaveStart = "2025-07-01";
    private const string LeaveEnd = "2025-07-03";

    private static HttpClient CreateClient(WfhDiaryApiFactory factory)
    {
        var client = factory.CreateClient();
        client.DefaultRequestHeaders.Add("X-Debug-User-Id", "leave-test-user");
        return client;
    }

    [Fact]
    public async Task Create_leave_marks_days_and_delete_clears_them()
    {
        await using var factory = new WfhDiaryApiFactory();
        var client = CreateClient(factory);

        await client.GetAsync($"/v1/diaries/{FyYear}");

        var create = await client.PostAsJsonAsync($"/v1/diaries/{FyYear}/leave", new CreateLeaveRequest(
            LeaveStart,
            LeaveEnd,
            "Holiday"));
        create.EnsureSuccessStatusCode();
        var created = await create.Content.ReadFromJsonAsync<LeavePeriodDto>();
        Assert.NotNull(created);
        Assert.Equal(LeaveStart, created!.StartDate);
        Assert.Equal(LeaveEnd, created.EndDate);

        var periods = await client.GetFromJsonAsync<List<LeavePeriodDto>>("/v1/leave-periods");
        Assert.NotNull(periods);
        Assert.Single(periods!);

        var days = await client.GetFromJsonAsync<List<DayEntryDto>>(
            $"/v1/diaries/{FyYear}/days?from={LeaveStart}&to={LeaveEnd}");
        Assert.NotNull(days);
        Assert.Equal(3, days!.Count);
        Assert.All(days, d => Assert.Equal("LEAVE", d.Description, ignoreCase: true));

        var delete = await client.DeleteAsync($"/v1/leave-periods/{created.Id}");
        Assert.Equal(HttpStatusCode.NoContent, delete.StatusCode);

        periods = await client.GetFromJsonAsync<List<LeavePeriodDto>>("/v1/leave-periods");
        Assert.NotNull(periods);
        Assert.Empty(periods!);

        days = await client.GetFromJsonAsync<List<DayEntryDto>>(
            $"/v1/diaries/{FyYear}/days?from={LeaveStart}&to={LeaveEnd}");
        Assert.NotNull(days);
        Assert.All(days!, d => Assert.Null(d.Description));
    }

    [Fact]
    public async Task Delete_leave_unknown_id_returns_not_found()
    {
        await using var factory = new WfhDiaryApiFactory();
        var client = CreateClient(factory);

        var response = await client.DeleteAsync($"/v1/leave-periods/{Guid.NewGuid()}");
        Assert.Equal(HttpStatusCode.NotFound, response.StatusCode);
    }

    [Fact]
    public async Task Delete_leave_preserves_days_covered_by_another_period()
    {
        await using var factory = new WfhDiaryApiFactory();
        var client = CreateClient(factory);

        await client.GetAsync($"/v1/diaries/{FyYear}");

        await client.PostAsJsonAsync($"/v1/diaries/{FyYear}/leave", new CreateLeaveRequest(
            "2025-07-01",
            "2025-07-05",
            "Long break"));

        var shortLeaveResponse = await client.PostAsJsonAsync($"/v1/diaries/{FyYear}/leave", new CreateLeaveRequest(
            "2025-07-02",
            "2025-07-02",
            "Single day"));
        shortLeaveResponse.EnsureSuccessStatusCode();
        var shortLeave = await shortLeaveResponse.Content.ReadFromJsonAsync<LeavePeriodDto>();

        var delete = await client.DeleteAsync($"/v1/leave-periods/{shortLeave!.Id}");
        Assert.Equal(HttpStatusCode.NoContent, delete.StatusCode);

        var day = await client.GetFromJsonAsync<List<DayEntryDto>>(
            $"/v1/diaries/{FyYear}/days?from=2025-07-02&to=2025-07-02");
        Assert.NotNull(day);
        Assert.Single(day!);
        Assert.Equal("LEAVE", day![0].Description, ignoreCase: true);
    }
}
