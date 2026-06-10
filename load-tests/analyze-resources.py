#!/usr/bin/env python3
"""Build human-readable summary from monitor-resources.tsv."""

from __future__ import annotations

import argparse
import csv
import json
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


def load_rows(path: Path) -> list[dict[str, str]]:
    with path.open(newline="") as f:
        return list(csv.DictReader(f, delimiter="\t"))


def fval(row: dict[str, str], key: str) -> float:
    try:
        return float(row.get(key) or 0)
    except ValueError:
        return 0.0


def summarize_component(rows: list[dict[str, str]]) -> dict:
    if not rows:
        return {}

    cpu = [fval(r, "cpu_cores") for r in rows]
    rss = [fval(r, "mem_est_mb") for r in rows]
    rss_raw = [fval(r, "rss_sum_mb") for r in rows]

    peak_cpu_idx = max(range(len(cpu)), key=lambda i: cpu[i])
    peak_mem_idx = max(range(len(rss)), key=lambda i: rss[i])

    return {
        "samples": len(rows),
        "procs_peak": max(int(r.get("procs") or 0) for r in rows),
        "cpu_cores_peak": cpu[peak_cpu_idx],
        "cpu_cores_peak_at": rows[peak_cpu_idx]["ts"],
        "cpu_cores_avg": statistics.mean(cpu[1:] or cpu),
        "mem_mb_peak": rss[peak_mem_idx],
        "mem_mb_peak_at": rows[peak_mem_idx]["ts"],
        "mem_mb_avg": statistics.mean(rss),
        "rss_sum_mb_peak": max(rss_raw),
        "threads_peak": max(int(r.get("threads") or 0) for r in rows),
    }


def build_summary(tsv_path: Path, k6_path: Path | None, nproc: int) -> dict:
    rows = load_rows(tsv_path)
    by_comp: dict[str, list[dict[str, str]]] = defaultdict(list)
    for row in rows:
        by_comp[row["component"]].append(row)

    components = {}
    for comp in LABELS:
        components[comp] = summarize_component(by_comp.get(comp, []))

    infra_cpu_peak = sum(components[c].get("cpu_cores_peak", 0) for c in INFRA if c in components)
    infra_mem_peak = sum(components[c].get("mem_mb_peak", 0) for c in INFRA if c in components)

    system_rows = [r for r in rows if r.get("load1")]
    load_peak = max((fval(r, "load1") for r in system_rows), default=0.0)
    mem_min = min((fval(r, "mem_avail_mb") for r in system_rows if fval(r, "mem_avail_mb") > 0), default=0.0)

    k6_text = k6_path.read_text(encoding="utf-8", errors="replace") if k6_path and k6_path.exists() else ""

    return {
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "host_cpu_cores": nproc,
        "tsv": str(tsv_path),
        "k6_log": str(k6_path) if k6_path else None,
        "system": {
            "load1_peak": load_peak,
            "mem_available_mb_min": mem_min,
        },
        "infra_totals": {
            "cpu_cores_peak_sum": infra_cpu_peak,
            "mem_mb_peak_sum_est": infra_mem_peak,
        },
        "components": components,
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
        "",
        "## PostgreSQL memory",
        "",
        "`rss_sum_mb` sums RSS of all PG processes and **overcounts shared memory**.",
        "`mem_est_mb` = shared_buffers + backends × private_per_conn (configured in monitor script).",
        "",
        "## By component (peak)",
        "",
        "| Component | CPU peak (cores) | % of host | RAM est. peak (MB) | RAM avg (MB) | Procs | Threads |",
        "|-----------|------------------|-----------|--------------------|--------------|-------|---------|",
    ]

    for comp, label in LABELS.items():
        s = data["components"].get(comp, {})
        if not s:
            continue
        cpu = s.get("cpu_cores_peak", 0)
        pct = (cpu / nproc * 100) if nproc else 0
        lines.append(
            f"| {label} | {cpu:.2f} | {pct:.1f}% | {s.get('mem_mb_peak', 0):.0f} | "
            f"{s.get('mem_mb_avg', 0):.0f} | {s.get('procs_peak', 0)} | {s.get('threads_peak', 0)} |"
        )

    infra = data["infra_totals"]
    lines.extend(
        [
            "",
            "## Infra total (app + PG + Redis + NATS, peaks summed)",
            "",
            f"- CPU peak sum: **{infra['cpu_cores_peak_sum']:.2f} cores** "
            f"({infra['cpu_cores_peak_sum'] / nproc * 100:.1f}% of host)" if nproc else "",
            f"- RAM peak sum (estimated): **{infra['mem_mb_peak_sum_est']:.0f} MB**",
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


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("tsv", type=Path)
    parser.add_argument("--k6-log", type=Path, default=None)
    parser.add_argument("--out-dir", type=Path, required=True)
    parser.add_argument("--nproc", type=int, default=1)
    args = parser.parse_args()

    args.out_dir.mkdir(parents=True, exist_ok=True)

    summary = build_summary(args.tsv, args.k6_log, args.nproc)
    json_path = args.out_dir / "summary.json"
    md_path = args.out_dir / "summary.md"

    json_path.write_text(json.dumps(summary, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    md_path.write_text(render_markdown(summary), encoding="utf-8")

    print(str(md_path))
    print(str(json_path))
    return 0


if __name__ == "__main__":
    sys.exit(main())
