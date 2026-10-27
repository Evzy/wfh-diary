# WFH Diary — constitution

Non-negotiable principles for all changes. If a change needs to break one, amend this file in the same change folder first.

1. **Specs are the source of truth for behaviour.** [`specs/`](specs/README.md) describes what the system does. Code that disagrees with a spec is a bug in one of them — reconcile before merging.
2. **Behaviour changes go through a change folder.** See [`changes/README.md`](changes/README.md). Refactors, dependency bumps, and fixes that do not change specified behaviour are exempt.
3. **One public type per `.kt` file.**
4. **Australian financial year** — 1 July to 30 June. Hours follow the ATO fixed-rate method.
5. **The backup format is the contract.** Changes to `BackupDocument` bump its `version` only with a reader for older versions, and are covered by `BackupCodecTest`.
6. **Testing.** Pure functions get table-driven unit tests with named cases; fakes over mocks. See [`testing/TEST_STRATEGY.md`](testing/TEST_STRATEGY.md).
7. **CI stays green.** No `--no-verify`; `version.json` is bumped by the git hook ([`CI_CD.md`](CI_CD.md)).
8. **Platra is the reference implementation** for CI and OTA patterns ([`TODO.md`](TODO.md#platra-comparison)).
