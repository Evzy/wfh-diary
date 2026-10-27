# Notifications

**Prefix:** `NOTIF` · **Last updated:** 2026-10-27

## Purpose & scope

On Android, the app shows a morning reminder on working days. The reminder has quick actions that log today as Home, Office or Leave. If the reminder is ignored, a configured "missed notification" default is applied automatically. Each background log attempt reports its result in a notification: success, or a "Couldn't save entry" failure with an Open app action. Writes are local, so there are no network failures and no Retry. Geofence matching, the Geolocation missed default and skipping the reminder at a known place are specified in [geolocation](../geolocation/spec.md). Storage of the settings fields (notification time, working-days mask, missed default, apply-at time) is specified in [settings](../settings/spec.md). Out of scope: server push (FCM), and template quick actions on the reminder (see Known gaps).

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

Tapping Home, Office or Leave logs that date in the background and reports the result in place of the reminder.

- **Given** the morning reminder for a date
- **When** the user taps Home, Office or Leave
- **Then** pending missed-default work for that date is cancelled and a background worker writes locally through `DiaryStore` (no network), either upserting the day using the settings' Home or Office default start time, finish time, break and description (with location `Home`/`Office`) or creating a single-day leave period. There is no pending state: the reminder keeps its action buttons until the worker finishes, and a repeated tap re-applies the same idempotent upsert. On success, a "logged" notification replaces the reminder (ID 1001) and auto-dismisses after 8 seconds. On failure, a "Couldn't save entry" notification with a single Open app action (which opens the app for that date) replaces the reminder (ID 1001)

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
- **Then** the job writes locally (upserts the day or creates leave) as in NOTIF-005, without showing a pending notification. An auto-log result notification on the `auto_log_result` channel names the default and the apply-at time in 12-hour form (for example "12pm" or "12:30pm"), and it auto-dismisses after 8 seconds. On failure (including when the job can't load settings, leave periods or the day entry), the same "Couldn't save entry" notification as in NOTIF-005 is posted in the auto-log slot (ID 1002) on the `auto_log_result` channel

### ~~NOTIF-009~~ — REMOVED (2026-10-27)

**Removed** (phase 2): writes are local; there are no network failures to classify or retry. (Was: failure classification.)

### ~~NOTIF-010~~ — REMOVED (2026-10-27)

**Removed** (phase 2): writes are local; there are no network failures to classify or retry. (Was: failure notification with Retry.)

### NOTIF-011 — Permission gating

Notifications are posted only when the user allows them.

- **Given** app notifications are disabled at the system level, or, on Android 13 (API 33) and later, `POST_NOTIFICATIONS` is not granted
- **When** any reminder, success or failure notification would be posted
- **Then** nothing is posted, and missed-default scheduling from the reminder (NOTIF-006) is skipped as well

### NOTIF-012 — Test notification

Settings can show the morning reminder immediately so the user can check that notifications work.

- **Given** the Settings screen
- **When** the user sends a test notification
- **Then** if notifications are allowed, the morning reminder for today is shown and a "sent" message appears; otherwise a permission-denied message appears and the user can request the permission or open the system notification settings

## Interfaces

| Kind | Location |
| --- | --- |
| Data | `apps/android/app/src/main/java/dev/wfhdiary/app/data/store/DiaryStore.kt` (settings, leave periods and day writes, reached via `WfhDiaryApplication.diaryRepository`) |
| Model | `apps/android/app/src/main/java/dev/wfhdiary/app/data/model/UserSettingsDto.kt` (`notificationTime`, `notificationWorkingDaysMask`, `missedNotificationDefault`, `missedNotificationApplyAt`) |
| Android | `apps/android/app/src/main/java/dev/wfhdiary/app/notification/` (`NotificationChannels`, `NotificationScheduler`, `MorningReminderWorker`, `MorningReminderNotifier`, `MissedNotificationScheduler`, `MissedNotificationWorker`, `MissedNotificationDefault`, `NotificationActionReceiver`, `NotificationActionWorker`, `NotificationDayActions`, `DayLogWorkEnqueuer`, `DayLogNotificationUpdater`, `NotificationPermissions`) |

## Test coverage

| Requirement | Tests |
| --- | --- |
| NOTIF-001 | `untested` |
| NOTIF-002 | `untested` |
| NOTIF-003 | `untested` |
| NOTIF-004 | `untested` |
| NOTIF-005 | `DayLogNotificationUpdaterTest.showFailure_posts_open_app_only_in_reminder_slot` (failure notification only; worker untested) |
| NOTIF-006 | `MissedNotificationDefaultTest.isEnabled_false_for_none`, `MissedNotificationDefaultTest.isEnabled_true_for_home_office_leave` (enabled check only) |
| NOTIF-007 | `DayEntryHelperTest.needsDefault_true_when_entry_null`, `DayEntryHelperTest.needsDefault_false_when_location_set`, `DayEntryHelperTest.needsDefault_false_when_leave_description`, `DayEntryHelperTest.needsDefault_true_when_empty_day`, `LeavePeriodHelperTest.isOnLeave_true_for_date_in_range` (helpers only) |
| NOTIF-008 | `DayLogNotificationUpdaterTest.showFailure_auto_target_uses_auto_log_slot` (failure notification only; worker untested) |
| NOTIF-009 | `removed` |
| NOTIF-010 | `removed` |
| NOTIF-011 | `untested` |
| NOTIF-012 | `untested` |

## Known gaps

- Backlog ([`TODO.md`](../../TODO.md) § C): optional work-template quick actions on the morning reminder. Template UI was never implemented, and templates are dropped by the on-device-only change.
- Backlog ([`TODO.md`](../../TODO.md) § B): ViewModel and repository tests. No worker, receiver or scheduler code has automated tests; coverage above comes from pure helpers and `DayLogNotificationUpdater` only.
- Suspected unintended: if `MorningReminderWorker` can't load settings (now a local Room read), the reminder is still shown using the 08:00 and Mon–Fri fallbacks, but no missed default is scheduled (NOTIF-006 needs settings).
- Suspected unintended: in `MorningReminderWorker`, a failed leave-period fetch is treated as "not on leave" (`getOrDefault(false)`), so the reminder can appear on a leave day.
- Suspected unintended: if `MissedNotificationWorker` can't load settings, its failure notification always names `Home` as the action, whatever the configured default is.
- Suspected unintended: missed-default scheduling depends on notification permission (NOTIF-011), so users who block notifications never get automatic logging.
- Suspected unintended: a manual quick action (NOTIF-005) upserts the day even if it already has an entry, overwriting existing times. The missed default checks for this case; the quick action does not.
- `NotificationChannels.ACTION_ADD_TIME` is defined but never used (Add time opens the quick-add activity directly).

## Revision log

- 2026-10-27: Initial spec backfilled from implementation ([change](../../changes/2026-10-27-spec-driven-setup/design.md)).
- 2026-10-27: Final-review consistency fixes ([change](../../changes/2026-10-27-spec-driven-setup/design.md)).
- 2026-10-27: On-device-only phase 1 — NOTIF-005 and NOTIF-008 now describe local writes; NOTIF-009/010 kept until phase 2 ([change](../../changes/2026-10-27-on-device-only/design.md)).
- 2026-10-27: Workers no longer fetch a Firebase ID token before local writes, so notification actions work offline; `FirebaseTokenHelper` removed ([change](../../changes/2026-10-27-on-device-only/design.md)).
- 2026-10-27: On-device-only phase 2 — NOTIF-005/008 failures post a "Couldn't save entry" notification with Open app only (no pending state); NOTIF-009/010 removed with `ApiErrorClassifier` and `DayLogRetryPolicy` ([change](../../changes/2026-10-27-on-device-only/design.md)).
