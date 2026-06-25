using System.Net;
using System.Net.Http.Json;
using WfhDiary.Shared.Dtos;

namespace WfhDiary.Api.Tests;

public class WorkTemplateFlowTests
{
    private static HttpClient CreateClient(WfhDiaryApiFactory factory)
    {
        var client = factory.CreateClient();
        client.DefaultRequestHeaders.Add("X-Debug-User-Id", "template-test-user");
        return client;
    }

    [Fact]
    public async Task Create_list_and_delete_work_template()
    {
        await using var factory = new WfhDiaryApiFactory();
        var client = CreateClient(factory);

        var create = await client.PostAsJsonAsync("/v1/work-templates", new CreateWorkTemplateRequest(
            Label: "Office geofence",
            SortOrder: null,
            StartTime: "08:30",
            FinishTime: "17:00",
            BreakHours: 1m,
            Location: "Office",
            Description: "Auto office day",
            IsDefault: false,
            GeofenceLatitude: -33.8688,
            GeofenceLongitude: 151.2093,
            GeofenceRadiusMeters: 200));
        create.EnsureSuccessStatusCode();
        var created = await create.Content.ReadFromJsonAsync<WorkTemplateDto>();
        Assert.NotNull(created);
        Assert.Equal("Office geofence", created!.Label);
        Assert.Equal(200, created.GeofenceRadiusMeters);

        var list = await client.GetFromJsonAsync<List<WorkTemplateDto>>("/v1/work-templates");
        Assert.NotNull(list);
        Assert.Single(list!);

        var delete = await client.DeleteAsync($"/v1/work-templates/{created.Id}");
        Assert.Equal(HttpStatusCode.NoContent, delete.StatusCode);

        list = await client.GetFromJsonAsync<List<WorkTemplateDto>>("/v1/work-templates");
        Assert.NotNull(list);
        Assert.Empty(list!);
    }
}
