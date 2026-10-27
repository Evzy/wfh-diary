# Authentication

**Prefix:** `AUTH` · **Last updated:** 2026-10-27

**Status: retired** (2026-10-27, [on-device only](../../changes/2026-10-27-on-device-only/design.md)). The app has no server and no sign-in.

## Purpose & scope

Formerly identified the user behind every API call (debug header in dev mode, Firebase ID token in verified mode) and gated the Android app behind Google sign-in via Firebase. The API, the Firebase sign-in code and `google-services.json` were removed in phase 2 of the on-device-only change. Data is stored on the device only (see [day-entries](../day-entries/spec.md)) and protected by backups (see [backup](../backup/spec.md)). The requirement IDs below are kept so references to them stay resolvable.

## Requirements

### ~~AUTH-001~~ — REMOVED (2026-10-27)

**Removed** (phase 2): the API was deleted. (Was: debug header identifies the user in dev mode.)

### ~~AUTH-002~~ — REMOVED (2026-10-27)

**Removed** (phase 2): the API was deleted. (Was: missing user identity returns 401.)

### ~~AUTH-003~~ — REMOVED (2026-10-27)

**Removed** (phase 2): the API was deleted. (Was: verified mode rejects API calls without a valid Bearer token.)

### ~~AUTH-004~~ — REMOVED (2026-10-27)

**Removed** (phase 2): the API was deleted. (Was: verified token's Firebase UID is the user id.)

### ~~AUTH-005~~ — REMOVED (2026-10-27)

**Removed** (phase 2): the API was deleted. (Was: paths outside the versioned API do not require auth.)

### ~~AUTH-006~~ — REMOVED (2026-10-27)

**Removed** (phase 2): the API was deleted. (Was: startup selects auth mode from Firebase configuration.)

### ~~AUTH-007~~ — REMOVED (2026-10-27)

**Removed** (phase 2): the app makes no diary API calls. (Was: Android chooses the auth header per request.)

### ~~AUTH-008~~ — REMOVED (2026-10-27)

**Removed** (phase 2): Firebase sign-in was removed from the app. (Was: Android syncs the ID token on auth-state changes.)

### ~~AUTH-009~~ — REMOVED (2026-10-27)

**Removed** (phase 2): the app opens straight to the month grid. (Was: login gate before main content.)

### ~~AUTH-010~~ — REMOVED (2026-10-27)

**Removed** (phase 2): Firebase sign-in was removed from the app. (Was: Google sign-in via Credential Manager.)

### ~~AUTH-011~~ — REMOVED (2026-10-27)

**Removed** (phase 2): there is no account to sign out of. (Was: sign out.)

## Interfaces

| Kind | Location |
| --- | --- |
| — | None. The API auth middleware, Android token holder, sign-in code and login screen were removed in phase 2. |

## Test coverage

| Requirement | Tests |
| --- | --- |
| AUTH-001 | `removed` |
| AUTH-002 | `removed` |
| AUTH-003 | `removed` |
| AUTH-004 | `removed` |
| AUTH-005 | `removed` |
| AUTH-006 | `removed` |
| AUTH-007 | `removed` |
| AUTH-008 | `removed` |
| AUTH-009 | `removed` |
| AUTH-010 | `removed` |
| AUTH-011 | `removed` |

## Known gaps

- None. The capability is retired.

## Revision log

- 2026-10-27: Initial spec backfilled from implementation ([change](../../changes/2026-10-27-spec-driven-setup/design.md)).
- 2026-10-27: Final-review consistency fixes ([change](../../changes/2026-10-27-spec-driven-setup/design.md)).
- 2026-10-27: On-device-only phase 2 — capability retired; AUTH-001..011 removed with the API and Firebase sign-in ([change](../../changes/2026-10-27-on-device-only/design.md)).
