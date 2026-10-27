# Spec-Driven Development Setup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a lightweight, in-repo spec-driven workflow (constitution, capability specs, change folders, agent rules) and backfill living specs for all existing WFH Diary capabilities.

**Architecture:** Markdown-only. `docs/specs/<capability>/spec.md` is the living source of truth per capability; `docs/changes/<date>-<slug>/` holds design/plan/tasks for behaviour changes. `AGENTS.md`, an always-on Cursor rule, and a PR template steer agents and humans through the workflow. See [`design.md`](design.md).

**Tech Stack:** Markdown, Cursor `.mdc` rules, GitHub PR templates. Repo: ASP.NET Core API (`services/api/`), shared DTOs (`packages/shared/`), Android Compose app (`apps/android/`).

## Global Constraints

- No changes to code, tests, workflows, or build files. Only touch: `AGENTS.md`, `README.md`, `.cursor/rules/`, `.github/pull_request_template.md`, `docs/`.
- Specs describe **current behaviour as implemented** — derived from reading code and tests. Behaviour that looks unintended goes in **Known gaps**, not Requirements.
- Requirement IDs: `<PREFIX>-NNN`, zero-padded to 3 digits, never reused. Prefixes: `DAY`, `FY`, `LEAVE`, `SET`, `TPL`, `NOTIF`, `GEO`, `XLSX`, `AUTH`, `OTA`.
- Every requirement has Given/When/Then acceptance criteria and appears in the spec's Test coverage table (test name or `untested`).
- Relative links only; all must resolve.
- Commit messages: sentence case, ending with a period (matches `git log`).

## Verification command (used by every task)

Run from repo root in PowerShell. Prints broken relative links in the given files; empty output = pass.

```powershell
function Test-DocLinks([string[]]$Paths) {
  Get-ChildItem -Path $Paths -Recurse -File -Include *.md,*.mdc | ForEach-Object {
    $f = $_
    [regex]::Matches((Get-Content $f.FullName -Raw), '\]\(([^)#\s]+)(#[^)]*)?\)') | ForEach-Object {
      $l = $_.Groups[1].Value
      if ($l -notmatch '^(https?:|mailto:)' -and -not (Test-Path (Join-Path $f.DirectoryName $l))) { "$($f.FullName): $l" }
    }
  }
}
```

---

### Task 1: Framework docs (constitution, spec + change templates, READMEs)

**Files:**
- Create: `docs/CONSTITUTION.md`
- Create: `docs/specs/README.md`
- Create: `docs/specs/_template.md`
- Create: `docs/changes/README.md`
- Create: `docs/changes/_template/design.md`
- Create: `docs/changes/_template/plan.md`
- Create: `docs/changes/_template/tasks.md`

**Interfaces:**
- Produces: spec format (sections 1–6) and capability index used by Tasks 3–7; change lifecycle statuses `proposed | approved | in-progress | shipped` used by Tasks 2 and 8.

- [ ] **Step 1: Create `docs/CONSTITUTION.md`**

```markdown
# WFH Diary — constitution

Non-negotiable principles for all changes. If a change needs to break one, amend this file in the same change folder first.

1. **Specs are the source of truth for behaviour.** [`specs/`](specs/README.md) describes what the system does. Code that disagrees with a spec is a bug in one of them — reconcile before merging.
2. **Behaviour changes go through a change folder.** See [`changes/README.md`](changes/README.md). Refactors, dependency bumps, and fixes that do not change specified behaviour are exempt.
3. **One public type per `.cs` / `.kt` file.**
4. **Australian financial year** — 1 July to 30 June. Hours follow the ATO fixed-rate method.
5. **Shared contracts.** Any DTO change updates [`packages/shared/contracts/`](../packages/shared/contracts/README.md) fixtures and both the API and Android contract tests.
6. **Testing.** Pure functions get table-driven unit tests with named cases; fakes over mocks. See [`testing/TEST_STRATEGY.md`](testing/TEST_STRATEGY.md).
7. **CI stays green.** No `--no-verify`; `version.json` is bumped by the git hook ([`CI_CD.md`](CI_CD.md)).
8. **Platra is the reference implementation** for auth, CI, and OTA patterns ([`TODO.md`](TODO.md#platra-comparison)).
```

