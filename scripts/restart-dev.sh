#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MODE="${1:-both}"
SERVER_PORT="${SERVER_PORT:-8080}"
CLIENT_PORT="${CLIENT_PORT:-5173}"
LOG_DIR="${TMPDIR:-/tmp}/private-chat-dev"
SERVER_LOG="$LOG_DIR/server.log"
CLIENT_LOG="$LOG_DIR/client.log"
SERVER_PID_FILE="$LOG_DIR/server.pid"
CLIENT_PID_FILE="$LOG_DIR/client.pid"

mkdir -p "$LOG_DIR"

stop_port() {
  local port="$1"
  local pids
  pids="$(lsof -ti:"$port" 2>/dev/null || true)"
  if [[ -n "$pids" ]]; then
    echo "Stopping process(es) on port $port: $pids"
    kill $pids 2>/dev/null || true
    sleep 1
    pids="$(lsof -ti:"$port" 2>/dev/null || true)"
    if [[ -n "$pids" ]]; then
      kill -9 $pids 2>/dev/null || true
    fi
  fi
}

load_env() {
  if [[ -f "$ROOT/.env" ]]; then
    set -a
    # shellcheck disable=SC1091
    source "$ROOT/.env"
    set +a
  fi
}

resolve_java_home() {
  local script="$HOME/.cursor/skills/gradle-java-home/scripts/resolve-java-home.sh"
  local resolved=""

  if [[ -x "$script" ]]; then
    resolved="$("$script" "$ROOT" 2>/dev/null || true)"
    if [[ -n "$resolved" && -x "$resolved/bin/java" ]]; then
      export JAVA_HOME="$resolved"
      export PATH="$JAVA_HOME/bin:$PATH"
      return 0
    fi
  fi

  for fallback in \
    "$HOME/.sdkman/candidates/java/25.0.2-graal" \
    "$HOME/.sdkman/candidates/java/21.0.10-graal"; do
    if [[ -x "$fallback/bin/java" ]]; then
      export JAVA_HOME="$fallback"
      export PATH="$JAVA_HOME/bin:$PATH"
      return 0
    fi
  done

  echo "No suitable JDK found. Install JDK 21+ or set JAVA_HOME." >&2
  return 1
}

start_server() {
  stop_port "$SERVER_PORT"
  load_env
  resolve_java_home

  echo "Starting server on :$SERVER_PORT (log: $SERVER_LOG)"
  (
    cd "$ROOT"
    exec ./gradlew :server:bootRun
  ) >"$SERVER_LOG" 2>&1 &
  echo $! >"$SERVER_PID_FILE"

  for _ in $(seq 1 90); do
    if curl -sf "http://localhost:$SERVER_PORT/actuator/health" >/dev/null 2>&1; then
      echo "Server is up: http://localhost:$SERVER_PORT/actuator/health"
      return 0
    fi
    if ! kill -0 "$(cat "$SERVER_PID_FILE")" 2>/dev/null; then
      echo "Server failed to start. Last log lines:"
      tail -n 40 "$SERVER_LOG" || true
      return 1
    fi
    sleep 2
  done

  echo "Server did not become healthy in time. Last log lines:"
  tail -n 40 "$SERVER_LOG" || true
  return 1
}

start_client() {
  stop_port "$CLIENT_PORT"

  if [[ ! -d "$ROOT/client/node_modules" ]]; then
    echo "Installing client dependencies..."
    (cd "$ROOT/client" && npm install)
  fi

  echo "Starting client on :$CLIENT_PORT (log: $CLIENT_LOG)"
  (
    cd "$ROOT/client"
    exec npm run dev -- --host 127.0.0.1 --port "$CLIENT_PORT"
  ) >"$CLIENT_LOG" 2>&1 &
  echo $! >"$CLIENT_PID_FILE"

  for _ in $(seq 1 30); do
    if curl -sf "http://127.0.0.1:$CLIENT_PORT" >/dev/null 2>&1; then
      echo "Client is up: http://localhost:$CLIENT_PORT"
      return 0
    fi
    if ! kill -0 "$(cat "$CLIENT_PID_FILE")" 2>/dev/null; then
      echo "Client failed to start. Last log lines:"
      tail -n 40 "$CLIENT_LOG" || true
      return 1
    fi
    sleep 1
  done

  echo "Client did not respond in time. Last log lines:"
  tail -n 40 "$CLIENT_LOG" || true
  return 1
}

case "$MODE" in
  server)
    start_server
    ;;
  client)
    start_client
    ;;
  both)
    start_server
    start_client
    ;;
  *)
    echo "Usage: $0 [server|client|both]" >&2
    exit 1
    ;;
esac

echo "PIDs: server=$(cat "$SERVER_PID_FILE" 2>/dev/null || echo -), client=$(cat "$CLIENT_PID_FILE" 2>/dev/null || echo -)"
