# Reliable morning reminder — design

**Status:** shipped · **Date:** 2026-10-28 · **Touches specs:** [notifications](../../specs/notifications/spec.md), [geolocation](../../specs/geolocation/spec.md)

## Problem

On 2026-10-28 (Samsung SM-A566B, Android 16, v0.1.26) neither the 10:00 morning reminder nor the 12:00 Geolocation missed default fired.

Evidence from the device:

- Settings were 10:00, Mon–Fri, missed default `Geolocation` at 12:00; notification and location permissions granted.
- The app was idle from 00:09; Android moved it to the `WORKING_SET` then `FREQUENT` standby bucket. `wfh_morning_reminder` is a OneTime WorkManager job with a ~10 h initial delay, which JobScheduler may defer in those buckets. Samsung's process killer also killed an app process at 10:02:43.
- No reminder was posted and no `wfh_missed_default_2026-10-28` job ever existed.
- At 11:44 the user opened the app. `WfhDiaryApplication.onCreate` calls `NotificationScheduler.scheduleNext`, which REPLACEs the pending job with the *next future* 10:00 — tomorrow. Today's reminder was dropped.
- Because the missed default is only scheduled by the reminder (NOTIF-006), it was lost too.
- Force-running the queued job at 12:05 worked end to end (reminder posted, missed default applied a minute later), so the worker logic is fine; delivery and rescheduling are the problem.

Two related weaknesses:

- `LocationHelper.getCurrentLocation` awaits the fused provider with no timeout, so a background run can hang.
- The worker REPLACEs its own unique work name while running, which cancels itself (`WorkerStoppedException` in logs). Harmless today because it's the last step, but fragile.

## Goals / non-goals

**Goals**

- The reminder fires at the configured time even when the app is idle or in Doze.
- A reminder that didn't run today (deferred, killed, phone off) is caught up the next time the app starts or reschedules, instead of skipping to tomorrow. Catch-up also schedules the missed default (or applies it after 60 s if the apply-at time has passed, per existing NOTIF-006).
- Location lookups in workers can't hang.

**Non-goals**

- Changing the missed-default worker, reminder content, or the Geolocation decision logic.
- Catching up on earlier days (only today).

## Requirement delta

- **MODIFIED** `NOTIF-002` — Morning reminder scheduling. The morning reminder is scheduled with an exact alarm for the next future occurrence of `notificationTime` on a day enabled in `notificationWorkingDaysMask`.
  - **Given** settings with a notification time and a working-days mask (08:00 if the time can't be parsed; 08:00 and Mon–Fri if settings can't be loaded inside the worker)
  - **When** the app starts, settings are saved, a backup is restored, the reminder worker finishes a run, or the device boots, the app is updated, or the time or time zone changes
  - **Then** a single exact, allow-while-idle alarm replaces any earlier one for that next time on the next enabled day. When it fires, the reminder worker is enqueued as expedited unique work `wfh_morning_reminder_run` (kept if one is already queued or running). If exact alarms aren't permitted, an inexact allow-while-idle alarm is used instead.
  - *Changed:* was a WorkManager job with an initial delay; restore was already a trigger in code; boot, app update and time changes are new triggers.
- **ADDED** `NOTIF-013` — Missed reminder catch-up. If today's reminder hasn't run by the time the app reschedules, it runs now.
  - **Given** today is enabled in the working-days mask, the current time is at or after today's `notificationTime`, and the reminder worker has not completed a run for today's date
  - **When** any NOTIF-002 trigger other than the reminder's own alarm or run reschedules the reminder
  - **Then** the reminder worker is enqueued to run immediately as a catch-up run (it applies NOTIF-003, GEO-008/009 and NOTIF-006 as usual), and the alarm is set for the next occurrence after today. A catch-up run shows no reminder (and schedules no missed default) if today already has an entry (as defined in NOTIF-007). The worker records today's date as run when it finishes, whatever the outcome.
- **MODIFIED** `GEO-003` — Current location lookup. Adds a timeout.
  - **Then** a balanced-power current location is requested with a 30-second timeout; if that fails, times out or returns nothing, the newest last-known fix from the GPS or network provider is used. Without permission or without any fix, no location is returned.
  - *Changed:* added the 30 s timeout.

