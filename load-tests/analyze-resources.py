#!/usr/bin/env python3
"""Build human-readable summary from monitor-resources.tsv."""

from __future__ import annotations

import argparse
import csv
import json
import re
import statistics
import sys
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path

LABELS = {
    "app": "Приложение (Spring Boot)",
    "postgres": "PostgreSQL",
    "redis": "Redis",
    "nats": "NATS (очередь)",
    "k6": "k6 (нагрузчик)",
}

INFRA = ("app", "postgres", "redis", "nats")

CHART_COLORS = {
    "app": "#2563eb",
    "postgres": "#7c3aed",
    "redis": "#dc2626",
    "nats": "#d97706",
    "k6": "#059669",
    "infra_cpu": "#0f172a",
    "infra_mem": "#0369a1",
    "load1": "#b45309",
    "mem_avail": "#64748b",
}

RUNTIME_COLORS = {
    "jvm": "#2563eb",
    "native": "#059669",
}


def load_rows(path: Path) -> list[dict[str, str]]:
    with path.open(newline="") as f:
        return list(csv.DictReader(f, delimiter="\t"))


def load_cpu_cores(path: Path | None) -> dict:
    if path is None or not path.exists():
        return {}

    with path.open(newline="") as f:
        rows = list(csv.DictReader(f, delimiter="\t"))
    if not rows:
        return {}

    core_cols = [c for c in rows[0].keys() if c.startswith("cpu")]
    if not core_cols:
        return {}

    core_ids = [c[3:] for c in core_cols]
    labels = [row.get("ts") or row.get("ts_epoch", "") for row in rows]
    util: list[list[float]] = []
    for col in core_cols:
        series: list[float] = []
        for row in rows:
            raw = row.get(col, "")
            series.append(float(raw) if raw not in (None, "") else 0.0)
        util.append(series)

    return {"core_ids": core_ids, "labels": labels, "util_pct": util}


def summarize_cpu_cores(cores: dict) -> dict:
    if not cores:
        return {}

    util = cores["util_pct"]
    core_ids = cores["core_ids"]
    per_core: list[dict] = []

    for idx, core_id in enumerate(core_ids):
        series = util[idx]
        usable = series[1:] if len(series) > 1 else series
        peak = max(usable) if usable else 0.0
        peak_idx = series.index(peak) if series else 0
        per_core.append(
            {
                "core": int(core_id),
                "util_pct_peak": peak,
                "util_pct_peak_at": cores["labels"][peak_idx] if cores["labels"] else "",
                "util_pct_avg": statistics.mean(usable) if usable else 0.0,
            }
        )

    hottest = max(per_core, key=lambda x: x["util_pct_peak"])
    avg_all = statistics.mean(c["util_pct_avg"] for c in per_core) if per_core else 0.0

    return {
        "core_count": len(core_ids),
        "util_pct_avg_all": avg_all,
        "hottest_core": hottest["core"],
        "hottest_util_pct_peak": hottest["util_pct_peak"],
        "per_core": per_core,
    }


def fval(row: dict[str, str], key: str) -> float:
    try:
        raw = row.get(key)
        if raw is None or raw == "":
            return 0.0
        return float(raw)
    except ValueError:
        return 0.0


def summarize_component(rows: list[dict[str, str]]) -> dict:
    if not rows:
        return {}

    cpu = [fval(r, "cpu_cores") for r in rows]
    mem = [fval(r, "mem_est_mb") for r in rows]
    rss_raw = [fval(r, "rss_sum_mb") for r in rows]
    pss = [fval(r, "pss_sum_mb") for r in rows if fval(r, "pss_sum_mb") > 0]

    peak_cpu_idx = max(range(len(cpu)), key=lambda i: cpu[i])
    peak_mem_idx = max(range(len(mem)), key=lambda i: mem[i])

    out = {
        "samples": len(rows),
        "procs_peak": max(int(r.get("procs") or 0) for r in rows),
        "cpu_cores_peak": cpu[peak_cpu_idx],
        "cpu_cores_peak_at": rows[peak_cpu_idx]["ts"],
        "cpu_cores_avg": statistics.mean(cpu[1:] or cpu),
        "mem_mb_peak": mem[peak_mem_idx],
        "mem_mb_peak_at": rows[peak_mem_idx]["ts"],
        "mem_mb_avg": statistics.mean(mem),
        "rss_sum_mb_peak": max(rss_raw),
        "threads_peak": max(int(r.get("threads") or 0) for r in rows),
    }
    if pss:
        out["pss_sum_mb_peak"] = max(pss)
        out["pss_sum_mb_avg"] = statistics.mean(pss)
    return out


def aligned_infra_totals(rows: list[dict[str, str]]) -> dict:
    """Sum infra memory/CPU at the same timestamp (peaks may occur at different seconds)."""
    by_ts: dict[str, dict[str, dict[str, str]]] = defaultdict(dict)
    for row in rows:
        comp = row.get("component", "")
        if comp not in INFRA:
            continue
        by_ts[row["ts_epoch"]][comp] = row

    cpu_sums: list[float] = []
    mem_sums: list[float] = []
    rss_sums: list[float] = []
    for comps in by_ts.values():
        cpu_sums.append(sum(fval(r, "cpu_cores") for r in comps.values()))
        mem_sums.append(sum(fval(r, "mem_est_mb") for r in comps.values()))
        rss_sums.append(sum(fval(r, "rss_sum_mb") for r in comps.values()))

    if not cpu_sums:
        return {}

    peak_cpu_idx = max(range(len(cpu_sums)), key=lambda i: cpu_sums[i])
    peak_mem_idx = max(range(len(mem_sums)), key=lambda i: mem_sums[i])
    peak_rss_idx = max(range(len(rss_sums)), key=lambda i: rss_sums[i])

    return {
        "samples_aligned": len(cpu_sums),
        "cpu_cores_peak_sum": cpu_sums[peak_cpu_idx],
        "mem_mb_peak_sum_est": mem_sums[peak_mem_idx],
        "rss_sum_mb_peak_sum": rss_sums[peak_rss_idx],
        "cpu_cores_avg_sum": statistics.mean(cpu_sums[1:] or cpu_sums),
        "mem_mb_avg_sum": statistics.mean(mem_sums),
    }


