#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
MAESTRO_BIN="${MAESTRO:-maestro}"
FLAVOR="${1:-autosky}"
BUILD_TYPE="${2:-debug}"

case "$FLAVOR" in
  autosky)
    APP_ID="com.natkibe.videoplayerpro.s26ultra"
    FLAVOR_TASK="Autosky"
    ;;
  s26ultra)
    APP_ID="com.natkibe.videoplayerpro.s26ultra"
    FLAVOR_TASK="S26ultra"
    ;;
  *)
    echo "Unsupported flavor: $FLAVOR"
    echo "Usage: $0 [autosky|s26ultra] [debug|release]"
    exit 1
    ;;
esac

case "$BUILD_TYPE" in
  debug)
    BUILD_TASK="Debug"
    ;;
  release)
    BUILD_TASK="Release"
    ;;
  *)
    echo "Unsupported build type: $BUILD_TYPE"
    echo "Usage: $0 [autosky|s26ultra] [debug|release]"
    exit 1
    ;;
esac

DEVICE_SERIAL="${ANDROID_SERIAL:-$(adb devices | awk 'NR > 1 && $2 == "device" { print $1; exit }')}"
if [ -z "$DEVICE_SERIAL" ]; then
  echo "No connected emulator/device found. Start a lightweight AVD such as Android_8_API26 first."
  exit 1
fi

if ! command -v ffmpeg >/dev/null 2>&1; then
  echo "ffmpeg is required to generate seeded mock videos."
  exit 1
fi

cd "$PROJECT_DIR"
./gradlew ":app:assemble${FLAVOR_TASK}${BUILD_TASK}"

APK_PATH="$PROJECT_DIR/app/build/outputs/apk/$FLAVOR/$BUILD_TYPE/app-$FLAVOR-$BUILD_TYPE.apk"
adb -s "$DEVICE_SERIAL" install -r "$APK_PATH"

adb -s "$DEVICE_SERIAL" shell pm grant "$APP_ID" android.permission.READ_MEDIA_VIDEO >/dev/null 2>&1 || true
adb -s "$DEVICE_SERIAL" shell pm grant "$APP_ID" android.permission.READ_EXTERNAL_STORAGE >/dev/null 2>&1 || true
adb -s "$DEVICE_SERIAL" shell pm grant "$APP_ID" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
adb -s "$DEVICE_SERIAL" shell appops set "$APP_ID" SYSTEM_ALERT_WINDOW allow >/dev/null 2>&1 || true

FIXTURE_DIR="$PROJECT_DIR/build/maestro-fixtures"
mkdir -p "$FIXTURE_DIR"

create_fixture() {
  local name="$1"
  local video_filter="$2"
  local frequency="$3"
  local duration="$4"

  if [ ! -f "$FIXTURE_DIR/$name" ]; then
    ffmpeg -hide_banner -loglevel error -y \
      -f lavfi -i "$video_filter" \
      -f lavfi -i "sine=frequency=${frequency}:sample_rate=44100" \
      -t "$duration" \
      -c:v libx264 -profile:v baseline -pix_fmt yuv420p \
      -c:a aac -b:a 96k \
      "$FIXTURE_DIR/$name"
  fi
}

if [ ! -f "$FIXTURE_DIR/vpp_maestro_01.mp4" ]; then
  ffmpeg -hide_banner -loglevel error -y \
    -f lavfi -i testsrc2=size=640x360:rate=24 \
    -f lavfi -i sine=frequency=440:sample_rate=44100 \
    -t 2 \
    -c:v libx264 -profile:v baseline -pix_fmt yuv420p \
    -c:a aac -b:a 96k \
    "$FIXTURE_DIR/vpp_maestro_01.mp4"
fi

if [ ! -f "$FIXTURE_DIR/vpp_maestro_02.mp4" ]; then
  ffmpeg -hide_banner -loglevel error -y \
    -f lavfi -i testsrc=size=640x360:rate=24 \
    -f lavfi -i sine=frequency=660:sample_rate=44100 \
    -t 2 \
    -c:v libx264 -profile:v baseline -pix_fmt yuv420p \
    -c:a aac -b:a 96k \
    "$FIXTURE_DIR/vpp_maestro_02.mp4"
