#!/usr/bin/env bash
# Aggressive wrapper: starts a background watcher that continuously kills
# MemCards Maestro processes, then runs our test.
# Usage: ./maestro/run_with_watcher.sh <flow_file.yml> [flow_file2.yml ...]

set -euo pipefail

MAESTRO="/Users/natialex/.maestro/bin/maestro"
WATCHER_PID=""

cleanup() {
  if [ -n "$WATCHER_PID" ]; then
    kill "$WATCHER_PID" 2>/dev/null || true
  fi
}
trap cleanup EXIT

# Start background watcher that kills MemCards processes every 2 seconds
watchdog() {
  while true; do
    # Kill MemCards processes on host
    PIDS=$(ps aux | grep -i "maestro.*MemCards\|maestro.*memcards\|maestro.*01_home_seeded" | grep -v grep | awk '{print $2}' 2>/dev/null || true)
    if [ -n "$PIDS" ]; then
      kill -9 $PIDS 2>/dev/null || true
    fi
    # Kill Maestro driver on emulator
    DRIVER_PID=$(adb shell "ps -A 2>/dev/null | grep maestro | awk '{print \$2}'" 2>/dev/null || echo "")
    if [ -n "$DRIVER_PID" ]; then
      adb shell "kill -9 $DRIVER_PID" 2>/dev/null || true
    fi
    # Remove ADB forwards
    adb forward --remove-all 2>/dev/null || true
    # Delete MemCards config
    rm -f "/Users/natialex/Dev/MemCards/maestro/config.yaml" 2>/dev/null || true
    sleep 2
  done
}

echo "=== Starting background watcher ==="
watchdog &
WATCHER_PID=$!

# Give watcher time to clean up
sleep 3

echo "=== Running Maestro test(s) ==="

for flow in "$@"; do
  echo "--- Running: $flow ---"
  $MAESTRO test "$flow" 2>&1 || echo "FAILED: $flow"
done

echo "=== Done ==="
