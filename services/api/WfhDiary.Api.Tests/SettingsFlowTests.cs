using System.Net.Http.Json;
using WfhDiary.Shared.Dtos;

namespace WfhDiary.Api.Tests;

public class SettingsFlowTests
{
    private static HttpClient CreateClient(WfhDiaryApiFactory factory)
    {
        var client = factory.CreateClient();
        client.DefaultRequestHeaders.Add("X-Debug-User-Id", "settings-test-user");
        return client;
    }

    [Fact]
    public async Task Settings_default_missed_notification_values()
    {
        await using var factory = new WfhDiaryApiFactory();
        var client = CreateClient(factory);

        var settings = await client.GetFromJsonAsync<UserSettingsDto>("/v1/settings");
        Assert.NotNull(settings);
        Assert.Equal("None", settings!.MissedNotificationDefault);
        Assert.Equal("12:00", settings.MissedNotificationApplyAt);
        Assert.Equal(31, settings.NotificationWorkingDaysMask);
    }

    [Fact]
    public async Task Settings_update_missed_notification_persists()
    {
        await using var factory = new WfhDiaryApiFactory();
        var client = CreateClient(factory);

        var updated = await client.PutAsJsonAsync("/v1/settings", new UpdateUserSettingsRequest(
            DisplayName: null,
            NotificationTime: null,
            NotificationWorkingDaysMask: null,
            MissedNotificationDefault: "Home",
            MissedNotificationApplyAt: "14:30",
            DefaultHomeStart: null,
            DefaultHomeFinish: null,
            DefaultHomeBreakHours: null,
            DefaultOfficeStart: null,
            DefaultOfficeFinish: null,
            DefaultOfficeBreakHours: null));
        updated.EnsureSuccessStatusCode();
        var dto = await updated.Content.ReadFromJsonAsync<UserSettingsDto>();
        Assert.NotNull(dto);
        Assert.Equal("Home", dto!.MissedNotificationDefault);
        Assert.Equal("14:30", dto.MissedNotificationApplyAt);

        var loaded = await client.GetFromJsonAsync<UserSettingsDto>("/v1/settings");
        Assert.NotNull(loaded);
        Assert.Equal("Home", loaded!.MissedNotificationDefault);
        Assert.Equal("14:30", loaded.MissedNotificationApplyAt);
    }

    [Fact]
    public async Task Settings_invalid_missed_notification_default_falls_back_to_none()
    {
        await using var factory = new WfhDiaryApiFactory();
        var client = CreateClient(factory);

        var updated = await client.PutAsJsonAsync("/v1/settings", new UpdateUserSettingsRequest(
            DisplayName: null,
            NotificationTime: null,
            NotificationWorkingDaysMask: null,
            MissedNotificationDefault: "Invalid",
            MissedNotificationApplyAt: null,
            DefaultHomeStart: null,
            DefaultHomeFinish: null,
            DefaultHomeBreakHours: null,
            DefaultOfficeStart: null,
            DefaultOfficeFinish: null,
            DefaultOfficeBreakHours: null));
        updated.EnsureSuccessStatusCode();
        var dto = await updated.Content.ReadFromJsonAsync<UserSettingsDto>();
        Assert.Equal("None", dto!.MissedNotificationDefault);
    }
}