- [ ] **Step 2: Create `docs/specs/_template.md`**

```markdown
# <Capability name>

**Prefix:** `XXX` · **Last updated:** YYYY-MM-DD

## Purpose & scope

One paragraph: what this capability does for the user, and what is explicitly out of scope.

## Requirements

### XXX-001 — <short name>

<One-sentence requirement statement.>

- **Given** <context>
- **When** <action>
- **Then** <observable outcome>

## Interfaces

| Kind | Location |
| --- | --- |
| API | `METHOD /v1/...` — `services/api/WfhDiary.Api/Endpoints/...` |
| DTO / contract | `packages/shared/WfhDiary.Shared/Dtos/...`, `packages/shared/contracts/...json` |
| Android | `apps/android/app/src/main/java/dev/wfhdiary/app/...` |

## Test coverage

| Requirement | Tests |
| --- | --- |
| XXX-001 | `TestClass.Test_name` or `untested` |

## Known gaps

- <Backlog item or suspected-unintended behaviour, with link to `TODO.md` section if relevant.>

## Revision log

- YYYY-MM-DD: <What changed> ([change](../../changes/YYYY-MM-DD-slug/design.md)).
```

- [ ] **Step 3: Create `docs/specs/README.md`**

```markdown
# Capability specs

Living specs describing what WFH Diary does **today**. Each requirement has a stable ID, Given/When/Then acceptance criteria, and a link to the tests that cover it. Process: [`../changes/README.md`](../changes/README.md). Principles: [`../CONSTITUTION.md`](../CONSTITUTION.md).

| Capability | Prefix | Spec |
| --- | --- | --- |
| Day entries & diaries | `DAY` | [day-entries](day-entries/spec.md) |
| FY summary & working days | `FY` | [summary-fy](summary-fy/spec.md) |
| Leave periods | `LEAVE` | [leave](leave/spec.md) |
| User settings | `SET` | [settings](settings/spec.md) |
| Work templates (presets) | `TPL` | [work-templates](work-templates/spec.md) |
| Notifications | `NOTIF` | [notifications](notifications/spec.md) |
| Geolocation | `GEO` | [geolocation](geolocation/spec.md) |
| XLSX export | `XLSX` | [export-xlsx](export-xlsx/spec.md) |
| Authentication | `AUTH` | [auth](auth/spec.md) |
| OTA updates & versioning | `OTA` | [ota-updates](ota-updates/spec.md) |

## Format

Copy [`_template.md`](_template.md). Rules:

- IDs are `<PREFIX>-NNN` and never reused. Retired requirements stay in place, headed `~~XXX-004~~ — REMOVED (YYYY-MM-DD)` with a one-line reason.
- Specs describe implemented behaviour. Aspirations and suspected bugs go in **Known gaps**.
- Every requirement appears in **Test coverage**, even if `untested`.
- Every edit adds a **Revision log** line.
```

- [ ] **Step 4: Create `docs/changes/README.md`**

```markdown
# Changes

Every behaviour change gets a folder `docs/changes/YYYY-MM-DD-<slug>/`. Copy [`_template/`](_template/design.md).

| Step | Artifact | Status |
| --- | --- | --- |
| 1. Propose | `design.md` — problem, specs touched, requirement delta, approach, test impact | `proposed` |
| 2. Approve | Human review of `design.md` | `approved` |
| 3. Plan | `plan.md` (implementation steps), `tasks.md` (checklist) | `in-progress` |
| 4. Ship | Apply the delta to [`../specs/`](../specs/README.md); update coverage tables and revision logs | `shipped` |

- Folders are kept after shipping as history.
- **Skip the folder** for refactors, dependency bumps, and fixes that do not change specified behaviour.
- A bug fix that exposes a spec gap updates the capability spec directly (revision log entry only).
- **Superpowers:** brainstorming writes `design.md` here (not `docs/superpowers/specs/`); writing-plans writes `plan.md` here (not `docs/superpowers/plans/`).

## Requirement delta format

In `design.md`, list changes per spec:

- **ADDED** `DAY-012` — <statement + Given/When/Then>
- **MODIFIED** `DAY-004` — <new statement; note what changed>
- **REMOVED** `DAY-007` — <reason>
```

