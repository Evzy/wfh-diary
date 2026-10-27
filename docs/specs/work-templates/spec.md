# Work templates (presets)

**Prefix:** `TPL` · **Last updated:** 2026-10-27

## Purpose & scope

Reusable work-day presets via the per-user **work templates** API (`/v1/work-templates`: label, times, break, location, description, default flag and optional geofence). Out of scope: the built-in Home and Office presets, which are default-schedule fields on user settings (see [settings](../settings/spec.md)), geofence capture and matching (see `geolocation` spec), and applying presets from notifications (see `notifications` spec). The Android app currently has no UI that reads or writes `/v1/work-templates` (see Known gaps).

## Requirements

### TPL-001 — List templates

`GET /v1/work-templates` returns the caller's templates ordered by `sortOrder`, then `label`.

- **Given** a user with templates (and other users with their own)
- **When** they call `GET /v1/work-templates`
- **Then** the response is `200` with only their templates, sorted by `sortOrder` then `label`

### TPL-002 — Label is required on create

Creating a template with a missing or blank label is rejected with `400`.

- **Given** an authenticated user
- **When** they `POST /v1/work-templates` with `label` empty or whitespace
- **Then** the response is `400` with `{ error: "Label is required." }` and nothing is stored

### TPL-003 — Create template

Creating a template stores it for the caller and returns it with `200`, normalising its fields.

- **Given** an authenticated user
- **When** they `POST /v1/work-templates` with a label and optional fields
- **Then** the response is `200` with a `WorkTemplateDto` whose label is trimmed, `sortOrder` is the supplied value or one more than the caller's current maximum (`0` for the first), times are parsed from `HH:mm`, blank location/description become `null`, and geofence fields are stored as sent

### TPL-004 — At most one default template

Marking a template as default (on create or update) clears the default flag on the caller's other templates.

- **Given** a user with a default template
- **When** they create a template with `isDefault: true`, or update another template with `isDefault: true`
- **Then** only the newly marked template has `isDefault: true`; `isDefault: false` on update just unsets it

### TPL-005 — Partial update

`PUT /v1/work-templates/{id}` changes only non-null fields of the caller's template and returns it with `200`.

- **Given** a template owned by the caller
- **When** they send an update with some fields set and the rest `null`
- **Then** the response is `200`; set fields change (label trimmed, blank location/description become `null`), `null` fields are unchanged

### TPL-006 — Update unknown or foreign template returns 404

Updating a template id that does not exist or belongs to another user returns `404`.

- **Given** a template id that is unknown or owned by a different user
- **When** the caller sends `PUT /v1/work-templates/{id}`
- **Then** the response is `404` and nothing changes

### TPL-007 — Delete template

`DELETE /v1/work-templates/{id}` removes the caller's template with `204`, or returns `404` if it is unknown or not theirs.

- **Given** a template id
- **When** the caller deletes it
- **Then** the response is `204` and it is no longer listed if it was theirs; otherwise `404`

### TPL-008 — Response matches the shared contract

The `work-template.json` contract fixture deserializes into both the API and Android template DTOs.

- **Given** the shared fixture `packages/shared/contracts/work-template.json`
- **When** it is deserialized as `WorkTemplateDto` on the API and on Android
- **Then** both succeed

## Interfaces

| Kind | Location |
| --- | --- |
| API | `GET/POST /v1/work-templates`, `PUT/DELETE /v1/work-templates/{id}` — `services/api/WfhDiary.Api/Endpoints/WorkTemplateEndpoints.cs`, `services/api/WfhDiary.Api/Services/DiaryService.cs` |
| Data | `services/api/WfhDiary.Api/Data/WorkTemplateEntity.cs` (table `work_templates`) |
| DTO / contract | `packages/shared/WfhDiary.Shared/Dtos/WorkTemplateDto.cs`, `packages/shared/WfhDiary.Shared/Dtos/CreateWorkTemplateRequest.cs`, `packages/shared/WfhDiary.Shared/Dtos/UpdateWorkTemplateRequest.cs`, `packages/shared/contracts/work-template.json` |
| Android | `apps/android/app/src/main/java/dev/wfhdiary/app/data/remote/WorkTemplateDto.kt`, `apps/android/app/src/main/java/dev/wfhdiary/app/data/remote/CreateWorkTemplateRequest.kt`, `apps/android/app/src/main/java/dev/wfhdiary/app/data/remote/DiaryRepository.kt` |

## Test coverage

| Requirement | Tests |
| --- | --- |
| TPL-001 | `WorkTemplateFlowTests.Create_list_and_delete_work_template` (ordering and per-user scoping untested) |
| TPL-002 | untested |
| TPL-003 | `WorkTemplateFlowTests.Create_list_and_delete_work_template` (default sort order and blank-to-null untested) |
| TPL-004 | untested |
| TPL-005 | untested |
| TPL-006 | untested |
| TPL-007 | `WorkTemplateFlowTests.Create_list_and_delete_work_template` (`404` path untested) |
| TPL-008 | `ApiContractTests.Fixture_deserializes_to_shared_dto`, `ContractDeserializationTest.workTemplate_fixture_deserializes` |
## Known gaps

- **Android does not use the work-templates API (docs drift).** `DiaryRepository` has `getWorkTemplates`, `createWorkTemplate` and `deleteWorkTemplate` but nothing calls them, there is no update call, and the template-management strings (e.g. `work_templates_manage`, `work_templates_add`) are unused. [TODO § C](../../TODO.md#c-work-scenario-templates--partial) and milestone 7 describe Settings → **Manage templates** and missed-notification geofence → apply template as shipped; neither was found in the current code.
- Backlog from [TODO § C](../../TODO.md#c-work-scenario-templates--partial):
  1. Day edit sheet — "Apply template…" dropdown/chips.
  2. Morning notification — optional template quick actions.
  3. Quick add activity — same template picker.
  4. Reorder templates and mark **default** in the UI (the API already supports `sortOrder` and `isDefault`).
- **Update can blank the label (suspected bug).** `PUT` with `label: " "` stores an empty label; only create validates it.
- **Update cannot clear some fields.** `null` means "unchanged", so geofence fields and break hours cannot be reset to `null` via `PUT`. Times can be cleared, but only as a side effect: an empty or unparseable `startTime`/`finishTime` sets the time to `null` (unlike settings, which ignores invalid times).
- No validation of times on create (unparseable values store `null`), geofence coordinate ranges, radius, or partially supplied geofences.
- Duplicate `sortOrder` values are possible when callers supply them explicitly.

## Revision log

- 2026-10-27: Initial spec backfilled from implementation ([change](../../changes/2026-10-27-spec-driven-setup/design.md)).