def build_time_series(rows: list[dict[str, str]]) -> dict:
    epochs = sorted({row["ts_epoch"] for row in rows if row.get("ts_epoch")})
    if not epochs:
        return {"labels": [], "by_component": {}, "infra": {}, "system": {}}

    ts_by_epoch = {}
    for row in rows:
        ts_by_epoch.setdefault(row["ts_epoch"], row.get("ts") or row["ts_epoch"])

    labels = [ts_by_epoch[e] for e in epochs]
    by_comp: dict[str, dict[str, list[float]]] = {}
    for comp in LABELS:
        index = {row["ts_epoch"]: row for row in rows if row.get("component") == comp}
        by_comp[comp] = {
            "cpu_cores": [fval(index.get(e, {}), "cpu_cores") for e in epochs],
            "mem_est_mb": [fval(index.get(e, {}), "mem_est_mb") for e in epochs],
            "rss_sum_mb": [fval(index.get(e, {}), "rss_sum_mb") for e in epochs],
            "threads": [fval(index.get(e, {}), "threads") for e in epochs],
        }

    infra_cpu: list[float] = []
    infra_mem: list[float] = []
    infra_rss: list[float] = []
    for epoch in epochs:
        comps = {row["component"]: row for row in rows if row.get("ts_epoch") == epoch and row.get("component") in INFRA}
        infra_cpu.append(sum(fval(r, "cpu_cores") for r in comps.values()))
        infra_mem.append(sum(fval(r, "mem_est_mb") for r in comps.values()))
        infra_rss.append(sum(fval(r, "rss_sum_mb") for r in comps.values()))

    system_index = {row["ts_epoch"]: row for row in rows if row.get("load1")}
    system = {
        "load1": [fval(system_index.get(e, {}), "load1") for e in epochs],
        "mem_avail_mb": [fval(system_index.get(e, {}), "mem_avail_mb") for e in epochs],
    }

    return {
        "labels": labels,
        "by_component": by_comp,
        "infra": {
            "cpu_cores": infra_cpu,
            "mem_est_mb": infra_mem,
            "rss_sum_mb": infra_rss,
        },
        "system": system,
    }


def parse_k6_metrics(text: str | None) -> dict[str, str]:
    metrics: dict[str, str] = {}
    for line in (text or "").splitlines():
        if "phase:measured" not in line:
            continue
        if "out of" in line and "errors" not in metrics:
            m = re.search(r"([\d.]+%)", line)
            if m:
                metrics["errors"] = m.group(1)
        elif "p(95)=" in line and "p95" not in metrics:
            m = re.search(r"p\(95\)=([^\s]+)", line)
            if m:
                metrics["p95"] = m.group(1)
    return metrics


def relative_labels(count: int) -> list[str]:
    return [f"+{i}s" for i in range(count)]


def align_series(values: list, length: int) -> list:
    if len(values) >= length:
        return values[:length]
    return values + [None] * (length - len(values))


def chart_payload_from_summary(data: dict) -> dict:
    ts = data.get("time_series", {})
    return {
        "labels": ts.get("labels", []),
        "colors": CHART_COLORS,
        "labels_map": LABELS,
        "nproc": data.get("host_cpu_cores", 1),
        "cpu_cores": data.get("cpu_cores", {}),
        "runtime": data.get("runtime", ""),
        **ts,
    }


def build_summary(
    tsv_path: Path,
    k6_path: Path | None,
    nproc: int,
    cpu_cores_path: Path | None = None,
    runtime: str = "",
) -> dict:
    rows = load_rows(tsv_path)
    by_comp: dict[str, list[dict[str, str]]] = defaultdict(list)
    for row in rows:
        by_comp[row["component"]].append(row)

    components = {}
    for comp in LABELS:
        components[comp] = summarize_component(by_comp.get(comp, []))

    aligned = aligned_infra_totals(rows)
    series = build_time_series(rows)

    if cpu_cores_path is None:
        candidate = tsv_path.with_name(f"{tsv_path.stem}.cpu-cores.tsv")
        if candidate.exists():
            cpu_cores_path = candidate
        else:
            alt = tsv_path.parent / "cpu-cores.tsv"
            cpu_cores_path = alt if alt.exists() else None

    cpu_cores_raw = load_cpu_cores(cpu_cores_path)
    cpu_cores_summary = summarize_cpu_cores(cpu_cores_raw)
    if cpu_cores_raw:
        series["per_core_cpu"] = cpu_cores_raw

    system_rows = [r for r in rows if r.get("load1")]
    load_peak = max((fval(r, "load1") for r in system_rows), default=0.0)
    mem_min = min((fval(r, "mem_avail_mb") for r in system_rows if fval(r, "mem_avail_mb") > 0), default=0.0)

    k6_text = k6_path.read_text(encoding="utf-8", errors="replace") if k6_path and k6_path.exists() else ""

    return {
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "runtime": runtime,
        "host_cpu_cores": nproc,
        "tsv": str(tsv_path),
        "k6_log": str(k6_path) if k6_path else None,
        "system": {
            "load1_peak": load_peak,
            "mem_available_mb_min": mem_min,
        },
        "infra_totals": aligned,
        "components": components,
        "time_series": series,
        "cpu_cores": cpu_cores_summary,
        "k6_tail": k6_text[-4000:] if k6_text else None,
    }