- [ ] **Step 5: Create `docs/changes/_template/design.md`**

```markdown
# <Change title> — design

**Status:** proposed · **Date:** YYYY-MM-DD · **Touches specs:** [<capability>](../../specs/<capability>/spec.md)

## Problem

## Goals / non-goals

## Requirement delta

- **ADDED** `XXX-NNN` — 
- **MODIFIED** `XXX-NNN` — 
- **REMOVED** `XXX-NNN` — 

## Approach

## Test impact

## Risks
```

Note: the `../../specs/<capability>/spec.md` link is a placeholder inside the template and is excluded from link verification (see Step 8).

- [ ] **Step 6: Create `docs/changes/_template/plan.md`**

```markdown
# <Change title> — implementation plan

Design: [`design.md`](design.md)

## Global constraints

## Tasks

### Task 1: <name>

**Files:**

- [ ] Step 1: 
```

- [ ] **Step 7: Create `docs/changes/_template/tasks.md`**

```markdown
# <Change title> — tasks

- [ ] Design approved
- [ ] Implementation
- [ ] Tests added/updated and passing
- [ ] Capability specs updated (requirements, coverage, revision log)
- [ ] Contract fixtures updated (if DTOs changed)
- [ ] Status set to `shipped` in `design.md`
```

- [ ] **Step 8: Verify links**

Run: `Test-DocLinks docs/CONSTITUTION.md, docs/specs/README.md, docs/changes/README.md, docs/changes/_template/plan.md, docs/changes/_template/tasks.md`
Expected: only lines pointing at `docs/specs/<capability>/spec.md` files (created in Tasks 3–7). No other output.

- [ ] **Step 9: Commit**

```bash
git add docs/CONSTITUTION.md docs/specs docs/changes/README.md docs/changes/_template
git commit -m "Add spec-driven constitution, templates, and process docs."
```

---

### Task 2: Agent entry points (AGENTS.md, Cursor rule, PR template, README link)

**Files:**
- Create: `AGENTS.md`
- Create: `.cursor/rules/spec-driven.mdc`
- Create: `.github/pull_request_template.md`
- Modify: `README.md` (line 7, the "Planning and continuity" sentence)

**Interfaces:**
- Consumes: `docs/CONSTITUTION.md`, `docs/specs/README.md`, `docs/changes/README.md` from Task 1.

- [ ] **Step 1: Create `AGENTS.md`**

```markdown
# AGENTS.md

Guide for agents and humans working in this repo. This project is **spec-driven**: read the relevant spec before changing behaviour.

## Start here

1. [`docs/CONSTITUTION.md`](docs/CONSTITUTION.md) — non-negotiable principles.
2. [`docs/specs/README.md`](docs/specs/README.md) — capability index; what the system does today.
3. [`docs/changes/README.md`](docs/changes/README.md) — how to propose and ship a behaviour change.
4. [`docs/TODO.md`](docs/TODO.md) — roadmap and backlog. [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — system overview.

## Repo map

| Path | Role |
| --- | --- |
| `services/api/WfhDiary.Api/` | ASP.NET Core minimal API (`Endpoints/`, `Services/`, `Data/`, `Auth/`) |
| `services/api/WfhDiary.Api.Tests/` | xUnit integration + unit tests |
| `packages/shared/WfhDiary.Shared/Dtos/` | Shared DTOs |
| `packages/shared/contracts/` | JSON fixtures used by API and Android contract tests |
| `apps/android/` | Android app (`dev.wfhdiary.app`) — Compose, Ktor, Room, WorkManager |
| `.github/workflows/` | CI, API deploy, Android release |

## Commands

- API tests: `dotnet test WfhDiary.sln -c Release`
- Run API: `cd services/api/WfhDiary.Api && dotnet run --launch-profile http` (dev auth header `X-Debug-User-Id: dev-user`)
- Android unit tests: `cd apps/android && ./gradlew :app:testDebugUnitTest`
- Android build: `cd apps/android && ./gradlew :app:assembleDebug`

## Workflow

1. Identify the capability spec(s) affected.
2. Behaviour change? Create or find an **approved** folder in `docs/changes/`. Not a behaviour change? Proceed.
3. Implement with tests mapped to requirement IDs.
4. Update the capability spec: requirements, Test coverage table, Revision log. Set the change status to `shipped`.
```

