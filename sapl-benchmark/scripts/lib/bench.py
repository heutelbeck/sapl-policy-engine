#!/usr/bin/env python3
#
# Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
#
# SPDX-License-Identifier: Apache-2.0
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

"""Benchmark statistics, convergence, and data I/O for SAPL benchmark scripts.

All math and data processing for the benchmark suite lives here.
Bash scripts handle OS interaction (processes, sysfs, signals) and call
this module for all computations.

Usage from bash:
    python3 lib/bench.py <subcommand> [args]
"""

import argparse
import json
import math
from pathlib import Path


# ---------------------------------------------------------------------------
# Statistics
# ---------------------------------------------------------------------------

def compute_stats(values):
    """Return (mean, stddev, cov_percent). Uses sample stddev (n-1)."""
    n = len(values)
    if n == 0:
        return 0.0, 0.0, 0.0
    mean = sum(values) / n
    if n < 2 or mean == 0:
        return mean, 0.0, 0.0 if mean == 0 else 999.99
    variance = sum((v - mean) ** 2 for v in values) / (n - 1)
    stddev = math.sqrt(variance)
    cov = stddev / mean * 100
    return mean, stddev, cov


# t-distribution critical values for 95% CI (two-tailed, alpha=0.05)
# Key: degrees of freedom (n-1). Values from scipy.stats.t.ppf(0.975, df).
_T_CRITICAL_95 = {
    1: 12.706, 2: 4.303, 3: 3.182, 4: 2.776, 5: 2.571,
    6: 2.447, 7: 2.365, 8: 2.306, 9: 2.262, 10: 2.228,
    15: 2.131, 20: 2.086, 25: 2.060, 30: 2.042, 50: 2.009,
    100: 1.984,
}


def _t_critical(df):
    """Look up t-critical value for given degrees of freedom (95% CI)."""
    if df in _T_CRITICAL_95:
        return _T_CRITICAL_95[df]
    # Interpolate from nearest smaller key, or use 1.96 for large df
    if df > 100:
        return 1.96
    keys = sorted(_T_CRITICAL_95.keys())
    for i in range(len(keys) - 1):
        if keys[i] <= df < keys[i + 1]:
            return _T_CRITICAL_95[keys[i]]
    return 1.96


def confidence_interval_95(values):
    """Return (ci_lower, ci_upper) for 95% confidence interval using t-distribution.

    Returns (mean, mean) if fewer than 2 values (no interval computable).
    """
    n = len(values)
    mean, stddev, _ = compute_stats(values)
    if n < 2:
        return mean, mean
    t = _t_critical(n - 1)
    margin = t * stddev / math.sqrt(n)
    return mean - margin, mean + margin


def compute_cov(values):
    """Return CoV percentage. Returns 999.99 if fewer than 2 values."""
    if len(values) < 2:
        return 999.99
    _, _, cov = compute_stats(values)
    return cov


def is_converged(values, window, threshold):
    """Check if the last `window` values have CoV <= threshold."""
    n = len(values)
    if n < window:
        return False
    recent = values[n - window:]
    return compute_cov(recent) <= threshold


def median(values):
    """Return the median of a sorted list of values."""
    if not values:
        return 0
    s = sorted(values)
    mid = len(s) // 2
    if len(s) % 2 == 0:
        return (s[mid - 1] + s[mid]) / 2
    return s[mid]


def ref_diff_pct(value, reference):
    """Return percentage difference from reference as formatted string."""
    if reference == 0:
        return "N/A"
    diff = (value - reference) / reference * 100
    return f"{diff:+.1f}"


# ---------------------------------------------------------------------------
# Latency
# ---------------------------------------------------------------------------

def latency_to_ns(wrk_value):
    """Convert wrk latency string (e.g. '5.23ms') to nanoseconds."""
    v = wrk_value.strip()
    if v.endswith("us"):
        return int(float(v[:-2]) * 1_000)
    if v.endswith("ms"):
        return int(float(v[:-2]) * 1_000_000)
    if v.endswith("s"):
        return int(float(v[:-1]) * 1_000_000_000)
    return 0


def parse_latency_str(latency):
    """Parse colon-delimited latency string into a dict.

    3-field (wrk):    p50:p90:p99
    5-field (native): p50:p90:p99:p999:max
    """
    if not latency or ":" not in latency:
        return {}
    parts = latency.split(":")
    parts = [p for p in parts if p]
    if len(parts) == 3:
        return {"p50": parts[0], "p90": parts[1], "p99": parts[2]}
    if len(parts) == 5:
        return {
            "p50": parts[0], "p90": parts[1], "p99": parts[2],
            "p999": parts[3], "max": parts[4],
        }
    return {}


