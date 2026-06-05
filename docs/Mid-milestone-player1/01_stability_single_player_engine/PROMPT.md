# Milestone 1 — Single Player Engine Stability

## Goal
Refactor Player Pro so the entire app uses exactly **one ExoPlayer instance** through a single `PlayerEngine` / `PlayerHolder` abstraction. This milestone fixes the root cause of duplicate player bugs, floating-player regressions, ghost playback, audio focus conflicts, and unstable lifecycle behavior.

## Non-negotiable rules
- There must be exactly one `ExoPlayer` instance while playback is active.
- Do not create a second player for floating mode.
- Do not create a second player for audio-only mode.
- Do not create a player inside playlist drawer UI.
- Do not create a player per RecyclerView row.
- Player screens and overlays may attach/detach surfaces, but they must share the same player.

## Required architecture
Create or refactor into this structure:

```text
player/
  PlayerEngine.kt
  PlayerEngineState.kt
  PlayerSurfaceRouter.kt
  PlaybackCommand.kt
  PlaybackMode.kt
  PlayerErrorMapper.kt
```

### PlayerEngine responsibilities
- Own the single `ExoPlayer` instance.
- Accept commands: play item, pause, resume, seek, next, previous, speed, repeat mode, audio-only, floating.
- Expose state to UI.
- Save progress through a repository callback or interface.
- Handle playback errors gracefully.

### PlayerSurfaceRouter responsibilities
- Attach the player to the fullscreen `PlayerView`.
- Detach from fullscreen when floating starts.
- Attach to floating overlay surface when floating starts.
- Detach all video surfaces for audio-only mode.
- Reattach fullscreen when returning from floating/audio-only.

### PlayerEngineState
Include:
- current video URI
- current title
- positionMs
- durationMs
- isPlaying
- isFloating
- isAudioOnly
- speed
- repeatMode
- error message if any

## Toggle behavior
All feature buttons must be true toggles:

```text
Float tap #1 -> floating ON
Float tap #2 -> floating OFF
Play as Music tap #1 -> audio-only ON
Play as Music tap #2 -> audio-only OFF
Repeat tap -> Off -> Repeat One -> Off, or use defined cycle
Repeat Folder tap -> Off -> Repeat Folder -> Off
```

Persist toggle defaults in DataStore, but active playback state should come from PlayerEngine.

## Error handling
When a video fails:
- Show visible error message.
- Offer retry and skip next.
- Do not crash.
- Do not release and recreate random players repeatedly.

## Acceptance criteria
- Searching the codebase shows only one place that constructs `ExoPlayer.Builder`.
- Floating mode does not create a second video/audio playback.
- Audio-only mode does not create another player.
- Repeat, Repeat Folder, Float, and Play as Music behave as toggles.
- Back press exits cleanly and releases player once.
- Orientation changes do not duplicate player.

## Maestro tests to update/add
Create `maestro/milestone_01_single_player_engine.yml`:
- Launch app.
- Open video.
- Tap Float twice; assert no duplicate float controls remain.
- Tap Play as Music twice; assert video returns.
- Tap Repeat twice; assert state changes on/off.
- Pause/play still works after toggles.

## Files likely touched
- `player/PlayerHolder.kt`
- `player/PlayerActivity.kt`
- `player/FloatingPlayerService.kt`
- `player/AudioOnlyService.kt`
- `settings/SettingsStore.kt`
- `maestro/`

## Important implementation warning
Do not polish UI in this milestone. The only mission is player stability and single-instance architecture.
