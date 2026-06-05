# Milestone 4 — Floating + Play as Music Rewrite

## Goal
Make floating video and Play as Music reliable, lightweight, and based on the same single-player engine. Fix the regression where floating mode used to work but now does not.

## Floating requirements
- Optional feature, off by default.
- Requires overlay permission when needed.
- Uses same `PlayerEngine` / ExoPlayer instance.
- Detaches fullscreen surface and attaches overlay surface.
- Returning to fullscreen reverses the surface attachment.
- No duplicate playback.
- No stuck ghost window.
- Drag support.
- Resize support using corner handle.
- Snap to corners optional.

## Audio-only / Play as Music requirements
- Button label: `Play as Music`.
- Uses same player.
- Detaches video surface.
- Continues audio playback.
- Shows lightweight notification/media controls if implemented.
- Tap again exits audio-only and reattaches video surface.
- This is not a music library feature.

## Required architecture
Create or refactor into:

```text
floating/
  FloatingWindowController.kt
  FloatingWindowService.kt
  FloatingWindowState.kt
  FloatingResizeController.kt
  OverlayPermissionHelper.kt

audioonly/
  AudioOnlyController.kt
  AudioOnlyState.kt
  AudioOnlyNotificationController.kt
```

## Toggle behavior
```text
Float OFF -> tap -> request permission if needed -> Float ON
Float ON -> tap -> Float OFF -> remove overlay -> restore fullscreen

AudioOnly OFF -> tap -> detach surface -> AudioOnly ON
AudioOnly ON -> tap -> attach surface -> AudioOnly OFF
```

## Bugs to prevent
- Multiple overlays.
- Overlay remains after app closed.
- Fullscreen and floating playing simultaneously.
- Audio-only stuck with black screen.
- Window leak on Activity destroy.

## Acceptance criteria
- Float button is a true toggle.
- Play as Music is a true toggle.
- Only one player instance exists.
- Overlay can be dragged.
- Overlay can be resized.
- Overlay closes cleanly.
- Fullscreen resumes after closing float.

## Maestro tests
Create `maestro/milestone_04_floating_audio_only.yml`:
- Open video.
- Open playback menu.
- Toggle Float ON.
- Assert floating controls/window visible.
- Toggle Float OFF.
- Assert floating window gone.
- Toggle Play as Music ON.
- Assert audio-only indicator visible.
- Toggle Play as Music OFF.
- Assert video visible.

## Files likely touched
- `floating/`
- `audioonly/`
- `player/PlayerSurfaceRouter.kt`
- `player/PlayerEngine.kt`
- `AndroidManifest.xml`
- `maestro/`

## Important implementation warning
This is the most fragile milestone. Do not combine it with visual redesign. Focus only on lifecycle and correctness.
