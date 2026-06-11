#!/usr/bin/env bash
# Runs k6 load test with resource monitoring and saves artifacts under load-tests/results/.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOAD_DIR="$ROOT/load-tests"
STAMP="$(date +%Y-%m-%dT%H-%M-%S)${RESULT_SUFFIX:+-${RESULT_SUFFIX}}"
OUT_DIR="$LOAD_DIR/results/$STAMP"
TMP_TSV="$(mktemp /tmp/load-test-resources.XXXXXX.tsv)"
TMP_K6="$(mktemp /tmp/load-test-k6.XXXXXX.log)"
K6_PHASE="${K6_PHASE:-all}"
MONITOR_SEC="${MONITOR_SEC:-}"
JVM_WARMUP_SEC="${JVM_WARMUP_SEC:-15}"
K6_ARGS="${K6_ARGS:-}"

if [[ -z "$MONITOR_SEC" ]]; then
  if [[ "$K6_PHASE" == "measured" ]]; then
    MONITOR_SEC=135
  else
    MONITOR_SEC=170
  fi
fi
export K6_PHASE
# Optional: pin app process (set by compare-jvm-native.sh when server PID is known).
export APP_SERVER_PID="${APP_SERVER_PID:-}"

mkdir -p "$OUT_DIR" "$LOAD_DIR/results"

echo "Results dir: $OUT_DIR"
echo "Starting monitor for ${MONITOR_SEC}s ..."
"$LOAD_DIR/monitor-resources.sh" "$TMP_TSV" 1 "$MONITOR_SEC" &
MON_PID=$!

sleep 3
if [[ "$JVM_WARMUP_SEC" -gt 0 ]]; then
  echo "JVM idle warmup: ${JVM_WARMUP_SEC}s ..."
  sleep "$JVM_WARMUP_SEC"
fi
case "$K6_PHASE" in
  warmup) echo "Starting k6 warmup (${WARMUP_DURATION:-30s} @ ${WARMUP_TPS:-200} TPS) ..." ;;
  measured) echo "Starting k6 measured (${DURATION:-2m} @ ${TARGET_TPS:-2000} TPS) ..." ;;
  *) echo "Starting k6 (warmup ${WARMUP_DURATION:-30s} @ ${WARMUP_TPS:-200} TPS, then measured) ..." ;;
esac
set +e
k6 run "$LOAD_DIR/message-write.k6.js" $K6_ARGS 2>&1 | tee "$TMP_K6"
K6_EXIT=${PIPESTATUS[0]}
set -e

wait "$MON_PID"

cp "$TMP_TSV" "$OUT_DIR/samples.tsv"
cp "$TMP_K6" "$OUT_DIR/k6.log"

python3 "$LOAD_DIR/analyze-resources.py" "$OUT_DIR/samples.tsv" \
  --k6-log "$OUT_DIR/k6.log" \
  --out-dir "$OUT_DIR" \
  --nproc "$(nproc)"

ln -sfn "$STAMP" "$LOAD_DIR/results/latest"
cp "$OUT_DIR/summary.md" "$LOAD_DIR/results/latest-summary.md"
cp "$OUT_DIR/summary.json" "$LOAD_DIR/results/latest-summary.json"

rm -f "$TMP_TSV" "$TMP_K6"

echo ""
echo "Saved:"
echo "  $OUT_DIR/samples.tsv"
echo "  $OUT_DIR/k6.log"
echo "  $OUT_DIR/summary.md"
echo "  $OUT_DIR/summary.json"
echo "  $LOAD_DIR/results/latest-summary.md"

exit "$K6_EXIT"
