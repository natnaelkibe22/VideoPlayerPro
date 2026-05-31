# v0.9 Feature Contracts

Purpose: make VideoPlayer Pro easier to finish by isolating every feature behind a tiny class.

Files:

- `core/contracts/FeatureModule.kt`
- `core/contracts/ModuleResult.kt`
- `core/contracts/VideoPlayerProAppContainer.kt`
- `features/library/VideoLibraryFeature.kt`
- `features/player/VideoPlayerFeature.kt`
- `features/resume/ResumePlaybackFeature.kt`
- `features/audioonly/PlayAsMusicFeature.kt`
- `features/floating/FloatingPlayerFeature.kt`
- `features/settings/SettingsFeature.kt`

Acceptance:

- Existing activities can keep working.
- New code should depend on features instead of copying repository/service logic.
- No new heavy dependency injection framework.