## Approach

- **`ReminderAlarmScheduler`** (new, `notification/`): computes the next time via existing `NotificationScheduler.nextReminderAt`, sets `AlarmManager.setExactAndAllowWhileIdle(RTC_WAKEUP, …)` with a `PendingIntent` to `ReminderAlarmReceiver`; falls back to `setAndAllowWhileIdle` when `canScheduleExactAlarms()` is false. Re-setting the same `PendingIntent` replaces the previous alarm.
- **`ReminderAlarmReceiver`** (new, not exported): enqueues `MorningReminderWorker` as expedited unique work `wfh_morning_reminder_run` (KEEP, falls back to non-expedited when out of quota). The worker implements `getForegroundInfo` for API < 31, where expedited work runs as a foreground service.
- **`ReminderRescheduleReceiver`** (new): on `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIME_SET` and `TIMEZONE_CHANGED`, reads settings (`goAsync`) and calls `scheduleNext`. Needs `RECEIVE_BOOT_COMPLETED`.
- **Catch-up when already logged**: the catch-up run passes `catch_up=true` input; the worker returns early (after recording the run) when today already has an entry.
- **`NotificationScheduler.scheduleNext`** keeps its signature (callers unchanged: `WfhDiaryApplication.onCreate`, restore callback, settings save, worker). It now: (1) checks catch-up — if due, enqueue the worker immediately; (2) sets the alarm for the next occurrence. Decision logic is a pure function `reminderCatchUpDue(now, notificationTime, mask, lastRunDate)` for unit tests.
- **Last-run date**: stored in a small `SharedPreferences` (`reminder_state`, key `last_run_date`), written by `MorningReminderWorker` on every exit path. Not part of backup (device-local state; CONSTITUTION principle 5 untouched).
- **Worker self-cancel**: the worker sets the next alarm instead of REPLACEing its own unique work, so it no longer cancels itself. It must not trigger catch-up for itself — it writes the last-run date before rescheduling.
- **Manifest**: add `USE_EXACT_ALARM` (auto-granted on API 33+, fine for a sideloaded app); keep `SCHEDULE_EXACT_ALARM` with `android:maxSdkVersion="32"`; register the receiver.
- **Location timeout**: wrap the fused `await()` in `withTimeoutOrNull(30_000)`, then fall back to last known.
- The legacy delayed `wfh_morning_reminder` job from 0.1.26 is cancelled by `scheduleNext` (idempotent).

## Test impact

- New `NotificationSchedulerTest` (pure): `reminderCatchUpDue` true after time on a working day with no run today; false before time, on a non-working day, or when already run today. Maps NOTIF-013.
- `nextReminderAt` tests (NOTIF-002): later today vs next working day, skips disabled days.
- `ReminderAlarmScheduler` Robolectric test: alarm set via `ShadowAlarmManager` at the expected trigger time; inexact fallback when exact not permitted. Maps NOTIF-002.
- `LocationHelper` timeout: unit test with a never-completing task returns the last-known fallback (or null) within the timeout using a test dispatcher. Maps GEO-003.
- Update notifications and geolocation spec Test coverage tables and Revision logs.
- Manual: on the phone, set the reminder 2 minutes ahead, background the app, confirm it fires; then set a time in the past and reopen the app to confirm catch-up.

## Risks

- **Reboot**: alarms are cleared on reboot; the boot receiver restores them (and catches up if the time passed while the phone was off).
- **Catch-up timing**: opening the app after the reminder time shows the reminder at that moment (unless today is logged). Intended: it also schedules the missed default.
- **Process start from the alarm**: `Application.onCreate` also reschedules, so the alarm firing can race a catch-up enqueue. Both use the same unique run name with KEEP, so only one run happens.
- **Samsung battery optimisation** can still kill or delay processes; exact alarms are the strongest standard mechanism short of asking the user to exempt the app.
- `USE_EXACT_ALARM` would need justification for Play Store distribution; the app is distributed via GitHub releases.
