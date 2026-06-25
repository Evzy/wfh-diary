using System.Net.Http.Json;
using System.Text.Json;
using WfhDiary.Shared.Dtos;

namespace WfhDiary.Api.Tests;

public class ApiContractTests
{
    private static readonly string[] SettingsJsonProperties =
    [
        "displayName",
        "notificationTime",
        "notificationWorkingDaysMask",
        "missedNotificationDefault",
        "missedNotificationApplyAt",
        "defaultHomeStart",
        "defaultHomeFinish",
        "defaultHomeBreakHours",
        "defaultOfficeStart",
        "defaultOfficeFinish",
        "defaultOfficeBreakHours",
        "homeGeofenceLatitude",
        "homeGeofenceLongitude",
        "homeGeofenceRadiusMeters",
        "officeGeofenceLatitude",
        "officeGeofenceLongitude",
        "officeGeofenceRadiusMeters",
    ];

    [Theory]
    [InlineData("user-settings.json", typeof(UserSettingsDto))]
    [InlineData("day-entry.json", typeof(DayEntryDto))]
    [InlineData("leave-period.json", typeof(LeavePeriodDto))]
    [InlineData("diary-summary.json", typeof(DiarySummaryDto))]
    [InlineData("diary.json", typeof(DiaryDto))]
    [InlineData("work-template.json", typeof(WorkTemplateDto))]
    public void Fixture_deserializes_to_shared_dto(string fileName, Type dtoType)
    {
        var json = File.ReadAllText(ContractTestPaths.ContractFile(fileName));
        var dto = JsonSerializer.Deserialize(json, dtoType, JsonOptions());
        Assert.NotNull(dto);
    }

    [Fact]
    public async Task Live_settings_response_matches_contract_properties()
    {
        await using var factory = new WfhDiaryApiFactory();
        var client = factory.CreateClient();
        client.DefaultRequestHeaders.Add("X-Debug-User-Id", "contract-test-user");

        var json = await client.GetStringAsync("/v1/settings");
        using var doc = JsonDocument.Parse(json);
        foreach (var property in SettingsJsonProperties)
        {
            Assert.True(
                doc.RootElement.TryGetProperty(property, out _),
                $"Missing JSON property '{property}' on GET /v1/settings.");
        }
    }

    [Fact]
    public async Task Live_day_upsert_response_matches_contract_properties()
    {
        await using var factory = new WfhDiaryApiFactory();
        var client = factory.CreateClient();
        client.DefaultRequestHeaders.Add("X-Debug-User-Id", "contract-day-user");
        const int fy = 2025;
        const string date = "2025-08-01";

        await client.GetAsync($"/v1/diaries/{fy}");
        var response = await client.PutAsJsonAsync($"/v1/diaries/{fy}/days/{date}", new
        {
            startTime = "08:00",
            finishTime = "17:00",
            breakHours = 1m,
            location = "Home",
        });
        response.EnsureSuccessStatusCode();

        var json = await response.Content.ReadAsStringAsync();
        using var doc = JsonDocument.Parse(json);
        foreach (var property in new[] { "date", "dayName", "startTime", "finishTime", "breakHours", "totalHours", "description", "location" })
        {
            Assert.True(
                doc.RootElement.TryGetProperty(property, out _),
                $"Missing JSON property '{property}' on PUT day response.");
        }
    }

    private static JsonSerializerOptions JsonOptions() => new()
    {
        PropertyNameCaseInsensitive = true,
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
    };
}