fi

create_fixture "vpp_maestro_03_square.mp4" "testsrc=size=480x480:rate=24" 550 3
create_fixture "vpp_maestro_04_portrait.mp4" "testsrc2=size=360x640:rate=24" 770 3
create_fixture "vpp_maestro_05_wide.mp4" "smptebars=size=854x360:rate=24" 880 3
create_fixture "vpp_maestro_06_long.mp4" "testsrc=size=640x360:rate=30" 990 6
create_fixture "vpp_maestro_07_stress_long.mp4" "testsrc2=size=640x360:rate=30" 1110 20
create_fixture "vpp_alt_01.mp4" "testsrc2=size=426x240:rate=24" 330 2
create_fixture "vpp_alt_02_portrait.mp4" "testsrc=size=240x426:rate=24" 660 2

REMOTE_ROOT="/sdcard/Movies"
REMOTE_DIR="$REMOTE_ROOT/VideoPlayerProMaestro"
REMOTE_ALT_DIR="$REMOTE_ROOT/VideoPlayerProMaestroAlt"
adb -s "$DEVICE_SERIAL" shell "rm -rf '$REMOTE_DIR' '$REMOTE_ALT_DIR' && mkdir -p '$REMOTE_DIR' '$REMOTE_ALT_DIR'"

PRIMARY_VIDEOS=(
  vpp_maestro_01.mp4
  vpp_maestro_02.mp4
  vpp_maestro_03_square.mp4
  vpp_maestro_04_portrait.mp4
  vpp_maestro_05_wide.mp4
  vpp_maestro_06_long.mp4
  vpp_maestro_07_stress_long.mp4
)
ALT_VIDEOS=(
  vpp_alt_01.mp4
  vpp_alt_02_portrait.mp4
)

for video in "${PRIMARY_VIDEOS[@]}"; do
  adb -s "$DEVICE_SERIAL" push "$FIXTURE_DIR/$video" "$REMOTE_DIR/$video"
done
for video in "${ALT_VIDEOS[@]}"; do
  adb -s "$DEVICE_SERIAL" push "$FIXTURE_DIR/$video" "$REMOTE_ALT_DIR/$video"
done

for video in "${PRIMARY_VIDEOS[@]}"; do
  adb -s "$DEVICE_SERIAL" shell am broadcast \
    -a android.intent.action.MEDIA_SCANNER_SCAN_FILE \
    -d "file://$REMOTE_DIR/$video" >/dev/null 2>&1 || true
  adb -s "$DEVICE_SERIAL" shell cmd media scan-file "$REMOTE_DIR/$video" >/dev/null 2>&1 || true
done
for video in "${ALT_VIDEOS[@]}"; do
  adb -s "$DEVICE_SERIAL" shell am broadcast \
    -a android.intent.action.MEDIA_SCANNER_SCAN_FILE \
    -d "file://$REMOTE_ALT_DIR/$video" >/dev/null 2>&1 || true
  adb -s "$DEVICE_SERIAL" shell cmd media scan-file "$REMOTE_ALT_DIR/$video" >/dev/null 2>&1 || true
done

adb -s "$DEVICE_SERIAL" shell am force-stop "$APP_ID" >/dev/null 2>&1 || true

TEMP_FLOW_DIR="$PROJECT_DIR/build/maestro-flows/$FLAVOR-$BUILD_TYPE"
mkdir -p "$TEMP_FLOW_DIR"

FLOW="${3:-maestro/seeded_player_regressions.yml}"
TEMP_FLOW="$TEMP_FLOW_DIR/$(basename "$FLOW")"
sed "s/com.natkibe.videoplayerpro.s26ultra/$APP_ID/g" "$PROJECT_DIR/$FLOW" > "$TEMP_FLOW"

"$MAESTRO_BIN" test "$TEMP_FLOW"
