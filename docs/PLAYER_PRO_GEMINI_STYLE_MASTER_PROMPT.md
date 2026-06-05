# Player Pro — Gemini-Style Video-First Headunit Player Prompt

Build/upgrade **Player Pro**, a video-only Kotlin Android headunit app. The design target is: fullscreen video first, minimal overlay controls, right-to-left playlist drawer, optional lightweight thumbnails, and one single ExoPlayer instance shared across fullscreen, floating, and audio-only modes.

## Product direction

The app is for Android car headunits, including low-end units. It must be fast, stable, simple, and video-focused. The only audio feature is **Play as Music**, which continues audio from a video while detaching/hiding the video surface.

## Visual design target

Use the Gemini-style design discussed:

- Video fills the entire screen.
- Tap video to show overlay controls.
- Overlay controls auto-hide after a few seconds.
- Playlist drawer slides from **right to left**.
- Playlist drawer takes only **25–30% of screen width** on wide/landscape headunits.
- Use semi-transparent dark overlay, not heavy blur.
- Keep UI readable with large headunit-friendly targets.
- Move advanced actions into a bottom-right or top-right menu instead of many permanent buttons.

## Architecture rules

### Single player rule

Use exactly one controlled player holder:

```text
PlayerHolder
 └── Media3 ExoPlayer
```

Do not create a second ExoPlayer for floating mode. Do not create one player per row. Do not create separate full player + floating player.

Player modes:

```text
Fullscreen Mode
Playlist Drawer Mode
Floating Video Mode
Audio Only / Play as Music Mode
```

All modes must use the same player instance.

## Core stack

- Kotlin
- XML layouts + RecyclerView for low overhead
- AndroidX Media3 ExoPlayer
- MediaStore for video discovery
- Room for cached library, favorites, resume progress
- DataStore for settings/toggles
- WorkManager for safe background refresh
- Maestro YAML tests

## Main features

- Video folder browser
- Storage sections: Internal/System, USB/External, SD if detected
- MediaStore video discovery only
- Room cache first, refresh in background
- Fullscreen player
- Right-side slide playlist drawer
- Optional video thumbnails
- Lazy-loaded thumbnail cache
- Play/pause, seek, next/previous
- 5s and 15s seek buttons
- Playback speed menu
- Repeat video, repeat folder, repeat all, repeat off
- Play as Music
- Floating video mode
- Floating controls-only mode
- Resume playback
- Recently watched
- Favorites / pinned folders
- Safe refresh library button
- Codec/error message instead of crash
- Headunit Safe Mode

## Feature toggles

Every heavy or optional feature must be toggleable:

- Show thumbnails: default false
- Floating player: default false
- Floating controls-only: default false
- Show playlist drawer: default true
- Auto-hide controls: default true
- Resume playback: default true
- Autoplay next: default false
- Headunit Safe Mode: default true on weak devices
- Use fancy blur: default false and avoid on Android 8–10

## Toggle behavior rule

Every button must act as a true toggle:

```text
Tap Float once -> enter floating mode
Tap Float again -> exit floating mode
Tap Repeat once -> repeat enabled
Tap Repeat again or cycle -> off/next mode
Tap Play as Music once -> audio-only mode
Tap Play as Music again -> restore video surface
Tap Playlist once -> drawer open
Tap Playlist again -> drawer closed
```

Never allow duplicate overlays or duplicate player surfaces.

## Right playlist drawer

The playlist drawer must:

- Slide from right to left.
- Use 25–30% width in landscape.
- Use 80–90% width only on narrow portrait screens.
- Show current video highlighted.
- Show optional thumbnail if enabled.
- Show title, duration, folder, and resume progress if available.
- Use RecyclerView.
- Lazy load rows.
- Never own a player instance.

## Thumbnail rules

- Thumbnails are optional and off by default.
- If enabled, load only visible thumbnails.
- Use disk cache.
- Do not generate all thumbnails on startup.
- Use placeholder cards for rows while thumbnails load.
- Allow “Text-only library mode” for maximum speed.

## Floating mode rules

Floating video mode:

- Uses the same ExoPlayer.
- Moves/detaches the video surface safely.
- Draggable.
- Resizable from corner/edge.
- Snaps to corners.
- Can be closed.
- Must cleanly remove overlay window.

Floating controls-only mode:

- Transparent, small, non-invasive.
- Shows previous, play/pause, next, repeat, speed.
- Does not render video.
- Should be lighter than floating video.

## Play as Music rules

- Detach/hide video surface.
- Continue audio playback.
- Use MediaSession/notification controls if implemented.
- Do not build album/artist/music library.
- Button toggles back to normal video mode.

## Performance rules

- No recursive storage scanning.
- No startup thumbnail generation.
- No multiple ExoPlayers.
- No real-time blur on video frames.
- No heavy animation loops.
- No network, ads, analytics.
- Use cached Room rows first.
- Use WorkManager for refresh.
- Save progress carefully, not every frame.
- Handle USB removed/reinserted gracefully.

## Maestro tests required

Create tests for:

- App launch and folder library visible.
- Right playlist drawer opens/closes.
- Player controls overlay appears and auto-hides.
- Repeat toggle cycles correctly.
- Play as Music toggles on/off.
- Floating mode toggles on/off without duplicate player.
- Thumbnail setting toggles on/off.
- Headunit Safe Mode disables heavy UI.
