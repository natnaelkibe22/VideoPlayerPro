# VideoPlayer Pro Micro-Module Architecture

This project avoids one giant Activity by splitting each responsibility into one small service-like file. This is not real backend microservices; it is a contained Android architecture that lets Codex/agentic tools edit one feature at a time.

## Rules

- One feature = one small class/file where possible.
- UI files call services/repositories; they do not scan storage or do heavy work directly.
- `media/` discovers and caches videos only.
- `player/` controls playback only.
- `settings/` owns DataStore only.
- `data/` owns Room only.
- No general audio/music library.

## Feature isolation examples

- To change Android permissions, edit `core/PermissionService.kt`.
- To change folder detection, edit `core/StorageClassifier.kt`.
- To change MediaStore columns, edit `media/MediaStoreVideoScanner.kt`.
- To change repeat/speed behavior, edit `player/PlayerControlService.kt`.
- To change resume logic, edit `player/ProgressService.kt`.
- To change settings defaults, edit `settings/SettingsStore.kt`.
- To change row UI, edit `ui/VideoAdapter.kt`, `ui/FolderAdapter.kt`, and XML rows.
