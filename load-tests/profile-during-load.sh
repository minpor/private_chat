#!/usr/bin/env bash
# CPU flame graph with async-profiler during a load-test run.
# Usage: PROFILE=1 ./load-tests/run-with-monitoring.sh
#    or: ./load-tests/profile-during-load.sh <app-pid> <output-dir> <duration-sec>
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TOOLS_DIR="$ROOT/load-tests/tools/async-profiler"
ASPROF="${ASPROF:-$TOOLS_DIR/bin/asprof}"
AP_VERSION="${ASYNC_PROFILER_VERSION:-3.0}"

APP_PID="${1:-${APP_SERVER_PID:-}}"
OUT_DIR="${2:-}"
DURATION_SEC="${3:-${PROFILE_DURATION_SEC:-120}}"

if [[ -z "$APP_PID" ]]; then
  APP_PID=$(pgrep -f 'private-chat-server' | head -1 || true)
fi

if [[ -z "$APP_PID" ]]; then
  echo "profile-during-load: no app PID (set APP_SERVER_PID or start server)" >&2
  exit 1
fi

if [[ ! -x "$ASPROF" ]]; then
  echo "Downloading async-profiler ${AP_VERSION} ..."
  mkdir -p "$ROOT/load-tests/tools"
  tmp="$(mktemp -d)"
  curl -fsSL "https://github.com/async-profiler/async-profiler/releases/download/v${AP_VERSION}/async-profiler-${AP_VERSION}-linux-x64.tar.gz" \
    | tar -xz -C "$tmp"
  rm -rf "$TOOLS_DIR"
  mv "$tmp/async-profiler-${AP_VERSION}-linux-x64" "$TOOLS_DIR"
  rm -rf "$tmp"
fi

if [[ -z "$OUT_DIR" ]]; then
  OUT_DIR="$ROOT/load-tests/results/profile-$(date +%Y-%m-%dT%H-%M-%S)"
fi
mkdir -p "$OUT_DIR"

COLLAPSED="$OUT_DIR/flamegraph.collapsed"
HTML="$OUT_DIR/flamegraph.html"

echo "Profiling PID $APP_PID for ${DURATION_SEC}s -> $OUT_DIR"
"$ASPROF" -d "$DURATION_SEC" -f "$HTML" -e cpu "$APP_PID"
"$ASPROF" -d 1 -f "$COLLAPSED" -e cpu "$APP_PID" 2>/dev/null || true

echo "Profile artifacts:"
echo "  $HTML"
echo "  $COLLAPSED"