def render_markdown(data: dict) -> str:
    nproc = data["host_cpu_cores"]
    lines = [
        "# Load test resource report",
        "",
        f"- Generated: `{data['generated_at']}`",
        f"- CPU cores on host: **{nproc}**",
        f"- Samples: `{data['components'].get('app', {}).get('samples', 0)}`",
        "",
        "## CPU methodology",
        "",
        "CPU measured as **cores used per interval** (not `ps` lifetime average).",
        "Value `5.2` = 5.2 physical cores busy; on a 24-core host that is ~22% of the machine.",
        "First sample per component is skipped in averages (no prior interval).",
        "",
        "## Host CPU per core",
        "",
        "From `/proc/stat`: **% utilization per physical core** per interval (0–100%).",
        "First sample skipped in averages.",
        "",
    ]

    cpu_cores = data.get("cpu_cores") or {}
    if cpu_cores:
        lines.extend(
            [
                f"- Cores tracked: **{cpu_cores.get('core_count', 0)}**",
                f"- Hottest core at peak: **CPU {cpu_cores.get('hottest_core')}** "
                f"({cpu_cores.get('hottest_util_pct_peak', 0):.1f}%)",
                f"- Average utilization (mean of per-core avgs): **{cpu_cores.get('util_pct_avg_all', 0):.1f}%**",
                "",
                "| Core | Peak % | Avg % | Peak at |",
                "|------|--------|-------|---------|",
            ]
        )
        for c in sorted(cpu_cores.get("per_core", []), key=lambda x: x["util_pct_peak"], reverse=True):
            lines.append(
                f"| CPU {c['core']} | {c['util_pct_peak']:.1f} | {c['util_pct_avg']:.1f} | {c['util_pct_peak_at']} |"
            )
        lines.append("")

    lines.extend(
        [
        "## Memory methodology",
        "",
        "| Column | Meaning |",
        "|--------|---------|",
        "| `rss_sum_mb` | Sum of VmRSS per process — **overcounts** shared pages (PostgreSQL). |",
        "| `pss_sum_mb` | Sum of PSS when `/proc/pid/smaps_rollup` is readable (app, k6). |",
        "| `mem_est_mb` | Best estimate: PSS if available; else `ΣRssAnon + ΣRssFile + max(RssShmem)` for multi-process groups. |",
        "",
        "Infra RAM total uses **aligned peaks**: sum of components at the same timestamp, then max over time.",
        "",
        ]
    )

    lines.extend(
        [
            "## By component (peak)",
            "",
            "| Component | CPU peak (cores) | % of host | RAM est. peak (MB) | RAM avg (MB) | RSS raw peak (MB) | Procs | Threads |",
            "|-----------|------------------|-----------|--------------------|--------------|-------------------|-------|---------|",
        ]
    )

    for comp, label in LABELS.items():
        s = data["components"].get(comp, {})
        if not s:
            continue
        cpu = s.get("cpu_cores_peak", 0)
        pct = (cpu / nproc * 100) if nproc else 0
        lines.append(
            f"| {label} | {cpu:.2f} | {pct:.1f}% | {s.get('mem_mb_peak', 0):.0f} | "
            f"{s.get('mem_mb_avg', 0):.0f} | {s.get('rss_sum_mb_peak', 0):.0f} | "
            f"{s.get('procs_peak', 0)} | {s.get('threads_peak', 0)} |"
        )

    infra = data.get("infra_totals") or {}
    lines.extend(
        [
            "",
            "## Infra total (app + PG + Redis + NATS)",
            "",
            f"- CPU peak (aligned sum): **{infra.get('cpu_cores_peak_sum', 0):.2f} cores**"
            + (f" ({infra.get('cpu_cores_peak_sum', 0) / nproc * 100:.1f}% of host)" if nproc else ""),
            f"- RAM peak (aligned sum, estimated): **{infra.get('mem_mb_peak_sum_est', 0):.0f} MB**",
            f"- RSS raw peak (aligned sum, overcounts PG): **{infra.get('rss_sum_mb_peak_sum', 0):.0f} MB**",
            f"- CPU avg (aligned sum): **{infra.get('cpu_cores_avg_sum', 0):.2f} cores**",
            f"- RAM avg (aligned sum): **{infra.get('mem_mb_avg_sum', 0):.0f} MB**",
            "",
            "## System",
            "",
            f"- Load average (1m) peak: **{data['system']['load1_peak']:.2f}**",
            f"- MemAvailable minimum: **{data['system']['mem_available_mb_min']:.0f} MB**",
        ]
    )

    if data.get("k6_tail"):
        lines.extend(["", "## k6 summary (tail)", "", "```text", data["k6_tail"].strip(), "```"])

    return "\n".join(line for line in lines if line is not None) + "\n"


def _pct_bar(value: float, max_value: float) -> float:
    if max_value <= 0:
        return 0.0
    return min(100.0, max(0.0, value / max_value * 100.0))


def _html_styles() -> str:
    return """
    :root {
      --bg: #f8fafc; --card: #fff; --text: #0f172a; --muted: #64748b;
      --border: #e2e8f0; --jvm: #2563eb; --native: #059669;
    }
    * { box-sizing: border-box; }
    body {
      margin: 0; font-family: system-ui, -apple-system, Segoe UI, sans-serif;
      background: var(--bg); color: var(--text); line-height: 1.45;
    }
    .wrap { max-width: 1200px; margin: 0 auto; padding: 1.5rem 1rem 3rem; }
    h1 { font-size: 1.5rem; margin: 0 0 .25rem; }
    h3 { font-size: .95rem; margin: 0 0 .5rem; }
    .meta { color: var(--muted); font-size: .9rem; margin-bottom: 1.25rem; }
    section, .panel {
      background: var(--card); border: 1px solid var(--border);
      border-radius: 12px; padding: 1rem 1.25rem; margin-bottom: 1rem;
    }
    h2 { font-size: 1.05rem; margin: 0 0 .75rem; }
    .kpi-grid {
      display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
      gap: .75rem; margin-bottom: 1rem;
    }
    .kpi {
      background: var(--card); border: 1px solid var(--border);
      border-radius: 10px; padding: .85rem 1rem;
    }
    .kpi-label { display: block; font-size: .75rem; color: var(--muted); text-transform: uppercase; letter-spacing: .04em; }
    .kpi-value { display: block; font-size: 1.6rem; font-weight: 700; margin: .15rem 0; }
    .kpi-sub { font-size: .8rem; color: var(--muted); }
    .chart-box { position: relative; height: 300px; }
    .chart-box.short { height: 220px; }
    table { width: 100%; border-collapse: collapse; font-size: .9rem; }
    th, td { padding: .45rem .5rem; border-bottom: 1px solid var(--border); text-align: left; }
    th { color: var(--muted); font-weight: 600; font-size: .75rem; text-transform: uppercase; }
    td.num { text-align: right; font-variant-numeric: tabular-nums; }
    td.muted { color: var(--muted); }
    .bar { height: 8px; background: #e2e8f0; border-radius: 4px; overflow: hidden; margin: .2rem 0; }
    .bar span { display: block; height: 100%; border-radius: 4px; }
    .bar-label { font-size: .75rem; color: var(--muted); }
    pre.log {
      background: #0f172a; color: #e2e8f0; padding: .75rem 1rem;
      border-radius: 8px; overflow: auto; font-size: .75rem; max-height: 280px;
    }
    .grid-2 { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; }
    @media (max-width: 800px) { .grid-2 { grid-template-columns: 1fr; } }
    .note { font-size: .85rem; color: var(--muted); margin: 0 0 .75rem; }
    .heatmap-wrap { overflow-x: auto; }
    .heatmap-canvas { display: block; max-width: 100%; height: auto; }
    .heatmap-legend {
      display: flex; align-items: center; gap: .5rem; margin-top: .5rem;
      font-size: .75rem; color: var(--muted);
    }
    .heatmap-legend .grad {
      flex: 1; max-width: 200px; height: 10px; border-radius: 4px;
      background: linear-gradient(90deg, #f1f5f9, #fde68a, #f97316, #dc2626);
    }
    .badge {
      display: inline-block; padding: .15rem .55rem; border-radius: 999px;
      font-size: .75rem; font-weight: 700; color: #fff; vertical-align: middle;
    }
    .badge.jvm { background: var(--jvm); }
    .badge.native { background: var(--native); }
    .tabs { display: flex; gap: .5rem; margin-bottom: 1rem; flex-wrap: wrap; }
    .tabs button {
      border: 1px solid var(--border); background: var(--card); color: var(--text);
      padding: .5rem 1rem; border-radius: 8px; cursor: pointer; font-weight: 600;
    }
    .tabs button.active { background: var(--text); color: #fff; border-color: var(--text); }
    .tab-panel { display: none; }
    .tab-panel.active { display: block; }
    .runtime-head {
      display: flex; align-items: center; gap: .6rem; margin-bottom: .75rem;
    }
    .compare-better { color: var(--native); font-weight: 600; }
    .compare-worse { color: #dc2626; font-weight: 600; }
    """