- [ ] **Step 2: Create `.cursor/rules/spec-driven.mdc`**

```markdown
---
description: Spec-driven workflow for WFH Diary
alwaysApply: true
---

# Spec-driven workflow

This repo is spec-driven. Read `AGENTS.md` and `docs/CONSTITUTION.md` when starting work.

Before changing behaviour in `services/`, `apps/`, or `packages/`:

1. Find the affected capability spec(s) via `docs/specs/README.md` and read them.
2. If the change adds, modifies, or removes specified behaviour, it needs a change folder in `docs/changes/YYYY-MM-DD-<slug>/` with `design.md` status `approved`. If none exists, propose one (status `proposed`) and get user approval before implementing.
3. Refactors, dependency bumps, and fixes that do not change specified behaviour skip the change folder.

After implementing:

- Apply the requirement delta to the capability spec, update its Test coverage table and Revision log, and set the change `design.md` status to `shipped`.
- If code and spec disagree, flag it to the user — do not silently pick one.
- DTO changes must update `packages/shared/contracts/` fixtures and both contract test suites.

Superpowers path override: brainstorming designs go to `docs/changes/<folder>/design.md` and writing-plans output to `docs/changes/<folder>/plan.md`, not `docs/superpowers/`.
```

- [ ] **Step 3: Create `.github/pull_request_template.md`**

```markdown
## Summary

## Spec-driven checklist

- [ ] Change folder: `docs/changes/...` (or N/A — reason: )
- [ ] Capability specs updated (requirements, Test coverage, Revision log)
- [ ] Contract fixtures + both contract test suites updated (or N/A — no DTO change)
- [ ] `dotnet test WfhDiary.sln -c Release` / `./gradlew :app:testDebugUnitTest` pass
```

- [ ] **Step 4: Modify `README.md` line 7**

Replace:

```markdown
Planning and continuity live under `docs/` — start with [`docs/TODO.md`](docs/TODO.md).
```

with:

```markdown
This repo is **spec-driven** — start with [`AGENTS.md`](AGENTS.md), then the capability specs in [`docs/specs/`](docs/specs/README.md). Roadmap and backlog: [`docs/TODO.md`](docs/TODO.md).
```

- [ ] **Step 5: Verify links**

Run: `Test-DocLinks AGENTS.md, README.md, .github/pull_request_template.md`
Expected: no output.

- [ ] **Step 6: Commit**

```bash
git add AGENTS.md .cursor/rules/spec-driven.mdc .github/pull_request_template.md README.md
git commit -m "Add AGENTS.md, spec-driven Cursor rule, and PR template."
```

---

## Backfill tasks (3–7) — shared procedure

Each backfill task writes one or more capability specs from `docs/specs/_template.md`. For each spec:

1. Read **every** source and test file listed for that capability.
2. Write requirements for observable behaviour: endpoint semantics (status codes, validation, ownership scoping by user id), computations, UI flows, background work. Aim for 4–12 requirements per capability; one behaviour per requirement.
3. For each requirement, search the listed tests for a covering test and record `ClassName.Method_name` (API) or `ClassName.method name` (Android); otherwise `untested`.
4. Copy relevant backlog items from `docs/TODO.md` (sections A–D, milestone 7) into Known gaps. Add anything that looks unintended.
5. Revision log: `- 2026-10-27: Initial spec backfilled from implementation ([change](../../changes/2026-10-27-spec-driven-setup/design.md)).`
6. Run `Test-DocLinks docs/specs/<capability>`; expected no output.

Paths below are relative to: API `services/api/WfhDiary.Api/`, API tests `services/api/WfhDiary.Api.Tests/`, DTOs `packages/shared/WfhDiary.Shared/Dtos/`, contracts `packages/shared/contracts/`, Android `apps/android/app/src/main/java/dev/wfhdiary/app/`, Android tests `apps/android/app/src/test/java/dev/wfhdiary/app/`.

### Task 3: Specs — day-entries (`DAY`) and summary-fy (`FY`)

**Files:**
- Create: `docs/specs/day-entries/spec.md`
- Create: `docs/specs/summary-fy/spec.md`

