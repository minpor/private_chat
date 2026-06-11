#!/usr/bin/env bash
# Samples component memory and interval CPU (cores, not lifetime ps %).
# CPU = delta(utime+stime) / CLK_TCK / interval  → 1.0 means one full core.
set -uo pipefail

OUT="${1:-/tmp/load-test-resources.tsv}"
INTERVAL="${2:-1}"
DURATION="${3:-130}"

PG_SHARED_BUFFERS_MB="${PG_SHARED_BUFFERS_MB:-1024}"
PG_PRIVATE_MB_PER_CONN="${PG_PRIVATE_MB_PER_CONN:-20}"
NPROC="$(nproc 2>/dev/null || echo 1)"
CLK_TCK="$(getconf CLK_TCK 2>/dev/null || echo 100)"

declare -A PREV_JIFFIES

echo -e "ts_epoch\tts\tcomponent\tprocs\trss_sum_mb\tmem_est_mb\tcpu_cores\tthreads\tload1\tmem_avail_mb" > "$OUT"

read_jiffies() {
  local pid="$1"
  awk '{print $14 + $15}' "/proc/$pid/stat" 2>/dev/null || echo 0
}

read_rss_kb() {
  local pid="$1"
  awk '/^VmRSS:/ {print $2; exit}' "/proc/$pid/status" 2>/dev/null || echo 0
}

read_threads() {
  local pid="$1"
  awk '/^Threads:/ {print $2; exit}' "/proc/$pid/status" 2>/dev/null || echo 0
}

estimate_postgres_mem_mb() {
  local rss_sum_mb="$1"
  local backends
  backends=$(pgrep -fc "postgres: 16/main: private_chat private_chat" 2>/dev/null || echo 0)
  if (( backends > 0 )); then
    awk -v sb="$PG_SHARED_BUFFERS_MB" -v b="$backends" -v p="$PG_PRIVATE_MB_PER_CONN" \
      'BEGIN { printf "%.1f", sb + b * p }'
  else
    echo "$rss_sum_mb"
  fi
}

is_java_pid() {
  local pid="$1"
  [[ -r "/proc/$pid/comm" ]] || return 1
  [[ "$(cat "/proc/$pid/comm" 2>/dev/null)" == "java" ]]
}

# Spring Boot: java -jar, GraalVM native binary, or ./gradlew bootRun.
find_app_pids() {
  if [[ -n "${APP_SERVER_PID:-}" ]] && kill -0 "$APP_SERVER_PID" 2>/dev/null; then
    echo "$APP_SERVER_PID"
    return 0
  fi

  local pattern pid seen=""
  local patterns=(
    "private-chat-server\\.jar"
    "nativeCompile/private-chat-server"
    "chat\\.privatechat\\.PrivateChatApplicationKt"
    "chat\\.privatechat\\.PrivateChatApplication"
  )
  if [[ -n "${APP_MONITOR_PATTERN:-}" ]]; then
    patterns=("$APP_MONITOR_PATTERN")
  fi
  for pattern in "${patterns[@]}"; do
    while IFS= read -r pid; do
      [[ -z "$pid" ]] && continue
      if [[ "$pattern" == *private-chat-server* ]]; then
        :
      elif ! is_java_pid "$pid"; then
        continue
      fi
      [[ " $seen " == *" $pid "* ]] && continue
      seen+="$pid "
      echo "$pid"
    done < <(pgrep -f "$pattern" 2>/dev/null || true)
  done
}

sample_component() {
  local ts_epoch="$1"
  local ts="$2"
  local component="$3"
  local pattern="$4"

  local pids
  if [[ "$component" == "app" ]]; then
    pids=$(find_app_pids | tr '\n' ' ')
  else
    pids=$(pgrep -f "$pattern" 2>/dev/null || true)
  fi
  if [[ -z "$pids" ]]; then
    echo -e "${ts_epoch}\t${ts}\t${component}\t0\t0.0\t0.0\t0.000\t0\t\t" >> "$OUT"
    return
  fi

  local total_rss_kb=0 total_threads=0 total_jiffies=0 proc_count=0
  for pid in $pids; do
    [[ -r "/proc/$pid/status" ]] || continue
    total_rss_kb=$((total_rss_kb + $(read_rss_kb "$pid")))
    total_threads=$((total_threads + $(read_threads "$pid")))
    total_jiffies=$((total_jiffies + $(read_jiffies "$pid")))
    proc_count=$((proc_count + 1))
  done

  local rss_sum_mb
  rss_sum_mb=$(awk -v kb="$total_rss_kb" 'BEGIN { printf "%.1f", kb / 1024 }')

  local mem_est_mb="$rss_sum_mb"
  if [[ "$component" == "postgres" ]]; then
    mem_est_mb=$(estimate_postgres_mem_mb "$rss_sum_mb")
  fi

  local cpu_cores="0.000"
  local prev="${PREV_JIFFIES[$component]:-}"
  if [[ -n "$prev" ]]; then
    local delta=$((total_jiffies - prev))
    if (( delta < 0 )); then delta=0; fi
    cpu_cores=$(awk -v d="$delta" -v tck="$CLK_TCK" -v iv="$INTERVAL" \
      'BEGIN { if (tck * iv > 0) printf "%.3f", d / tck / iv; else print "0.000" }')
  fi
  PREV_JIFFIES[$component]=$total_jiffies

  local load1="" mem_avail_mb=""
  if [[ -r /proc/loadavg ]]; then
    load1=$(awk '{print $1}' /proc/loadavg)
  fi
  if [[ -r /proc/meminfo ]]; then
    mem_avail_mb=$(awk '/^MemAvailable:/ {printf "%.0f", $2/1024; exit}' /proc/meminfo)
  fi

  echo -e "${ts_epoch}\t${ts}\t${component}\t${proc_count}\t${rss_sum_mb}\t${mem_est_mb}\t${cpu_cores}\t${total_threads}\t${load1}\t${mem_avail_mb}" >> "$OUT"
}

end=$((SECONDS + DURATION))
while (( SECONDS < end )); do
  ts_epoch=$(date +%s)
  ts=$(date +%H:%M:%S)
  sample_component "$ts_epoch" "$ts" "app" ""
  sample_component "$ts_epoch" "$ts" "postgres" "postgres: 16/main"
  sample_component "$ts_epoch" "$ts" "redis" "redis-server 127.0.0.1:6379"
  sample_component "$ts_epoch" "$ts" "nats" "nats-server"
  sample_component "$ts_epoch" "$ts" "k6" "k6 run"
  sleep "$INTERVAL"
done