def render_html(data: dict) -> str:
    nproc = data["host_cpu_cores"]
    infra = data.get("infra_totals") or {}
    app = data["components"].get("app", {})
    cpu_cores = data.get("cpu_cores") or {}
    runtime = (data.get("runtime") or "").lower()
    runtime_badge = ""
    if runtime in RUNTIME_COLORS:
        runtime_badge = f' <span class="badge {runtime}">{runtime.upper()}</span>'
    chart_payload = json.dumps(chart_payload_from_summary(data), ensure_ascii=False)
    k6_tail = (data.get("k6_tail") or "").strip()

    peak_rows = []
    for comp, label in LABELS.items():
        s = data["components"].get(comp, {})
        if not s:
            continue
        cpu_peak = s.get("cpu_cores_peak", 0)
        cpu_pct = _pct_bar(cpu_peak, nproc)
        mem_peak = s.get("mem_mb_peak", 0)
        peak_rows.append(
            f"""<tr>
  <td>{label}</td>
  <td class="num">{cpu_peak:.2f}</td>
  <td><div class="bar"><span style="width:{cpu_pct:.1f}%;background:{CHART_COLORS[comp]}"></span></div>
      <span class="bar-label">{cpu_pct:.1f}% хоста</span></td>
  <td class="num">{mem_peak:.0f}</td>
  <td class="num muted">{s.get('mem_mb_avg', 0):.0f}</td>
  <td class="num muted">{s.get('rss_sum_mb_peak', 0):.0f}</td>
</tr>"""
        )

    kpi_cards = f"""
<div class="kpi-grid">
  <div class="kpi"><span class="kpi-label">App CPU peak</span>
    <span class="kpi-value">{app.get('cpu_cores_peak', 0):.2f}</span>
    <span class="kpi-sub">cores ({_pct_bar(app.get('cpu_cores_peak', 0), nproc):.0f}% хоста)</span></div>
  <div class="kpi"><span class="kpi-label">App RAM peak</span>
    <span class="kpi-value">{app.get('mem_mb_peak', 0):.0f}</span>
    <span class="kpi-sub">MB (est.)</span></div>
  <div class="kpi"><span class="kpi-label">Infra CPU peak</span>
    <span class="kpi-value">{infra.get('cpu_cores_peak_sum', 0):.2f}</span>
    <span class="kpi-sub">cores aligned</span></div>
  <div class="kpi"><span class="kpi-label">Infra RAM peak</span>
    <span class="kpi-value">{infra.get('mem_mb_peak_sum_est', 0):.0f}</span>
    <span class="kpi-sub">MB aligned est.</span></div>
  <div class="kpi"><span class="kpi-label">Load avg peak</span>
    <span class="kpi-value">{data['system']['load1_peak']:.2f}</span>
    <span class="kpi-sub">1 min</span></div>
  <div class="kpi"><span class="kpi-label">MemAvailable min</span>
    <span class="kpi-value">{data['system']['mem_available_mb_min']:.0f}</span>
    <span class="kpi-sub">MB</span></div>
  <div class="kpi"><span class="kpi-label">Hottest CPU core</span>
    <span class="kpi-value">{cpu_cores.get('hottest_util_pct_peak', 0):.0f}%</span>
    <span class="kpi-sub">CPU {cpu_cores.get('hottest_core', '—')} peak</span></div>
</div>"""

    cpu_cores_block = ""
    if cpu_cores.get("per_core"):
        cpu_cores_block = """
  <section>
    <h2>Загрузка по ядрам CPU (%)</h2>
    <p class="note">Данные из /proc/stat. По оси Y — физические ядра, по X — время. Цвет = % загрузки ядра.</p>
    <div class="heatmap-wrap">
      <canvas id="coreHeatmap" class="heatmap-canvas"></canvas>
      <div class="heatmap-legend"><span>0%</span><span class="grad"></span><span>100%</span></div>
    </div>
    <div class="chart-box short" style="margin-top:1rem"><canvas id="corePeakChart"></canvas></div>
  </section>"""

    k6_block = ""
    if k6_tail:
        escaped = k6_tail.replace("&", "&amp;").replace("<", "&lt;")
        k6_block = f'<section><h2>k6 (хвост лога)</h2><pre class="log">{escaped}</pre></section>'

    return f"""<!DOCTYPE html>
<html lang="ru">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Load test resources{(' — ' + runtime.upper()) if runtime else ''} — {data['generated_at'][:19]}</title>
  <script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.7/dist/chart.umd.min.js"></script>
  <style>{_html_styles()}</style>
</head>
<body>
<div class="wrap">
  <h1>Нагрузочный тест — ресурсы{runtime_badge}</h1>
  <p class="meta">Сгенерировано: {data['generated_at']} · CPU хоста: {nproc} cores · сэмплов: {app.get('samples', 0)}</p>
  {kpi_cards}
  <section>
    <h2>CPU по компонентам (cores / интервал)</h2>
    <p class="note">1.0 = одно полное ядро. Первый сэмпл = 0 (нет предыдущего интервала).</p>
    <div class="chart-box"><canvas id="cpuChart"></canvas></div>
  </section>
  {cpu_cores_block}
  <section>
    <h2>RAM по компонентам (MB, est.)</h2>
    <p class="note">Дедупликация shared memory для PostgreSQL. См. также график RSS raw ниже.</p>
    <div class="chart-box"><canvas id="memChart"></canvas></div>
  </section>
  <div class="grid-2">
    <section>
      <h2>Infra суммарно (aligned)</h2>
      <div class="chart-box short"><canvas id="infraChart"></canvas></div>
    </section>
    <section>
      <h2>Система: load &amp; MemAvailable</h2>
      <div class="chart-box short"><canvas id="sysChart"></canvas></div>
    </section>
  </div>
  <section>
    <h2>PostgreSQL RSS raw vs est.</h2>
    <div class="chart-box short"><canvas id="pgMemChart"></canvas></div>
  </section>
  <section>
    <h2>Пики по компонентам</h2>
    <table>
      <thead><tr>
        <th>Компонент</th><th>CPU peak</th><th>Нагрузка</th>
        <th>RAM peak</th><th>RAM avg</th><th>RSS raw peak</th>
      </tr></thead>
      <tbody>{''.join(peak_rows)}</tbody>
    </table>
  </section>
  {k6_block}
</div>
<script>
const DATA = {chart_payload};

function lineDataset(label, data, color, opts = {{}}) {{
  return {{
    label, data, borderColor: color, backgroundColor: color + '22',
    borderWidth: 2, pointRadius: 0, tension: 0.15, fill: false, ...opts
  }};
}}

const baseOpts = (yTitle) => ({{
  responsive: true, maintainAspectRatio: false, interaction: {{ mode: 'index', intersect: false }},
  plugins: {{
    legend: {{ position: 'bottom', labels: {{ boxWidth: 12, font: {{ size: 11 }} }} }},
    tooltip: {{ callbacks: {{ label: (ctx) => `${{ctx.dataset.label}}: ${{ctx.parsed.y.toFixed(ctx.dataset.yAxisID === 'y1' ? 0 : 2)}}` }} }}
  }},
  scales: {{
    x: {{ ticks: {{ maxTicksLimit: 14, font: {{ size: 10 }} }} }},
    y: {{ title: {{ display: true, text: yTitle }}, beginAtZero: true }}
  }}
}});

const comps = ['app', 'postgres', 'redis', 'nats', 'k6'];
const cpuDatasets = comps.map((c) => lineDataset(DATA.labels_map[c], DATA.by_component[c].cpu_cores, DATA.colors[c]));
new Chart(document.getElementById('cpuChart'), {{
  type: 'line', data: {{ labels: DATA.labels, datasets: cpuDatasets }},
  options: baseOpts('cores')
}});

const memDatasets = comps.map((c) => lineDataset(DATA.labels_map[c], DATA.by_component[c].mem_est_mb, DATA.colors[c]));
new Chart(document.getElementById('memChart'), {{
  type: 'line', data: {{ labels: DATA.labels, datasets: memDatasets }},
  options: baseOpts('MB')
}});

new Chart(document.getElementById('infraChart'), {{
  type: 'line',
  data: {{
    labels: DATA.labels,
    datasets: [
      lineDataset('CPU infra (cores)', DATA.infra.cpu_cores, DATA.colors.infra_cpu),
      lineDataset('RAM infra (MB est.)', DATA.infra.mem_est_mb, DATA.colors.infra_mem, {{ yAxisID: 'y1' }})
    ]
  }},
  options: {{
    ...baseOpts('cores'),
    scales: {{
      x: {{ ticks: {{ maxTicksLimit: 10, font: {{ size: 10 }} }} }},
      y: {{ position: 'left', beginAtZero: true, title: {{ display: true, text: 'CPU cores' }} }},
      y1: {{ position: 'right', beginAtZero: true, grid: {{ drawOnChartArea: false }},
             title: {{ display: true, text: 'RAM MB' }} }}
    }}
  }}
}});

new Chart(document.getElementById('sysChart'), {{
  type: 'line',
  data: {{
    labels: DATA.labels,
    datasets: [
      lineDataset('Load avg (1m)', DATA.system.load1, DATA.colors.load1),
      lineDataset('MemAvailable (MB)', DATA.system.mem_avail_mb, DATA.colors.mem_avail, {{ yAxisID: 'y1' }})
    ]
  }},
  options: {{
    ...baseOpts('load'),
    scales: {{
      x: {{ ticks: {{ maxTicksLimit: 10, font: {{ size: 10 }} }} }},
      y: {{ position: 'left', beginAtZero: true, title: {{ display: true, text: 'Load 1m' }} }},
      y1: {{ position: 'right', beginAtZero: true, grid: {{ drawOnChartArea: false }},
             title: {{ display: true, text: 'MemAvail MB' }} }}
    }}
  }}
}});

const pg = DATA.by_component.postgres;
new Chart(document.getElementById('pgMemChart'), {{
  type: 'line',
  data: {{
    labels: DATA.labels,
    datasets: [
      lineDataset('PG RSS raw (MB)', pg.rss_sum_mb, '#94a3b8', {{ borderDash: [4, 4] }}),
      lineDataset('PG est. (MB)', pg.mem_est_mb, DATA.colors.postgres)
    ]
  }},
  options: baseOpts('MB')
}});

function heatColor(pct) {{
  const t = Math.max(0, Math.min(100, pct)) / 100;
  if (t < 0.5) {{
    const u = t * 2;
    return `rgb(${{Math.round(241 + (253 - 241) * u)}},${{Math.round(245 + (230 - 245) * u)}},${{Math.round(249 + (138 - 249) * u)}})`;
  }}
  const u = (t - 0.5) * 2;
  return `rgb(${{Math.round(253 + (220 - 253) * u)}},${{Math.round(230 + (38 - 230) * u)}},${{Math.round(138 + (38 - 138) * u)}})`;
}}

function drawCoreHeatmap() {{
  const pc = DATA.per_core_cpu;
  if (!pc || !pc.util_pct || !pc.util_pct.length) return;
  const canvas = document.getElementById('coreHeatmap');
  if (!canvas) return;

  const cores = pc.core_ids;
  const times = pc.labels;
  const data = pc.util_pct;
  const leftPad = 36, topPad = 18, cellW = Math.max(4, Math.min(14, Math.floor((1100 - leftPad) / times.length)));
  const cellH = Math.max(10, Math.min(18, Math.floor(320 / cores.length)));
  canvas.width = leftPad + times.length * cellW + 8;
  canvas.height = topPad + cores.length * cellH + 8;
  const ctx = canvas.getContext('2d');
  ctx.fillStyle = '#fff';
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  ctx.font = '10px system-ui';
  ctx.fillStyle = '#64748b';

  for (let r = 0; r < cores.length; r++) {{
    const y = topPad + r * cellH + cellH * 0.7;
    ctx.fillText('CPU' + cores[r], 2, y);
    for (let c = 0; c < times.length; c++) {{
      const v = data[r][c] || 0;
      ctx.fillStyle = heatColor(v);
      ctx.fillRect(leftPad + c * cellW, topPad + r * cellH, cellW - 1, cellH - 1);
    }}
  }}
}}

function buildCorePeakChart() {{
  const summary = DATA.cpu_cores;
  if (!summary || !summary.per_core || !summary.per_core.length) return;
  const el = document.getElementById('corePeakChart');
  if (!el) return;
  const sorted = [...summary.per_core].sort((a, b) => a.core - b.core);
  new Chart(el, {{
    type: 'bar',
    data: {{
      labels: sorted.map((c) => 'CPU ' + c.core),
      datasets: [
        {{
          label: 'Peak %',
          data: sorted.map((c) => c.util_pct_peak),
          backgroundColor: '#2563eb99',
          borderColor: '#2563eb',
          borderWidth: 1
        }},
        {{
          label: 'Avg %',
          data: sorted.map((c) => c.util_pct_avg),
          backgroundColor: '#94a3b899',
          borderColor: '#94a3b8',
          borderWidth: 1
        }}
      ]
    }},
    options: {{
      responsive: true,
      maintainAspectRatio: false,
      plugins: {{ legend: {{ position: 'bottom' }} }},
      scales: {{
        y: {{ beginAtZero: true, max: 100, title: {{ display: true, text: '% utilization' }} }}
      }}
    }}
  }});
}}

drawCoreHeatmap();
buildCorePeakChart();
</script>
</body>
</html>
"""