**Sources — day-entries:** API `Endpoints/DiaryEndpoints.cs` (diary + day routes), `Services/DiaryService.cs`, `Services/TimeHelpers.cs`, `Data/DiaryEntity.cs`, `Data/DayEntryEntity.cs`; DTOs `DiaryDto.cs`, `DayEntryDto.cs`, `UpsertDayEntryRequest.cs`; contracts `diary.json`, `day-entry.json`; Android `ui/day/DayEditSheet.kt`, `ui/month/MonthGridScreen.kt`, `ui/month/MonthGridViewModel.kt`, `ui/month/MonthGridUiState.kt`, `ui/quick/QuickAddActivity.kt`, `ui/BreakHoursField.kt`, `data/local/*`, `data/remote/DiaryRepository.kt`, `util/DayEntryHelper.kt`, `util/DayBadge.kt`, `util/TimeHelpers.kt`. Tests: `DiaryFlowTests.cs`, `TimeHelpersTests.cs`, `ApiContractTests.cs`; Android `util/DayEntryHelperTest.kt`, `util/TimeHelpersTest.kt`, `data/remote/ContractDeserializationTest.kt`.

**Sources — summary-fy:** API `Services/SummaryCalculator.cs`, `Services/FinancialYearHelper.cs`, `Services/WorkingDaysMask.cs`, summary route in `Endpoints/DiaryEndpoints.cs`; DTO `DiarySummaryDto.cs`; contract `diary-summary.json`; Android `ui/month/FySummaryGrid.kt`, `util/FinancialYearHelper.kt`, `util/WorkingDaysMask.kt`. Tests: `SummaryCalculatorTests.cs`, `FinancialYearHelperTests.cs`; Android `util/FinancialYearHelperTest.kt`, `util/WorkingDaysMaskTest.kt`.

- [ ] **Step 1:** Write `docs/specs/day-entries/spec.md` per the shared procedure.
- [ ] **Step 2:** Write `docs/specs/summary-fy/spec.md` per the shared procedure.
- [ ] **Step 3:** Run `Test-DocLinks docs/specs/day-entries, docs/specs/summary-fy`. Expected: no output.
- [ ] **Step 4:** Commit: `git add docs/specs/day-entries docs/specs/summary-fy` then `git commit -m "Backfill day-entries and FY summary specs."`

### Task 4: Specs — leave (`LEAVE`), settings (`SET`), work-templates (`TPL`)

**Files:**
- Create: `docs/specs/leave/spec.md`
- Create: `docs/specs/settings/spec.md`
- Create: `docs/specs/work-templates/spec.md`

**Sources — leave:** API leave routes in `Endpoints/DiaryEndpoints.cs`, `Services/DiaryService.cs`, `Data/LeavePeriodEntity.cs`; DTOs `CreateLeaveRequest.cs`, `LeavePeriodDto.cs`; contract `leave-period.json`; Android `ui/leave/LeaveScheduleSheet.kt`, `util/LeavePeriodHelper.kt`. Tests: `LeaveFlowTests.cs`; Android `util/LeavePeriodHelperTest.kt`.

**Sources — settings:** API `Endpoints/SettingsEndpoints.cs`, `Data/UserSettingsEntity.cs`; DTOs `UserSettingsDto.cs`, `UpdateUserSettingsRequest.cs`; contract `user-settings.json`; Android `ui/settings/SettingsScreen.kt`, `SettingsViewModel.kt`, `SettingsFormState.kt`, `WorkScheduleCustomizeSheet.kt`, `WorkScheduleSummaryRow.kt`, `WorkingDaysSelector.kt`, `MissedNotificationDefaultSelector.kt`, `SettingsInfoIcon.kt`, `SettingsSectionTitleRow.kt`. Tests: `SettingsFlowTests.cs`. (Geolocation settings UI belongs to Task 5.)

**Sources — work-templates:** API `Endpoints/WorkTemplateEndpoints.cs`, `Data/WorkTemplateEntity.cs`, `Migrations/20260625151406_AddDefaultPresetDescriptions.cs`; DTOs `WorkTemplateDto.cs`, `CreateWorkTemplateRequest.cs`, `UpdateWorkTemplateRequest.cs`; contract `work-template.json`; Android `ui/settings/WorkPresetsSection.kt`, `data/remote/CreateWorkTemplateRequest.kt`, `data/remote/WorkTemplateDto.kt`. Tests: `WorkTemplateFlowTests.cs`. Known gaps must include `TODO.md` § C backlog (day-edit picker, notification quick actions, quick-add picker, reorder/default UI).

