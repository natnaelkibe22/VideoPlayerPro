# Room cache and resume playback

## Goal
Focus on AppDatabase, VideoDao, VideoProgressEntity, ProgressService. Save/restore progress and show recently watched.

## Micro-module focus
Edit only the files related to this milestone. Keep every feature small and isolated.

## Done checklist
- App remains video-only.
- No audio scanner/music library is added.
- UI stays car-friendly and simple.
- Work does not block the main thread.
- Maestro milestone test passes or is updated with stable visible text.

## Maestro
Run:

```bash
maestro test milestones/v0.2-cache-resume/maestro/milestone.yml
```