def _app_stats(data: dict) -> tuple[float | str, float | str, float | str]:
    app = data.get("components", {}).get("app", {})
    return (
        app.get("cpu_cores_peak", "n/a"),
        app.get("cpu_cores_avg", "n/a"),
        app.get("mem_mb_peak", "n/a"),
    )


def render_comparison_markdown(jvm: dict, native: dict) -> str:
    jcpu, jcpu_avg, jmem = _app_stats(jvm)
    ncpu, ncpu_avg, nmem = _app_stats(native)
    jk = parse_k6_metrics(jvm.get("k6_tail"))
    nk = parse_k6_metrics(native.get("k6_tail"))

    def fmt_cpu_avg(v: float | str) -> str:
        return f"{v:.2f}" if isinstance(v, (int, float)) else str(v)

    return "\n".join(
        [
            "# JVM vs Native (fair protocol)",
            "",
            "Per runtime: **truncate DB + flush Redis** → start server → idle warmup →",
            "k6 warmup **30s @ 200 TPS** → measured **2m @ 2000 TPS** (monitored).",
            "",
            "| Metric | JVM | Native |",
            "|--------|-----|--------|",
            f"| App CPU peak (cores) | {jcpu} | {ncpu} |",
            f"| App CPU avg (cores) | {fmt_cpu_avg(jcpu_avg)} | {fmt_cpu_avg(ncpu_avg)} |",
            f"| App RAM peak (MB) | {jmem} | {nmem} |",
            f"| p95 latency (measured) | {jk.get('p95', 'n/a')} | {nk.get('p95', 'n/a')} |",
            f"| Error rate (measured) | {jk.get('errors', 'n/a')} | {nk.get('errors', 'n/a')} |",
            "",
            "Интерактивный отчёт: `jvm-vs-native-comparison.html`",
            "",
        ]
    ) + "\n"


