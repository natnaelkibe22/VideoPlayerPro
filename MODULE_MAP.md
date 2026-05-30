# Module Map

```text
MainActivity
  -> VideoLibraryFeature
       -> VideoLibraryRepository
            -> MediaStoreVideoScanner
            -> VideoDao
  -> SettingsFeature
       -> SettingsStore

PlayerActivity
  -> VideoPlayerFeature
       -> PlayerHolder
  -> ResumePlaybackFeature
       -> ProgressService
            -> VideoDao
  -> PlayAsMusicFeature
       -> AudioOnlyService
  -> FloatingPlayerFeature
       -> FloatingPlayerService
```

## Rule for future work
Do not put feature logic directly into Activity files unless it is only wiring UI events.
