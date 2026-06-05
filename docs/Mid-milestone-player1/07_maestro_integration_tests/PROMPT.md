# Milestone 7 — Maestro Integration and Regression Tests

## Goal
Create a complete Maestro test suite that protects the main Player Pro workflows from regressions, especially floating player, playlist drawer, toggles, and single-player behavior.

## Required test files
```text
maestro/
  smoke_launch.yml
  folder_browser.yml
  player_basic_controls.yml
  playlist_drawer.yml
  playback_menu_toggles.yml
  floating_player.yml
  play_as_music.yml
  settings_safe_mode.yml
  thumbnails_toggle.yml
  resume_playback.yml
  regression_no_duplicate_float.yml
```

## Smoke test
- Launch app.
- Grant media permission if prompted.
- Verify main screen visible.

## Folder browser test
- Open folder list.
- Select folder.
- Select video.
- Verify player opens.

## Player controls test
- Tap screen.
- Play/pause.
- Seek.
- Next/previous if playlist exists.

## Playlist drawer test
- Open drawer from right.
- Tap row.
- Swipe right to close.
- Tap outside to close.

## Toggle test
- Open playback menu.
- Toggle Repeat Video on/off.
- Toggle Repeat Folder on/off.
- Toggle Play as Music on/off.
- Toggle Float on/off.

## Floating regression test
- Toggle Float ON.
- Toggle Float OFF.
- Toggle Float ON again.
- Assert only one floating window/control exists.
- Assert fullscreen is restored cleanly.

## Settings test
- Toggle thumbnails.
- Toggle Headunit Safe Mode.
- Toggle playlist drawer.
- Toggle floating player permission setting if possible.

## Test IDs
Add stable test IDs/content descriptions for:
- Player screen
- Main folder screen
- Playlist drawer
- Playback menu
- Float toggle
- Repeat toggle
- Repeat folder toggle
- Play as Music toggle
- Speed button
- Settings screen
- Safe Mode toggle
- Thumbnail toggle

## Acceptance criteria
- All Maestro files exist.
- Tests use stable IDs/text labels.
- Tests are split by feature, not one giant flow.
- Regression test covers duplicate float bug.
- README documents how to run tests.

## Important implementation warning
Do not depend on exact video names in tests unless fake/sample data is included. Prefer test IDs and mocked/fake library mode when possible.
