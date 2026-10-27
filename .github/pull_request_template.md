## Summary

## Spec-driven checklist

- [ ] Change folder: `docs/changes/...` (or N/A — reason: )
- [ ] Capability specs updated (requirements, Test coverage, Revision log)
- [ ] Contract fixtures + both contract test suites updated (or N/A — no DTO change)
- [ ] Tests pass: `dotnet test WfhDiary.sln -c Release` (repo root) and `./gradlew :app:testDebugUnitTest` (`apps/android/`)
