#!/usr/bin/env bash
# Samples component memory and interval CPU (cores, not lifetime ps %).
# CPU = delta(utime+stime) / CLK_TCK / interval  → 1.0 means one full core.
#
# Memory:
#   rss_sum_mb  — sum of VmRSS (overcounts shared pages for multi-process PG).
#   pss_sum_mb  — sum of Pss from smaps_rollup when readable (best; same-user procs).
#   mem_est_mb  — deduplicated estimate: PSS if available, else anon+file+max(shmem).
set -uo pipefail

OUT="${1:-/tmp/load-test-resources.tsv}"
INTERVAL="${2:-1}"
DURATION="${3:-130}"
CPU_CORES_OUT="${CPU_CORES_OUT:-${1%.tsv}.cpu-cores.tsv}"

CLK_TCK="$(getconf CLK_TCK 2>/dev/null || echo 100)"

declare -A PREV_JIFFIES
declare -A PREV_CORE_BUSY
declare -A PREV_CORE_TOTAL
CORE_IDS=()

init_cpu_cores_file() {
  mapfile -t CORE_IDS < <(awk '/^cpu[0-9]+ / { print substr($1, 4) }' /proc/stat 2>/dev/null | sort -n)
  if ((${#CORE_IDS[@]} == 0)); then
    return 1
  fi
  {
    printf 'ts_epoch\tts'
    for core in "${CORE_IDS[@]}"; do printf '\tcpu%s' "$core"; done
    printf '\n'
  } > "$CPU_CORES_OUT"
}

echo -e "ts_epoch\tts\tcomponent\tprocs\trss_sum_mb\tpss_sum_mb\tmem_est_mb\tcpu_cores\tthreads\tload1\tmem_avail_mb" > "$OUT"
init_cpu_cores_file || true

read_jiffies() {
  local pid="$1" v
  v=$(awk '{
    i = index($0, ")")
    if (!i) { print 0; exit }
    n = split(substr($0, i + 2), f, " ")
    if (n >= 13) print f[12] + f[13]; else print 0
  }' "/proc/$pid/stat" 2>/dev/null || true)
  echo "${v:-0}"
}

read_status_field_kb() {
  local pid="$1" field="$2" v
  v=$(awk -v key="$field" '$1 == key ":" { print $2; exit }' "/proc/$pid/status" 2>/dev/null || true)
  echo "${v:-0}"
}

read_pss_kb() {
  local pid="$1" v
  [[ -r "/proc/$pid/smaps_rollup" ]] || { echo 0; return; }
  v=$(awk '/^Pss:/ { print $2; exit }' "/proc/$pid/smaps_rollup" 2>/dev/null || true)
  echo "${v:-0}"
}

is_java_pid() {
  local pid="$1"
  [[ -r "/proc/$pid/comm" ]] || return 1
  [[ "$(cat "/proc/$pid/comm" 2>/dev/null)" == "java" ]]
}

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
      if [[ "$pattern" != *private-chat-server* ]] && ! is_java_pid "$pid"; then
        continue
      fi
      [[ " $seen " == *" $pid "* ]] && continue
      seen+="$pid "
      echo "$pid"
    done < <(pgrep -f "$pattern" 2>/dev/null || true)
  done
}

find_postgres_pids() {
  local pm pattern="${PG_CLUSTER_PATTERN:-16/main}"
  pm=$(pgrep -f "[/]postgres .*-D" 2>/dev/null | head -1 || true)
  if [[ -n "$pm" ]]; then
    echo "$pm"
  fi
  pgrep -f "postgres:.*${pattern}" 2>/dev/null || true
}

collect_pids() {
  local component="$1" pattern="$2"
  local pid seen=""

  while IFS= read -r pid; do
    [[ -z "$pid" ]] && continue
    [[ " $seen " == *" $pid "* ]] && continue
    seen+="$pid "
    echo "$pid"
  done < <(
    case "$component" in
      app) find_app_pids ;;
      postgres) find_postgres_pids ;;
      redis)
        if [[ -n "$pattern" ]]; then pgrep -f "$pattern" 2>/dev/null || true
        else pgrep -x redis-server 2>/dev/null || true
        fi
        ;;
      nats) pgrep -x nats-server 2>/dev/null || true ;;
      k6) pgrep -x k6 2>/dev/null || true ;;
      *) pgrep -f "$pattern" 2>/dev/null || true ;;
    esac
  )
}

