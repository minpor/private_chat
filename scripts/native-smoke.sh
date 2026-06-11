#!/usr/bin/env bash
# Smoke test for JVM JAR or GraalVM native binary.
# Usage: ./scripts/native-smoke.sh [path-to-executable-or-jar]
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [[ -f "$ROOT/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$ROOT/.env"
  set +a
fi

BINARY="${1:-$ROOT/server/build/native/nativeCompile/private-chat-server}"
PORT="${SMOKE_PORT:-18080}"
BASE="http://127.0.0.1:${PORT}"
LOG="/tmp/private-chat-smoke-$$.log"
PID=""

cleanup() {
  if [[ -n "$PID" ]] && kill -0 "$PID" 2>/dev/null; then
    kill "$PID" 2>/dev/null || true
    wait "$PID" 2>/dev/null || true
  fi
}
trap cleanup EXIT

start_server() {
  export SERVER_PORT="$PORT"
  export JWT_SECRET="${JWT_SECRET:-change-me-to-random-256-bit-string-min-32-chars}"

  local start_ms
  start_ms=$(date +%s%3N)

  if [[ "$BINARY" == *.jar ]]; then
    java -jar "$BINARY" >"$LOG" 2>&1 &
  else
    "$BINARY" >"$LOG" 2>&1 &
  fi
  PID=$!

  for _ in $(seq 1 60); do
    if curl -sf "$BASE/actuator/health" >/dev/null 2>&1; then
      local ready_ms
      ready_ms=$(date +%s%3N)
      echo "cold_start_ms=$((ready_ms - start_ms))"
      return 0
    fi
    if ! kill -0 "$PID" 2>/dev/null; then
      echo "Server exited before becoming healthy. Log:"
      cat "$LOG"
      return 1
    fi
    sleep 0.5
  done

  echo "Timeout waiting for health. Log:"
  tail -50 "$LOG"
  return 1
}

assert_http() {
  local method="$1"
  local url="$2"
  local expected="$3"
  local body="${4:-}"
  local code

  if [[ -n "$body" ]]; then
    code=$(curl -sf -o /tmp/smoke-body-$$.json -w "%{http_code}" -X "$method" \
      -H "Content-Type: application/json" -d "$body" "$url")
  else
    code=$(curl -sf -o /tmp/smoke-body-$$.json -w "%{http_code}" -X "$method" "$url")
  fi

  if [[ "$code" != "$expected" ]]; then
    echo "FAIL $method $url expected HTTP $expected got $code"
    cat /tmp/smoke-body-$$.json 2>/dev/null || true
    return 1
  fi
  echo "OK   $method $url -> $expected"
}

echo "Binary: $BINARY"
echo "Port:   $PORT"

start_server

assert_http GET "$BASE/actuator/health" 200
assert_http GET "$BASE/actuator/prometheus" 200

USER="smoke_$(date +%s)"
assert_http POST "$BASE/api/v1/auth/register" 201 \
  "{\"username\":\"$USER\",\"password\":\"secret12345\",\"displayName\":\"Smoke\"}"

ACCESS=$(jq -r '.accessToken' /tmp/smoke-body-$$.json)

code=$(curl -sf -o /tmp/smoke-body-$$.json -w "%{http_code}" \
  -H "Authorization: Bearer $ACCESS" "$BASE/api/v1/users/me")
if [[ "$code" != "200" ]]; then
  echo "FAIL authenticated /users/me expected 200 got $code"
  exit 1
fi
echo "OK   GET /api/v1/users/me (authenticated) -> 200"

if command -v ps >/dev/null 2>&1 && [[ -n "$PID" ]]; then
  rss_kb=$(ps -o rss= -p "$PID" 2>/dev/null | tr -d ' ')
  if [[ -n "$rss_kb" ]]; then
    echo "rss_mb=$((rss_kb / 1024))"
  fi
fi

echo "Smoke test passed."
