#!/usr/bin/env python3
"""
perf-compare.py — k6 regression detector

Compares two k6 result JSON files and reports regressions vs the
NFR thresholds declared in the test scripts. Used by:
  ci/github-actions/performance.yml
  ci/jenkins/PerformanceRegression.groovy

Exit codes:
  0 = no regression (results within tolerance)
  1 = regression detected
  2 = usage / config error
"""

import argparse
import json
import sys
from pathlib import Path
from typing import Any

# Allowed drift per metric (percent). Anything beyond this is a regression.
DEFAULT_TOLERANCE = 5.0  # 5% slower = regression

# Hard NFR thresholds (failing these is a regression regardless of baseline)
NFR_THRESHOLDS = {
    "http_req_duration{scenario:load}": {"p(95)": 300.0, "p(99)": 500.0},
    "http_req_duration{scenario:smoke}": {"p(95)": 250.0, "p(99)": 400.0},
    "http_req_duration{scenario:stress}": {"p(95)": 600.0, "p(99)": 1000.0},
    "http_req_failed": {"rate<": 0.01},
    "dashboard_latency": {"p(95)": 300.0},
    "config_publish_latency": {"p(95)": 100.0},
    "double_charge_rate": {"rate<": 0.001},
    "widget_success_rate": {"rate>": 0.95},
    "partial_response_rate": {"rate>": 0.90},
}


def load_k6_results(path: Path) -> dict[str, Any]:
    """Load a k6 JSON summary file."""
    with path.open("r", encoding="utf-8") as f:
        return json.load(f)


def get_metric(summary: dict[str, Any], key: str) -> dict[str, Any] | None:
    """Read a metric block from the k6 root summary."""
    return summary.get("root_metrics", {}).get(key) or summary.get(key)


def extract_p(value: dict[str, Any], percentile: str) -> float | None:
    """Read p(50)/p(95)/p(99) from a Trend metric."""
    if "values" not in value:
        return None
    # k6 writes keys like "p(50)", "p(95)", "p(99)"
    return value["values"].get(percentile)


def extract_rate(value: dict[str, Any]) -> float | None:
    if "values" not in value:
        return None
    return value["values"].get("rate")


def check_nfr_thresholds(
    summary: dict[str, Any]
) -> list[str]:
    """Check metrics against hard NFR thresholds. Return list of violations."""
    violations: list[str] = []
    for metric_key, limits in NFR_THRESHOLDS.items():
        block = get_metric(summary, metric_key)
        if not block:
            continue
        for limit_key, limit_value in limits.items():
            if limit_key.startswith("p("):
                actual = extract_p(block, limit_key)
                if actual is not None and actual > limit_value:
                    violations.append(
                        f"NFR VIOLATION: {metric_key} {limit_key} = {actual:.1f}ms "
                        f"(limit: < {limit_value}ms)"
                    )
            elif limit_key == "rate<":
                actual = extract_rate(block)
                if actual is not None and actual > limit_value:
                    violations.append(
                        f"NFR VIOLATION: {metric_key} rate = {actual:.4f} "
                        f"(limit: < {limit_value})"
                    )
            elif limit_key == "rate>":
                actual = extract_rate(block)
                if actual is not None and actual < limit_value:
                    violations.append(
                        f"NFR VIOLATION: {metric_key} rate = {actual:.4f} "
                        f"(limit: > {limit_value})"
                    )
    return violations


def check_drift(
    baseline: dict[str, Any],
    current: dict[str, Any],
    tolerance_pct: float,
) -> list[str]:
    """Compare p(95) of every common metric; flag drift beyond tolerance."""
    drifts: list[str] = []
    base_root = baseline.get("root_metrics", {})
    curr_root = current.get("root_metrics", {})

    for metric_key, base_block in base_root.items():
        curr_block = curr_root.get(metric_key)
        if not curr_block:
            continue
        # Compare p(95) on Trend metrics
        for pct in ("p(50)", "p(95)", "p(99)"):
            base_val = extract_p(base_block, pct)
            curr_val = extract_p(curr_block, pct)
            if base_val is None or curr_val is None or base_val <= 0:
                continue
            delta_pct = ((curr_val - base_val) / base_val) * 100.0
            if delta_pct > tolerance_pct:
                drifts.append(
                    f"REGRESSION: {metric_key} {pct} drifted {delta_pct:+.1f}% "
                    f"({base_val:.1f}ms → {curr_val:.1f}ms)"
                )
        # Compare rate on Rate/Counter metrics
        base_rate = extract_rate(base_block)
        curr_rate = extract_rate(curr_block)
        if (
            base_rate is not None
            and curr_rate is not None
            and "rate" in (base_block.get("type", "").lower())
        ):
            if base_rate == 0:
                # New error rate emerging
                if curr_rate > 0.001:
                    drifts.append(
                        f"REGRESSION: {metric_key} rate went 0 → {curr_rate:.4f}"
                    )
            else:
                delta_pct = ((curr_rate - base_rate) / base_rate) * 100.0
                if delta_pct > tolerance_pct:
                    drifts.append(
                        f"REGRESSION: {metric_key} rate drifted {delta_pct:+.1f}% "
                        f"({base_rate:.4f} → {curr_rate:.4f})"
                    )
    return drifts


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Compare k6 results and report regressions."
    )
    parser.add_argument(
        "--baseline",
        type=Path,
        required=True,
        help="Path to baseline k6 summary JSON (known-good run).",
    )
    parser.add_argument(
        "--current",
        type=Path,
        required=True,
        help="Path to current k6 summary JSON (this run).",
    )
    parser.add_argument(
        "--tolerance",
        type=float,
        default=DEFAULT_TOLERANCE,
        help=f"Allowed drift in percent (default: {DEFAULT_TOLERANCE})",
    )
    parser.add_argument(
        "--strict",
        action="store_true",
        help="Fail on any drift, even within tolerance (use for hot paths).",
    )
    args = parser.parse_args()

    if not args.baseline.exists():
        print(f"ERROR: baseline file not found: {args.baseline}", file=sys.stderr)
        return 2
    if not args.current.exists():
        print(f"ERROR: current file not found: {args.current}", file=sys.stderr)
        return 2

    baseline = load_k6_results(args.baseline)
    current = load_k6_results(args.current)

    nfr_violations = check_nfr_thresholds(current)
    drift_violations = check_drift(baseline, current, args.tolerance)

    print("=" * 70)
    print("PERFORMANCE COMPARISON REPORT")
    print("=" * 70)
    print(f"Baseline: {args.baseline}")
    print(f"Current : {args.current}")
    print(f"Tolerance: ±{args.tolerance}%")
    print("=" * 70)

    if not nfr_violations and not drift_violations:
        print("OK: no regressions detected")
        return 0

    if nfr_violations:
        print()
        print("NFR THRESHOLD VIOLATIONS:")
        for v in nfr_violations:
            print(f"  ✗ {v}")
    if drift_violations:
        print()
        print("DRIFT REGRESSIONS:")
        for d in drift_violations:
            print(f"  ✗ {d}")
    print()
    if args.strict and (nfr_violations or drift_violations):
        print("FAILED: strict mode requires zero violations")
        return 1
    if nfr_violations:
        # NFR violations are always a hard fail
        print("FAILED: NFR violation")
        return 1
    print("FAILED: drift beyond tolerance")
    return 1


if __name__ == "__main__":
    sys.exit(main())