sample_component() {
  local ts_epoch="$1"
  local ts="$2"
  local component="$3"
  local pattern="$4"

  local pids
  pids=$(collect_pids "$component" "$pattern" | tr '\n' ' ')
  if [[ -z "${pids// }" ]]; then
    echo -e "${ts_epoch}\t${ts}\t${component}\t0\t0.0\t\t0.0\t0.000\t0\t\t" >> "$OUT"
    return
  fi

  local total_rss_kb=0 total_pss_kb=0 total_anon_kb=0 total_file_kb=0 max_shmem_kb=0
  local total_threads=0 total_jiffies=0 proc_count=0 pss_readable=0

  for pid in $pids; do
    [[ -r "/proc/$pid/status" ]] || continue
    local anon_kb file_kb shmem_kb rss_kb pss_kb
    anon_kb=$(read_status_field_kb "$pid" "RssAnon")
    file_kb=$(read_status_field_kb "$pid" "RssFile")
    shmem_kb=$(read_status_field_kb "$pid" "RssShmem")
    rss_kb=$(read_status_field_kb "$pid" "VmRSS")
    pss_kb=$(read_pss_kb "$pid")

    total_rss_kb=$((total_rss_kb + rss_kb))
    total_anon_kb=$((total_anon_kb + anon_kb))
    total_file_kb=$((total_file_kb + file_kb))
    if (( shmem_kb > max_shmem_kb )); then max_shmem_kb=$shmem_kb; fi
    if (( pss_kb > 0 )); then
      total_pss_kb=$((total_pss_kb + pss_kb))
      pss_readable=$((pss_readable + 1))
    fi
    total_threads=$((total_threads + $(read_status_field_kb "$pid" "Threads")))
    total_jiffies=$((total_jiffies + $(read_jiffies "$pid")))
    proc_count=$((proc_count + 1))
  done

  local rss_sum_mb pss_sum_mb="" mem_est_mb
  rss_sum_mb=$(awk -v kb="$total_rss_kb" 'BEGIN { printf "%.1f", kb / 1024 }')

  if (( pss_readable == proc_count && proc_count > 0 )); then
    pss_sum_mb=$(awk -v kb="$total_pss_kb" 'BEGIN { printf "%.1f", kb / 1024 }')
    mem_est_mb="$pss_sum_mb"
  elif (( proc_count == 1 )); then
    mem_est_mb="$rss_sum_mb"
  else
    mem_est_mb=$(awk -v anon="$total_anon_kb" -v file="$total_file_kb" -v shmem="$max_shmem_kb" \
      'BEGIN { printf "%.1f", (anon + file + shmem) / 1024 }')
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

  echo -e "${ts_epoch}\t${ts}\t${component}\t${proc_count}\t${rss_sum_mb}\t${pss_sum_mb}\t${mem_est_mb}\t${cpu_cores}\t${total_threads}\t${load1}\t${mem_avail_mb}" >> "$OUT"
}

# Per-core host CPU % from /proc/stat (0–100 per physical core).
sample_host_cpus() {
  local ts_epoch="$1"
  local ts="$2"

  ((${#CORE_IDS[@]})) || return 0
  [[ -r /proc/stat ]] || return 0

  local core busy total prev_b prev_t delta_b delta_t util
  declare -A util_pct=()

  while read -r core busy total; do
    [[ -n "$core" ]] || continue
    prev_b="${PREV_CORE_BUSY[$core]:-}"
    prev_t="${PREV_CORE_TOTAL[$core]:-}"
    util=""
    if [[ -n "$prev_b" ]]; then
      delta_b=$((busy - prev_b))
      delta_t=$((total - prev_t))
      if (( delta_t > 0 && delta_b >= 0 )); then
        util=$(awk -v b="$delta_b" -v t="$delta_t" 'BEGIN { printf "%.1f", b / t * 100 }')
      else
        util="0.0"
      fi
    fi
    PREV_CORE_BUSY[$core]=$busy
    PREV_CORE_TOTAL[$core]=$total
    util_pct[$core]="${util:-}"
  done < <(awk '/^cpu[0-9]+ / {
    busy = $2 + $3 + $4 + $7 + $8 + ($9 + 0)
    idle = $5 + $6
    print substr($1, 4), busy, busy + idle
  }' /proc/stat)

  {
    printf '%s\t%s' "$ts_epoch" "$ts"
    for core in "${CORE_IDS[@]}"; do
      printf '\t%s' "${util_pct[$core]:-}"
    done
    printf '\n'
  } >> "$CPU_CORES_OUT"
}

end=$((SECONDS + DURATION))
while (( SECONDS < end )); do
  ts_epoch=$(date +%s)
  ts=$(date +%H:%M:%S)
  sample_host_cpus "$ts_epoch" "$ts"
  sample_component "$ts_epoch" "$ts" "app" ""
  sample_component "$ts_epoch" "$ts" "postgres" ""
  sample_component "$ts_epoch" "$ts" "redis" ""
  sample_component "$ts_epoch" "$ts" "nats" ""
  sample_component "$ts_epoch" "$ts" "k6" ""
  sleep "$INTERVAL"
done
