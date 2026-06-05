# Milestone 02 — Single ExoPlayer + Toggle Modes

Goal: Fix double-player/floating-player bug by enforcing one ExoPlayer architecture.

Implement:
- `PlayerHolder` owning the only Media3 ExoPlayer.
- Mode enum:
  - FULLSCREEN
  - PLAYLIST_DRAWER
  - FLOATING_VIDEO
  - FLOATING_CONTROLS
  - AUDIO_ONLY
- Mode controller that toggles states.
- Buttons must toggle on/off instead of creating duplicate overlays.

Required behavior:
- Tap Float -> enter floating video.
- Tap Float again -> return/close floating video.
- Tap Play as Music -> detach video surface and continue audio.
- Tap Play as Music again -> restore video surface.
- Tap Playlist -> drawer opens.
- Tap Playlist again -> drawer closes.

Never:
- Create a second ExoPlayer.
- Create another overlay if one is already active.
- Leave overlay window stuck after exit.

Maestro tests:
- `maestro/milestones/02_single_player_modes.yml`
- Toggle Float twice and verify only one floating surface.
- Toggle Play as Music twice.
- Toggle Playlist twice.
