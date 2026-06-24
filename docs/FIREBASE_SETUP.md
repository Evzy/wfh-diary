# Firebase Auth — Android + Azure API

Use this when you want **real sign-in** on the live Azure API. Each user's diary data is scoped to their **Firebase UID** (not shared `dev-user`).

**Related:** Azure app settings → [`AZURE_DEPLOY.md`](AZURE_DEPLOY.md). Platra reference → [`API_AUTH_AND_AI_USAGE.md`](https://github.com/Evzy/platra/blob/main/docs/API_AUTH_AND_AI_USAGE.md).

## Do you need Firebase today?

| Azure API config | Android sign-in | What works |
|------------------|-----------------|------------|
| **Firebase OFF** (current default) | Not required | App sends `X-Debug-User-Id: dev-user` on every request. **All devices share one user's data** — fine for solo testing, not for real accounts. |
| **Firebase ON** | **Required** (Google) | App sends `Authorization: Bearer <firebase-id-token>`. API verifies token; data is **per Firebase user**. |

**Use Azure without Firebase first:** Settings → API backend → **Azure** (now the app default). Log days — data lands on Azure SQLite.

**Enable Firebase** when you want your Google account (only yours) on the live API.

---

## Checklist (in order)

### 1. Firebase project + Android app

1. [Firebase Console](https://console.firebase.google.com/) → create or pick a project (e.g. `wfh-diary-dev`).
2. **Add app** → Android → package name **`dev.wfhdiary.app`**.
3. Download **`google-services.json`** → save as:

   `apps/android/app/google-services.json`

   (Do **not** commit.)

4. **Sync Gradle** in Android Studio (plugin applies automatically when the file exists).

### 2. Register signing certificates (required for Google Sign-In)

From `apps/android`:

```powershell
.\gradlew :app:signingReport
```

Copy **SHA-1** and **SHA-256** from the **`debug`** variant (uses your `ci-shared-debug.keystore` when configured in `local.properties`).

Or with keytool:

```powershell
keytool -list -v -keystore app\ci-shared-debug.keystore -alias wfh_shared_debug
```

In Firebase: **Project settings** → your Android app → **Add fingerprint** → paste SHA-1, then SHA-256.

### 3. Enable Google Sign-In

1. Firebase → **Authentication** → **Sign-in method** → **Google** → Enable.
2. Note the **Web client ID** (OAuth 2.0, ends with `.apps.googleusercontent.com`).

In `apps/android/local.properties` (optional if parsed from json):

```properties
FIREBASE_WEB_CLIENT_ID=YOUR_WEB_CLIENT_ID.apps.googleusercontent.com
```

Rebuild the app after adding `google-services.json`.

### 4. Azure API — verify Firebase tokens

Download **service account JSON**: Firebase → **Project settings** → **Service accounts** → **Generate new private key**.

**Dev (quick):** paste the **entire JSON** as one App Service setting (Portal → `app-wfh-diary-api-dev` → **Environment variables**):

| Name | Value |
|------|--------|
| `Firebase__ServiceAccountJson` | `{ "type": "service_account", ... }` entire file body |

Restart the web app. After this, **`/v1/*` requires a valid Bearer token** (no more `X-Debug-User-Id` for data routes).

**Better (later):** store JSON in **Key Vault** and use `@Microsoft.KeyVault(SecretUri=...)` — see [`AZURE_DEPLOY.md`](AZURE_DEPLOY.md).

Optional fail-fast:

| Name | Value |
|------|--------|
| `Firebase__RequireConfigured` | `true` |

### 5. Test end-to-end

1. Rebuild/install the Android app (with `google-services.json`).
2. Confirm Settings → API backend → **Azure**.
3. Settings → **Sign in with Google**.
4. Log a day on the month grid → sync should hit Azure with your Firebase UID.

---

## How the app wires auth

- **`WfhDiaryApplication`:** when signed in, syncs Firebase ID token → Ktor adds `Authorization: Bearer …` on each API call.
- **Not signed in + no Firebase json:** falls back to `X-Debug-User-Id: dev-user` (dev only).
- **Firebase ON (Azure) + not signed in:** API returns **401** on `/v1/*` — sign in first.

---

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| Google sign-in code 16 / re-auth | SHA-1/SHA-256 missing in Firebase for the keystore that signed the APK |
| API 401 after enabling Firebase | Sign in on device; check `Firebase__ServiceAccountJson` on App Service |
| Data "missing" after sign-in | New Firebase UID = new user row; data under `dev-user` is a different account |
| App still hits local API | Settings → Azure; reinstall if DataStore still has `local` |
