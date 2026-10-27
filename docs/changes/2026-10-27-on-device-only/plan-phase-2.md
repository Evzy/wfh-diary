# On-device only — phase 2 (remove backend) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove everything server-side — the old-server importer, Ktor diary client, Firebase sign-in, network failure/retry notification paths, the .NET API, shared DTO package, deploy/keep-warm workflows and Azure provisioning — leaving a fully offline Android app with backups.

**Architecture:** Phase 1 already made Room (`DiaryStore`) the single source of truth. Phase 2 is deletion plus three small reshapes: the DTO classes in `data/remote/` move to `data/model/` (they are the app's domain model and part of the backup format), the GitHub update check moves from the login screen to app start, and notification failures become a plain "couldn't save — open the app" notification without Retry. Azure deletion is a script the user runs by hand.

**Tech Stack:** Kotlin 2.0.21, Jetpack Compose, Room 2.6.1, WorkManager 2.10, kotlinx.serialization 1.7.3, Ktor 3.0.3 (kept only for `GithubApkReleaseUpdater`), Robolectric 4.14.1, GitHub Actions, Azure CLI.

**Design:** [`design.md`](design.md) § Rollout → Phase 2 and § Removed (phase 2). Gate status: [`tasks.md`](tasks.md) § Phase-1 gate.

## Global Constraints

- Windows PowerShell; no bash, no .NET SDK. Gradle from `apps/android`: `.\gradlew.bat :app:testDebugUnitTest --no-daemon` and `.\gradlew.bat :app:assembleDebug --no-daemon`.
- Commit with `$env:SKIP_VERSION_BUMP="1"` for Tasks 1–4. Task 5 bumps the version once with the updated script.
- Robolectric tests use `@Config(sdk = [34], application = Application::class)` and `runBlocking`.
- Do not change the backup format (`BackupDocument`, `BackupCodec`, `format = "wfh-diary-backup"`, `version = 1`). Moving `UserSettingsDto` to another package must not change its serialized field names.
- Keep: Maps key (`MAPS_API_KEY`, `GOOGLE_MAPS_CONFIGURED`), geolocation, `GithubApkReleaseUpdater` and its Ktor client, `kotlinx-coroutines-play-services` (location uses `Task.await()`), shared debug keystore signing.
- Match surrounding code style; comments only for constraints the code can't show.
- Every task ends with the full unit suite and `assembleDebug` passing.

## File map

| Area | Delete | Modify |
| --- | --- | --- |
| Importer + remote client (Task 1) | `backup/LegacyServerImporter.kt`, `test/.../backup/LegacyServerImporterTest.kt`, `data/remote/DiaryRepository.kt`, `data/remote/WorkTemplateDto.kt`, `data/remote/CreateWorkTemplateRequest.kt`, `test/.../data/remote/ContractDeserializationTest.kt` | `WfhDiaryApplication.kt`, `ui/backup/BackupViewModel.kt`, `ui/backup/BackupUiState.kt`, `ui/backup/BackupSection.kt`, `MainActivity.kt`, `res/values/strings.xml`, `app/build.gradle.kts`, `gradle/libs.versions.toml`; move remaining `data/remote/*.kt` → `data/model/` and fix imports everywhere (main + test); rename `MonthGridViewModel.refreshFromRemote` → `reload` |
| Firebase sign-in (Task 2) | `data/auth/` (2 files), `ui/auth/AuthViewModel.kt`, `ui/auth/LoginScreen.kt`, `app/google-services.json` | `MainActivity.kt`, `ui/update/AppUpdateViewModel.kt`, `ui/settings/SettingsScreen.kt`, `app/build.gradle.kts`, root `build.gradle.kts`, `libs.versions.toml`, `strings.xml`, `local.properties.example`, `.github/workflows/release-android.yml` |
| Notification failure paths (Task 3) | `notification/ApiErrorClassifier.kt`, `notification/DayLogRetryPolicy.kt`, their tests | `DayLogNotificationUpdater.kt`, `NotificationActionReceiver.kt`, `NotificationActionWorker.kt`, `MissedNotificationWorker.kt`, `MorningReminderWorker.kt`, `DayLogWorkEnqueuer.kt`, `NotificationChannels.kt`, `strings.xml`; new `test/.../notification/DayLogNotificationUpdaterTest.kt` |
| Backend + repo (Task 4) | `services/`, `packages/shared/`, `WfhDiary.sln`, `.github/workflows/deploy-api.yml`, `.github/workflows/keep-api-warm.yml`, `scripts/azure/provision-wfh-diary-dev.ps1`, `scripts/azure/provision-wfh-diary-sql.ps1`, `docs/AZURE_DEPLOY.md`, `docs/AZURE_DATABASE_PLAN.md`, `docs/FIREBASE_SETUP.md`, `docs/DEVICE_AND_FIREBASE_SETUP.md`, `docs/testing/API_TEST_PLAN.md` | new `scripts/azure/decommission.ps1`; `.github/workflows/reusable-tests.yml`, `scripts/bump-version.ps1`, `.githooks/pre-commit`, `AGENTS.md`, `docs/CONSTITUTION.md`, `docs/ARCHITECTURE.md`, `docs/TODO.md`, `docs/CI_CD.md`, `docs/testing/TEST_STRATEGY.md`, `README.md`, `.cursor/rules/spec-driven.mdc`, `.gitignore` |
| Specs + version (Task 5) | — | specs `notifications`, `ota-updates`, `auth`, `work-templates`, `backup`, `day-entries`, `settings`, `README.md`; `design.md`, `tasks.md`, `docs/testing/ANDROID_TEST_PLAN.md`, `version.json` |

Paths under `apps/android/app/src/main/java/dev/wfhdiary/app/` are abbreviated (e.g. `backup/…`); tests live under `apps/android/app/src/test/java/dev/wfhdiary/app/`.

---

### Task 1: Remove the importer and the remote diary client; move DTOs to `data/model`

Removes BAK-011 behaviour (the "Import from old server" button) and the HTTP client used only by it. The remaining `data/remote` classes are plain data classes used by the whole app and by the backup format, so they move package unchanged.

**Files:** see File map, Task 1 row.

**Interfaces:**
- Produces: package `dev.wfhdiary.app.data.model` containing (unchanged class bodies) `CreateLeaveRequest`, `DayEntryDto`, `DiaryDto`, `DiarySummaryDto`, `LeavePeriodDto`, `UpdateUserSettingsRequest`, `UpsertDayEntryRequest`, `UserSettingsDto`. No class named `*Repository` remains in `data/`.
- Produces: `MonthGridViewModel.reload(showProgress: Boolean)` (renamed from `refreshFromRemote`, same body).
- `WfhDiaryApplication` keeps `database`, `diaryStore`, `diaryRepository: DiaryDataSource get() = diaryStore`, `backupPreferences`, `backupService`, `restoreService`. It loses `httpClient`, `remoteDiaryRepository`, `legacyServerImporter`, `resolveApiBaseUrl()`. Firebase code in it stays until Task 2.

- [ ] **Step 1: Delete importer and remote client files**

```powershell
git rm apps/android/app/src/main/java/dev/wfhdiary/app/backup/LegacyServerImporter.kt `
  apps/android/app/src/test/java/dev/wfhdiary/app/backup/LegacyServerImporterTest.kt `
  apps/android/app/src/main/java/dev/wfhdiary/app/data/remote/DiaryRepository.kt `
  apps/android/app/src/main/java/dev/wfhdiary/app/data/remote/WorkTemplateDto.kt `
  apps/android/app/src/main/java/dev/wfhdiary/app/data/remote/CreateWorkTemplateRequest.kt `
  apps/android/app/src/test/java/dev/wfhdiary/app/data/remote/ContractDeserializationTest.kt
```

- [ ] **Step 2: Move the remaining DTOs**

```powershell
$src = "apps/android/app/src/main/java/dev/wfhdiary/app/data"
New-Item -ItemType Directory -Force "$src/model" | Out-Null
Get-ChildItem "$src/remote/*.kt" | ForEach-Object { git mv $_.FullName "$src/model/$($_.Name)" }
```

In every moved file change `package dev.wfhdiary.app.data.remote` to `package dev.wfhdiary.app.data.model`. Then replace `dev.wfhdiary.app.data.remote.` with `dev.wfhdiary.app.data.model.` in all `.kt` files under `apps/android/app/src` (imports and fully-qualified references such as `dev.wfhdiary.app.data.remote.UserSettingsDto` in `MissedNotificationWorker.kt`). Edit with the editor tools or a UTF-8-safe script (`[System.IO.File]::ReadAllText/WriteAllText` with `UTF8Encoding($false)`); do not use `Get-Content`/`Set-Content` (it corrupts non-ASCII characters on PowerShell 5).

Verify: `rg -n "data\.remote" apps/android` returns nothing.

- [ ] **Step 3: Strip the importer from the application class**

In `WfhDiaryApplication.kt` delete the `httpClient` property, `remoteDiaryRepository`, `legacyServerImporter`, `resolveApiBaseUrl()`, and the now-unused imports (`LegacyServerImporter`, `DiaryRepository`, all `io.ktor.*`, `kotlinx.serialization.json.Json`). Keep `firebaseIdTokenHolder` and the `FirebaseAuth` listener for now (Task 2 removes them).

- [ ] **Step 4: Remove import from the backup UI**

- `BackupViewModel.kt`: delete `importFromServer()` and everything only it uses (the Firebase token refresh block, `FirebaseAuth`/`await` imports, any `importing`/source flag used only to label server imports). The restore confirm flow for files stays exactly as is.
- `BackupUiState.kt`: delete fields used only by import (e.g. a pending-import flag or source enum value). If a "source" enum ends up with one value, remove the enum and its uses.
- `BackupSection.kt`: delete the `onImportFromServer` parameter and the "Import from old server" button and its hint text.
- `MainActivity.kt`: drop `onImportFromServer = backupViewModel::importFromServer`; rename the three `refreshFromRemote(` calls to `reload(`.
- `MonthGridViewModel.kt`: rename `fun refreshFromRemote(` to `fun reload(`.
- `strings.xml`: delete strings referenced only by the removed import code (find candidates with `rg -n "import" apps/android/app/src/main/res/values/strings.xml`, then confirm each is unreferenced with `rg -n "R.string.<name>" apps/android/app/src/main`).

- [ ] **Step 5: Remove build config and test-only dependency**

- `app/build.gradle.kts`: delete the `AZURE_API_BASE_URL` `buildConfigField` line and `testImplementation(libs.ktor.client.mock)`.
- `gradle/libs.versions.toml`: delete the `ktor-client-mock` library entry. Keep `ktor-client-android`, `ktor-client-content-negotiation`, `ktor-serialization-kotlinx-json` (used by `GithubApkReleaseUpdater`).

Verify: `rg -n "AZURE_API_BASE_URL|legacyServerImporter|remoteDiaryRepository|importFromServer|ktor.client.mock|refreshFromRemote" apps/android` returns nothing.

- [ ] **Step 6: Run the full suite and build**

Run (from `apps/android`): `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon`
Expected: BUILD SUCCESSFUL. Test count drops by the deleted importer and contract tests only; every remaining test passes.

- [ ] **Step 7: Commit**

```powershell
git add -A apps/android
$env:SKIP_VERSION_BUMP="1"; git commit -m "refactor(android): remove old-server importer and remote client; move DTOs to data.model"
```

---

### Task 2: Remove Firebase sign-in; check for updates at app start

Removes AUTH-007..011 behaviour (login gate, Google sign-in, sign out, ID-token header) and moves the automatic update check (OTA-009) from the login screen to the main screen.

**Files:** see File map, Task 2 row.

**Interfaces:**
- Consumes: Task 1's `WfhDiaryApplication` (no HTTP client).
- Produces: `AppUpdateViewModel.checkOnStartIfNeeded()` (renamed from `checkOnLoginIfNeeded`, same once-per-process guard). New composable `ui/update/UpdateAvailableDialog.kt`:

```kotlin
@Composable
fun UpdateAvailableDialog(
    pendingUpdate: GithubApkReleaseInfo,
    busy: Boolean,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
)
```

- Removed BuildConfig fields: `GOOGLE_SERVICES_CONFIGURED`, `FIREBASE_WEB_CLIENT_ID`.

- [ ] **Step 1: Extract the update dialog before deleting the login screen**

Create `apps/android/app/src/main/java/dev/wfhdiary/app/ui/update/UpdateAvailableDialog.kt` with the `AlertDialog` currently in `LoginScreen.kt` lines 56–80, using renamed strings:

```kotlin
package dev.wfhdiary.app.ui.update

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.wfhdiary.app.R
import dev.wfhdiary.app.update.GithubApkReleaseInfo

@Composable
fun UpdateAvailableDialog(
    pendingUpdate: GithubApkReleaseInfo,
    busy: Boolean,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_dialog_title)) },
        text = {
            Text(
                stringResource(
                    R.string.update_dialog_message,
                    pendingUpdate.tagName,
                    pendingUpdate.releaseTitle?.takeIf { it.isNotBlank() } ?: pendingUpdate.assetName,
                ),
            )
        },
        confirmButton = {
            Button(onClick = onDownload, enabled = !busy) {
                Text(stringResource(R.string.github_update_download_install))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.update_dialog_later))
            }
        },
    )
}
```

In `strings.xml` rename `login_update_dialog_title` → `update_dialog_title`, `login_update_dialog_message` → `update_dialog_message`, `login_update_dialog_later` → `update_dialog_later` (same text).

- [ ] **Step 2: Make `MainActivity` always show the app and run the update check at start**

- Delete `authViewModel`, the `when` on `authState`, and the `LoginScreen` branch. `setContent` becomes:

```kotlin
setContent {
    WfhDiaryTheme {
        MainAppContent(
            settingsViewModel = settingsViewModel,
            monthViewModel = monthViewModel,
            appUpdateViewModel = appUpdateViewModel,
            backupViewModel = backupViewModel,
        )
    }
}
```

- `MainAppContent`: remove `accountUserLabel` and `onSignOut` parameters. Add, near the other effects:

```kotlin
LaunchedEffect(Unit) {
    appUpdateViewModel.checkOnStartIfNeeded()
}
val pendingUpdate = githubUpdateUi.pendingRelease
if (githubUpdateUi.showUpdateDialog && pendingUpdate != null) {
    UpdateAvailableDialog(
        pendingUpdate = pendingUpdate,
        busy = githubUpdateUi.busy,
        onDownload = { appUpdateViewModel.downloadAndInstall(activity) },
        onDismiss = appUpdateViewModel::dismissUpdateDialog,
    )
}
```

- Remove now-unused imports (`AuthViewModel`, `LoginScreen`, `Box`, `CircularProgressIndicator`, `Alignment`, etc.).
- `AppUpdateViewModel.kt`: rename `checkOnLoginIfNeeded` → `checkOnStartIfNeeded`. `checkOnStartIfNeeded` already returns early when `!GithubApkReleaseUpdater.isConfigured()`.

- [ ] **Step 3: Remove the Settings account section**

In `SettingsScreen.kt` delete the `accountUserLabel` and `onSignOut` parameters and the whole `if (BuildConfig.GOOGLE_SERVICES_CONFIGURED) { … }` block (lines ~194–209). Remove `BuildConfig` import if unused. Update the call in `MainActivity.kt`.

- [ ] **Step 4: Delete auth code and Firebase from the application class**

```powershell
git rm -r apps/android/app/src/main/java/dev/wfhdiary/app/data/auth apps/android/app/src/main/java/dev/wfhdiary/app/ui/auth apps/android/app/google-services.json
```

In `WfhDiaryApplication.kt` delete `firebaseIdTokenHolder`, the `if (BuildConfig.GOOGLE_SERVICES_CONFIGURED) { FirebaseAuth… }` block, and imports `FirebaseAuth`, `FirebaseIdTokenHolder`, `kotlinx.coroutines.tasks.await`. Rename `authSyncScope` to `appScope` (it still runs the backup-change collector and notification scheduling).

- [ ] **Step 5: Remove Firebase from Gradle and CI**

- `app/build.gradle.kts`: delete `hasGoogleServicesJson` and its `apply(plugin = "com.google.gms.google-services")` block, `firebaseWebClientIdFromGoogleServicesJson()`, the `GOOGLE_SERVICES_CONFIGURED` and `FIREBASE_WEB_CLIENT_ID` `buildConfigField`s (and the `firebaseWebClientId` val), and dependencies `platform(libs.firebase.bom)`, `libs.firebase.auth`, `libs.androidx.credentials`, `libs.androidx.credentials.play.services`, `libs.googleid`. Keep `JsonSlurper` import (still used for `version.json`).
- Root `apps/android/build.gradle.kts`: delete `alias(libs.plugins.google.services) apply false`.
- `libs.versions.toml`: delete versions `googleServices`, `firebaseBom`, `googleId` (and the credentials version if only used by the removed libraries), libraries `firebase-bom`, `firebase-auth`, `googleid`, `androidx-credentials`, `androidx-credentials-play-services`, plugin `google-services`.
- `local.properties.example`: delete the `FIREBASE_WEB_CLIENT_ID` comment block (lines ~20–22).
- `.github/workflows/release-android.yml`: delete the `FIREBASE_WEB_CLIENT_ID: ${{ secrets.WFH_FIREBASE_WEB_CLIENT_ID }}` env line.
- `strings.xml`: delete `login_title`, `login_subtitle`, `login_button`, `login_busy`, `login_update_download_available`, `settings_account_section_title`, `settings_account_sign_out`, `settings_account_signed_in_as`. Check `rg -n "login_" apps/android/app/src/main` returns nothing.

Verify: `rg -n -i "firebase|GOOGLE_SERVICES|googleid|credentials|signIn|signOut" apps/android .github` returns nothing (matches in `.gradle/` caches or `build/` don't count).

- [ ] **Step 6: Run the full suite and build**

Run (from `apps/android`): `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 7: Commit**