- [ ] **Step 1:** Write `docs/specs/leave/spec.md`.
- [ ] **Step 2:** Write `docs/specs/settings/spec.md`.
- [ ] **Step 3:** Write `docs/specs/work-templates/spec.md`.
- [ ] **Step 4:** Run `Test-DocLinks docs/specs/leave, docs/specs/settings, docs/specs/work-templates`. Expected: no output.
- [ ] **Step 5:** Commit: `git add docs/specs/leave docs/specs/settings docs/specs/work-templates` then `git commit -m "Backfill leave, settings, and work-template specs."`

### Task 5: Specs — notifications (`NOTIF`) and geolocation (`GEO`)

**Files:**
- Create: `docs/specs/notifications/spec.md`
- Create: `docs/specs/geolocation/spec.md`

**Sources — notifications:** Android `notification/*` (all files: channels, scheduler, morning reminder worker/notifier, missed notification scheduler/worker/default, action receiver/worker, day actions, day-log enqueuer/retry policy/notification updater, API error classifier, permissions, Firebase token helper). Tests: Android `notification/ApiErrorClassifierTest.kt`, `notification/DayLogRetryPolicyTest.kt`, `notification/MissedNotificationDefaultTest.kt`.

**Sources — geolocation:** Android `util/GeofenceHelper.kt`, `util/LocationHelper.kt`, `notification/GeolocationNotificationHelper.kt`, `ui/settings/GeolocationSettingsSheet.kt`, `ui/settings/GeolocationSettingsDraft.kt`, `ui/settings/GeofenceMapPickerSheet.kt`, `ui/settings/WorkLocationsSection.kt`; geofence fields in `WorkTemplateDto.cs` / `UserSettingsDto.cs`. Tests: Android `util/GeofenceHelperTest.kt`. Known gaps must include `TODO.md` § A (proactive arrival logging, background geofence monitoring).

- [ ] **Step 1:** Write `docs/specs/notifications/spec.md`.
- [ ] **Step 2:** Write `docs/specs/geolocation/spec.md`.
- [ ] **Step 3:** Run `Test-DocLinks docs/specs/notifications, docs/specs/geolocation`. Expected: no output.
- [ ] **Step 4:** Commit: `git add docs/specs/notifications docs/specs/geolocation` then `git commit -m "Backfill notification and geolocation specs."`

### Task 6: Specs — export-xlsx (`XLSX`)

**Files:**
- Create: `docs/specs/export-xlsx/spec.md`

**Sources:** API `Services/XlsxExportService.cs`, export route in `Endpoints/DiaryEndpoints.cs`; Android `export/XlsxExportHelper.kt`, `res/xml/file_paths.xml`, export UI in `ui/month/MonthGridScreen.kt` / `ui/settings/SettingsScreen.kt` (search for `export`). Tests: `XlsxExportServiceTests.cs`. Known gaps must include golden-file xlsx tests (`TODO.md` § B).

- [ ] **Step 1:** Write `docs/specs/export-xlsx/spec.md`.
- [ ] **Step 2:** Run `Test-DocLinks docs/specs/export-xlsx`. Expected: no output.
- [ ] **Step 3:** Commit: `git add docs/specs/export-xlsx` then `git commit -m "Backfill XLSX export spec."`

### Task 7: Specs — auth (`AUTH`) and ota-updates (`OTA`)

**Files:**
- Create: `docs/specs/auth/spec.md`
- Create: `docs/specs/ota-updates/spec.md`

**Sources — auth:** API `Auth/FirebaseAuthGate.cs`, `Auth/FirebaseBearerMiddleware.cs`, `Auth/FirebaseOptions.cs`, `Auth/UnauthorizedExceptionMiddleware.cs`, `Program.cs` (auth wiring, `X-Debug-User-Id`); Android `data/auth/FirebaseIdTokenHolder.kt`, `data/auth/GoogleFirebaseSignIn.kt`, `ui/auth/AuthViewModel.kt`, `ui/auth/LoginScreen.kt`. Tests: `AuthGateTests.cs`. Link `../../FIREBASE_SETUP.md` in Interfaces.

