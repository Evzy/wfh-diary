# Authentication

**Prefix:** `AUTH` · **Last updated:** 2026-10-27

## Purpose & scope

Identifies the user behind every `/v1/*` API call so that diary data is scoped per user. The API runs in one of two modes chosen at startup: **dev mode** (no Firebase credentials; the user id comes from the `X-Debug-User-Id` header) or **verified mode** (Firebase Admin credentials present; every `/v1/*` request must carry a valid Firebase ID token as `Authorization: Bearer …`). On Android, when `google-services.json` is present the app requires Google sign-in via Firebase and attaches the ID token to API calls; otherwise it sends the debug header. Out of scope: per-resource ownership rules (covered by each capability's spec), roles/permissions, account linking or migrating data between user ids, and non-Google sign-in providers.

## Requirements

### AUTH-001 — Debug header identifies the user in dev mode

When Firebase token verification is off, the API uses the `X-Debug-User-Id` header value as the user id for `/v1/*` requests.

- **Given** the API started without Firebase credentials
- **When** a client calls `GET /v1/settings` with header `X-Debug-User-Id: auth-test-user`
- **Then** the request succeeds and is scoped to user id `auth-test-user`

### AUTH-002 — Missing user identity returns 401

A user-scoped endpoint that cannot resolve a user id (no authenticated claim and no `X-Debug-User-Id` header) responds `401 Unauthorized` with a JSON `error` body.

- **Given** the API is in dev mode
- **When** a client calls `GET /v1/settings` with no `Authorization` or `X-Debug-User-Id` header
- **Then** the response is `401` with body `{ "error": "User id required." }`

### AUTH-003 — Verified mode rejects `/v1/*` without a valid Bearer token

When Firebase token verification is on, any request under `/v1/` whose `Authorization` header is missing, not `Bearer …`, empty, or carries a token Firebase rejects receives `401` with a JSON `error` message, before reaching the endpoint.

- **Given** the API started with Firebase service-account credentials
- **When** a client calls any `/v1/*` path with no header, a non-Bearer header, an empty Bearer token, or an invalid/expired token
- **Then** the response is `401` with `error` set to `Missing or invalid Authorization header.`, `Empty bearer token.`, or `Invalid or expired token.` respectively

### AUTH-004 — Verified token's Firebase UID is the user id

When a Bearer token verifies, the Firebase UID becomes the request's user id and takes precedence over any `X-Debug-User-Id` header.

- **Given** the API is in verified mode
- **When** a client calls a `/v1/*` endpoint with a valid Firebase ID token (with or without an `X-Debug-User-Id` header)
- **Then** the endpoint runs scoped to the token's Firebase UID

### AUTH-005 — Paths outside `/v1/` do not require auth

Requests whose path does not start with `/v1/` (case-insensitive) bypass the Bearer check in both modes.

- **Given** the API is running in either mode
- **When** a client calls `GET /health` with no auth headers
- **Then** the request succeeds

### AUTH-006 — Startup selects auth mode from Firebase configuration

At startup the API loads credentials from `Firebase:ServiceAccountJson` (inline JSON) or, failing that, `Firebase:ServiceAccountJsonPath` (absolute or relative to the content root); verified mode is enabled only if credentials load, and startup fails if `Firebase:RequireConfigured` is true and no credentials are available.

- **Given** Firebase configuration values at startup
- **When** the API starts
- **Then** inline JSON wins over the path; a loaded credential enables verified mode; no credential gives dev mode unless `RequireConfigured` is true, in which case startup throws `InvalidOperationException` (including when the configured path does not exist)

### AUTH-007 — Android chooses the auth header per request

Every Ktor request from the app adds `Authorization: Bearer <token>` when a Firebase ID token is held; otherwise it adds `X-Debug-User-Id: dev-user` only if the build has no `google-services.json`.

- **Given** the app's HTTP client is making an API call
- **When** a token is held / no token is held and `google-services.json` is absent / no token is held and `google-services.json` is present
- **Then** the request carries the Bearer header / the debug header / neither header respectively

### AUTH-008 — Android syncs the ID token on auth-state changes

When Firebase is configured, the app listens for Firebase auth-state changes and stores the current user's ID token (fetched without forcing refresh), clearing it on sign-out or on fetch failure.

- **Given** a build with `google-services.json`
- **When** the Firebase auth state changes to a signed-in user / to no user / token fetch fails
- **Then** the held token is set to that user's ID token / cleared / cleared

### AUTH-009 — Login gate before main content

When Firebase is configured, the app shows a loading spinner until the auth state is first checked, then the login screen if no user is signed in, otherwise the main app; builds without `google-services.json` go straight to the main app.

- **Given** the app launches
- **When** Firebase is configured and auth is unchecked / checked with no user / checked with a user — or Firebase is not configured
- **Then** it shows the spinner / login screen / main app with the user's email (or UID) as account label — or the main app

### AUTH-010 — Google sign-in via Credential Manager

Tapping **Continue with Google** obtains a Google ID token via Credential Manager (using `FIREBASE_WEB_CLIENT_ID` as server client id with a random nonce) and signs in to Firebase with it; failures show a human-readable error on the login screen.

- **Given** the login screen is shown
- **When** the user taps sign in
- **Then** the button shows a busy state; on success the auth listener moves the app to main content; on failure the message is shown, with specific guidance for missing `google-services.json`, missing `FIREBASE_WEB_CLIENT_ID`, no available credential, or Google error code 16 / re-auth (register the signing SHA-1/SHA-256 in Firebase)

### AUTH-011 — Sign out

Signing out from Settings signs out of Firebase (when configured) and clears any auth error message.

- **Given** a signed-in user in the main app
- **When** they choose sign out
- **Then** Firebase signs out, the held token is cleared by the auth listener, and the login screen is shown

## Interfaces

| Kind | Location |
| --- | --- |
| API middleware | `services/api/WfhDiary.Api/Auth/FirebaseBearerMiddleware.cs`, `Auth/UnauthorizedExceptionMiddleware.cs` |
| API mode flag & options | `services/api/WfhDiary.Api/Auth/FirebaseAuthGate.cs`, `Auth/FirebaseOptions.cs` (config section `Firebase`: `ServiceAccountJson`, `ServiceAccountJsonPath`, `RequireConfigured`) |
| API wiring | `services/api/WfhDiary.Api/Program.cs` (credential loading, middleware order) |
| User id resolution | `DiaryService.RequireUserId` — `services/api/WfhDiary.Api/Services/DiaryService.cs` (claim `NameIdentifier`, then `X-Debug-User-Id`) |
| Android token & header | `apps/android/app/src/main/java/dev/wfhdiary/app/data/auth/FirebaseIdTokenHolder.kt`, `WfhDiaryApplication.kt` |
| Android sign-in | `apps/android/app/src/main/java/dev/wfhdiary/app/data/auth/GoogleFirebaseSignIn.kt`, `ui/auth/AuthViewModel.kt`, `ui/auth/LoginScreen.kt`, `MainActivity.kt` |
| Setup guide | [FIREBASE_SETUP.md](../../FIREBASE_SETUP.md) |

## Test coverage

| Requirement | Tests |
| --- | --- |
| AUTH-001 | `AuthGateTests.Settings_with_debug_user_header_succeeds_in_dev_mode` |
| AUTH-002 | `AuthGateTests.Settings_without_auth_returns_unauthorized_in_dev_mode` |
| AUTH-003 | untested |
| AUTH-004 | untested |
| AUTH-005 | `AuthGateTests.Health_does_not_require_auth` (dev mode only) |
| AUTH-006 | untested |
| AUTH-007 | untested |
| AUTH-008 | untested |
| AUTH-009 | untested |
| AUTH-010 | untested |
| AUTH-011 | untested |

## Known gaps

- **Verified mode has no automated tests.** The test factory never configures Firebase, so AUTH-003/004 and verified-mode AUTH-005 are untested.
- **Suspected bug — ID token never refreshed.** The app fetches the token with `getIdToken(false)` only when the auth state changes. Firebase ID tokens expire after about an hour, and nothing refreshes the held token (no `IdTokenListener`, no retry on 401). Long-running sessions are likely to get `401`s once verified mode is on.
- **Suspected risk — silent fallback to dev mode.** If `ServiceAccountJsonPath` points at a missing file and `RequireConfigured` is false, the API starts in dev mode and trusts any `X-Debug-User-Id` header. Production should set `RequireConfigured=true`.
- **Debug header still read in verified mode.** `RequireUserId` falls back to `X-Debug-User-Id` whenever there is no claim. Today this is safe only because the Bearer middleware blocks every `/v1/*` path first. A user-scoped endpoint outside `/v1/` would accept a spoofed header. Related checklist item: "No `X-Debug-User-Id` in production builds pointed at prod API" in `AZURE_DATABASE_PLAN.md`.
- The auth sources don't send a signed-in user back to the login screen when the API rejects their token with `401`. The notification flow does classify `401` as `SIGN_IN_REQUIRED` (`notification/ApiErrorClassifier.kt`), but `AuthViewModel` only reacts to Firebase auth-state changes, so that classification never reaches the login gate.
- Firebase is not yet enabled on the live API ([TODO milestone 8](../../TODO.md#ordered-milestones)). Data logged as `dev-user` is a separate account from the Firebase UID after sign-in, and there is no migration.
- Android ViewModel tests are on the backlog ([TODO § B](../../TODO.md#b-strong-unit-testing--partial)).

## Revision log

- 2026-10-27: Initial spec backfilled from implementation ([change](../../changes/2026-10-27-spec-driven-setup/design.md)).