```powershell
git add -A apps/android .github/workflows/release-android.yml
$env:SKIP_VERSION_BUMP="1"; git commit -m "feat(android): remove Firebase sign-in; check for updates at app start"
```

---

### Task 3: Replace network failure classification and Retry with a plain local failure notification

Writes are local, so NOTIF-009 (classification) and NOTIF-010 (Retry) go away, along with the "Logging…" pending notification. A local write can still fail (for example storage full), so failures post one notification: title "Couldn't save entry", text "Couldn't log <label>. Open the app to log it.", single action **Open app**.

**Files:** see File map, Task 3 row.

**Interfaces:**
- Produces in `DayLogNotificationUpdater`:

```kotlin
fun showFailure(context: Context, action: String, date: String, target: DayLogNotificationTarget)
```

  (no `reason`, no `retryMode`; one action, "Open app"). `showPending` and `retryPendingIntent` are deleted. `DayLogNotificationTarget` stays.
- `NotificationActionWorker`: input keys `KEY_ACTION`, `KEY_DATE` only; always reports to `DayLogNotificationTarget.MORNING_REMINDER` (it is only enqueued from the reminder's quick actions).
- `DayLogWorkEnqueuer.enqueueManualAction(context, action, date)` is the only enqueue function.
- Deleted: `ApiErrorClassifier`, `DayLogFailureReason`, `DayLogRetryPolicy`, `DayLogFailureRetry`, `NotificationChannels.ACTION_RETRY`, `EXTRA_ACTION`, `EXTRA_RETRY_MODE`, `EXTRA_NOTIFICATION_TARGET`, `RETRY_MODE_*`, `TARGET_*`, `NotificationActionWorker.KEY_SOURCE`, `KEY_NOTIFICATION_TARGET`, `SOURCE_*`.

- [ ] **Step 1: Write the failing test**

Create `apps/android/app/src/test/java/dev/wfhdiary/app/notification/DayLogNotificationUpdaterTest.kt`:

```kotlin
package dev.wfhdiary.app.notification

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DayLogNotificationUpdaterTest {
    private lateinit var context: Context
    private lateinit var manager: NotificationManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        NotificationChannels.create(context)
        manager = context.getSystemService(NotificationManager::class.java)
    }

    @Test
    fun showFailure_posts_open_app_only_in_reminder_slot() {
        DayLogNotificationUpdater.showFailure(
            context = context,
            action = NotificationChannels.ACTION_HOME,
            date = "2026-10-28",
            target = DayLogNotificationTarget.MORNING_REMINDER,
        )

        val posted = shadowOf(manager).getNotification(DayLogNotificationUpdater.MORNING_REMINDER_ID)
        assertEquals(1, posted.actions.size)
        assertEquals("Open app", posted.actions[0].title.toString())
        assertEquals(
            "Couldn\u2019t log Home. Open the app to log it.",
            posted.extras.getCharSequence("android.text").toString(),
        )
    }

    @Test
    fun showFailure_auto_target_uses_auto_log_slot() {
        DayLogNotificationUpdater.showFailure(
            context = context,
            action = MissedNotificationDefault.OFFICE,
            date = "2026-10-28",
            target = DayLogNotificationTarget.DAY_LOG_RESULT,
        )

        val posted = shadowOf(manager).getNotification(DayLogNotificationUpdater.DAY_LOG_RESULT_ID)
        assertEquals(1, posted.actions.size)
    }
}
```

(If `notification_action_home` is not "Home" in `strings.xml`, use its actual value in the expected text.)

- [ ] **Step 2: Run it to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "dev.wfhdiary.app.notification.DayLogNotificationUpdaterTest" --no-daemon`
Expected: FAIL — compilation error, `showFailure` has no overload without `reason`/`retryMode`.

- [ ] **Step 3: Simplify `DayLogNotificationUpdater`**

Replace `showFailure` with:

```kotlin
fun showFailure(
    context: Context,
    action: String,
    date: String,
    target: DayLogNotificationTarget,
) {
    if (!NotificationPermissions.canPost(context)) return
    val message = context.getString(R.string.notification_log_failure_local, actionLabel(context, action))
    val builder =
        NotificationCompat.Builder(context, channelFor(target))
            .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
            .setContentTitle(context.getString(R.string.notification_log_failure_title))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .addAction(
                0,
                context.getString(R.string.notification_action_open_app),
                openAppPendingIntent(context, date),
            )
    NotificationManagerCompat.from(context).notify(notificationIdFor(target), builder.build())
}
```

Delete `showPending` and `retryPendingIntent`. In `strings.xml` add `<string name="notification_log_failure_local">Couldn\u2019t log %1$s. Open the app to log it.</string>` and delete `notification_action_retry`, `notification_log_pending_title`, `notification_log_pending_text`, `notification_log_failure_server`, `notification_log_failure_database`, `notification_log_failure_sign_in`, `notification_log_failure_generic`.

- [ ] **Step 4: Simplify receiver, enqueuer, worker and channels**

- `NotificationActionReceiver.onReceive`: drop the `ACTION_RETRY` branch, `handleRetry` and `notificationTarget`. `handleManualAction` keeps `MissedNotificationScheduler.cancel(context, date)` and `DayLogWorkEnqueuer.enqueueManualAction(context, action, date)`; delete the `showPending` call.
- `DayLogWorkEnqueuer`: single function

```kotlin
fun enqueueManualAction(context: Context, action: String, date: String) {
    val request =
        OneTimeWorkRequestBuilder<NotificationActionWorker>()
            .setInputData(
                workDataOf(
                    NotificationActionWorker.KEY_ACTION to action,
                    NotificationActionWorker.KEY_DATE to date,
                ),
            )
            .build()
    WorkManager.getInstance(context).enqueue(request)
}
```

- `NotificationActionWorker.doWork`: remove `source`/`target`; on success call `DayLogNotificationUpdater.showManualSuccess(applicationContext, action)`; on failure `DayLogNotificationUpdater.showFailure(applicationContext, action, date, DayLogNotificationTarget.MORNING_REMINDER)` then `Result.failure()`. Companion keeps `KEY_ACTION`, `KEY_DATE`.
- `MissedNotificationWorker`: every `showFailure(...)` call drops `reason` and `retryMode`; the final failure uses `action = default` (no `DayLogRetryPolicy`). `showSettingsFailure` loses its `error` parameter.
- `MorningReminderWorker`: the geolocation-apply failure calls `showFailure(applicationContext, DayLogNotificationUpdater.actionForPlace(place), todayStr, DayLogNotificationTarget.DAY_LOG_RESULT)`; drop the unused `error` val.
- `NotificationChannels`: delete `ACTION_RETRY`, `EXTRA_ACTION`, `EXTRA_RETRY_MODE`, `EXTRA_NOTIFICATION_TARGET`, `RETRY_MODE_APPLY`, `RETRY_MODE_MISSED`, `TARGET_MORNING`, `TARGET_DAY_LOG`.
- Check `AndroidManifest.xml`: if the receiver's intent filter lists the retry action, remove it.

- [ ] **Step 5: Delete the classifier and retry policy**

```powershell
git rm apps/android/app/src/main/java/dev/wfhdiary/app/notification/ApiErrorClassifier.kt `
  apps/android/app/src/main/java/dev/wfhdiary/app/notification/DayLogRetryPolicy.kt `
  apps/android/app/src/test/java/dev/wfhdiary/app/notification/ApiErrorClassifierTest.kt `
  apps/android/app/src/test/java/dev/wfhdiary/app/notification/DayLogRetryPolicyTest.kt
```

Verify: `rg -n "ApiErrorClassifier|DayLogFailureReason|DayLogRetryPolicy|RETRY_MODE|ACTION_RETRY|showPending|SOURCE_RETRY|KEY_NOTIFICATION_TARGET" apps/android/app/src` returns nothing.

- [ ] **Step 6: Run the new test, then the full suite and build**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "dev.wfhdiary.app.notification.DayLogNotificationUpdaterTest" --no-daemon` → PASS.
Run: `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon` → BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```powershell
git add -A apps/android
$env:SKIP_VERSION_BUMP="1"; git commit -m "feat(android): local failure notification replaces network classification and retry"
```

---

### Task 4: Delete the backend, its CI and Azure provisioning; add the decommission script; update repo docs

**Files:** see File map, Task 4 row.

**Interfaces:**
- Produces `scripts/azure/decommission.ps1` (run by the user, never by CI or agents).
- Produces `scripts/bump-version.ps1` that only edits `version.json`.

- [ ] **Step 1: Delete backend code, workflows and Azure/Firebase docs**

```powershell
git rm -r services packages/shared WfhDiary.sln `
  .github/workflows/deploy-api.yml .github/workflows/keep-api-warm.yml `
  scripts/azure/provision-wfh-diary-dev.ps1 scripts/azure/provision-wfh-diary-sql.ps1 `
  docs/AZURE_DEPLOY.md docs/AZURE_DATABASE_PLAN.md docs/FIREBASE_SETUP.md docs/DEVICE_AND_FIREBASE_SETUP.md `
  docs/testing/API_TEST_PLAN.md
```

If `packages/` is now empty, it is gone from git automatically. Remove .NET entries (`bin/`, `obj/`, `*.user`, `appsettings.*.local.json` etc.) from `.gitignore` only if they are clearly .NET-only.

- [ ] **Step 2: Add the decommission script**

Create `scripts/azure/decommission.ps1`:

```powershell
# Deletes the WFH Diary Azure resource group (API App Service, App Service plan, SQL server/database).
# Run manually, once, after the phone holds all data and a backup file exists. Irreversible.
param(
    [string]$Subscription = "platra-dev",
    [string]$ResourceGroup = "rg-wfh-diary-dev-australiaeast"
)

$ErrorActionPreference = "Stop"

if (-not (Get-Command az -ErrorAction SilentlyContinue)) {
    Write-Error "Azure CLI (az) is not installed."
}

az account set --subscription $Subscription
if ($LASTEXITCODE -ne 0) { Write-Error "Could not select subscription '$Subscription'. Run 'az login' first." }

$exists = az group exists --name $ResourceGroup
if ($exists -ne "true") {
    Write-Host "Resource group '$ResourceGroup' does not exist in '$Subscription'. Nothing to delete."
    exit 0
}

Write-Host "Resources in ${ResourceGroup}:"
az resource list --resource-group $ResourceGroup --query "[].{name:name, type:type}" --output table

Write-Host ""
Write-Host "This permanently deletes every resource above, including the SQL database." -ForegroundColor Yellow
$typed = Read-Host "Type the resource group name to confirm"
if ($typed -ne $ResourceGroup) {
    Write-Host "Confirmation did not match. Nothing deleted."
    exit 1
}

az group delete --name $ResourceGroup --yes
if ($LASTEXITCODE -ne 0) { Write-Error "az group delete failed." }
Write-Host "Deleted $ResourceGroup."

# The GitHub Actions deploy identity lives in Entra ID, outside the resource group.
$appId = az ad app list --display-name $DeployAppName --query "[0].appId" --output tsv
if ([string]::IsNullOrWhiteSpace($appId)) {
    Write-Host "No app registration named '$DeployAppName'. Done."
    exit 0
}
$answer = Read-Host "Also delete the GitHub deploy app registration '$DeployAppName' ($appId)? [y/N]"
if ($answer -eq "y") {
    az ad app delete --id $appId
    if ($LASTEXITCODE -ne 0) { Write-Error "az ad app delete failed." }
    Write-Host "Deleted app registration $DeployAppName."
}
```

Add `[string]$DeployAppName = "github-wfh-diary-deploy"` to the script's `param(...)` block, and mention the app registration in the header comment.

Verify it parses: `powershell -NoProfile -Command "[System.Management.Automation.Language.Parser]::ParseFile('scripts/azure/decommission.ps1',[ref]`$null,[ref]`$null) | Out-Null; 'ok'"` prints `ok`. Do NOT run the script.

- [ ] **Step 3: Version script and hook only touch `version.json`**

- `scripts/bump-version.ps1`: delete the `# --- Sync API ---` section (AppVersion.cs and csproj writes) and the `-SyncOnly` switch and its `if (-not $SyncOnly)` wrapper (keep the bump logic). Update the header comment to "Bumps version.json (versionCode + patch versionName); the Android build reads it." Keep `Write-Utf8NoBom` and write keys in the existing order: `@{ versionName = ...; versionCode = ... }` produces unordered output on PowerShell 5, so build the JSON string explicitly: `"{""versionCode"":$versionCode,""versionName"":""$versionName""}"`.
- `.githooks/pre-commit`: `git add version.json` only; update the comment "(API + Android read this file)" → "(the Android build reads this file)".

- [ ] **Step 4: CI runs Android only**

`.github/workflows/reusable-tests.yml`: delete the `run_dotnet_tests` input, the `dotnet-test` job, `dotnet-test` from `test-report.needs`, the `DOTNET` env var, its summary row, and `DOTNET` from the `for name in …` loop. `ci.yml` and `release-android.yml` need no change beyond Task 2.

- [ ] **Step 5: Update repo docs**

- `AGENTS.md`: repo map keeps `apps/android/`, `.github/workflows/` (CI, Android release), `docs/`, `scripts/`; delete the `services/` and `packages/shared/` rows. Commands: delete the API test and run commands. Workflow step 3 "DTO changes…" style lines: remove contract-fixture mentions.
- `docs/CONSTITUTION.md`: rewrite principle 5 (contract fixtures) to: "The backup format is the contract. Changes to `BackupDocument` bump its `version` only with a reader for older versions, and are covered by `BackupCodecTest`." Remove other server/API references.
- `.cursor/rules/spec-driven.mdc`: change "Before changing behaviour in `services/`, `apps/`, or `packages/`" to "in `apps/`"; replace the "DTO changes must update `packages/shared/contracts/`…" line with "Backup format changes must follow CONSTITUTION principle 5."
- `docs/ARCHITECTURE.md`: rewrite as an on-device architecture (Compose UI → ViewModels/workers → `DiaryStore` (Room) → `BackupService`/`RestoreService` → SAF folder; `XlsxWriter`; `GithubApkReleaseUpdater` → `wfh-diary-releases`; Maps SDK for geofence picking). Remove API, Azure, Firebase sections.
- `docs/CI_CD.md`: remove Deploy API, Keep API warm, Azure OIDC secrets and Firebase secret; keep CI (Android unit tests + assemble) and Release Android.
- `docs/TODO.md`: remove or mark done items that are backend-only; add nothing new.
- `docs/testing/TEST_STRATEGY.md`: remove .NET/API test layers and contract tests; describe JVM + Robolectric tests and the manual Android test plan.
- `README.md`: one-paragraph description of the offline Android app with backups; link `AGENTS.md`.

Verify: `rg -n -i "services/api|WfhDiary.sln|packages/shared|dotnet|azure|firebase|contract fixture" AGENTS.md README.md docs/CONSTITUTION.md docs/ARCHITECTURE.md docs/CI_CD.md docs/TODO.md docs/testing/TEST_STRATEGY.md .cursor/rules .github scripts` only shows intentional mentions (the decommission script, and historical notes if any).

- [ ] **Step 6: Run the full suite and build**

Run (from `apps/android`): `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon` → BUILD SUCCESSFUL (Android is unaffected, but `version.json` must still parse).

- [ ] **Step 7: Commit**

```powershell
git add -A
$env:SKIP_VERSION_BUMP="1"; git commit -m "chore: delete API, shared DTOs, deploy workflows and Azure provisioning; add decommission script"
```

---

### Task 5: Apply phase-2 spec deltas, update checklists, bump version

**Files:** see File map, Task 5 row. Each spec gets requirement edits, its Test coverage table updated, and a Revision log entry `- 2026-10-27: On-device-only phase 2 — <summary> ([change](../../changes/2026-10-27-on-device-only/design.md)).`

- [ ] **Step 1: Notifications spec**
  - NOTIF-005: remove the "ongoing progress notification replaces the reminder" clause; add "on failure, a 'Couldn't save entry' notification with a single Open app action replaces the reminder (ID 1001)".
  - NOTIF-008: add "on failure, the same notification is posted in the auto-log slot (ID 1002)". Coverage: `DayLogNotificationUpdaterTest.showFailure_posts_open_app_only_in_reminder_slot` for NOTIF-005, `DayLogNotificationUpdaterTest.showFailure_auto_target_uses_auto_log_slot` for NOTIF-008.
  - NOTIF-009, NOTIF-010: replace the requirement bodies with `**Removed** (phase 2): writes are local; there are no network failures to classify or retry.` Coverage rows: `removed`.
  - Delete the Known-gaps bullet "Server-era failure handling remains until phase 2."
- [ ] **Step 2: OTA spec** — OTA-001, OTA-002 marked **Removed** (no API). OTA-009 retitled "Automatic check at app start": once per process, when the main screen first shows and the updater is configured, check the latest release and show the update dialog if newer. Delete the "OTA-009 is behind the on-device-only design" known gap.
- [ ] **Step 3: Auth and work-templates specs** — add at the top `**Status: retired** (2026-10-27, [on-device only](../../changes/2026-10-27-on-device-only/design.md)). The app has no server and no sign-in.` (work templates: "Work templates were dropped; server templates are preserved in backups under `legacy.workTemplates`."). Mark every AUTH-001..011 / TPL-001..008 requirement **Removed**; coverage rows `removed`.
- [ ] **Step 4: Backup spec** — BAK-011 marked **Removed** (phase 2); BAK-009 text: drop any "and import" mention; coverage row `removed`.
- [ ] **Step 5: Other specs** — `day-entries`: delete the "Server (legacy, until phase 2)" row and any remaining API/HTTP wording; `settings` and others: `rg -n -i "phase 2|server|api|firebase|sign" docs/specs` and fix wording that describes the old server as current (historical revision-log lines stay). `docs/specs/README.md`: mark auth and work-templates as retired in the index.
- [ ] **Step 6: Checklists** — `docs/testing/ANDROID_TEST_PLAN.md`: delete login/sign-in and "Import from old server" checks; add "Fresh install opens straight to the month grid (no sign-in)", and "Update dialog appears at app start when a newer release exists" (the failure notification is covered by `DayLogNotificationUpdaterTest`; a local write failure can't be triggered on a phone). `tasks.md`: tick phase-2 items done by Tasks 1–5; leave "User runs `scripts/azure/decommission.ps1`" unchecked; set top checklist items as appropriate. `design.md`: status `shipped`.
- [ ] **Step 7: Bump version** — run `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/bump-version.ps1`; expect `version.json` = `{"versionCode":26,"versionName":"0.1.25"}`. Run the full suite and build once more → BUILD SUCCESSFUL.
- [ ] **Step 8: Commit**

```powershell
git add -A
$env:SKIP_VERSION_BUMP="1"; git commit -m "docs: phase-2 spec deltas, checklists, ship on-device-only; bump to 0.1.25"
```

---

## After merge (user, manual)

1. Install v0.1.25 over v0.1.24 (OTA). Data stays (same Room v2 schema). Check the app opens without sign-in, data is present, a backup is written.
2. Finish phase-1 gate step 4 and any remaining checks you care about.
3. Run `scripts/azure/decommission.ps1` (needs `az login`). It lists the resources and asks you to type the resource group name, then offers to delete the `github-wfh-diary-deploy` app registration (Entra ID, outside the resource group).
4. Delete unused GitHub secrets: `AZURE_CLIENT_ID`, `AZURE_TENANT_ID`, `AZURE_SUBSCRIPTION_ID`, `WFH_FIREBASE_WEB_CLIENT_ID` (`gh secret delete <name>`).
5. Firebase: optionally disable Authentication in the Firebase console. **Do not delete the Google Cloud/Firebase project if the Maps API key (`WFH_MAPS_API_KEY`) lives in it** — geolocation would stop working.
