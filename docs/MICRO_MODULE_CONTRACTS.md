# Player Pro Micro-Module Contracts

Goal: make the app easier for Codex/agentic chat to improve safely by keeping every feature small and replaceable.

## Rule
One feature package should own one job:

- `features/library` = video folders, MediaStore/Room library access
- `features/player` = shared ExoPlayer access
- `features/resume` = save/restore playback progress
- `features/audioonly` = Play as Music only
- `features/floating` = optional floating overlay
- `features/settings` = DataStore settings access

## Feature boundary pattern
Each feature exposes a tiny `*Feature.kt` class. Activities may call the feature, but should not copy the feature's internal code.

```text
Activity -> Feature -> Repository/Service/DAO
```

This keeps each milestone small enough to debug independently.

## Readiness increase
This version improves readiness by adding:

1. Manual app container instead of heavy DI.
2. Feature contracts.
3. Fake data for test-driven implementation.
4. Integration checklist.
5. Milestone-specific Maestro flows.
6. Strict video-only boundary.

## What Codex should do next
Build one module at a time:

1. Run Gradle sync.
2. Fix compile errors in only one feature package.
3. Run the feature's Maestro test.
4. Commit that milestone.
5. Move to the next feature.