# ---------------------------------------------------------------------------
# JSON I/O
# ---------------------------------------------------------------------------

def build_fork_record(score, unit, **metadata):
    """Build the standard fork JSON structure."""
    record = {"primaryMetric": {"score": score, "scoreUnit": unit}}
    if "scenario" in metadata:
        record["params"] = {"scenarioName": metadata.pop("scenario")}
    record.update(metadata)
    return [record]


def write_fork_json(path, score, unit, **metadata):
    """Write fork JSON file."""
    data = build_fork_record(score, unit, **metadata)
    with open(path, "w") as f:
        json.dump(data, f, indent=4)


def parse_score(path):
    """Read a fork JSON file and return primaryMetric.score. Returns 0.0 on error."""
    try:
        with open(path) as f:
            data = json.load(f)
        return float(data[0]["primaryMetric"]["score"])
    except (OSError, json.JSONDecodeError, KeyError, IndexError, TypeError):
        return 0.0


# ---------------------------------------------------------------------------
# CSV I/O
# ---------------------------------------------------------------------------

def write_csv_report(path, title, unit, throughputs, metadata, latency_str=None):
    """Write a CSV report with comment headers and per-fork rows.

    Args:
        path: output file path
        title: report title (e.g. "SAPL 4.0 Native Benchmark Results")
        unit: column header (e.g. "throughput_ops_s" or "throughput_req_s")
        throughputs: list of per-fork throughput values
        metadata: dict of key-value pairs for comment headers
        latency_str: optional colon-delimited latency string
    """
    mean, stddev, cov = compute_stats(throughputs)

    lines = [f"# {title}"]
    for key, value in metadata.items():
        lines.append(f"# {key}: {value}")
    lines.append(f"# Mean: {mean:.2f} {unit.replace('_', '/')}")
    lines.append(f"# StdDev: {stddev:.2f}")
    lines.append(f"# CoV: {cov:.2f}%")
    lines.append(f"# Forks: {len(throughputs)}")

    if latency_str:
        latency = parse_latency_str(latency_str)
        for key, value in latency.items():
            label = f"p{key[1:]}" if key.startswith("p") else key
            lines.append(f"# Latency {label} (ns): {value}")

    lines.append(f"fork,{unit}")
    for i, v in enumerate(throughputs, 1):
        lines.append(f"{i},{v:.2f}")

    with open(path, "w") as f:
        f.write("\n".join(lines) + "\n")


# ---------------------------------------------------------------------------
# Latency aggregation (replaces summarize-latency.sh)
# ---------------------------------------------------------------------------

