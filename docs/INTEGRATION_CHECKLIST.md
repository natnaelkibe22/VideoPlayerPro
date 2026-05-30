# Integration Checklist

Use this before calling the app 90% ready.

## Compile
- [ ] `./gradlew :app:assembleDebug`
- [ ] Fix imports and manifest service declarations.
- [ ] Confirm Room KSP generation succeeds.

## Runtime smoke
- [ ] Launch app on emulator/device.
- [ ] Grant video permission.
- [ ] Folder tab shows no crash even with no videos.
- [ ] Refresh button does not block UI.
- [ ] Settings tab opens.

## Media
- [ ] MP4 H.264 720p plays.
- [ ] MP4 H.264 1080p plays.
- [ ] Unsupported 4K/HEVC fails gracefully with error text.
- [ ] USB removed during playback does not crash app.

## Features
- [ ] Resume playback works after closing player.
- [ ] Repeat one works.
- [ ] Repeat folder works.
- [ ] Speed cycles without crash.
- [ ] Play as Music detaches video surface.
- [ ] Floating player asks for overlay permission when missing.

## Performance
- [ ] No thumbnails by default.
- [ ] RecyclerView only loads visible rows.
- [ ] One ExoPlayer instance shared.
- [ ] No file scanning on main thread.
