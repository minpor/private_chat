#!/usr/bin/env bash
# Compare cold start and RSS between JVM JAR and GraalVM native binary.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

JAR="$ROOT/server/build/libs/private-chat-server.jar"
NATIVE="$ROOT/server/build/native/nativeCompile/private-chat-server"

if [[ ! -f "$JAR" ]]; then
  echo "Building JVM JAR..."
  ./gradlew :server:bootJar -q
fi

if [[ ! -f "$NATIVE" ]]; then
  echo "Building native binary..."
  ./gradlew :server:nativeCompile -q
fi

echo "=== JVM ==="
SMOKE_PORT=18081 "$ROOT/scripts/native-smoke.sh" "$JAR" | tee /tmp/smoke-jvm.txt

echo ""
echo "=== Native ==="
SMOKE_PORT=18082 "$ROOT/scripts/native-smoke.sh" "$NATIVE" | tee /tmp/smoke-native.txt

echo ""
echo "=== Summary ==="
grep -E 'cold_start_ms|rss_mb' /tmp/smoke-jvm.txt | sed 's/^/jvm: /'
grep -E 'cold_start_ms|rss_mb' /tmp/smoke-native.txt | sed 's/^/native: /'
