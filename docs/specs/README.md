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
