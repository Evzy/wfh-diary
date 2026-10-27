# On-device only (remove backend) — tasks

Phase 1 (this branch, `on-device-only`) keeps the backend running and ships on-device storage, backups, restore and the one-time importer. Phase 2 removes the backend after the phase-1 gate below passes.

- [x] Design approved
- [x] Implementation — phase 1 (Tasks 1–11) and phase 2 ([`plan-phase-2.md`](plan-phase-2.md) Tasks 1–5) done; only the manual Azure decommission remains
- [x] Tests added/updated and passing (`:app:testDebugUnitTest`; .NET tests deleted with the API in phase 2)
- [x] Capability specs updated (requirements, coverage, revision log) — `backup` spec added, MODIFIED deltas applied (including `OTA-009` at app start), REMOVED deltas applied in phase 2; `auth` and `work-templates` retired
- [x] Contract fixtures updated (if DTOs changed) — not applicable: `packages/shared/contracts/` and `ContractDeserializationTest` were deleted in phase 2 (the importer was removed first, so the fixtures were not copied into Android test resources); the backup format (`BackupCodecTest`) is now the only serialized contract
- [x] Status set to `shipped` in `design.md`

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
- [x] Final-review fix wave — offline notification actions, restore/import require a backup folder (no cache fallback), confirm dialog shows current counts, post-restore backup result shown, importer fetches through next FY, data-safety tests, smaller hardening

### Deliberate deviations from the design

- `BackupCodec` also refuses `version` < 1, a non-primitive `format`/`version`, negative settings break hours and duplicate leave ids (BAK-008 is stricter than the design text).
- The import flow force-refreshes the Firebase ID token before fetching.
- The backup dialogs reuse the existing `time_picker_cancel` string instead of adding `common_cancel`.
- Folder selection failures show a message instead of crashing.

### Known spec gaps (recorded earlier, not fixed here)

- Work-template UI and template-geofence matching were never implemented; templates are dropped by this change.
- Cross-FY leave is only marked within the FY the request was made for ([LEAVE](../../specs/leave/spec.md) Known gaps).

## Phase-1 gate (manual, on phone)

**Partly run (2026-10-27):** steps 1, 2, 3 and 5 confirmed on the phone with v0.1.24 (import succeeded, grid and FY summary match the old server, app XLSX export looks right, backup file present). Step 4 and steps 6–15 are still to do before `scripts/azure/decommission.ps1` runs. These checks are for the human to perform on the phone and record here. **Phase 2 planning waits on this gate: it may only be planned once steps 2–5 have passed.** Procedure and BAK/DAY/XLSX IDs are also in [`ANDROID_TEST_PLAN.md`](../../testing/ANDROID_TEST_PLAN.md#backup--restore-on-device).

Do steps 2 and 3 **before logging any day on the new build** (the import replaces everything on the phone; notification actions count as logging).

- [ ] 1. Install the new build over the old one (GitHub Releases OTA or `adb install -r`). The Room v1 cache is dropped on first launch, so the grid is empty; that is expected. Restore and import are disabled until a folder is chosen.
- [ ] 2. Settings → Backup & restore → **Choose backup folder**, pick a folder that syncs off the phone (e.g. Google Drive-synced or Documents). The app is still empty on the upgrade path, so the message says "Nothing to back up yet" and no file is written (an existing same-day file is left untouched); the first `wfh-diary-backup-YYYY-MM-DD.json` appears after the import in step 3. (BAK-002, BAK-005)
- [ ] 3. **Import from old server** → confirm → the replace summary shows the current phone counts (0 days, 0 leave periods) → confirm. The month grid shows historic days for several FYs, Settings shows your name and geofences, and the result message says the backup was written. (BAK-011, BAK-009)
- [ ] 4. Drive folder overwrite check: after another backup the same day (e.g. **Back up now**), the day's file in the Drive folder has a new size or modified time, and the file opens as valid JSON. (BAK-005)
- [ ] 5. The in-app FY summary matches an XLSX export from the old server for the same FY; then export XLSX in the app and open it in Excel or Google Sheets: layout, colours and totals match. (XLSX-003..009)
- [ ] 6. Edit a day; within about 10–15 minutes the backup file's modified time changes. (BAK-003)
- [ ] 7. Leave the phone untouched for more than 24 hours (no edits); a new day's `wfh-diary-backup-YYYY-MM-DD.json` appears. (BAK-004)
- [ ] 8. Clear a field in the edit sheet (e.g. remove the description) and save; it stays cleared after reopening. (DAY-010)
- [ ] 9. **Share backup…** opens the share sheet with a `.json` file. (BAK-007)
- [ ] 10. **Restore from file…** with the backup → the summary shows current phone counts → confirm. Data is unchanged, a `wfh-diary-pre-restore-*.json` file exists in the folder, and the message says a backup was written. (BAK-009)
- [ ] 11. Restore a non-backup file (any other JSON): it is refused with a message. (BAK-008)
- [ ] 12. Morning notification and "Home"/"Office" actions still log days. (NOTIF)
- [ ] 13. Airplane mode notification action: turn on airplane mode, tap "Home" or "Office" on the morning notification; the day is logged and a success notification shows. (NOTIF)
- [ ] 14. Revoke the folder (move or delete it) and tap **Back up now**. A failure message shows and the month-grid banner appears. (BAK-006)
- [ ] 15. Restore the post-import backup on a fresh install: uninstall (or clear app data), install, choose the same folder, **Restore from file…** with the backup written after step 3. Days, leave periods and settings match. (BAK-009, BAK-010)

## Phase 2 (after the gate passes)

Plan: [`plan-phase-2.md`](plan-phase-2.md) (branch `remove-backend`). Scope per [`design.md`](design.md) § Rollout and § Removed (phase 2).

- [x] `plan-phase-2.md` written and approved (`d94cedb`, `ec036e8`)
- [x] Remove the importer, `data/remote/` Ktor code, Firebase auth, login gate, `ApiErrorClassifier`, `DayLogRetryPolicy` and retry/pending paths; move the automatic update check to app start (Tasks 1–3: `67e3068`, `c1a9c2e`, `cac85ea`, `7c5433d`)
- [x] Apply REMOVED deltas to specs: `NOTIF-009`, `NOTIF-010`, `OTA-001`, `OTA-002`, `AUTH-001`..`AUTH-011`, `TPL-001`..`TPL-008`, `BAK-011`; update `OTA-009` text (Task 5, the commit that ticks this box)
- [x] Delete `services/`, `packages/shared/`, `WfhDiary.sln`, deploy/keep-warm workflows and .NET CI jobs; update `AGENTS.md`, `CONSTITUTION.md`, `ARCHITECTURE.md`, `TODO.md`, `README.md` (Task 4: `939c3d8`, `f7f59a4`)
- [ ] User runs `scripts/azure/decommission.ps1` after confirming phase 1
- [x] Status set to `shipped` in `design.md` (Task 5); version bumped to 0.1.25