def summarize_latency_results(results_dir):
    """Discover seed CSV files, compute median/min/max per percentile,
    write summary.csv and summary.md."""
    results_path = Path(results_dir)
    csv_files = sorted(results_path.glob("*_seed*_decideOnceBlocking_1t.csv"))

    if not csv_files:
        print(f"No latency result files found in {results_dir}")
        return

    scenarios = {}
    for f in csv_files:
        name = f.name.split("_seed")[0]
        scenarios.setdefault(name, []).append(f)

    csv_out = results_path / "summary.csv"
    md_out = results_path / "summary.md"

    csv_lines = [
        "app,scaling_factor,seeds,"
        "p50_median_ns,p90_median_ns,p99_median_ns,p999_median_ns,"
        "p50_min_ns,p50_max_ns,p99_min_ns,p99_max_ns"
    ]

    md_lines = [
        "# Latency Benchmark Summary",
        "",
        f"**Completed:** {len(csv_files)} runs | **Directory:** {results_dir}",
        "",
        "## Latency (median across seeds)",
        "",
        "| App | n | Seeds | p50 (ns) | p90 (ns) | p99 (ns) | p99.9 (ns) "
        "| p50 range | p99 range |",
        "|-----|---|-------|----------|----------|----------|------------|"
        "-----------|-----------|",
    ]

    for scenario_name in sorted(scenarios.keys()):
        files = scenarios[scenario_name]
        parts = scenario_name.rsplit("-", 1)
        app = parts[0] if len(parts) == 2 else scenario_name
        n = parts[1] if len(parts) == 2 else "?"

        percentiles = {"p50": [], "p90": [], "p99": [], "p999": []}
        for f in files:
            content = f.read_text()
            for line in content.splitlines():
                if line.startswith("# Latency p50"):
                    percentiles["p50"].append(int(line.split(": ")[1]))
                elif line.startswith("# Latency p90"):
                    percentiles["p90"].append(int(line.split(": ")[1]))
                elif line.startswith("# Latency p99.9"):
                    percentiles["p999"].append(int(line.split(": ")[1]))
                elif line.startswith("# Latency p99 ") or line.startswith("# Latency p99:"):
                    percentiles["p99"].append(int(line.split(": ")[1]))

        count = len(files)
        p50_med = int(median(percentiles["p50"])) if percentiles["p50"] else 0
        p90_med = int(median(percentiles["p90"])) if percentiles["p90"] else 0
        p99_med = int(median(percentiles["p99"])) if percentiles["p99"] else 0
        p999_med = int(median(percentiles["p999"])) if percentiles["p999"] else 0

        p50_vals = sorted(percentiles["p50"]) if percentiles["p50"] else [0]
        p99_vals = sorted(percentiles["p99"]) if percentiles["p99"] else [0]

        csv_lines.append(
            f"{app},{n},{count},"
            f"{p50_med},{p90_med},{p99_med},{p999_med},"
            f"{p50_vals[0]},{p50_vals[-1]},{p99_vals[0]},{p99_vals[-1]}"
        )
        md_lines.append(
            f"| {app} | {n} | {count} | {p50_med} | {p90_med} | {p99_med} "
            f"| {p999_med} | {p50_vals[0]}-{p50_vals[-1]} "
            f"| {p99_vals[0]}-{p99_vals[-1]} |"
        )

    md_lines.extend(["", "## Raw file count by scenario", "", "```"])
    for scenario_name in sorted(scenarios.keys()):
        count = len(scenarios[scenario_name])
        md_lines.append(f"  {scenario_name:<20s} {count:3d} seeds")
    md_lines.append("```")

    csv_out.write_text("\n".join(csv_lines) + "\n")
    md_out.write_text("\n".join(md_lines) + "\n")

    print(f"Summary written:")
    print(f"  CSV: {csv_out}")
    print(f"  MD:  {md_out}")
    print()
    print(md_out.read_text())


# ---------------------------------------------------------------------------
# General results aggregation
# ---------------------------------------------------------------------------

def _parse_csv_metadata(path):
    """Parse comment headers from a benchmark CSV file into a dict."""
    meta = {}
    with open(path) as f:
        for line in f:
            if not line.startswith("#"):
                break
            line = line[2:].strip()
            if ": " in line:
                key, value = line.split(": ", 1)
                meta[key] = value
    return meta


def _parse_csv_throughputs(path):
    """Parse throughput values from data rows of a benchmark CSV file."""
    values = []
    in_data = False
    with open(path) as f:
        for line in f:
            if line.startswith("#"):
                continue
            if not in_data:
                in_data = True
                continue
            parts = line.strip().split(",")
            if len(parts) >= 2:
                try:
                    values.append(float(parts[1]))
                except ValueError:
                    pass
    return values


def _format_ops(value):
    """Format ops/s with K/M suffix for readable tables."""
    if value >= 1_000_000:
        return f"{value / 1_000_000:.2f}M"
    if value >= 1_000:
        return f"{value / 1_000:.0f}K"
    return f"{value:.0f}"


def _format_ci(ci_lower, ci_upper):
    """Format confidence interval as +/- range using the same K/M suffix."""
    margin = (ci_upper - ci_lower) / 2
    mean = (ci_upper + ci_lower) / 2
    if mean == 0:
        return ""
    pct = margin / mean * 100
    return f"+/-{pct:.1f}%"


def _find_latency_json(results_path, csv_stem):
    """Find the matching latency JSON file for a CSV result file."""
    latency_path = results_path / (csv_stem + "_latency.json")
    if latency_path.exists():
        try:
            with open(latency_path) as f:
                data = json.load(f)
            pm = data[0].get("primaryMetric", {})
            ci = pm.get("scoreConfidence", [])
            return {
                "ci_lower": ci[0] if len(ci) >= 2 else 0,
                "ci_upper": ci[1] if len(ci) >= 2 else 0,
            }
        except (json.JSONDecodeError, KeyError, IndexError, OSError):
            pass
    return {"ci_lower": 0, "ci_upper": 0}


