# WFH Diary — architecture

**Last updated:** 2026-06-24 · **Backlog:** [`TODO.md`](TODO.md)

## System context

```mermaid
flowchart TB
  subgraph device [Android dev.wfhdiary.app]
    Notif[Morning notification]
    Grid[Month grid UI]
    Room[(Room cache)]
    Updater[GitHub OTA]
  end
  subgraph cloud [WfhDiary.Api]
    API[HTTPS / LAN]
    DB[(SQLite dev / PostgreSQL prod)]
    Export[xlsx export]
  end
  subgraph releases [wfh-diary-releases public]
    APK[APK releases]
  end
  Grid --> API
  Notif --> API
  API --> DB
  API --> Export
  Grid <--> Room
  Updater --> APK
```

**Current:** Cloud-primary API; Android syncs via Ktor; Room caches month days. Firebase auth optional (dev uses `X-Debug-User-Id`). Morning WorkManager notification with Home/Office/Leave/Add time actions.

**Reference:** [`platra`](../platra) uses the same auth, CI, and OTA patterns.

## Revision log

- 2026-06-24: Initial architecture — API shipped, Android scaffold + notifications.
