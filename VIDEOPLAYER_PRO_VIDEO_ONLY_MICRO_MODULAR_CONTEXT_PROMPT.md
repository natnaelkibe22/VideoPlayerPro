# VideoPlayer Pro Micro-Modular Context Prompt

Build/improve VideoPlayer Pro, a Kotlin Android video-only headunit player. Keep it video focused. The only audio feature is Play as Music from the currently playing video.

Use a micro-module Android architecture: each feature lives in a small service-like class so it can be edited, tested, and replaced independently. Do not make one giant Activity.

Core modules:
- core/PermissionService.kt
- core/StorageClassifier.kt
- media/MediaStoreVideoScanner.kt
- media/VideoLibraryRepository.kt
- media/LibraryRefreshWorker.kt
- data/AppDatabase.kt
- data/VideoDao.kt
- settings/SettingsStore.kt
- player/PlayerHolder.kt
- player/PlayerControlService.kt
- player/ProgressService.kt
- player/PlayerActivity.kt
- player/AudioOnlyService.kt
- player/FloatingPlayerService.kt
- ui/FolderAdapter.kt
- ui/VideoAdapter.kt

Performance rules:
- Query MediaStore.Video only. Never scan audio.
- Cache videos in Room and show cache first.
- Refresh in WorkManager/background.
- Keep thumbnails off by default.
- Use one ExoPlayer instance.
- Handle unsupported codec/4K/USB errors gracefully.
- Make floating and audio-only optional/on-demand.

Milestone flow:
1. V0.1 folders/videos only.
2. V0.2 Room cache and resume.
3. V0.3 player controls.
4. V0.4 Play as Music.
5. V0.5 floating resizable player.
6. V0.6 storage/settings.
7. V0.7 stability/performance.
8. V0.8 polish/micro-module refactor.

Testing:
Use Maestro tests from `maestro/` and milestone tests from each milestone folder. Keep test IDs/text stable where possible.
