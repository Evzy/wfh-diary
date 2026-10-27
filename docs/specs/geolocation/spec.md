# Geolocation

**Prefix:** `GEO` · **Last updated:** 2026-10-27

## Purpose & scope

On Android, the user can save a Home geofence and an Office geofence (latitude, longitude and radius). When the missed-notification default is `Geolocation`, the app checks the phone's location once, at the moment a default is due. A match applies the Home or Office defaults; no match applies an "away" fallback. Each place can also be set to skip the morning reminder and log automatically. The reminder and missed-default pipeline is specified in [notifications](../notifications/spec.md). Storage of these settings fields is specified in [settings](../settings/spec.md). Template geofence fields are specified in [work-templates](../work-templates/spec.md). Out of scope: continuous background geofence monitoring, logging on arrival, and matching against template geofences (see Known gaps).

## Requirements

### GEO-001 — Within-radius test

A location is inside a geofence when its great-circle (haversine) distance from the centre is at most the radius.

- **Given** a centre point, a radius in metres, and a location
- **When** distance is computed with an Earth radius of 6,371,000 m
- **Then** the location is inside if the distance is less than or equal to the radius, and outside otherwise

### GEO-002 — Place matching and precedence

Matching returns Office, Home or no place. A geofence counts only if its latitude, longitude and radius are all set.

- **Given** the home and office geofence settings and a current location
- **When** the location is matched
- **Then** the result is `OFFICE` if it is inside a configured office geofence (even if it is also inside the home geofence), otherwise `HOME` if it is inside a configured home geofence, otherwise no place

### GEO-003 — Current location lookup

The current location comes from the fused location provider, with a fallback to the last known location.

- **Given** fine or coarse location permission is granted
- **When** a location is needed (place matching, Use current location, or the map picker)
- **Then** a balanced-power current location is requested; if that fails or returns nothing, the newest last-known fix from the GPS or network provider is used. Without permission or without any fix, no location is returned

### GEO-004 — Set geofence from current location

"Use current location" saves Home or Office at the phone's current position with a 200 m radius.

- **Given** the geolocation settings sheet is open and location permission is granted
- **When** the user taps Use current location for Home or Office
- **Then** the draft takes the current latitude and longitude with a 200 m radius and a "saved" message appears. With no permission or no fix, an error message appears instead and the draft is unchanged

### GEO-005 — Pick geofence on a map

The map picker lets the user place a geofence centre and choose a radius between 50 m and 500 m.

- **Given** a Google Maps key is configured and location permission is granted
- **When** the user opens Pick on map for Home or Office
- **Then** the map centres on the existing geofence, or on the current location if none is set. The radius slider runs from 50 to 500 m, starting at the existing radius or 200 m. Save is enabled only after the location lookup has finished with no error and a centre is known, and saving writes the centre and radius into the draft. Without a Maps key, the sheet shows a not-configured message and only Cancel

### GEO-006 — Clear geofence

