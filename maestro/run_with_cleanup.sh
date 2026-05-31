#!/usr/bin/env bash
# Wrapper script: kills MemCards processes, removes ADB forwards,
# deletes MemCards config.yaml, then runs the specified Maestro test flow.
# Usage: ./maestro/run_with_cleanup.sh <flow_file.yml> [flow_file2.yml ...]

set -euo pipefail

MAESTRO="/Users/natialex/.maestro/bin/maestro"

echo "=== Cleaning up MemCards interference ==="

# Kill any running MemCards Maestro processes
MEMCARDS_PIDS=$(ps aux | grep -i "maestro.*MemCards\|maestro.*memcards\|maestro.*01_home_seeded" | grep -v grep | awk '{print $2}' 2>/dev/null || true)
if [ -n "$MEMCARDS_PIDS" ]; then
  echo "Killing MemCards processes: $MEMCARDS_PIDS"
  kill -9 $MEMCARDS_PIDS 2>/dev/null || true
  sleep 1
fi

# Delete MemCards config.yaml to prevent restart
if [ -f "/Users/natialex/Dev/MemCards/maestro/config.yaml" ]; then
  echo "Deleting MemCards config.yaml"
  rm -f "/Users/natialex/Dev/MemCards/maestro/config.yaml"
fi

# Remove all ADB forwards
echo "Removing ADB forwards"
adb forward --remove-all 2>/dev/null || true

# Small wait for things to settle
sleep 2

echo "=== Running Maestro test(s) ==="

for flow in "$@"; do
  echo "--- Running: $flow ---"
  $MAESTRO test "$flow" 2>&1 || echo "FAILED: $flow"
done

echo "=== Done ==="
