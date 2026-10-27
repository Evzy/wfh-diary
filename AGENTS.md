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

- API tests (from repo root): `dotnet test WfhDiary.sln -c Release`
- Run API (from `services/api/WfhDiary.Api/`): `dotnet run --launch-profile http` (dev auth header `X-Debug-User-Id: dev-user`)
- Android unit tests (from `apps/android/`): `./gradlew :app:testDebugUnitTest`
- Android build (from `apps/android/`): `./gradlew :app:assembleDebug`

## Workflow

1. Identify the capability spec(s) affected.
2. Behaviour change? Create or find a folder in `docs/changes/` and get user approval of its `design.md` before implementing. Implementation may proceed when the status is `approved` or `in-progress`; set it to `in-progress` when you start the plan. Not a behaviour change? Proceed (a bug fix that exposes a spec gap updates the capability spec directly, with a Revision log entry and no change folder).
3. Implement with tests mapped to requirement IDs.
4. Update the capability spec: requirements, Test coverage table, Revision log. Set the change status to `shipped`.
