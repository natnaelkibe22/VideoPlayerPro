# targetSdk=35 Compatibility Analysis: Head Units (Android 9–13)

> Analysis date: Current
> Target SDK: 35 (compileSdk=35, minSdk=23, targetSdk=35)
> Flavors: autosky (head units), s26ultra
> Target device range: Android 9 (API 28) through Android 13 (API 33)

---

## 1. Build Configuration

**Status: ✅ OK**

- `compileSdk = 35` — Latest API surface for compilation
- `minSdk = 23` — Android 6.0+ (well below the 9–13 range)
- `targetSdk = 35` — Targets the latest SDK; app runs in API 35 compatibility mode on newer devices, but on Android 9–13 it runs under the respective OS version's behavior.

No issues for the 9–13 range.

---

## 2. AndroidManifest.xml

**Status: ✅ OK**

| Permission | Status | Notes |
|---|---|---|
| `READ_EXTERNAL_STORAGE` (maxSdkVersion=32) | ✅ | Correctly scoped to pre-13 |
| `READ_MEDIA_VIDEO` | ✅ | For Android 13+ |
| `SYSTEM_ALERT_WINDOW` | ✅ | For floating overlay |
| `FOREGROUND_SERVICE` | ✅ | Required for any foreground service |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | ✅ | Android 14+ requires typed FGS permissions |
| `WAKE_LOCK` | ✅ | For keeping screen on |
| `POST_NOTIFICATIONS` | ⚠️ | Declared but **not requested at runtime** (see §3) |

All `<service>` declarations include `android:foregroundServiceType="mediaPlayback"` — this is required for Android 14+ but harmless on 9–13.

---

## 3. POST_NOTIFICATIONS Runtime Permission — ⚠️ MODERATE ISSUE

**Files affected:** All 4 foreground services:
- `AudioOnlyNotificationController.kt` — calls `startForeground()` at line 43
- `FloatingWindowService.kt` — calls `startForeground()` at line 87
- `AudioOnlyService.kt` — calls `startForeground()` at line 17 (legacy)
- `FloatingPlayerService.kt` — calls `startForeground()` at line 44 (legacy)
- `PlayerActivity.kt` — calls `notificationManager?.notify()` at line 1599

**The problem:**
- `POST_NOTIFICATIONS` is declared in the manifest (line 8) but **never requested at runtime** anywhere in the codebase.
- The only runtime permission request is for `READ_MEDIA_VIDEO`/`READ_EXTERNAL_STORAGE` in `MainActivity.kt` (line 94).

**Impact on Android 13 (API 33):**
- `startForeground()` will **succeed without crashing** — the foreground service starts, but the notification is **silently suppressed**.
- `notificationManager?.notify()` calls will also be silently suppressed.
- Users won't see the persistent notification for audio-only playback or floating mode. The service still works, but there's no user-visible indication it's running.
- If the service is killed by the system, there's no notification to let the user know it's still running.

**Impact on Android 9–12:**
- No issue — `POST_NOTIFICATIONS` permission doesn't exist on these API levels. The permission declaration is simply ignored by the OS.

**Impact on Android 14+ (out of scope but relevant):**
- `startForeground()` with `POST_NOTIFICATIONS` not granted on API 34+ (targetSdk=35) will throw a `SecurityException` crash.

**Recommendation:** Add runtime permission request for `POST_NOTIFICATIONS` before starting foreground services on Android 13+. Use `ActivityResultContracts.RequestPermission()` similar to the video permission flow. Check `shouldShowRationale()` and handle denial gracefully.

---

## 4. Foreground Service Types — ✅ OK for 9–13

**Status: ✅ No issue for Android 9–13 scope.**

All four services in the manifest declare `android:foregroundServiceType="mediaPlayback"`. This is required for Android 14+ (API 34+) and acts as documentation on earlier versions. No crash risk.

---

## 5. PendingIntent Mutability — ✅ OK

**Status: ✅ No issue.**

Every `PendingIntent` in the codebase uses `PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT`:

- `PlayerEngine.kt:111` — `PendingIntent.getActivity()` for MediaSession
- `PlayerActivity.kt:1544,1555,1566,1574` — `PendingIntent.getBroadcast()` / `getActivity()` for media notification
- `AudioOnlyNotificationController.kt:94,149` — `PendingIntent.getActivity()` / `getService()` for audio notification

Since Android 12 (API 31), a `PendingIntent` without `FLAG_IMMUTABLE` will crash. All PendingIntents have `FLAG_IMMUTABLE` → no crash risk.

---

## 6. Broadcast Receiver Registration — ✅ OK

**Status: ✅ No issue.**

The app uses `registerReceiver()` with `RECEIVER_NOT_EXPORTED` on API 33+ (lines 378–380 and 1523–1527 in `PlayerActivity.kt`). This is the correct pattern for Android 13+ where broadcast receiver flags are mandatory.

