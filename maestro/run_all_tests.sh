#!/bin/bash
# ============================================================
# VideoPlayerPro - Maestro Test Runner for Android Headunit Emulators
# ============================================================
# Runs all test flows across all Android version emulators
# Usage: ./maestro/run_all_tests.sh [emulator_name]
#   If emulator_name is provided, only runs on that emulator
#   Otherwise runs on all emulators sequentially
# ============================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
APK_PATH="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"
REPORT_DIR="$PROJECT_DIR/maestro/reports"

# Emulators to test (excluding S26_Ultra which is used by another agent)
EMULATORS=(
  "Android_8_API26"
  "Android_9_API28"
  "Android_10_API29"
  "Android_11_API30"
  "Android_12_API31"
  "Android_13_API33"
  "Android_14_API34"
  "Android_15_API35"
  "Android_16_API36"
  "Android_17_API37"
)

# Test flows organized by category
DEBUG_TESTS=(
  "debug_overlay_permissions"
  "debug_resizing"
  "debug_orientation"
  "debug_lifecycle"
)

EXOPLAYER_TESTS=(
  "exoplayer_4k_h265"
  "exoplayer_corrupted_mkv"
  "exoplayer_unsupported_codecs"
  "exoplayer_hdr"
)

MODULE_TESTS=(
  "module_core"
  "module_media_store"
  "module_room_cache"
  "module_settings"
  "module_player"
  "module_audio_only"
  "module_floating_player"
  "module_storage"
  "module_thumbnail"
  "module_maestro"
)

EXISTING_TESTS=(
  "module_contract_smoke"
  "no_video_empty_state"
  "video_folder_tab"
  "settings_tab"
)

