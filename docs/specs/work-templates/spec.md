# Work templates (presets)

**Prefix:** `TPL` · **Last updated:** 2026-10-27

**Status: retired** (2026-10-27, [on-device only](../../changes/2026-10-27-on-device-only/design.md)). Work templates were dropped; server templates are preserved in backups under `legacy.workTemplates`.

## Purpose & scope

Formerly specified reusable work-day presets served by the per-user work templates API. The Android app never had template UI. The API and the Android template DTOs were removed in phase 2 of the on-device-only change. The built-in Home and Office presets remain as default-schedule fields on user settings (see [settings](../settings/spec.md)) and are what notification quick actions apply (NOTIF-005 in [notifications](../notifications/spec.md)). The requirement IDs below are kept so references to them stay resolvable.

## Requirements

### ~~TPL-001~~ — REMOVED (2026-10-27)

**Removed** (phase 2): the API was deleted. (Was: list templates.)

### ~~TPL-002~~ — REMOVED (2026-10-27)

**Removed** (phase 2): the API was deleted. (Was: label is required on create.)

### ~~TPL-003~~ — REMOVED (2026-10-27)

**Removed** (phase 2): the API was deleted. (Was: create template.)

### ~~TPL-004~~ — REMOVED (2026-10-27)

**Removed** (phase 2): the API was deleted. (Was: at most one default template.)

### ~~TPL-005~~ — REMOVED (2026-10-27)

**Removed** (phase 2): the API was deleted. (Was: partial update.)

### ~~TPL-006~~ — REMOVED (2026-10-27)

**Removed** (phase 2): the API was deleted. (Was: update unknown or foreign template returns 404.)

### ~~TPL-007~~ — REMOVED (2026-10-27)

**Removed** (phase 2): the API was deleted. (Was: delete template.)

### ~~TPL-008~~ — REMOVED (2026-10-27)

**Removed** (phase 2): the shared DTO package and its contract fixtures were deleted. (Was: response matches the shared contract.)

## Interfaces

| Kind | Location |
| --- | --- |
| Backup | `legacy` object of `apps/android/app/src/main/java/dev/wfhdiary/app/backup/BackupDocument.kt` (templates kept verbatim under `legacy.workTemplates` when present; see [backup](../backup/spec.md)) |

## Test coverage

| Requirement | Tests |
| --- | --- |
| TPL-001 | `removed` |
| TPL-002 | `removed` |
| TPL-003 | `removed` |
| TPL-004 | `removed` |
| TPL-005 | `removed` |
| TPL-006 | `removed` |
| TPL-007 | `removed` |
| TPL-008 | `removed` |

## Known gaps

- Template quick actions and pickers listed in [TODO § C](../../TODO.md#c-work-scenario-templates--dropped) were never built and are not planned.

## Revision log

- 2026-10-27: Initial spec backfilled from implementation ([change](../../changes/2026-10-27-spec-driven-setup/design.md)).
- 2026-10-27: Final-review consistency fixes ([change](../../changes/2026-10-27-spec-driven-setup/design.md)).
- 2026-10-27: On-device-only phase 2 — capability retired; TPL-001..008 removed with the API and shared DTOs ([change](../../changes/2026-10-27-on-device-only/design.md)).
