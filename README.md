# WFH Diary

Offline Android app (`dev.wfhdiary.app`) for tracking **working from home** hours (Australian FY, ATO fixed-rate method) and exporting the MGIS-style **Working from Home Diary** spreadsheet. All data lives on the phone in Room — there is no server or sign-in — and the app writes daily JSON backups to a folder you choose, which you can restore from Settings. A morning notification logs the day as Home, Office or Leave, and APK updates come from the public [`Evzy/wfh-diary-releases`](https://github.com/Evzy/wfh-diary-releases) repo.

This repo is **spec-driven** — start with [`AGENTS.md`](AGENTS.md), then the capability specs in [`docs/specs/`](docs/specs/README.md). Roadmap and backlog: [`docs/TODO.md`](docs/TODO.md). Build, release and versioning: [`docs/CI_CD.md`](docs/CI_CD.md).

## Why two repos?

| Repo | Visibility | Purpose |
|------|------------|---------|
| [`wfh-diary`](https://github.com/Evzy/wfh-diary) | **Private** | Source, docs, CI workflows |
| [`wfh-diary-releases`](https://github.com/Evzy/wfh-diary-releases) | **Public** | APK GitHub Releases only |

On GitHub’s free tier, **private** repos can keep code off the public internet while a **public** releases repo lets the app (and CI) download APK assets **without a token** — same pattern as Platra’s `platra` + `platra-releases`. No paid GitHub plan required for private code + sideload updates.