Tapping Clear for Home or Office asks the server to remove that geofence. Server semantics: [SET-006](../settings/spec.md#set-006--geofence-set-requires-all-three-fields-clear-wins).

- **Given** a configured home or office geofence
- **When** the user taps Clear and the draft is saved
- **Then** the request sends `clearHomeGeofence` or `clearOfficeGeofence` as `true`. The app only treats a geofence as set when latitude, longitude and radius are all present (GEO-002), so it never relies on a partial set

### GEO-007 — Geolocation settings auto-save

Changes in the geolocation settings sheet are saved without an explicit Save button.

- **Given** the geolocation settings sheet is open
- **When** the away fallback, a skip toggle, or a geofence changes
- **Then** `PUT /v1/settings` is sent 500 ms after the last change, and again when the sheet is dismissed. A "saved" message appears on success; the error message appears on failure

### GEO-008 — Geolocation missed default

When the missed default is `Geolocation`, a matching place applies its defaults; with no match, the away fallback is applied.

- **Given** `missedNotificationDefault` is `Geolocation` and the day still needs an entry (NOTIF-007)
- **When** the missed-default job runs
- **Then** matching runs only if at least one geofence is fully configured and a location is available (GEO-003). A match on `HOME` or `OFFICE` applies that place's default times, and the auto-log notification names the place. With no match (including no configured geofence or no location), `missedNotificationAwayDefault` (stored as described in [SET-004](../settings/spec.md#set-004--mode-fields-fall-back-on-unknown-values)) is applied, and the notification names the fallback. On failure, Retry re-runs the whole missed-default job, including the location check

### GEO-009 — Skip morning reminder at a known place

At reminder time, the app can log today automatically instead of showing the reminder if the phone is at a place whose skip toggle is on.

- **Given** the missed default is `Geolocation`, today needs an entry, at least one geofence is fully configured, a location is available, and it matches `HOME` or `OFFICE`, and `geolocationSkipNotificationAtHome` or `geolocationSkipNotificationAtOffice` is on for that place
- **When** the morning reminder worker runs
- **Then** no reminder is shown, the place's defaults are applied, and an auto-log notification is shown. On failure, a failure notification with Retry for that place's action is shown. If any condition is not met, the normal reminder is shown

### GEO-010 — Location permission prompts

The geolocation sheet asks for foreground location first, then background location, and enables each geofence control only when it can work.

- **Given** the geolocation settings sheet
- **When** foreground location isn't granted, or it is granted but background location isn't
- **Then** the sheet shows an "enable location" button, or an "enable background location" button, respectively. Pick on map needs location permission and a Maps key; Use current location needs location permission. While a geofence operation is busy, the permission buttons, Pick on map, Use current location and Clear are disabled; the away-fallback chips and skip switches stay enabled

## Interfaces

| Kind | Location |
| --- | --- |
| API | `GET/PUT /v1/settings` — `services/api/WfhDiary.Api/Endpoints/SettingsEndpoints.cs`, `services/api/WfhDiary.Api/Services/DiaryService.cs` |
| DTO / contract | `packages/shared/WfhDiary.Shared/Dtos/UserSettingsDto.cs`, `packages/shared/WfhDiary.Shared/Dtos/UpdateUserSettingsRequest.cs` (`Home*`/`Office*Geofence*`, `Clear*Geofence`, `MissedNotificationAwayDefault`, `GeolocationSkipNotificationAt*`), `packages/shared/WfhDiary.Shared/Dtos/WorkTemplateDto.cs` (`Geofence*`) |
| Android | `apps/android/app/src/main/java/dev/wfhdiary/app/util/GeofenceHelper.kt`, `util/LocationHelper.kt`, `notification/GeolocationNotificationHelper.kt`, `ui/settings/GeolocationSettingsSheet.kt`, `ui/settings/GeolocationSettingsDraft.kt`, `ui/settings/GeofenceMapPickerSheet.kt`, `ui/settings/WorkLocationsSection.kt`, `ui/settings/SettingsViewModel.kt` |

## Test coverage

| Requirement | Tests |
| --- | --- |
| GEO-001 | `GeofenceHelperTest.isWithinRadius_samePoint_isTrue`, `GeofenceHelperTest.isWithinRadius_farPoint_isFalse` |
| GEO-002 | `GeofenceHelperTest.matchingPlace_prefersOfficeWhenBothMatch`, `GeofenceHelperTest.matchingPlace_returnsHomeWhenOnlyHomeConfigured` |
| GEO-003 | `untested` |
| GEO-004 | `untested` |
| GEO-005 | `untested` |
| GEO-006 | `untested` (server clear is covered under SET-006) |
| GEO-007 | `untested` |
| GEO-008 | `DayLogRetryPolicyTest.failureRetryForMissedDefault_geolocation_uses_missed_worker` (retry policy only; worker untested) |
| GEO-009 | `DayLogNotificationUpdaterActionTest.actionForPlace_maps_home_and_office` (helper only; worker untested) |
| GEO-010 | `untested` |

## Known gaps

- Backlog ([`TODO.md`](../../TODO.md) § A): proactive logging on arrival, and background geofence monitoring. Today, location is checked only once, when the morning reminder or the missed-default job runs.
- Suspected unintended: [`TODO.md`](../../TODO.md) § A and § C say "geofence match → apply template", but no Android notification code reads work-template geofences. Only the Home and Office settings geofences are matched, so template `Geofence*` fields are stored but unused.
- Suspected unintended: the API doesn't validate geofence values (latitude and longitude range, or radius). The 50–500 m radius limit exists only in the map picker slider.
- Suspected unintended: the sheet asks for background location (GEO-010), but location lookups only check foreground permission. `LocationHelper.canCheckLocationInBackground` and `canUseGeofencing` are never called, so on Android 10 and later the worker's location request may fail without background permission and quietly fall back to the away default.
- Suspected unintended: `MorningReminderWorker` treats a failed day-entry fetch (null) as "needs entry", so skip-at-place (GEO-009) could auto-log over an existing entry.
- The map picker uses Sydney CBD (-33.8688, 151.2093) as the starting camera position until the real location resolves.

## Revision log

- 2026-10-27: Initial spec backfilled from implementation ([change](../../changes/2026-10-27-spec-driven-setup/design.md)).
- 2026-10-27: Final-review consistency fixes ([change](../../changes/2026-10-27-spec-driven-setup/design.md)).
