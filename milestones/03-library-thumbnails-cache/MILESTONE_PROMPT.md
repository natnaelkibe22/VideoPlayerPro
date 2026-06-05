# Milestone 03 — Right Playlist Drawer + Thumbnails + Cache

Goal: Add the Gemini-style right-to-left playlist drawer and lightweight thumbnail system.

Implement:
- Right-side drawer that slides from right to left.
- Width: 25–30% in landscape/headunit mode.
- Use RecyclerView.
- Show current video highlighted.
- Row contents:
  - optional thumbnail
  - title
  - duration
  - folder/path summary
  - resume progress indicator
- Drawer never owns ExoPlayer.
- Tapping row sends command to PlayerHolder.

Thumbnail rules:
- Setting `showThumbnails`, default false.
- If false, text-only rows.
- If true, lazy-load visible thumbnails only.
- Cache thumbnails on disk.
- Never generate all thumbnails at startup.

Room cache:
- Save MediaStore video rows.
- Save progress.
- Save recently watched.
- Save pinned/favorite folders.

Maestro tests:
- `maestro/milestones/03_playlist_thumbnails_cache.yml`
- Open drawer.
- Verify width/visible compact playlist.
- Toggle thumbnails on/off in settings.
- Verify playlist still works text-only.
