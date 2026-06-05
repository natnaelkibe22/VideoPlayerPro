# Milestone 5 — Library Cache, Thumbnails, Resume, Favorites

## Goal
Make the video library feel instant and lightweight while supporting thumbnails and useful library features. The app must show cached data first, refresh in background, and never generate all thumbnails at startup.

## Required data flow
```text
App launch
  -> Load cached Room library immediately
  -> Show folders/videos
  -> Background MediaStore refresh
  -> Update Room
  -> UI refreshes only changed rows
```

## Required architecture
Create or refactor into:

```text
library/
  VideoLibraryRepository.kt
  MediaStoreVideoScanner.kt
  VideoLibraryCache.kt
  StorageClassifier.kt
  LibraryRefreshCoordinator.kt

thumbnail/
  ThumbnailLoader.kt
  ThumbnailDiskCache.kt
  ThumbnailMemoryPolicy.kt

resume/
  ResumeRepository.kt
  ResumeProgressWriter.kt

favorites/
  FavoriteRepository.kt
  PinnedFolderRepository.kt
```

## Features
- MediaStore video discovery only.
- System/Internal storage tab.
- USB/External storage tab when available.
- Folder grouping.
- Recently watched.
- Resume position.
- Favorite videos.
- Pinned folders.
- Optional thumbnails.
- Optional resume progress bar in rows.

## Thumbnail rules
Default: OFF.

When ON:
- Load only visible rows.
- Use small target size.
- Use disk cache.
- Avoid decoding full-size frames.
- Do not block UI thread.
- Clear memory cache under pressure.

## Resume rules
Save progress:
- on pause
- on stop
- every 15–30 seconds while playing, not every second

Resume only if:
- position > 10 seconds
- position is not near end
- setting enabled

## Acceptance criteria
- App launch shows cached folders quickly.
- Refresh library button works.
- USB/external videos appear when MediaStore exposes them.
- Thumbnails can be turned on/off.
- Turning thumbnails off reduces work immediately.
- Recent and resume data works.

## Maestro tests
Create `maestro/milestone_05_library_cache_thumbnails.yml`:
- Open app.
- Navigate folders.
- Toggle thumbnails ON.
- Assert rows still visible.
- Toggle thumbnails OFF.
- Open video, seek, exit, reopen.
- Assert resume prompt or resumed position.

## Files likely touched
- `data/`
- `library/`
- `thumbnail/`
- `resume/`
- `favorites/`
- `settings/SettingsStore.kt`
- `maestro/`

## Important implementation warning
Do not add manual recursive file scanning. MediaStore remains the source of truth.
