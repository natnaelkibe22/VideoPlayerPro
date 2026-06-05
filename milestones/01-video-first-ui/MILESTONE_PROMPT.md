# Milestone 01 — Gemini-Style Video-First UI

Goal: Replace dashboard/sidebar-heavy UI with fullscreen video-first player.

Implement:
- Fullscreen PlayerActivity where video occupies 100% background.
- Tap-to-show overlay controls.
- Auto-hide controls after 3 seconds.
- Large center play/pause.
- Previous/next controls.
- 5s back and 15s forward seek controls.
- Seek bar with current time and total duration.
- Top title bar with current video title.
- Bottom-right action menu button.
- No permanent large button bar.

Rules:
- Keep XML layouts lightweight.
- No real-time blur.
- No heavy animations.
- Use alpha fade only if needed.

Maestro tests:
- `maestro/milestones/01_video_first_ui.yml`
- Launch player.
- Tap video.
- Verify play/pause, seek controls, title, menu button.
- Wait and verify controls auto-hide.
