# WFH Diary

Android app + ASP.NET Core API for tracking **working from home** hours (Australian FY, ATO fixed-rate method) and exporting the MGIS-style **Working from Home Diary** spreadsheet.

Planning and continuity live under `docs/` — start with [`docs/TODO.md`](docs/TODO.md).

## Layout

| Path | Role |
|------|------|
| `docs/TODO.md` | Continuation backlog and milestone status |
| `docs/testing/` | Test strategy and plans |
| `services/api/WfhDiary.Api/` | HTTP API (settings, diaries, export) |
| `packages/shared/WfhDiary.Shared/` | Shared DTOs (`Dtos/` — one type per file) |
| `apps/android/` | Android app (Kotlin + Compose) — pending |

APK releases are published to the separate repo **[`Evzy/wfh-diary-releases`](https://github.com/Evzy/wfh-diary-releases)** for in-app OTA updates.

**Reference implementation:** the sibling [`platra`](https://github.com/Evzy/platra) repo uses the same monorepo layout, Firebase auth, GitHub Actions CI, and Android OTA updater patterns. When unsure how to wire something, check Platra first (see [`docs/TODO.md`](docs/TODO.md) § Platra comparison).

## API (local)

```bash
cd services/api/WfhDiary.Api
dotnet run --launch-profile http
```

- Health: `GET http://localhost:5222/health`
- Local dev auth: send header `X-Debug-User-Id: dev-user` when Firebase is not configured.

## Tests

```bash
dotnet test WfhDiary.sln -c Release
```

## Conventions

- One public type per `.cs` / `.kt` file.
- Australian financial year: 1 July – 30 June.
