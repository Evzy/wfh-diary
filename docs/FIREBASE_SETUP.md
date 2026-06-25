# Firebase Auth — Android + Azure API

**Start here for a full walkthrough:** [`DEVICE_AND_FIREBASE_SETUP.md`](DEVICE_AND_FIREBASE_SETUP.md) (new Firebase project, Maps, sign-in, geofence, Azure — step by step).

Use this doc as a **reference** for how auth behaves once configured.

**Platra uses the same pattern** — see Platra's [`local.properties.example`](https://github.com/Evzy/platra/blob/main/apps/android/local.properties.example) and [`API_AUTH_AND_AI_USAGE.md`](https://github.com/Evzy/platra/blob/main/docs/API_AUTH_AND_AI_USAGE.md).

## Keys and files specific to WFH Diary SSO

| What | Where | Notes |
| --- | --- | --- |
| **Firebase Android app** | [Firebase Console](https://console.firebase.google.com/) | Package name **`dev.wfhdiary.app`** (not Platra's `dev.platra.app`) |
| **`google-services.json`** | `apps/android/app/google-services.json` | Download from Firebase → **do not commit** |
| **SHA-1 / SHA-256** | Firebase → Project settings → Android app → fingerprints | From `.\gradlew :app:signingReport` (debug + release keystores) |
| **`FIREBASE_WEB_CLIENT_ID`** | `apps/android/local.properties` (optional) | OAuth **Web client** id (`…apps.googleusercontent.com`). Auto-read from `google-services.json` if omitted — same as Platra |
| **`MAPS_API_KEY`** | `apps/android/local.properties` | Google Maps SDK key for **Pick on map** geofence UI (same Google Cloud / Firebase project) |
| **`Firebase__ServiceAccountJson`** | Azure App Service `app-wfh-diary-api-dev` | Full service-account JSON body — **separate Firebase project from Platra** unless you intentionally share one |
| Google Sign-In provider | Firebase → Authentication → Sign-in method | Enable **Google** |

WFH Diary does **not** reuse Platra's `google-services.json` — create a Firebase project (or add a second Android app) for `dev.wfhdiary.app`.

---

## Do you need Firebase today?

| Azure API config | Android sign-in | What works |
| --- | --- | --- |
| **Firebase OFF** (current default) | Not required | App sends `X-Debug-User-Id: dev-user` when no `google-services.json`. |
| **Firebase ON** | **Required** (Google) | App sends `Authorization: Bearer <firebase-id-token>`. API verifies token; data is **per Firebase user**. |

---

## Checklist (in order)

### 1. Firebase project + Android app

1. Firebase Console → create project (e.g. `wfh-diary-dev`) or add app to existing project.
2. **Add app** → Android → package **`dev.wfhdiary.app`**.
3. Download **`google-services.json`** → `apps/android/app/google-services.json`.
4. **Sync Gradle** (plugin applies when the file exists).

### 2. Register signing certificates

From `apps/android`:

```powershell
.\gradlew :app:signingReport
```

Add **SHA-1** and **SHA-256** for the **debug** variant (and release if you sideload release builds).

### 3. Enable Google Sign-In + Web client id

1. Firebase → **Authentication** → **Google** → Enable.
2. Copy the **Web client ID** (OAuth 2.0, ends with `.apps.googleusercontent.com`).

Optional in `apps/android/local.properties` (see `local.properties.example`):

```properties
FIREBASE_WEB_CLIENT_ID=YOUR_WEB_CLIENT_ID.apps.googleusercontent.com
```

Rebuild after adding `google-services.json`.

### 3b. Google Maps (home / office picker)

1. [Google Cloud Console](https://console.cloud.google.com/) → same project as Firebase → **APIs & Services** → enable **Maps SDK for Android**.
2. **Credentials** → **Create credentials** → **API key**.
3. Restrict the key (recommended): Android apps → package **`dev.wfhdiary.app`** + your SHA-1.
4. Add to `apps/android/local.properties`:

```properties
MAPS_API_KEY=AIza...
```

5. Sync Gradle and rebuild. Settings → **Pick on map** on Home / Office rows.

### 4. Azure API — verify Firebase tokens

Firebase → **Project settings** → **Service accounts** → **Generate new private key**.

Azure Portal → `app-wfh-diary-api-dev` → **Environment variables**:

| Name | Value |
| --- | --- |
| `Firebase__ServiceAccountJson` | Entire JSON file body `{ "type": "service_account", ... }` |

Restart the web app. After this, **`/v1/*` requires a valid Bearer token**.

### 5. Test end-to-end

1. Rebuild/install the Android app (with `google-services.json`).
2. Settings → **Sign in with Google**.
3. Log a day → sync should hit Azure with your Firebase UID.

---

## How the app wires auth

- **`WfhDiaryApplication`:** when signed in, syncs Firebase ID token → Ktor adds `Authorization: Bearer …` on each API call.
- **No `google-services.json`:** app sends `X-Debug-User-Id: dev-user` (local/dev only).
- **`google-services.json` present but not signed in:** no debug header — API returns **401** on `/v1/*`.

---

## Home & office geofence (separate from SSO)

Settings → **Home & office locations** → **Allow location** → **Pick on map** (or **Use current location**).

If you ignore the morning reminder, the app uses GPS (200 m radius) to pick Home or Office default times before falling back to work templates or the missed-notification default.

---

## Troubleshooting

| Symptom | Fix |
| --- | --- |
| Google sign-in code 16 / re-auth | SHA-1/SHA-256 missing in Firebase for the keystore that signed the APK |
| API 401 after enabling Firebase | Sign in on device; check `Firebase__ServiceAccountJson` on App Service |
| Data "missing" after sign-in | New Firebase UID = new user row; data under `dev-user` is a different account |
| Used Platra's json by mistake | Wrong package — download json for **`dev.wfhdiary.app`** |
