# Milestone 04 — Headunit Stability + Polish

Goal: Make Player Pro safer on weak Android headunits.

Implement:
- Headunit Safe Mode setting.
- Safe Mode disables thumbnails, blur, heavy animations, and auto floating.
- USB refresh button.
- Graceful USB removed handling.
- Playback error UI.
- Unsupported codec message.
- Retry/skip buttons.
- Developer diagnostics screen:
  - decoder type if available
  - video resolution if available
  - storage type
  - library count
  - cache count
- Progress save throttling.
- Release player and overlay cleanly.

Feature toggles:
- Floating video off by default.
- Floating controls off by default.
- Thumbnails off by default.
- Resume playback on by default.
- Playlist drawer on by default.

Maestro tests:
- `maestro/milestones/04_stability_headunit_polish.yml`
- Toggle Safe Mode.
- Refresh library.
- Open diagnostics.
- Verify heavy features disabled in Safe Mode.
