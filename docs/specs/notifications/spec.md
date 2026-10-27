# Notifications

**Prefix:** `NOTIF` · **Last updated:** 2026-10-27

## Purpose & scope

On Android, the app shows a morning reminder on working days. The reminder has quick actions that log today as Home, Office or Leave. If the reminder is ignored, a configured "missed notification" default is applied automatically. Each background log attempt reports its result in a notification: pending, success, or failure with Retry. Geofence matching, the Geolocation missed default and skipping the reminder at a known place are specified in `docs/specs/geolocation/spec.md`. Storage of the settings fields (notification time, working-days mask, missed default, apply-at time) is specified in `docs/specs/settings/spec.md`. Out of scope: server push (FCM), and template quick actions on the reminder (see Known gaps).

## Requirements

### NOTIF-001 — Notification channels

The app registers two notification channels at startup: one for the morning reminder and one for auto-log results.

- **Given** Android 8.0 (API 26) or later
- **When** the application process starts
- **Then** channels `morning_reminder` and `auto_log_result` exist, both with default importance

### NOTIF-002 — Morning reminder scheduling

The morning reminder is scheduled for the next future occurrence of `notificationTime` that falls on a day enabled in `notificationWorkingDaysMask`.

- **Given** settings with a notification time and a working-days mask (if the time can't be parsed, 08:00 is used; if settings can't be loaded inside the worker, 08:00 and the Mon–Fri default mask are used)
- **When** the app starts, settings are saved, or the reminder worker finishes a run
- **Then** a single unique WorkManager job (`wfh_morning_reminder`) replaces any earlier one, delayed until that next time on the next enabled day

### NOTIF-003 — No reminder on non-working or leave days

The morning reminder is not shown on a day excluded by the working-days mask, or on a day inside a leave period.

- **Given** today is not enabled in the mask, or today falls on or between a leave period's start and end dates
- **When** the morning reminder worker runs
- **Then** no notification is shown and the next reminder is scheduled

### NOTIF-004 — Morning reminder content

The morning reminder offers four actions: Home, Office, Leave and Add time.

- **Given** the app can post notifications (NOTIF-011) and today is a reminder day
- **When** the morning reminder is shown (notification ID 1001)
- **Then** it has Home, Office and Leave actions that log today (NOTIF-005), an Add time action that opens the quick-add screen for today, and it is dismissed when tapped

### NOTIF-005 — Quick action logs today

Tapping Home, Office or Leave logs that date in the background and shows progress in place of the reminder.

- **Given** the morning reminder for a date
- **When** the user taps Home, Office or Leave
- **Then** pending missed-default work for that date is cancelled, an ongoing progress notification replaces the reminder, and a background worker refreshes the Firebase token and either upserts the day using the settings' Home or Office default start time, finish time, break and description (with location `Home`/`Office`) or creates a single-day leave period; on success, a "logged" notification is shown and auto-dismisses after 8 seconds

### NOTIF-006 — Missed default scheduling

When the reminder is shown and a missed default is enabled, a one-off job is scheduled to apply that default at `missedNotificationApplyAt` on the same day.