def build_compare_payload(jvm: dict, native: dict) -> dict:
    jvm_p = chart_payload_from_summary({**jvm, "runtime": "jvm"})
    native_p = chart_payload_from_summary({**native, "runtime": "native"})
    max_len = max(len(jvm_p.get("labels", [])), len(native_p.get("labels", [])), 1)
    rel = relative_labels(max_len)

    def align_path(payload: dict, length: int, *keys: str) -> list:
        cur: object = payload
        for key in keys[:-1]:
            if not isinstance(cur, dict):
                return align_series([], length)
            cur = cur.get(key, {})
        if not isinstance(cur, dict):
            return align_series([], length)
        values = cur.get(keys[-1], [])
        if not isinstance(values, list):
            return align_series([], length)
        return align_series(values, length)

    return {
        "labels": rel,
        "jvm": jvm_p,
        "native": native_p,
        "k6": {
            "jvm": parse_k6_metrics(jvm.get("k6_tail")),
            "native": parse_k6_metrics(native.get("k6_tail")),
        },
        "overlay": {
            "jvm_app_cpu": align_path(jvm_p, max_len, "by_component", "app", "cpu_cores"),
            "native_app_cpu": align_path(native_p, max_len, "by_component", "app", "cpu_cores"),
            "jvm_app_mem": align_path(jvm_p, max_len, "by_component", "app", "mem_est_mb"),
            "native_app_mem": align_path(native_p, max_len, "by_component", "app", "mem_est_mb"),
            "jvm_infra_cpu": align_path(jvm_p, max_len, "infra", "cpu_cores"),
            "native_infra_cpu": align_path(native_p, max_len, "infra", "cpu_cores"),
        },
        "summary": {
            "jvm": {
                "app_cpu_peak": _app_stats(jvm)[0],
                "app_mem_peak": _app_stats(jvm)[2],
                "p95": parse_k6_metrics(jvm.get("k6_tail")).get("p95", "n/a"),
            },
            "native": {
                "app_cpu_peak": _app_stats(native)[0],
                "app_mem_peak": _app_stats(native)[2],
                "p95": parse_k6_metrics(native.get("k6_tail")).get("p95", "n/a"),
            },
        },
    }


def _runtime_panel_html(prefix: str, title: str, badge: str) -> str:
    return f"""
  <div class="runtime-head"><span class="badge {badge}">{title}</span></div>
  <section>
    <h2>CPU по компонентам</h2>
    <div class="chart-box short"><canvas id="{prefix}-cpuChart"></canvas></div>
  </section>
  <section>
    <h2>RAM по компонентам</h2>
    <div class="chart-box short"><canvas id="{prefix}-memChart"></canvas></div>
  </section>
  <section>
    <h2>Загрузка по ядрам CPU</h2>
    <div class="heatmap-wrap">
      <canvas id="{prefix}-coreHeatmap" class="heatmap-canvas"></canvas>
      <div class="heatmap-legend"><span>0%</span><span class="grad"></span><span>100%</span></div>
    </div>
    <div class="chart-box short" style="margin-top:1rem"><canvas id="{prefix}-corePeakChart"></canvas></div>
  </section>
"""


