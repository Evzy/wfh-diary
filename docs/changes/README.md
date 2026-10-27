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
