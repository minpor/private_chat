#!/usr/bin/env bash
# Fair JVM vs native load test: clean DB, warmup, measured run — repeat per runtime.
# Default order (all): native first, then JVM.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

JAR="$ROOT/server/build/libs/private-chat-server.jar"
NATIVE="$ROOT/server/build/native/nativeCompile/private-chat-server"
PORT="8080"
PID=""
JVM_SUMMARY=""
NATIVE_SUMMARY=""
K6_EXIT=0

cleanup() {
  if [[ -n "$PID" ]] && kill -0 "$PID" 2>/dev/null; then
    kill "$PID" 2>/dev/null || true
    wait "$PID" 2>/dev/null || true
  fi
}
trap cleanup EXIT

stop_port() {
  local pids
  pids=$(lsof -ti ":$PORT" 2>/dev/null || true)
  if [[ -n "$pids" ]]; then
    kill $pids 2>/dev/null || true
    sleep 2
  fi
}

reset_data() {
  cleanup
  stop_port
  "$ROOT/load-tests/reset-db.sh"
}

start_server() {
  local mode="$1"
  local binary="$2"
  cleanup
  stop_port
  PID=""

  set -a
  # shellcheck disable=SC1091
  source "$ROOT/.env"
  set +a
  export MESSAGE_RATE_LIMIT=200000
  export SERVER_PORT="$PORT"
  export JWT_SECRET="${JWT_SECRET:-change-me-to-random-256-bit-string-min-32-chars}"

  if [[ "$mode" == "jvm" ]]; then
    local java_home="${RUNTIME_JAVA_HOME:-$HOME/.sdkman/candidates/java/25.0.2-graal}"
    nohup "$java_home/bin/java" -jar "$binary" >"/tmp/private-chat-$mode.log" 2>&1 &
  else
    nohup "$binary" >"/tmp/private-chat-$mode.log" 2>&1 &
  fi
  PID=$!

  for _ in $(seq 1 90); do
    if curl -sf "http://127.0.0.1:${PORT}/actuator/health" >/dev/null 2>&1; then
      echo "[$mode] healthy (PID $PID)"
      return 0
    fi
    if ! kill -0 "$PID" 2>/dev/null; then
      echo "[$mode] server exited. Log:"
      tail -40 "/tmp/private-chat-$mode.log"
      return 1
    fi
    sleep 1
  done
  echo "[$mode] health timeout"
  return 1
}

run_k6_warmup() {
  local mode="$1"
  export BASE_URL="http://127.0.0.1:${PORT}"
  export K6_PHASE=warmup
  echo "[$mode] k6 warmup (${WARMUP_DURATION:-30s} @ ${WARMUP_TPS:-200} TPS) ..."
  k6 run "$ROOT/load-tests/message-write.k6.js"
}

run_measured() {
  local suffix="$1"
  export RESULT_SUFFIX="$suffix"
  export APP_SERVER_PID="$PID"
  export K6_PHASE=measured
  export JVM_WARMUP_SEC=0
  export MONITOR_SEC="${MONITOR_SEC:-150}"
  export BASE_URL="http://127.0.0.1:${PORT}"
  "$ROOT/load-tests/run-with-monitoring.sh"
  cp "$ROOT/load-tests/results/latest-summary.json" \
    "$ROOT/load-tests/results/latest-${suffix}-summary.json"
  cp "$ROOT/load-tests/results/latest-summary.md" \
    "$ROOT/load-tests/results/latest-${suffix}-summary.md"
}

run_mode() {
  local mode="$1"
  local binary="$2"

  echo ""
  echo "========== ${mode^^}: reset DB + Redis =========="
  reset_data

  echo ""
  echo "========== ${mode^^}: start server =========="
  start_server "$mode" "$binary"

  local idle_sec="${IDLE_WARMUP_SEC:-15}"
  echo ""
  echo "========== ${mode^^}: idle warmup (${idle_sec}s) =========="
  sleep "$idle_sec"

  echo ""
  echo "========== ${mode^^}: k6 warmup =========="
  run_k6_warmup "$mode"

  echo ""
  echo "========== ${mode^^}: measured load test =========="
  if ! run_measured "$mode"; then
    K6_EXIT=1
    echo "[$mode] measured run: k6 thresholds failed (results saved anyway)"
  fi
}

[[ -f "$JAR" ]] || { echo "Missing $JAR — run ./gradlew :server:bootJar"; exit 1; }
[[ -f "$NATIVE" ]] || { echo "Missing $NATIVE — run ./gradlew :server:nativeCompile"; exit 1; }

chmod +x "$ROOT/load-tests/reset-db.sh"
chmod +x "$ROOT/load-tests/run-with-monitoring.sh"

RUN_TARGET="${1:-all}"

case "$RUN_TARGET" in
  jvm)
    run_mode jvm "$JAR"
    ;;
  native)
    run_mode native "$NATIVE"
    ;;
  all)
    run_mode native "$NATIVE"
    run_mode jvm "$JAR"
    ;;
  *)
    echo "Usage: $0 [all|jvm|native]" >&2
    exit 2
    ;;
esac

JVM_SUMMARY="$ROOT/load-tests/results/latest-jvm-summary.json"
NATIVE_SUMMARY="$ROOT/load-tests/results/latest-native-summary.json"
COMPARE_HTML="$ROOT/load-tests/results/jvm-vs-native-comparison.html"

python3 "$ROOT/load-tests/analyze-resources.py" --compare \
  "$JVM_SUMMARY" "$NATIVE_SUMMARY" \
  --out "$COMPARE_HTML"

cp "$COMPARE_HTML" "$ROOT/load-tests/results/latest-comparison.html"
cp "$ROOT/load-tests/results/jvm-vs-native-comparison.md" \
  "$ROOT/load-tests/results/latest-comparison.md"

echo ""
cat "$ROOT/load-tests/results/jvm-vs-native-comparison.md"
echo ""
echo "HTML: $COMPARE_HTML"

exit "$K6_EXIT"
