# Stability and performance hardening

## Goal
Focus on background refresh, unsupported codec handling, no main-thread work, no thumbnail startup work.

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
maestro test milestones/v0.7-stability-performance/maestro/milestone.yml
```
