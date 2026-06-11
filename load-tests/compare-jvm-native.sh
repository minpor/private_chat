#!/usr/bin/env bash
# Fair JVM vs native load test: same warmup protocol (BASELINE.md).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

JAR="$ROOT/server/build/libs/private-chat-server.jar"
NATIVE="$ROOT/server/build/native/nativeCompile/private-chat-server"
PORT="8080"
PID=""
JVM_SUMMARY=""
NATIVE_SUMMARY=""

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

run_load() {
  local suffix="$1"
  export RESULT_SUFFIX="$suffix"
  export MONITOR_SEC=170
  export JVM_WARMUP_SEC=15
  export BASE_URL="http://127.0.0.1:${PORT}"
  "$ROOT/load-tests/run-with-monitoring.sh"
  JVM_SUMMARY="$ROOT/load-tests/results/latest-summary.json"
  cp "$ROOT/load-tests/results/latest-summary.json" \
    "$ROOT/load-tests/results/latest-${suffix}-summary.json"
  cp "$ROOT/load-tests/results/latest-summary.md" \
    "$ROOT/load-tests/results/latest-${suffix}-summary.md"
}

[[ -f "$JAR" ]] || { echo "Missing $JAR — run ./gradlew :server:bootJar"; exit 1; }
[[ -f "$NATIVE" ]] || { echo "Missing $NATIVE — run ./gradlew :server:nativeCompile"; exit 1; }

chmod +x "$ROOT/load-tests/run-with-monitoring.sh"

echo "=== JVM ==="
start_server jvm "$JAR"
run_load jvm || true
JVM_SUMMARY="$ROOT/load-tests/results/latest-jvm-summary.json"

echo ""
echo "=== Native ==="
start_server native "$NATIVE"
run_load native || true
NATIVE_SUMMARY="$ROOT/load-tests/results/latest-native-summary.json"

python3 - "$JVM_SUMMARY" "$NATIVE_SUMMARY" "$ROOT/load-tests/results/jvm-vs-native-comparison.md" <<'PY'
import json
import re
import sys
from pathlib import Path

def load(path):
    p = Path(path)
    if not p.exists():
        return {}
    return json.loads(p.read_text())

def parse_k6(text):
    m = {}
    for line in text.splitlines():
        if "phase:measured" in line and "http_req_duration" in line:
            p95 = re.search(r"p\(95\)=([^\s]+)", line)
            if p95:
                m["p95"] = p95.group(1)
        if "phase:measured" in line and "http_req_failed" in line:
            rate = re.search(r":\s+([\d.]+%)", line)
            if rate:
                m["errors"] = rate.group(1)
        if line.strip().startswith("iterations"):
            parts = line.split()
            if len(parts) >= 2:
                m["iterations"] = parts[1]
    return m

jvm = load(sys.argv[1])
native = load(sys.argv[2])
jk = parse_k6(jvm.get("k6_tail", ""))
nk = parse_k6(native.get("k6_tail", ""))

def app_stats(data):
    c = data.get("components", {}).get("app", {})
    return c.get("cpu_cores_peak", "n/a"), c.get("mem_mb_peak", "n/a")

jcpu, jmem = app_stats(jvm)
ncpu, nmem = app_stats(native)

lines = [
    "# JVM vs Native (warmup protocol)",
    "",
    "Protocol: JVM idle **15s**, k6 warmup **30s @ 200 TPS**, measured **2m @ 2000 TPS**.",
    "",
    "| Metric | JVM | Native |",
    "|--------|-----|--------|",
    f"| App CPU peak (cores) | {jcpu} | {ncpu} |",
    f"| App RAM peak (MB) | {jmem} | {nmem} |",
    f"| p95 latency (measured) | {jk.get('p95', 'n/a')} | {nk.get('p95', 'n/a')} |",
    f"| Error rate (measured) | {jk.get('errors', 'n/a')} | {nk.get('errors', 'n/a')} |",
    "",
    "See `latest-jvm-summary.md` and `latest-native-summary.md` for full reports.",
]
Path(sys.argv[3]).write_text("\n".join(lines) + "\n")
print(sys.argv[3])
PY

echo ""
cat "$ROOT/load-tests/results/jvm-vs-native-comparison.md"