def summarize_results(results_dir):
    """Aggregate all CSV results in a directory into summary.csv and summary.md.

    Reports mean throughput with 95% CI, latency percentiles with JMH CI.
    """
    results_path = Path(results_dir)
    csv_files = sorted(results_path.glob("*.csv"))
    csv_files = [f for f in csv_files if f.name != "summary.csv"]

    if not csv_files:
        print(f"No CSV result files found in {results_dir}")
        return

    rows = []
    for f in csv_files:
        meta = _parse_csv_metadata(f)
        throughputs = _parse_csv_throughputs(f)
        if not throughputs:
            continue
        mean_thrpt, _, cov = compute_stats(throughputs)
        ci_lower, ci_upper = confidence_interval_95(throughputs)

        csv_stem = f.stem
        latency_ci = _find_latency_json(results_path, csv_stem)

        rows.append({
            "scenario": meta.get("Scenario", "?"),
            "method": meta.get("Method", "?"),
            "threads": meta.get("Threads", "?"),
            "indexing": meta.get("Indexing", "?"),
            "mean_ops": mean_thrpt,
            "ci_lower": ci_lower,
            "ci_upper": ci_upper,
            "cov": cov,
            "forks": len(throughputs),
            "p50": meta.get("Latency p50 (ns)", ""),
            "p90": meta.get("Latency p90 (ns)", ""),
            "p99": meta.get("Latency p99 (ns)", ""),
            "lat_ci_lower": latency_ci["ci_lower"],
            "lat_ci_upper": latency_ci["ci_upper"],
            "permit": meta.get("Decisions PERMIT", ""),
            "deny": meta.get("Decisions DENY", ""),
        })

    rows.sort(key=lambda r: (r["scenario"], r["method"], int(r["threads"])))

    csv_out = results_path / "summary.csv"
    md_out = results_path / "summary.md"

    csv_lines = [
        "scenario,method,threads,indexing,"
        "mean_ops_s,ci_lower_ops_s,ci_upper_ops_s,cov_pct,forks,"
        "p50_ns,p90_ns,p99_ns,lat_ci_lower_ns,lat_ci_upper_ns,"
        "permit,deny"
    ]
    for r in rows:
        csv_lines.append(
            f"{r['scenario']},{r['method']},{r['threads']},{r['indexing']},"
            f"{r['mean_ops']:.2f},{r['ci_lower']:.2f},{r['ci_upper']:.2f},"
            f"{r['cov']:.2f},{r['forks']},"
            f"{r['p50']},{r['p90']},{r['p99']},"
            f"{r['lat_ci_lower']:.1f},{r['lat_ci_upper']:.1f},"
            f"{r['permit']},{r['deny']}"
        )

    md_lines = [
        "# Benchmark Summary",
        "",
        f"**Results:** {len(rows)} data points | **Directory:** {results_dir}",
        "",
        "| Scenario | Threads | Throughput | 95% CI | p50 (ns) | p99 (ns) | Latency CI (ns) |",
        "|----------|---------|------------|--------|----------|----------|-----------------|",
    ]
    for r in rows:
        thrpt_ci = _format_ci(r["ci_lower"], r["ci_upper"])
        lat_ci = ""
        if r["lat_ci_lower"] > 0 and r["lat_ci_upper"] > 0:
            lat_ci = f"[{r['lat_ci_lower']:.0f}, {r['lat_ci_upper']:.0f}]"
        md_lines.append(
            f"| {r['scenario']} | {r['threads']} "
            f"| {_format_ops(r['mean_ops'])} ops/s | {thrpt_ci} "
            f"| {r['p50']} | {r['p99']} "
            f"| {lat_ci} |"
        )

    csv_out.write_text("\n".join(csv_lines) + "\n")
    md_out.write_text("\n".join(md_lines) + "\n")

    print(f"Summary written:")
    print(f"  CSV: {csv_out}")
    print(f"  MD:  {md_out}")
    print()
    print(md_out.read_text())


# ---------------------------------------------------------------------------
# CLI subcommands
# ---------------------------------------------------------------------------

def cmd_cov(args):
    print(f"{compute_cov(args.values):.2f}")


def cmd_converged(args):
    result = is_converged(args.values, args.window, args.threshold)
    print("true" if result else "false")


def cmd_write_fork_json(args):
    metadata = {}
    for attr in ["benchmark", "mode", "transport", "threads", "cores",
                 "connections", "vt_per_connection", "runtime",
                 "warmup_iterations", "warmup_time", "warmup_seconds",
                 "measurement_time", "scenario", "indexing"]:
        val = getattr(args, attr, None)
        if val is not None:
            key = attr.replace("_", "")
            if attr == "vt_per_connection":
                key = "vtPerConnection"
            elif attr == "warmup_iterations":
                key = "warmupIterations"
            elif attr == "warmup_time":
                key = "warmupTime"
            elif attr == "warmup_seconds":
                key = "warmupSeconds"
            elif attr == "measurement_time":
                key = "measurementTime"
            elif attr == "scenario":
                key = "scenario"
            else:
                key = attr
            metadata[key] = val
    write_fork_json(args.output, args.score, args.unit, **metadata)


