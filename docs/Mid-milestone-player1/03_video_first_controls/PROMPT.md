# Milestone 3 — Video-First Auto-Hide Controls

## Goal
Move Player Pro away from permanent large buttons and toward a video-first interface like VidMate/MX Player/Gemini design: the video is primary, controls appear only when needed, and advanced controls move into a lightweight playback sheet/menu.

## Desired UX
Default player screen:
- 100% video.
- No permanent bottom control bar.
- Tap video -> show overlay controls.
- Overlay controls auto-hide after 3 seconds.
- Overlay includes only essential controls.

Essential controls:
- Play/Pause center button.
- Seek bar.
- Current time / duration.
- Previous / Next.
- 10s/15s rewind/forward optional.
- Playlist drawer toggle.
- Playback menu button.

Advanced controls move to menu/sheet:
- Speed.
- Repeat video.
- Repeat folder.
- Play as Music.
- Floating mode.
- Settings.
- Refresh library.

## Required architecture
Create or refactor into:

```text
controls/
  PlayerControlsController.kt
  ControlsVisibilityState.kt
  PlaybackMenuController.kt
  PlaybackSpeedSheet.kt
  RepeatModeSheet.kt
```

### ControlsVisibilityState
Include:
- controlsVisible
- lastInteractionTime
- autoHideEnabled
- autoHideDelayMs default 3000

## Lightweight rules
- No heavy animations.
- Fade in/out only, or instant show/hide for Headunit Safe Mode.
- No blur.
- Avoid nested complex layouts.
- Large touch targets: minimum 56dp, ideally 64dp+.

## Top buttons recommendation
Replace many top buttons with:
```text
[Title]                 [Speed label] [Music status if active] [Menu] [Close]
```

But keep speed/music tappable through the menu too.

## Acceptance criteria
- Player opens video-first.
- Tap shows controls.
- Controls auto-hide after delay.
- Advanced features are available in menu, not permanent large buttons.
- Menu actions update PlayerEngine toggle state.
- Headunit Safe Mode disables fancy animation.

## Maestro tests
Create `maestro/milestone_03_video_first_controls.yml`:
- Open video.
- Assert controls visible after tap.
- Tap speed menu and select 1.25x.
- Assert speed label changed.
- Tap menu -> Play as Music.
- Assert audio-only indicator shown.
- Tap menu -> Play as Music again.
- Assert normal video mode restored.

## Files likely touched
- `controls/`
- `player/PlayerActivity.kt`
- `res/layout/activity_player.xml`
- `settings/SettingsStore.kt`
- `maestro/`

## Important implementation warning
Do not remove functionality. Move it from always-visible buttons into a menu/sheet.