---

## 7. Implicit Broadcast Sends — ✅ OK

**Status: ✅ No issue.**

Three `sendBroadcast(Intent(ACTION_*))` calls exist:
- `FloatingWindowService.kt:123` — `sendBroadcast(Intent(ACTION_FLOATING_CLOSED))`
- `FloatingWindowService.kt:169` — `sendBroadcast(Intent(ACTION_RETURN_FULLSCREEN))`
- `FloatingPlayerService.kt:69` — `sendBroadcast(Intent(ACTION_FLOATING_CLOSED))`

Sending implicit broadcasts (with just an action string) is **still allowed on all API levels**. The Android 8 restriction only applies to **registering** for implicit broadcasts in the manifest, not to sending them. These broadcasts are received by the in-activity `BroadcastReceiver` registered at runtime, which works correctly.

---

## 8. Scoped Storage / Media Permissions — ✅ OK

**Status: ✅ No issue.**

The app correctly handles the transition:

| Android Version | Permission Used | Where |
|---|---|---|
| Android 9–12 (API 28–32) | `READ_EXTERNAL_STORAGE` (declared with `maxSdkVersion=32`) | `AndroidManifest.xml:2` |
| Android 13+ (API 33+) | `READ_MEDIA_VIDEO` | `AndroidManifest.xml:3`, `PermissionService.kt:11` |

`MainActivity.kt` correctly requests the appropriate permission at runtime via `PermissionService.videoPermission()` (line 94).

---

## 9. Foreground Service Start Limits (Android 12+) — ✅ OK

**Status: ✅ No issue.**

Android 12 (API 31) introduced restrictions on starting foreground services from the background. The app starts foreground services only from **direct user interaction** (button taps in `PlayerActivity`):

- `AudioOnlyController.enter()` — called from user-initiated "Play as Music" toggle
- `FloatingWindowController.enter()` — called from user-initiated "Floating" toggle

Both use `context.startForegroundService(intent)` on API 26+. Since they're user-initiated, the foreground service start is always allowed.

---

## 10. Foreground Service 5-Second Rule — ✅ OK

**Status: ✅ No issue.**

All four foreground services call `startForeground()` immediately in their `onCreate()` method — well within the 5-second window that Android 8+ enforces.

---

## 11. Package Visibility (Android 11+) — ✅ OK

**Status: ✅ No issue (no impact on app).**

Android 11 introduced package visibility restrictions (`QUERY_ALL_PACKAGES`). The app does not query installed packages, so no issue.

---

## 12. Full-Screen Intent Permission (Android 11+) — ✅ OK

Android 11+ changed requirements for `SYSTEM_ALERT_WINDOW` and `USE_FULL_SCREEN_INTENT`. The app uses `SYSTEM_ALERT_WINDOW` for the floating overlay (which already requires user-granted overlay permission) but does not use `USE_FULL_SCREEN_INTENT`.

---

## 13. WebView — ✅ OK (Not Used)

The app does not use WebView, so Android 10+ `QUERY_ALL_PACKAGES` and WebView compatibility mode changes are irrelevant.

---

## 14. Room Database — ✅ OK

**Status: ✅ No issue.**

The Room database (`AppDatabase.kt`) uses version 4 with explicit SQL migration paths (`MIGRATION_2_3`, `MIGRATION_3_4`). The SQL is straightforward (CREATE TABLE, ALTER TABLE ADD COLUMN) and compatible with SQLite across all API levels 23–35.

---

## 15. Media3 ExoPlayer — ✅ OK

**Status: ✅ No issue.**

Using `androidx.media3:media3-exoplayer:1.4.1` — a modern, actively maintained library. Media3 itself handles API compatibility internally. No known issues for the 9–13 range.

---

## 16. Notification Channel Handling — ✅ OK

**Status: ✅ No issue.**

All notification channels are guarded with `Build.VERSION.SDK_INT >= 26` checks (notification channels were introduced in API 26, which is below our minimum of 28). The channels are created with `IMPORTANCE_LOW` which is appropriate.

---

## Summary of Issues

| # | Issue | Severity | Affects | Impact |
|---|---|---|---|---|
| 1 | **POST_NOTIFICATIONS not requested at runtime** | ⚠️ Moderate | Android 13 only | Notifications are suppressed silently. Foreground services still work but user has no visual indicator. On Android 14+ this would crash. |
| 2 | All other checks | ✅ OK | — | No issues found for Android 9–13 |

### Total: 1 actionable issue for Android 9–13 head unit compatibility.

The autosky APK targeting SDK 35 will **run without crashes** on Android 9–13 head units. The only deficiency is the missing `POST_NOTIFICATIONS` runtime permission request on Android 13, which causes notifications to be silently suppressed (no crash, but users won't see persistent "play as music" or "floating active" notifications).