ALL_TESTS=("${DEBUG_TESTS[@]}" "${EXOPLAYER_TESTS[@]}" "${MODULE_TESTS[@]}" "${EXISTING_TESTS[@]}")

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Check if a specific emulator was requested
if [ $# -ge 1 ]; then
  TARGET_EMULATOR="$1"
  echo -e "${BLUE}Targeting single emulator: $TARGET_EMULATOR${NC}"
  EMULATORS=("$TARGET_EMULATOR")
fi

# Check if APK exists, build if not
if [ ! -f "$APK_PATH" ]; then
  echo -e "${YELLOW}APK not found. Building project...${NC}"
  cd "$PROJECT_DIR" && ./gradlew assembleDebug
fi

# Create report directory
mkdir -p "$REPORT_DIR"

# Function to wait for emulator to be ready
wait_for_emulator() {
  local emulator_name="$1"
  local timeout=180
  local elapsed=0

  echo -e "${YELLOW}Waiting for emulator '$emulator_name' to boot...${NC}"

  while [ $elapsed -lt $timeout ]; do
    local boot_completed=$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r\n')
    if [ "$boot_completed" = "1" ]; then
      echo -e "${GREEN}Emulator booted in ${elapsed}s${NC}"
      sleep 5
      return 0
    fi
    sleep 5
    elapsed=$((elapsed + 5))
    echo -n "."
  done

  echo -e "\n${RED}Emulator boot timeout after ${timeout}s${NC}"
  return 1
}

# Function to get adb device serial for an emulator
get_adb_serial() {
  local emulator_name="$1"
  # Get the device serial for the running emulator matching our AVD
  adb devices | grep "emulator-" | head -1 | awk '{print $1}'
}

# Function to run a single test
run_test() {
  local emulator="$1"
  local test_name="$2"
  local device_serial="$3"
  local test_file="$SCRIPT_DIR/${test_name}.yml"
  local report_file="$REPORT_DIR/${emulator}_${test_name}.xml"

  if [ ! -f "$test_file" ]; then
    echo -e "${RED}  Test file not found: $test_file${NC}"
    return 1
  fi

  echo -e "${BLUE}  Running: $test_name on $emulator ($device_serial)${NC}"
  cd "$PROJECT_DIR" && maestro test \
    --format junit \
    --output "$report_file" \
    "$test_file" 2>&1

  local result=$?
  if [ $result -eq 0 ]; then
    echo -e "${GREEN}  ✓ $test_name passed${NC}"
  else
    echo -e "${RED}  ✗ $test_name failed (exit code: $result)${NC}"
  fi
  return $result
}

# Function to test on a single emulator
test_on_emulator() {
  local emulator="$1"
  local results=()
  local passed=0
  local failed=0

  echo -e "\n${BLUE}========================================${NC}"
  echo -e "${BLUE}  Testing on: $emulator${NC}"
  echo -e "${BLUE}========================================${NC}"

  # Kill any existing emulator
  adb emu kill 2>/dev/null || true
  sleep 3

  # Make sure no adb server conflicts
  adb kill-server 2>/dev/null || true
  sleep 2
  adb start-server 2>/dev/null || true
  sleep 2

  # Start emulator
  echo -e "${YELLOW}Starting emulator: $emulator${NC}"
  emulator -avd "$emulator" -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect -read-only &
  EMULATOR_PID=$!

  # Wait for boot
  if ! wait_for_emulator "$emulator"; then
    echo -e "${RED}Failed to boot $emulator, skipping${NC}"
    kill $EMULATOR_PID 2>/dev/null || true
    return 1
  fi

  # Get device serial
  DEVICE_SERIAL=$(get_adb_serial "$emulator")
  echo -e "${GREEN}Device serial: $DEVICE_SERIAL${NC}"

  # Install APK
  echo -e "${YELLOW}Installing APK...${NC}"
  adb -s "$DEVICE_SERIAL" install -r "$APK_PATH" 2>&1

  # Grant overlay permission (needed for floating player tests)
  adb -s "$DEVICE_SERIAL" shell appops set com.natkibe.videoplayerpro SYSTEM_ALERT_WINDOW allow 2>/dev/null || true

  # Run all tests
  for test in "${ALL_TESTS[@]}"; do
    if run_test "$emulator" "$test" "$DEVICE_SERIAL"; then
      passed=$((passed + 1))
    else
      failed=$((failed + 1))
    fi
    echo "---"
  done

  # Kill emulator
  echo -e "${YELLOW}Shutting down emulator...${NC}"
  adb emu kill 2>/dev/null || true
  kill $EMULATOR_PID 2>/dev/null || true
  sleep 3

  # Summary for this emulator
  echo -e "\n${BLUE}Results for $emulator:${NC}"
  echo -e "${GREEN}  Passed: $passed${NC}"
  echo -e "${RED}  Failed: $failed${NC}"
  echo -e "${BLUE}  Total:  $((passed + failed))${NC}"

  return $failed
}

# Main execution
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  VideoPlayerPro Maestro Test Runner${NC}"
echo -e "${BLUE}  $(date)${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
echo -e "Emulators: ${#EMULATORS[@]}"
echo -e "Test flows: ${#ALL_TESTS[@]}"
echo ""

TOTAL_PASSED=0
TOTAL_FAILED=0

for emulator in "${EMULATORS[@]}"; do
  test_on_emulator "$emulator"
  TOTAL_FAILED=$((TOTAL_FAILED + $?))
done

# Final summary
echo -e "\n${BLUE}========================================${NC}"
echo -e "${BLUE}  FINAL SUMMARY${NC}"
echo -e "${BLUE}========================================${NC}"
echo -e "Emulators tested: ${#EMULATORS[@]}"
echo -e "Total test flows: ${#ALL_TESTS[@]}"
echo -e "${GREEN}Total passed: $TOTAL_PASSED${NC}"
echo -e "${RED}Total failed: $TOTAL_FAILED${NC}"

if [ $TOTAL_FAILED -eq 0 ]; then
  echo -e "${GREEN}All tests passed!${NC}"
  exit 0
else
  echo -e "${RED}Some tests failed. Check reports in: $REPORT_DIR${NC}"
  exit 1
fi