def cmd_write_csv(args):
    throughputs = [float(v) for v in args.throughputs.split(",") if v.strip()]
    metadata = {}
    for attr in ["scenario", "method", "threads", "runtime", "cores",
                 "connections", "vt_per_connection", "warmup", "measurement",
                 "convergence_threshold", "convergence_window"]:
        val = getattr(args, attr, None)
        if val is not None:
            label = attr.replace("_", " ").title()
            metadata[label] = val
    write_csv_report(args.output, args.title, args.unit, throughputs,
                     metadata, args.latency)


def cmd_parse_score(args):
    for path in args.files:
        print(f"{parse_score(path):.0f}")


def cmd_latency_to_ns(args):
    print(latency_to_ns(args.value))


def cmd_ref_diff(args):
    print(ref_diff_pct(args.value, args.reference))


def cmd_summarize(args):
    summarize_results(args.results_dir)


def cmd_summarize_latency(args):
    summarize_latency_results(args.results_dir)


# ---------------------------------------------------------------------------
# CLI entry point
# ---------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(
        description="SAPL benchmark statistics and data I/O"
    )
    sub = parser.add_subparsers(dest="command", required=True)

    # cov
    p = sub.add_parser("cov", help="Coefficient of variation")
    p.add_argument("values", type=float, nargs="+")
    p.set_defaults(func=cmd_cov)

    # converged
    p = sub.add_parser("converged", help="Check convergence")
    p.add_argument("--window", type=int, required=True)
    p.add_argument("--threshold", type=float, required=True)
    p.add_argument("values", type=float, nargs="+")
    p.set_defaults(func=cmd_converged)

    # write-fork-json
    p = sub.add_parser("write-fork-json", help="Write per-fork JSON result")
    p.add_argument("--output", required=True)
    p.add_argument("--score", type=float, required=True)
    p.add_argument("--unit", required=True)
    p.add_argument("--benchmark")
    p.add_argument("--mode")
    p.add_argument("--transport")
    p.add_argument("--threads", type=int)
    p.add_argument("--cores", type=int)
    p.add_argument("--connections", type=int)
    p.add_argument("--vt-per-connection", type=int)
    p.add_argument("--runtime")
    p.add_argument("--warmup-iterations", type=int)
    p.add_argument("--warmup-time")
    p.add_argument("--warmup-seconds", type=int)
    p.add_argument("--measurement-time")
    p.add_argument("--scenario")
    p.add_argument("--indexing")
    p.set_defaults(func=cmd_write_fork_json)

    # write-csv
    p = sub.add_parser("write-csv", help="Write CSV report with statistics")
    p.add_argument("--output", required=True)
    p.add_argument("--throughputs", required=True,
                   help="Comma-separated fork throughput values")
    p.add_argument("--title", required=True)
    p.add_argument("--unit", required=True)
    p.add_argument("--scenario")
    p.add_argument("--method")
    p.add_argument("--threads", type=int)
    p.add_argument("--runtime")
    p.add_argument("--cores", type=int)
    p.add_argument("--connections", type=int)
    p.add_argument("--vt-per-connection", type=int)
    p.add_argument("--warmup")
    p.add_argument("--measurement")
    p.add_argument("--convergence-threshold")
    p.add_argument("--convergence-window")
    p.add_argument("--latency", help="Colon-delimited latency percentiles")
    p.set_defaults(func=cmd_write_csv)

    # parse-score
    p = sub.add_parser("parse-score", help="Extract score from fork JSON")
    p.add_argument("files", nargs="+")
    p.set_defaults(func=cmd_parse_score)

    # latency-to-ns
    p = sub.add_parser("latency-to-ns", help="Convert wrk latency to ns")
    p.add_argument("value")
    p.set_defaults(func=cmd_latency_to_ns)

    # ref-diff
    p = sub.add_parser("ref-diff", help="Percentage difference from reference")
    p.add_argument("value", type=float)
    p.add_argument("reference", type=float)
    p.set_defaults(func=cmd_ref_diff)

    # summarize
    p = sub.add_parser("summarize",
                        help="Aggregate all CSV results into summary table")
    p.add_argument("results_dir")
    p.set_defaults(func=cmd_summarize)

    # summarize-latency
    p = sub.add_parser("summarize-latency",
                        help="Aggregate latency results into summary")
    p.add_argument("results_dir")
    p.set_defaults(func=cmd_summarize_latency)

    args = parser.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
