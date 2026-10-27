# Spec-driven development setup — design

**Status:** proposed · **Date:** 2026-10-27 · **Touches specs:** none (introduces the spec system)

## Problem

Behaviour of WFH Diary is documented only loosely (`docs/TODO.md`, `ARCHITECTURE.md`, test plans). There is no single place that states what each feature does today, no agreed process for proposing behaviour changes, and no agent guidance (`AGENTS.md` / Cursor rules). Agents and humans have to reverse-engineer intent from code.

## Goals

- One **living spec per capability** describing current behaviour with stable requirement IDs and acceptance criteria.
- A lightweight **change workflow** (design → plan → tasks → merge into spec) for behaviour changes.
- **Agent-level enforcement**: `AGENTS.md` + an always-on Cursor rule + PR template. No CI gate.
- Reuse existing docs; no large reorganisation.

## Non-goals

- No code or test changes.
- No external tooling (Spec Kit, OpenSpec).
- No CI check that blocks PRs without spec changes.
- No rewrite of setup/ops guides (`AZURE_*`, `FIREBASE_SETUP.md`, `DEVICE_AND_FIREBASE_SETUP.md`, `CI_CD.md`).

## Layout

```
AGENTS.md                         # entry point: repo map, commands, workflow, links
.cursor/rules/spec-driven.mdc     # alwaysApply rule enforcing spec-first behaviour changes
.github/pull_request_template.md  # spec/change/test checklist
docs/
  CONSTITUTION.md                 # non-negotiable principles (from existing conventions)
  specs/
    README.md                     # capability index + spec format
    _template.md
    day-entries/spec.md
    leave/spec.md
    settings/spec.md
    work-templates/spec.md
    notifications/spec.md
    geolocation/spec.md
    export-xlsx/spec.md
    auth/spec.md
    ota-updates/spec.md
    summary-fy/spec.md
  changes/
    README.md                     # change lifecycle
    _template/design.md
    _template/plan.md
    _template/tasks.md
    2026-10-27-spec-driven-setup/ # this change
```

Existing docs stay in place. Root `README.md` gains a link to `AGENTS.md` and `docs/specs/README.md`. `TODO.md` gains a link to the spec index and change folders; `testing/API_TEST_PLAN.md` and `testing/ANDROID_TEST_PLAN.md` gain requirement-ID cross-references.

## Capability spec format

Each `docs/specs/<capability>/spec.md` contains:

1. **Purpose & scope** — one paragraph; what is in/out.
2. **Requirements** — each with a stable ID (`<PREFIX>-NNN`, e.g. `DAY-001`) and Given/When/Then acceptance criteria. IDs are never reused; removed requirements are marked `REMOVED` rather than deleted.
3. **Interfaces** — API endpoints, DTOs, contract fixtures in `packages/shared/contracts/`, Android screens/workers.
4. **Test coverage** — table mapping requirement ID → test(s), or `untested`.
5. **Known gaps** — backlog items (pulled from `TODO.md`).
6. **Revision log** — dated entries linking to the change folder that caused them.

Requirement prefixes: `DAY` (day-entries), `LEAVE`, `SET` (settings), `TPL` (work-templates), `NOTIF`, `GEO`, `XLSX`, `AUTH`, `OTA`, `FY` (summary-fy).

Specs describe **current behaviour as implemented**, derived from reading code and tests — not aspirational behaviour. Aspirations go in Known gaps.

## Change workflow

| Step | Artifact | Status |
| --- | --- | --- |
| 1. Propose | `docs/changes/YYYY-MM-DD-<slug>/design.md` — problem, specs touched, requirement delta (ADDED / MODIFIED / REMOVED with IDs), approach, test impact | `proposed` |
| 2. Approve | Human review of `design.md` | `approved` |
| 3. Plan | `plan.md` (implementation steps), `tasks.md` (checklist) | `in-progress` |
| 4. Ship | Apply delta to capability specs; update test coverage tables; add revision log entries | `shipped` |

- Change folders are kept after shipping as history.
- **Skip the change folder** for refactors, dependency bumps, and fixes that do not change specified behaviour.
- A bug fix that exposes a spec gap updates the capability spec directly (revision log entry, no change folder required).
- Superpowers integration: brainstorming designs are written to the change folder's `design.md` and writing-plans output to its `plan.md`, overriding the default `docs/superpowers/` paths.

## Enforcement (agent-level)

- **`AGENTS.md`** — short: repo layout, build/test commands (`dotnet test WfhDiary.sln -c Release`, Android `./gradlew :app:testDebugUnitTest` from `apps/android/`), the workflow above, links to `CONSTITUTION.md` and `docs/specs/README.md`.
- **`.cursor/rules/spec-driven.mdc`** (`alwaysApply: true`) — before changing behaviour under `services/`, `apps/`, or `packages/`: identify the capability spec(s); require an approved change folder for non-trivial behaviour changes (or propose one); after implementation update specs and coverage tables. States the Superpowers path override.
- **`.github/pull_request_template.md`** — checkboxes: change folder linked (or N/A with reason), capability specs updated, coverage tables updated, contract fixtures updated if DTOs changed.

## Constitution

`docs/CONSTITUTION.md` codifies existing conventions only:

- One public type per `.cs` / `.kt` file.
- Australian financial year (1 July – 30 June); ATO fixed-rate method.
- DTO changes update `packages/shared/contracts/` fixtures and both API and Android contract tests.
- Pure functions get table-driven unit tests; fakes over mocks.
- CI must be green; no `--no-verify`.
- `version.json` is bumped by the git hook.
- Platra is the reference implementation for auth, CI, and OTA patterns.
- Specs are the source of truth for behaviour; code that disagrees with a spec is a bug in one of them and must be reconciled.

## Backfill approach

For each capability: read the relevant endpoints, services, Android UI/workers, and tests; write requirements describing observed behaviour; map each to covering tests or mark `untested`; copy relevant backlog items into Known gaps.

## Error handling / risks

- **Spec drift** — mitigated by the always-on rule and PR checklist; accepted residual risk since there is no CI gate.
- **Inaccurate backfill** — specs derived from code may encode bugs as requirements. Where behaviour looks unintended, record it under Known gaps with a note rather than as a requirement.

## Verification

- All ten capability specs exist and follow the template.
- `AGENTS.md`, `CONSTITUTION.md`, rule file, PR template, templates, and READMEs exist.
- All relative links in new/edited docs resolve.
- No files outside `docs/`, `AGENTS.md`, `.cursor/`, `.github/pull_request_template.md`, and `README.md` are modified.
