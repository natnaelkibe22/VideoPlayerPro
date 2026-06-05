# Milestone 6 — Headunit Performance, Safe Mode, Polish

## Goal
Optimize Player Pro for weak Android headunits and old Android versions. The app should feel fast, stable, and simple in real car usage.

## Add Headunit Safe Mode
Create a setting called `Headunit Safe Mode`.

When ON:
- Disable thumbnails.
- Disable nonessential animations.
- Disable blur/glass effects.
- Disable background auto-refresh unless user taps Refresh.
- Keep drawer transitions simple.
- Keep overlay opacity simple.
- Prefer instant UI over fancy UI.

Default: ON for low-memory devices if detectable, otherwise OFF or ask first launch.

## Add performance settings
- Show thumbnails: default false.
- Enable floating player: default false.
- Show playlist drawer: default true.
- Auto-hide controls: default true.
- Resume playback: default true.
- Autoplay next: default false.
- Repeat mode: default off.
- Compact playlist rows: default true.

## Add debug/performance panel
Hidden under Settings > Developer/Diagnostics:
- library item count
- current storage source
- thumbnail cache size
- player mode
- current decoder info if available
- current video MIME type
- last playback error

## Stability handling
If playback error happens:
- Show user-friendly message.
- Offer retry.
- Offer skip.
- Offer Play as Music if video decoding fails but audio may work.

## UI polish
- Use video-first design.
- Keep text readable from driver/passenger seat.
- Avoid thin touch targets.
- Use 64dp+ controls when possible.
- Drawer should not exceed 30% width in landscape.
- Avoid permanent giant button bars.

## Acceptance criteria
- Safe Mode exists and toggles expensive features off.
- App works with thumbnails disabled.
- App works without floating enabled.
- App recovers from bad video error.
- UI remains usable on 1024x600 and 1280x720 headunits.

## Maestro tests
Create `maestro/milestone_06_headunit_performance_polish.yml`:
- Open Settings.
- Toggle Headunit Safe Mode ON.
- Assert thumbnails OFF.
- Open video.
- Open drawer.
- Close drawer.
- Open playback menu.
- Verify controls still work.

## Files likely touched
- `settings/`
- `diagnostics/`
- `player/PlayerErrorMapper.kt`
- `controls/`
- `playlist/`
- `maestro/`

## Important implementation warning
Do not add new heavy visual effects. This milestone is about making the app lighter and more reliable.
