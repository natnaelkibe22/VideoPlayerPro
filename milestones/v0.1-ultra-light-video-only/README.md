# Ultra-light video-only browser

## Goal
Focus on MainActivity, FolderAdapter, VideoAdapter, PermissionService. Show cached folders/videos and open PlayerActivity. No thumbnails/floating/audio service yet.

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
maestro test milestones/v0.1-ultra-light-video-only/maestro/milestone.yml
```