def render_comparison_html(jvm: dict, native: dict) -> str:
    payload = build_compare_payload(jvm, native)
    data_json = json.dumps(payload, ensure_ascii=False)
    jvm_app = jvm.get("components", {}).get("app", {})
    native_app = native.get("components", {}).get("app", {})
    jvm_cpu = jvm_app.get("cpu_cores_peak", 0)
    native_cpu = native_app.get("cpu_cores_peak", 0)
    jvm_mem = jvm_app.get("mem_mb_peak", 0)
    native_mem = native_app.get("mem_mb_peak", 0)
    jvm_p95 = payload["k6"]["jvm"].get("p95", "n/a")
    native_p95 = payload["k6"]["native"].get("p95", "n/a")

    return f"""<!DOCTYPE html>
<html lang="ru">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>JVM vs Native — load test</title>
  <script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.7/dist/chart.umd.min.js"></script>
  <style>{_html_styles()}</style>
</head>
<body>
<div class="wrap">
  <h1>JVM vs Native <span class="badge jvm">JVM</span> <span class="badge native">Native</span></h1>
  <p class="meta">Сравнение двух прогонов с одинаковым протоколом · ось времени на графиках = секунды от старта мониторинга</p>

  <div class="tabs">
    <button type="button" class="active" data-tab="compare">Сравнение</button>
    <button type="button" data-tab="jvm">JVM</button>
    <button type="button" data-tab="native">Native</button>
  </div>

  <div id="tab-compare" class="tab-panel active">
    <div class="grid-2">
      <div class="kpi"><span class="kpi-label">JVM App CPU peak</span>
        <span class="kpi-value" style="color:var(--jvm)">{jvm_cpu:.2f}</span><span class="kpi-sub">cores</span></div>
      <div class="kpi"><span class="kpi-label">Native App CPU peak</span>
        <span class="kpi-value" style="color:var(--native)">{native_cpu:.2f}</span><span class="kpi-sub">cores</span></div>
      <div class="kpi"><span class="kpi-label">JVM App RAM peak</span>
        <span class="kpi-value" style="color:var(--jvm)">{jvm_mem:.0f}</span><span class="kpi-sub">MB</span></div>
      <div class="kpi"><span class="kpi-label">Native App RAM peak</span>
        <span class="kpi-value" style="color:var(--native)">{native_mem:.0f}</span><span class="kpi-sub">MB</span></div>
    </div>
    <section>
      <h2>Сводка</h2>
      <table>
        <thead><tr><th>Метрика</th><th>JVM</th><th>Native</th></tr></thead>
        <tbody>
          <tr><td>App CPU peak (cores)</td><td class="num">{jvm_cpu:.2f}</td><td class="num">{native_cpu:.2f}</td></tr>
          <tr><td>App RAM peak (MB)</td><td class="num">{jvm_mem:.0f}</td><td class="num">{native_mem:.0f}</td></tr>
          <tr><td>p95 latency</td><td class="num">{jvm_p95}</td><td class="num">{native_p95}</td></tr>
        </tbody>
      </table>
    </section>
    <section>
      <h2>App CPU: JVM vs Native</h2>
      <div class="chart-box"><canvas id="cmpAppCpu"></canvas></div>
    </section>
    <section>
      <h2>App RAM: JVM vs Native</h2>
      <div class="chart-box"><canvas id="cmpAppMem"></canvas></div>
    </section>
    <section>
      <h2>Infra CPU (aligned): JVM vs Native</h2>
      <div class="chart-box short"><canvas id="cmpInfraCpu"></canvas></div>
    </section>
    <div class="grid-2">
      <section>
        <h3><span class="badge jvm">JVM</span> ядра CPU</h3>
        <div class="heatmap-wrap"><canvas id="cmp-jvm-heatmap" class="heatmap-canvas"></canvas></div>
      </section>
      <section>
        <h3><span class="badge native">Native</span> ядра CPU</h3>
        <div class="heatmap-wrap"><canvas id="cmp-native-heatmap" class="heatmap-canvas"></canvas></div>
      </section>
    </div>
    <section>
      <h2>Пики по компонентам</h2>
      <div class="chart-box"><canvas id="cmpPeakBar"></canvas></div>
    </section>
  </div>

  <div id="tab-jvm" class="tab-panel">
    {_runtime_panel_html("jvm", "JVM", "jvm")}
  </div>

  <div id="tab-native" class="tab-panel">
    {_runtime_panel_html("native", "Native", "native")}
  </div>
</div>
<script>
const DATA = {data_json};

document.querySelectorAll('.tabs button').forEach((btn) => {{
  btn.addEventListener('click', () => {{
    document.querySelectorAll('.tabs button').forEach((b) => b.classList.remove('active'));
    document.querySelectorAll('.tab-panel').forEach((p) => p.classList.remove('active'));
    btn.classList.add('active');
    document.getElementById('tab-' + btn.dataset.tab).classList.add('active');
  }});
}});

function lineDataset(label, data, color, opts = {{}}) {{
  return {{ label, data, borderColor: color, backgroundColor: color + '22',
    borderWidth: 2, pointRadius: 0, tension: 0.15, fill: false, spanGaps: true, ...opts }};
}}

function baseOpts(yTitle) {{
  return {{
    responsive: true, maintainAspectRatio: false, interaction: {{ mode: 'index', intersect: false }},
    plugins: {{ legend: {{ position: 'bottom' }} }},
    scales: {{ x: {{ ticks: {{ maxTicksLimit: 14 }} }}, y: {{ beginAtZero: true, title: {{ display: true, text: yTitle }} }} }}
  }};
}}

new Chart(document.getElementById('cmpAppCpu'), {{
  type: 'line',
  data: {{ labels: DATA.labels, datasets: [
    lineDataset('JVM App CPU', DATA.overlay.jvm_app_cpu, '#2563eb'),
    lineDataset('Native App CPU', DATA.overlay.native_app_cpu, '#059669')
  ]}},
  options: baseOpts('cores')
}});

new Chart(document.getElementById('cmpAppMem'), {{
  type: 'line',
  data: {{ labels: DATA.labels, datasets: [
    lineDataset('JVM App RAM', DATA.overlay.jvm_app_mem, '#2563eb'),
    lineDataset('Native App RAM', DATA.overlay.native_app_mem, '#059669')
  ]}},
  options: baseOpts('MB')
}});

new Chart(document.getElementById('cmpInfraCpu'), {{
  type: 'line',
  data: {{ labels: DATA.labels, datasets: [
    lineDataset('JVM infra CPU', DATA.overlay.jvm_infra_cpu, '#2563eb'),
    lineDataset('Native infra CPU', DATA.overlay.native_infra_cpu, '#059669')
  ]}},
  options: baseOpts('cores')
}});

const comps = ['app', 'postgres', 'redis', 'nats'];
const peakLabels = comps.map((c) => DATA.jvm.labels_map[c]);
new Chart(document.getElementById('cmpPeakBar'), {{
  type: 'bar',
  data: {{
    labels: peakLabels,
    datasets: [
      {{ label: 'JVM CPU peak', data: comps.map((c) => Math.max(...(DATA.jvm.by_component[c].cpu_cores || [0]))),
         backgroundColor: '#2563eb99', borderColor: '#2563eb', borderWidth: 1 }},
      {{ label: 'Native CPU peak', data: comps.map((c) => Math.max(...(DATA.native.by_component[c].cpu_cores || [0]))),
         backgroundColor: '#05966999', borderColor: '#059669', borderWidth: 1 }}
    ]
  }},
  options: {{ ...baseOpts('cores'), plugins: {{ legend: {{ position: 'bottom' }} }} }}
}});

function heatColor(pct) {{
  const t = Math.max(0, Math.min(100, pct)) / 100;
  if (t < 0.5) {{
    const u = t * 2;
    return `rgb(${{Math.round(241 + 12 * u)}},${{Math.round(245 - 15 * u)}},${{Math.round(249 - 111 * u)}})`;
  }}
  const u = (t - 0.5) * 2;
  return `rgb(${{Math.round(253 - 33 * u)}},${{Math.round(230 - 192 * u)}},${{Math.round(138 - 100 * u)}})`;
}}

function drawHeatmap(canvasId, pc) {{
  if (!pc || !pc.util_pct || !pc.util_pct.length) return;
  const canvas = document.getElementById(canvasId);
  if (!canvas) return;
  const cores = pc.core_ids, times = pc.labels, data = pc.util_pct;
  const leftPad = 36, topPad = 18;
  const cellW = Math.max(3, Math.min(10, Math.floor((520 - leftPad) / Math.max(times.length, 1))));
  const cellH = Math.max(8, Math.min(14, Math.floor(260 / Math.max(cores.length, 1))));
  canvas.width = leftPad + times.length * cellW + 8;
  canvas.height = topPad + cores.length * cellH + 8;
  const ctx = canvas.getContext('2d');
  ctx.fillStyle = '#fff'; ctx.fillRect(0, 0, canvas.width, canvas.height);
  ctx.font = '10px system-ui'; ctx.fillStyle = '#64748b';
  for (let r = 0; r < cores.length; r++) {{
    ctx.fillText('CPU' + cores[r], 2, topPad + r * cellH + cellH * 0.7);
    for (let c = 0; c < times.length; c++) {{
      ctx.fillStyle = heatColor(data[r][c] || 0);
      ctx.fillRect(leftPad + c * cellW, topPad + r * cellH, cellW - 1, cellH - 1);
    }}
  }}
}}

drawHeatmap('cmp-jvm-heatmap', DATA.jvm.per_core_cpu);
drawHeatmap('cmp-native-heatmap', DATA.native.per_core_cpu);

function initRuntimeCharts(prefix, payload) {{
  const comps = ['app', 'postgres', 'redis', 'nats', 'k6'];
  const color = prefix === 'jvm' ? '#2563eb' : '#059669';
  new Chart(document.getElementById(prefix + '-cpuChart'), {{
    type: 'line',
    data: {{ labels: payload.labels,
      datasets: comps.map((c) => lineDataset(payload.labels_map[c], payload.by_component[c].cpu_cores, payload.colors[c])) }},
    options: baseOpts('cores')
  }});
  new Chart(document.getElementById(prefix + '-memChart'), {{
    type: 'line',
    data: {{ labels: payload.labels,
      datasets: comps.map((c) => lineDataset(payload.labels_map[c], payload.by_component[c].mem_est_mb, payload.colors[c])) }},
    options: baseOpts('MB')
  }});
  const summary = payload.cpu_cores;
  if (summary && summary.per_core && summary.per_core.length) {{
    const sorted = [...summary.per_core].sort((a, b) => a.core - b.core);
    new Chart(document.getElementById(prefix + '-corePeakChart'), {{
      type: 'bar',
      data: {{
        labels: sorted.map((c) => 'CPU ' + c.core),
        datasets: [
          {{ label: 'Peak %', data: sorted.map((c) => c.util_pct_peak), backgroundColor: color + '99', borderColor: color }},
          {{ label: 'Avg %', data: sorted.map((c) => c.util_pct_avg), backgroundColor: '#94a3b899', borderColor: '#94a3b8' }}
        ]
      }},
      options: {{ ...baseOpts('%'), scales: {{ y: {{ max: 100, beginAtZero: true }} }} }}
    }});
  }}
  drawHeatmap(prefix + '-coreHeatmap', payload.per_core_cpu);
}}

initRuntimeCharts('jvm', DATA.jvm);
initRuntimeCharts('native', DATA.native);
</script>
</body>
</html>
"""


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("tsv", type=Path, nargs="?")
    parser.add_argument("--compare", nargs=2, metavar=("JVM_JSON", "NATIVE_JSON"))
    parser.add_argument("--k6-log", type=Path, default=None)
    parser.add_argument("--out-dir", type=Path)
    parser.add_argument("--out", type=Path, help="Output HTML path for --compare mode")
    parser.add_argument("--nproc", type=int, default=1)
    parser.add_argument("--cpu-cores", type=Path, default=None)
    parser.add_argument("--runtime", type=str, default="")
    args = parser.parse_args()

    if args.compare:
        jvm_path, native_path = Path(args.compare[0]), Path(args.compare[1])
        jvm = json.loads(jvm_path.read_text(encoding="utf-8"))
        native = json.loads(native_path.read_text(encoding="utf-8"))
        out_base = args.out or Path("jvm-vs-native-comparison.html")
        html_path = out_base if out_base.suffix == ".html" else out_base / "jvm-vs-native-comparison.html"
        md_path = html_path.with_suffix(".md")
        html_path.parent.mkdir(parents=True, exist_ok=True)
        html_path.write_text(render_comparison_html(jvm, native), encoding="utf-8")
        md_path.write_text(render_comparison_markdown(jvm, native), encoding="utf-8")
        print(str(html_path))
        print(str(md_path))
        return 0

    if not args.tsv or not args.out_dir:
        parser.error("tsv and --out-dir are required unless --compare is used")

    args.out_dir.mkdir(parents=True, exist_ok=True)

    summary = build_summary(args.tsv, args.k6_log, args.nproc, args.cpu_cores, args.runtime)
    json_path = args.out_dir / "summary.json"
    md_path = args.out_dir / "summary.md"
    html_path = args.out_dir / "report.html"

    json_path.write_text(json.dumps(summary, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    md_path.write_text(render_markdown(summary), encoding="utf-8")
    html_path.write_text(render_html(summary), encoding="utf-8")

    print(str(md_path))
    print(str(json_path))
    print(str(html_path))
    return 0


if __name__ == "__main__":
    sys.exit(main())
