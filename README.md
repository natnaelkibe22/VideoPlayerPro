# VideoPlayer Pro — Video-Only Android Headunit Player

VideoPlayer Pro is a lightweight Kotlin Android project for Android headunits. It is intentionally **video-only**. The only audio-related feature is **Play as Music**, which keeps audio from the current video playing while the video surface is detached to reduce GPU/video-rendering load.

## Goal

Build a fast, stable, small-resource video player for weak Android headunits:

- MediaStore discovery instead of recursive file scanning
- Room cache-first library
- DataStore toggle-based settings
- Media3 ExoPlayer hardware decoding
- Optional thumbnails, optional floating player
- One controlled ExoPlayer instance
- Maestro tests divided by feature and milestone

## Micro-module architecture

The app is divided into small contained services, similar to a microservice/MFE style but inside one APK:

```text
core/
  PermissionService.kt       Android version-specific video permission
  StorageClassifier.kt       System/USB/Movies/Downloads classification
  TimeFormat.kt              Tiny formatting helper

data/
  AppDatabase.kt             Room DB provider
  VideoDao.kt                Video cache + progress queries
  VideoItemEntity.kt         Cached video row
  VideoProgressEntity.kt     Resume/recent progress row

media/
  MediaStoreVideoScanner.kt  Only scans MediaStore.Video
  VideoLibraryRepository.kt  Cache-first library API
  LibraryRefreshWorker.kt    Background refresh worker

player/
  PlayerHolder.kt            Single ExoPlayer holder
  PlayerActivity.kt          Fullscreen playback
  PlayerControlService.kt    Repeat/speed controls
  ProgressService.kt         Resume saving/restoring
  AudioOnlyService.kt        Play as Music foreground mode
  FloatingPlayerService.kt   Optional overlay window

settings/
  SettingsStore.kt           DataStore toggles

ui/
  FolderAdapter.kt           Folder rows
  VideoAdapter.kt            Video rows
```

This keeps each feature small enough to rewrite/debug independently.

## Build

Open the project in Android Studio, let Gradle sync, then build/run `app`. This skeleton does not include a Gradle wrapper; Android Studio can generate one if needed.

## Important defaults

- Thumbnails: off
- Floating overlay: off
- Playlist side panel: on
- Resume playback: on
- Autoplay next: off
- Repeat: off

## Stability rules

1. Do not scan raw storage on startup. Query MediaStore and cache in Room.
2. Show cached Room data immediately, refresh in background.
3. Do not generate thumbnails unless setting is on.
4. Never create one ExoPlayer per row. Use `PlayerHolder`.
5. Video-only first. No music library, albums, artists, lyrics, or audio scanner.
6. Treat USB removal, bad videos, and unsupported codecs as recoverable UI errors.
7. Overlay and audio-only services run only when the user explicitly enables/starts them.

## Maestro

Feature tests live in `maestro/`. Milestone-specific tests live inside each `milestones/v*/maestro/` folder. Build/install an Autosky debug APK, then run any flow against a connected emulator/headunit:

```bash
./gradlew :app:assembleAutoskyDebug
adb install -r app/build/outputs/apk/autosky/debug/app-autosky-debug.apk
maestro test maestro/smoke_launch.yml
```

Milestone 07 regression coverage is split by workflow:

```bash
maestro test maestro/smoke_launch.yml
maestro test maestro/folder_browser.yml
maestro test maestro/player_basic_controls.yml
maestro test maestro/playlist_drawer.yml
maestro test maestro/playback_menu_toggles.yml
maestro test maestro/floating_player.yml
maestro test maestro/play_as_music.yml
maestro test maestro/settings_safe_mode.yml
maestro test maestro/thumbnails_toggle.yml
maestro test maestro/resume_playback.yml
maestro test maestro/regression_no_duplicate_float.yml
```

The flows prefer stable resource IDs/content descriptions and avoid exact video names. Empty media libraries are handled with conditional branches; player-specific steps execute when at least one folder/video row is available. Floating overlay assertions require Android overlay permission to be granted before running those flows; without permission the flows verify fullscreen recovery instead.

## Milestones

- `v0.1-ultra-light-video-only` — folders/videos only
- `v0.2-cache-resume` — Room cache + resume positions
- `v0.3-player-controls` — speed/repeat/playlist controls
- `v0.4-play-as-music-only-audio-feature` — audio-only mode from current video
- `v0.5-floating-resizable` — optional draggable/resizable overlay
- `v0.6-storage-usb-settings` — storage tabs + DataStore settings
- `v0.7-stability-performance` — graceful error handling/performance hardening
- `v0.8-micro-modular-90-ready` — split services and ready-to-debug structure

## v0.9 micro-module readiness upgrade

This project now includes feature contracts under `features/*` and documentation under `docs/`.
The intended workflow is:

```text
one feature package -> one README/milestone -> one Maestro flow -> one small PR
```

This helps avoid the common Android problem where player, scanner, settings, floating overlay, and database code all become tangled together.

See:

- `docs/MICRO_MODULE_CONTRACTS.md`
- `docs/INTEGRATION_CHECKLIST.md`
- `docs/CODEX_NEXT_PROMPT.md`
