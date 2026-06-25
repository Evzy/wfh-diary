# WFH Diary — device setup walkthrough

One checklist to get **Firebase sign-in**, **Google Maps geofence picker**, and **live Azure sync** working on your phone.

**Recommendation:** create a **new Firebase project** (e.g. `wfh-diary-dev`). Do not reuse Platra’s project — different package name (`dev.wfhdiary.app` vs `dev.platra.app`), separate user accounts, separate API keys, and no accidental cross-wiring.

**Time:** ~30–45 minutes first time.

---

## What you’ll end up with

| Piece | Result |
| --- | --- |
| Firebase project | Google SSO; each user gets their own diary on Azure |
| `google-services.json` | Android app talks to Firebase |
| `local.properties` | Maps API key (+ optional Web client id) |
| Azure app setting | API verifies Bearer tokens |
| On device | Sign in → pick home/office on map → log days |

**Do not commit:** `google-services.json`, `local.properties`, service-account JSON, or API keys.

---

## Part A — Firebase project (Console)

### A1. Create project

1. Open [Firebase Console](https://console.firebase.google.com/).
2. **Add project** → name e.g. **`wfh-diary-dev`**.
3. Google Analytics: optional (off is fine for personal dev).
4. Wait for project creation.

### A2. Register the Android app

1. Project overview → **Add app** → **Android**.
2. **Android package name:** `dev.wfhdiary.app` (must match exactly).
3. App nickname: e.g. `WFH Diary`.
4. Debug signing certificate: skip for now — you’ll add SHA fingerprints in A4.
5. **Register app** → **Download `google-services.json`**.
6. Save the file to:

   ```
   apps/android/app/google-services.json
   ```

7. Click through the remaining Firebase wizard (SDK steps already done in this repo).

### A3. Enable Google Sign-In

1. Firebase → **Build** → **Authentication** → **Get started**.
2. **Sign-in method** tab → **Google** → **Enable**.
3. Support email: your Google account → **Save**.
4. Note the **Web client ID** (ends with `.apps.googleusercontent.com`) — optional if you rely on auto-parse from json.

### A4. Register signing fingerprints

From PowerShell:

```powershell
cd C:\Users\evanb\wfh-diary\apps\android
.\gradlew :app:signingReport
```

Copy **SHA-1** and **SHA-256** from the **debug** variant (the keystore that builds your day-to-day APK).

Firebase → **Project settings** (gear) → **Your apps** → Android `dev.wfhdiary.app` → **Add fingerprint** → paste SHA-1, then SHA-256.

If you install **release** or **CI** APKs later, repeat for that keystore’s SHA values.

---

## Part B — Google Maps (same Google Cloud project)

Firebase projects are linked to Google Cloud. Use that project for Maps.

### B1. Enable Maps SDK

1. [Google Cloud Console](https://console.cloud.google.com/) → select project **`wfh-diary-dev`** (same as Firebase).
2. **APIs & Services** → **Library**.
3. Search **Maps SDK for Android** → **Enable**.

### B2. Create API key

1. **APIs & Services** → **Credentials** → **Create credentials** → **API key**.
2. Copy the key (starts with `AIza…`).
3. **Edit API key** (recommended):
   - **Application restrictions** → **Android apps**.
   - Add package `dev.wfhdiary.app` + SHA-1 from A4.
   - **API restrictions** → **Restrict key** → **Maps SDK for Android** only.
4. Save.

5. Add the same key in two places:
   - **`apps/android/local.properties`:** `MAPS_API_KEY=AIza...` (Part C)
   - **GitHub** → `wfh-diary` repo → Settings → Secrets → **`WFH_MAPS_API_KEY`** (so CI release APKs get the map picker too)

---

## Part C — Android `local.properties`

If the file doesn’t exist, copy the example:

```powershell
copy C:\Users\evanb\wfh-diary\apps\android\local.properties.example C:\Users\evanb\wfh-diary\apps\android\local.properties
```

Edit `apps/android/local.properties` — ensure `sdk.dir` points at your SDK, then add:

```properties
MAPS_API_KEY=AIza...your-key...
# Optional — usually auto-read from google-services.json:
# FIREBASE_WEB_CLIENT_ID=123456789-xxxx.apps.googleusercontent.com
```

After saving, rebuild locally (`.\gradlew :app:assembleDebug`). **Pick on map** in Settings should enable (requires location permission too).

**Also add** the same `MAPS_API_KEY` value as GitHub secret **`WFH_MAPS_API_KEY`** on the private `wfh-diary` repo so APKs from `wfh-diary-releases` include Maps.

---

## Part D — Build and install the app

```powershell
cd C:\Users\evanb\wfh-diary\apps\android
.\gradlew :app:assembleDebug
```

Install the APK from `apps/android/app/build/outputs/apk/debug/` (USB, or Android Studio **Run**).

After `google-services.json` is present, the app **requires Google sign-in** for API calls (no more shared `dev-user`).

---

## Part E — Azure API (verify Firebase tokens)

### E1. Service account key

1. Firebase → **Project settings** → **Service accounts**.
2. **Generate new private key** → download JSON (keep secret).

### E2. App Service setting

1. [Azure Portal](https://portal.azure.com/) → **App Services** → **`app-wfh-diary-api-dev`**.
2. **Settings** → **Environment variables** → **App settings**.
3. **New application setting:**

   | Name | Value |
   | --- | --- |
   | `Firebase__ServiceAccountJson` | Paste the **entire** JSON file contents (one line is fine) |

4. **Save** → **Restart** the web app.

After restart, `/v1/*` requires `Authorization: Bearer <firebase-id-token>`. `/health` and `/v1/version` stay public.

**Quick check (should be 401 without token):**

```powershell
curl https://app-wfh-diary-api-dev.azurewebsites.net/v1/settings
```

---

## Part F — On-device smoke test

Work through in order:

- [ ] Open app → **Settings** (drawer).
- [ ] **Sign in with Google** — should succeed (if code 16, re-check SHA fingerprints in A4).
- [ ] **Allow location** when prompted (Home & office section).
- [ ] **Home location** → **Pick on map** — map opens near you; pan so the pin sits on home; adjust radius; **Save location**.
- [ ] Repeat for **Office location** (or skip if WFH-only).
- [ ] Set **Morning reminder** times / **If reminder is ignored** default if you want auto-log behaviour.
- [ ] **Save settings**.
- [ ] Month grid → tap a day → log **Home** or **Office** → should sync (no 401).
- [ ] Optional: **Export financial year (XLSX)** to confirm API round-trip.

---

## Part G — Geofence behaviour (what you configured)

When you **ignore** the morning notification on a working day:

1. App reads GPS.
2. If inside **office** geofence → logs **Office** with your default office times.
3. Else if inside **home** geofence → logs **Home** with default home times.
4. Else if a **work template** geofence matches → applies that template.
5. Else → **If reminder is ignored** setting (Home / Office / Leave / None).

Office wins if both home and office circles overlap (unlikely unless you set them on top of each other).

---

## Troubleshooting

| Symptom | Likely fix |
| --- | --- |
| Google sign-in **code 16** | SHA-1/256 for the keystore that signed this APK missing in Firebase |
| **Pick on map** disabled / blank map | Add `MAPS_API_KEY` to `local.properties`, enable Maps SDK for Android, rebuild |
| API **401** everywhere | Sign in on device; confirm `Firebase__ServiceAccountJson` on Azure and restart app service |
| Diary **empty** after sign-in | Expected — Firebase UID is a new user; old `dev-user` data is a different account |
| Map shows but “For development purposes only” | Billing not enabled on Google Cloud (Maps free tier still needs billing account linked) |
| Wrong project / Platra data | You used Platra’s `google-services.json` — use one for **`dev.wfhdiary.app`** only |

---

## File checklist (local only)

```
apps/android/app/google-services.json     ← from Firebase (A2)
apps/android/local.properties             ← sdk.dir + MAPS_API_KEY (C)
Azure: Firebase__ServiceAccountJson       ← service account JSON (E)
```

---

## Related docs

- [`FIREBASE_SETUP.md`](FIREBASE_SETUP.md) — auth behaviour reference
- [`AZURE_DEPLOY.md`](AZURE_DEPLOY.md) — Azure app + Postgres later
- [`CI_CD.md`](CI_CD.md) — GitHub Actions + shared debug keystore
- [`apps/android/local.properties.example`](../apps/android/local.properties.example) — all local keys
