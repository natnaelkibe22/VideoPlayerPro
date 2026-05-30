# v1.0 Integration Polish

Purpose: connect the micro-modules into a stable app shell.

Tasks:

1. Run Gradle sync and fix compile issues.
2. Replace direct activity construction with `PlayerProAppContainer` where useful.
3. Keep video-only scope.
4. Make no-video empty state friendly.
5. Make playback error handling visible instead of crashing.
6. Confirm services are declared and foreground service notification is valid.
7. Run all Maestro flows.

Acceptance:

- App launches.
- Video permission flow works.
- Folder screen handles empty library.
- Settings screen opens.
- Player screen can receive a video URI.
- Play as Music does not start a music library.