**Sources — ota-updates:** API `Endpoints/VersionEndpoints.cs`, `AppVersion.cs`, `Endpoints/HealthEndpoints.cs`; Android `update/*`, `ui/update/AppUpdateViewModel.kt`; `version.json`, `scripts/bump-version.ps1`, `.githooks/pre-commit`, `.github/workflows/release-android.yml`. Tests: `VersionEndpointTests.cs`, `HealthTests.cs`. Link `../../CI_CD.md` in Interfaces.

- [ ] **Step 1:** Write `docs/specs/auth/spec.md`.
- [ ] **Step 2:** Write `docs/specs/ota-updates/spec.md`.
- [ ] **Step 3:** Run `Test-DocLinks docs/specs/auth, docs/specs/ota-updates`. Expected: no output.
- [ ] **Step 4:** Commit: `git add docs/specs/auth docs/specs/ota-updates` then `git commit -m "Backfill auth and OTA update specs."`

---

### Task 8: Cross-link existing docs and ship the change

**Files:**
- Modify: `docs/TODO.md` (header paragraph, line 3)
- Modify: `docs/testing/API_TEST_PLAN.md`
- Modify: `docs/testing/ANDROID_TEST_PLAN.md`
- Modify: `docs/changes/2026-10-27-spec-driven-setup/design.md` (status line)
- Create: `docs/changes/2026-10-27-spec-driven-setup/tasks.md`

**Interfaces:**
- Consumes: requirement IDs from Tasks 3–7 coverage tables.

- [ ] **Step 1: Modify `docs/TODO.md` line 3**

Replace:

```markdown
Use this file to resume work (human or agent). **Architecture:** [`ARCHITECTURE.md`](ARCHITECTURE.md). **CI/CD:** [`CI_CD.md`](CI_CD.md).
```

with:

```markdown
Use this file to resume work (human or agent). **Specs (source of truth for behaviour):** [`specs/`](specs/README.md). **Changes:** [`changes/`](changes/README.md). **Architecture:** [`ARCHITECTURE.md`](ARCHITECTURE.md). **CI/CD:** [`CI_CD.md`](CI_CD.md).

New backlog work should become a change folder before implementation — see [`changes/README.md`](changes/README.md).
```

- [ ] **Step 2: Cross-reference test plans**

In each of `API_TEST_PLAN.md` and `ANDROID_TEST_PLAN.md`: add under the title `Requirement IDs refer to [capability specs](../specs/README.md).` Then, for each test case/section that maps to a requirement in a Task 3–7 coverage table, append the IDs in brackets, e.g. `[DAY-002, DAY-003]`. Do not change test plan content otherwise.

- [ ] **Step 3: Mark change shipped**

In `design.md` replace `**Status:** proposed` with `**Status:** shipped`. Create `tasks.md`:

```markdown
# Spec-driven development setup — tasks

- [x] Design approved
- [x] Framework docs (constitution, templates, READMEs)
- [x] AGENTS.md, Cursor rule, PR template, README link
- [x] Capability specs backfilled (10)
- [x] TODO.md and test plans cross-linked
- [x] Status set to `shipped` in `design.md`
```

- [ ] **Step 4: Full verification**

Run: `Test-DocLinks AGENTS.md, README.md, .github/pull_request_template.md, docs/CONSTITUTION.md, docs/specs, docs/changes, docs/TODO.md, docs/testing`
Expected: only (a) the template placeholder `docs/changes/_template/design.md: ../../specs/<capability>/spec.md`, and (b) links that were already broken before this change. For each (b) line, confirm the link text is not one this plan added (e.g. pre-existing `OPERABILITY_CHECKLIST.md` in `TEST_STRATEGY.md`).

Run: `git diff --stat 4414ca9..HEAD -- . ':!docs' ':!AGENTS.md' ':!README.md' ':!.cursor' ':!.github/pull_request_template.md'`
Expected: no output (no code touched).

- [ ] **Step 5: Commit**

```bash
git add docs/TODO.md docs/testing docs/changes/2026-10-27-spec-driven-setup
git commit -m "Cross-link specs from TODO and test plans; ship spec-driven setup."
```
