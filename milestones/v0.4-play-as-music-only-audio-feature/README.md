# Play as Music only

## Goal
Focus on AudioOnlyService and PlayerActivity.playAsMusic. Detach video surface, keep audio alive, no music library.

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
maestro test milestones/v0.4-play-as-music-only-audio-feature/maestro/milestone.yml
```
