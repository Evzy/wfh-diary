# On-device only (remove backend) — tasks

Phase 1 (this branch, `on-device-only`) keeps the backend running and ships on-device storage, backups, restore and the one-time importer. Phase 2 removes the backend after the phase-1 gate below passes.

- [x] Design approved
- [ ] Implementation — phase 1 code done (Tasks 1–11); phase 2 pending
- [x] Tests added/updated and passing (phase 1: `:app:testDebugUnitTest`)
- [ ] Capability specs updated (requirements, coverage, revision log) — phase-1 MODIFIED deltas and the new `backup` spec are applied; REMOVED items wait for phase 2
- [ ] Contract fixtures updated (if DTOs changed) — no DTO change in phase 1; fixtures are copied into Android test resources before phase 2 deletes them
- [ ] Status set to `shipped` in `design.md` — stays `in-progress` until phase 2 ships

## Phase 1 plan (Android only)

- [x] Task 1 — Backup document v1 model and codec, hardened validation (`2b89a99`, `ae9aa83`)
- [x] Task 2 — Port diary, leave, summary and settings rules on-device (`9e647cc`)
- [x] Task 3 — Room v2 schema for days, leave and settings (`56a0a78`)
- [x] Task 4 — Generate the diary xlsx on-device (`1a39413`)
- [x] Task 5 — Local `DiaryStore` behind a `DiaryDataSource` interface, legacy preserved on settings update (`6af6e50`, `afd23ef`)
- [x] Task 6 — Use the on-device store as the app's data source (`cbcf197`)
- [x] Task 7 — Automatic JSON backups to a chosen folder (`58da86c`)
- [x] Task 8 — Restore from backup with safety copy (`cdeb8c3`)
- [x] Task 9 — One-time import from the old server (`1cc2c09`)
- [x] Task 10 — Backup and restore Settings section with warning banner, folder-permission and restore-reload guards (`fd15a39`, `c0280a5`)
- [x] Task 11 — Specs, change docs, manual checklist and version bump (`docs: backup spec and on-device phase-1 spec updates; bump version`, the commit that adds this file)

### Deliberate deviations from the design

- `BackupCodec` also refuses `version` < 1, a non-primitive `format`/`version`, negative settings break hours and duplicate leave ids (BAK-008 is stricter than the design text).
- The import flow force-refreshes the Firebase ID token before fetching.
- The backup dialogs reuse the existing `time_picker_cancel` string instead of adding `common_cancel`.
- Folder selection failures show a message instead of crashing.

### Known spec gaps (recorded earlier, not fixed here)

- Work-template UI and template-geofence matching were never implemented; templates are dropped by this change.
- Cross-FY leave is only marked within the FY the request was made for ([LEAVE](../../specs/leave/spec.md) Known gaps).

## Phase-1 gate (manual, on phone)

**Not yet run.** These checks are for the human to perform on the phone and record here. **Phase 2 planning waits on this gate: it may only be planned once steps 2–4 have passed.** Procedure and BAK/DAY/XLSX IDs are also in [`ANDROID_TEST_PLAN.md`](../../testing/ANDROID_TEST_PLAN.md#backup--restore-on-device).

- [ ] 1. Install the new build over the old one (GitHub Releases OTA or `adb install -r`). The Room v1 cache is dropped on first launch, so the grid is empty; that is expected.
- [ ] 2. Settings → Backup & restore → **Choose backup folder**, pick a folder that syncs off the phone (e.g. Google Drive-synced or Documents). A `wfh-diary-backup-YYYY-MM-DD.json` file appears immediately. (BAK-002, BAK-005)
- [ ] 3. **Import from old server** → confirm → confirm the replace summary. The month grid shows historic days for several FYs, Settings shows your name and geofences, and a backup file is updated. (BAK-011, BAK-009)
- [ ] 4. The in-app FY summary matches an XLSX export from the old server for the same FY; then export XLSX in the app and open it in Excel or Google Sheets: layout, colours and totals match. (XLSX-003..009)
- [ ] 5. Edit a day; within about 10–15 minutes the backup file's modified time changes. (BAK-003)
- [ ] 6. Clear a field in the edit sheet (e.g. remove the description) and save; it stays cleared after reopening. (DAY-010)
- [ ] 7. **Share backup…** opens the share sheet with a `.json` file. (BAK-007)
- [ ] 8. **Restore from file…** with the backup → confirm. Data is unchanged and a `wfh-diary-pre-restore-*.json` file exists. (BAK-009)
- [ ] 9. Restore a non-backup file (any other JSON): it is refused with a message. (BAK-008)
- [ ] 10. Morning notification and "Home"/"Office" actions still log days. (NOTIF)
- [ ] 11. Revoke the folder (move or delete it) and tap **Back up now**. A failure message shows and the month-grid banner appears. (BAK-006)

## Phase 2 (after the gate passes)

Plan to be written as `plan-phase-2.md` in this folder. Scope per [`design.md`](design.md) § Rollout and § Removed (phase 2).

- [ ] `plan-phase-2.md` written and approved
- [ ] Remove the importer, `data/remote/` Ktor code, Firebase auth, login gate, `ApiErrorClassifier`, `DayLogRetryPolicy` and retry/pending paths; move the automatic update check to app start
- [ ] Apply REMOVED deltas to specs: `NOTIF-009`, `NOTIF-010`, `OTA-001`, `OTA-002`, `AUTH-001`..`AUTH-011`, `TPL-001`..`TPL-008`, `BAK-011`; update `OTA-009` text
- [ ] Delete `services/`, `packages/shared/`, `WfhDiary.sln`, deploy/keep-warm workflows and .NET CI jobs; update `AGENTS.md`, `CONSTITUTION.md`, `ARCHITECTURE.md`, `TODO.md`, `README.md`
- [ ] User runs `scripts/azure/decommission.ps1` after confirming phase 1
- [ ] Status set to `shipped` in `design.md`
