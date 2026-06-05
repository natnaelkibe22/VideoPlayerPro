# Milestone 2 — Right Playlist Drawer Interactions

## Goal
Replace the current playlist/folder overlay behavior with a lightweight right-to-left sliding playlist drawer inspired by the Gemini design. It should take a small portion of the screen and must not block normal player interaction when closed.

## Desired UX
- Video uses 100% of the screen when drawer is closed.
- Playlist drawer opens from the **right side**.
- Drawer width should be 25–30% on landscape headunits.
- Drawer should not use heavy blur.
- Use a simple semi-transparent dark background.
- Tapping outside drawer closes it.
- Swiping right closes it.
- Tapping a video row immediately plays that video through `PlayerEngine`.

## What to avoid
- Do not make a permanent left drawer unless user setting says so.
- Do not make drawer 50% width.
- Do not use real-time blur.
- Do not intercept all touches when drawer is closed.
- Do not create a player inside drawer.
- Do not rebuild player when selecting item.

## Required architecture
Create or refactor into:

```text
playlist/
  PlaylistDrawerController.kt
  PlaylistDrawerState.kt
  PlaylistItemAdapter.kt
  PlaylistItemUiModel.kt
  PlaylistInteractionContract.kt
```

### PlaylistDrawerState
Include:
- isOpen
- selectedVideoUri
- drawerWidthPercent
- compactModeEnabled
- showThumbnails

### PlaylistInteractionContract
Expose:
- onVideoSelected(uri)
- onDrawerOpen()
- onDrawerClose()
- onToggleDrawer()

The implementation must call `PlayerEngine.play(uri)` or equivalent, not create its own player.

## Row design
Each row should support:
- thumbnail if enabled
- title
- duration
- folder name optional
- current playing indicator
- resume progress line if available

Default compact row:
```text
[thumbnail 64x36] title one line
```

## Gesture behavior
- Swipe from right edge opens drawer.
- Swipe right on drawer closes drawer.
- Tap outside drawer closes drawer.
- Player taps must work after drawer closes.

## Acceptance criteria
- Playlist opens right-to-left.
- Drawer width is 25–30% landscape.
- Swipe close works.
- Tap outside close works.
- Clicking video row starts playback.
- Video controls still work after closing drawer.
- No duplicate players.

## Maestro tests
Create `maestro/milestone_02_playlist_drawer.yml`:
- Open player.
- Tap playlist/menu icon.
- Assert drawer visible.
- Tap a playlist row.
- Assert title/current item changes.
- Swipe right on drawer.
- Assert drawer hidden.
- Tap video area.
- Assert controls appear.

## Files likely touched
- `player/PlayerActivity.kt`
- `playlist/`
- `ui/VideoAdapter.kt`
- `res/layout/activity_player.xml`
- `maestro/`

## Important implementation warning
This milestone is about interaction correctness, not visual perfection.