- **Given** `missedNotificationDefault` is not blank and not `None` (any case), and the reminder date is today
- **When** the morning reminder is shown
- **Then** a unique job named `wfh_missed_default_<date>` replaces any earlier one for that date. It runs at the apply-at time (12:00 if that time can't be parsed), or after 60 seconds if that time has already passed

### NOTIF-007 — Missed default skips logged and leave days

The missed-default job does nothing if the default is now disabled, if the date is on leave, or if the day already has an entry.

- **Given** the job runs and re-reads settings, leave periods and that day's entry
- **When** the default is `None`, or the date is inside a leave period, or the entry has a location, a start time, or a `LEAVE` description
- **Then** nothing is written and no notification is shown

### NOTIF-008 — Missed default applies Home, Office or Leave

When the day still needs an entry, the job applies the configured Home, Office or Leave default and reports it.

- **Given** the missed default is `Home`, `Office` or `Leave`, and the day needs an entry
- **When** the missed-default job runs
- **Then** the job upserts the day or creates leave as in NOTIF-005, without showing a pending notification. An auto-log result notification on the `auto_log_result` channel names the default and the apply-at time in 12-hour form (for example "12pm" or "12:30pm"), and it auto-dismisses after 8 seconds

### NOTIF-009 — Failure classification

A failed log attempt is classified into one of four reasons, each with its own message.

- **Given** an exception from a background log attempt (its cause chain is walked)
- **When** it is classified
- **Then** request, connect or socket timeouts, and I/O errors whose message contains "timeout", "timed out" or "connection reset", map to `DATABASE_TIMEOUT`. Other connect or I/O errors and HTTP 502, 503 and 504 map to `SERVER_WAKING`. HTTP 401 maps to `SIGN_IN_REQUIRED`. Anything else maps to `GENERIC`

### NOTIF-010 — Failure notification with Retry

A failed log attempt shows a persistent failure notification with Retry and Open app actions, and Retry re-runs the same kind of work.

- **Given** a log attempt failed for a date
- **When** the failure notification is shown, in the reminder slot (ID 1001) for quick-action attempts or in the auto-log slot (ID 1002) for automatic attempts
- **Then** the Retry intent carries that slot, and the retry's pending and failure notifications reuse it. Success after re-running the original action always posts to ID 1001 on the `morning_reminder` channel; success after re-running the missed-default job posts to ID 1002. Retry cancels missed work for that date and shows the pending state. It re-runs the whole missed-default job when the missed job couldn't load settings, leave periods or the day entry, or when a Geolocation missed default failed; otherwise it re-runs the original action. Open app launches the app for that date

### NOTIF-011 — Permission gating

Notifications are posted only when the user allows them.

- **Given** app notifications are disabled at the system level, or, on Android 13 (API 33) and later, `POST_NOTIFICATIONS` is not granted
- **When** any reminder, pending, success or failure notification would be posted
- **Then** nothing is posted, and missed-default scheduling from the reminder (NOTIF-006) is skipped as well

### NOTIF-012 — Test notification

Settings can show the morning reminder immediately so the user can check that notifications work.

- **Given** the Settings screen
- **When** the user sends a test notification
- **Then** if notifications are allowed, the morning reminder for today is shown and a "sent" message appears; otherwise a permission-denied message appears and the user can request the permission or open the system notification settings

## Interfaces

| Kind | Location |
| --- | --- |
| API | `GET /v1/settings`, `GET /v1/leave-periods`, `GET /v1/diaries/{fyYear}/days`, `PUT /v1/diaries/{fyYear}/days/{date}`, `POST /v1/diaries/{fyYear}/leave` — `services/api/WfhDiary.Api/Endpoints/` (called via `DiaryRepository`) |
| DTO / contract | `packages/shared/WfhDiary.Shared/Dtos/UserSettingsDto.cs` (`NotificationTime`, `NotificationWorkingDaysMask`, `MissedNotificationDefault`, `MissedNotificationApplyAt`) |
| Android | `apps/android/app/src/main/java/dev/wfhdiary/app/notification/` (`NotificationChannels`, `NotificationScheduler`, `MorningReminderWorker`, `MorningReminderNotifier`, `MissedNotificationScheduler`, `MissedNotificationWorker`, `MissedNotificationDefault`, `NotificationActionReceiver`, `NotificationActionWorker`, `NotificationDayActions`, `DayLogWorkEnqueuer`, `DayLogRetryPolicy`, `DayLogNotificationUpdater`, `ApiErrorClassifier`, `NotificationPermissions`, `FirebaseTokenHelper`) |

## Test coverage

| Requirement | Tests |
| --- | --- |
| NOTIF-001 | `untested` |
| NOTIF-002 | `untested` |
| NOTIF-003 | `untested` |
| NOTIF-004 | `untested` |
| NOTIF-005 | `untested` |
| NOTIF-006 | `MissedNotificationDefaultTest.isEnabled_false_for_none`, `MissedNotificationDefaultTest.isEnabled_true_for_home_office_leave` (enabled check only) |
| NOTIF-007 | `DayEntryHelperTest.needsDefault_true_when_entry_null`, `DayEntryHelperTest.needsDefault_false_when_location_set`, `DayEntryHelperTest.needsDefault_false_when_leave_description`, `DayEntryHelperTest.needsDefault_true_when_empty_day`, `LeavePeriodHelperTest.isOnLeave_true_for_date_in_range` (helpers only) |
| NOTIF-008 | `DayLogRetryPolicyTest.failureRetryForMissedDefault_home_uses_apply_action`, `DayLogRetryPolicyTest.failureRetryForMissedDefault_office_uses_apply_action` (failure path only) |
| NOTIF-009 | `ApiErrorClassifierTest.classify_connect_exception_as_server_waking`, `ApiErrorClassifierTest.classify_timeout_message_as_database_timeout`, `ApiErrorClassifierTest.classify_connection_reset_as_database_timeout`, `ApiErrorClassifierTest.classify_connect_timeout_exception_as_database_timeout`, `ApiErrorClassifierTest.classify_nested_connect_timeout_as_database_timeout`, `ApiErrorClassifierTest.classify_unknown_as_generic`, `ApiErrorClassifierTest.classifyResponseStatus_503_as_server_waking`, `ApiErrorClassifierTest.classifyResponseStatus_502_as_server_waking`, `ApiErrorClassifierTest.classifyResponseStatus_504_as_server_waking`, `ApiErrorClassifierTest.classifyResponseStatus_401_as_sign_in_required`, `ApiErrorClassifierTest.classifyResponseStatus_500_as_generic` |
| NOTIF-010 | `DayLogRetryPolicyTest.failureRetryForMissedDefault_geolocation_uses_missed_worker`, `DayLogRetryPolicyTest.notificationTargetFromKey_morning`, `DayLogRetryPolicyTest.notificationTargetFromKey_day_log_or_null`, `DayLogRetryPolicyTest.notificationTargetKey_round_trips`, `DayLogRetryPolicyTest.notificationTargetForWorker_stored_target_overrides_source`, `DayLogRetryPolicyTest.usesMissedWorkerRetry_only_for_missed_mode` (policy helpers only; receiver, worker and load-failure retry paths untested) |
| NOTIF-011 | `untested` |
| NOTIF-012 | `untested` |

## Known gaps

- Backlog ([`TODO.md`](../../TODO.md) § C): optional work-template quick actions on the morning reminder.
- Backlog ([`TODO.md`](../../TODO.md) § B): ViewModel and repository tests. No worker, receiver or scheduler code has automated tests; coverage above comes from pure helpers only.
- Suspected unintended: `MorningReminderWorker` fetches settings without first calling `FirebaseTokenHelper.ensureToken`. If that fetch fails (for example with an expired token), the reminder is still shown using the 08:00 and Mon–Fri fallbacks, but no missed default is scheduled (NOTIF-006 needs settings).
- Suspected unintended: in `MorningReminderWorker`, a failed leave-period fetch is treated as "not on leave" (`getOrDefault(false)`), so the reminder can appear on a leave day.
- Suspected unintended: if `MissedNotificationWorker` can't load settings, its failure notification always names `Home` as the action, whatever the configured default is.
- Suspected unintended: missed-default scheduling depends on notification permission (NOTIF-011), so users who block notifications never get automatic logging.
- Suspected unintended: a manual quick action (NOTIF-005) upserts the day even if it already has an entry, overwriting existing times. The missed default checks for this case; the quick action does not.
- Suspected unintended: when a retried automatic failure (ID 1002) re-runs the original action and succeeds, the success notification goes to ID 1001. The ongoing pending notification in ID 1002 is never cleared.
- `NotificationChannels.ACTION_ADD_TIME` is defined but never used (Add time opens the quick-add activity directly). `NotificationActionWorker.SOURCE_AUTO_APPLY` is never enqueued by production code.

## Revision log

- 2026-10-27: Initial spec backfilled from implementation ([change](../../changes/2026-10-27-spec-driven-setup/design.md)).
