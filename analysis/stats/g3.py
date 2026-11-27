#!/usr/bin/env python3
"""g3.py — G3 development floor check per arm (D57, PROPOSED 27 Nov 2026, declared before the Day-5 runs).

    python3 g3.py WORK_DIR [--label day5]

Per arm: T1 = schema-valid calls / calls (>= 95%); T2 = structurally valid tests / tests in schema-valid calls
(>= 80%); T3 = executed tests (PASS or FAIL) / structurally valid tests (>= 70%), from exec-<label>.jsonl (T3 is
reported as pending until that file exists). Also the median and mean fresh-call time per arm (input to the
40-hour rule). R-T4 shares per arm are A5 (assertion_quality.py), not a G3 bar.
"""
import argparse
import json
import os
import statistics
from collections import defaultdict

BARS = {"T1": 0.95, "T2": 0.80, "T3": 0.70}


def g3(work, label):
    calls = defaultdict(lambda: {"calls": 0, "schema_valid": 0, "tests": 0, "valid": 0, "ms": []})
    valid_keys = {}
    for line in open(os.path.join(work, "calls.jsonl")):
        if not line.strip():
            continue
        c = json.loads(line)
        a = calls[c["arm"]]
        a["calls"] += 1
        if c.get("totalMs") is not None and not c.get("fromCache"):
            a["ms"].append(c["totalMs"])
        v = c.get("validation", {})
        if not v.get("schemaValid"):
            continue
        a["schema_valid"] += 1
        for t in v.get("tests", []):
            a["tests"] += 1
            if t.get("structurallyValid"):
                a["valid"] += 1
                valid_keys[(c["callId"], t["index"])] = c["arm"]
    executed = defaultdict(int)
    exec_path = os.path.join(work, f"exec-{label}.jsonl")
    have_exec = os.path.exists(exec_path)
    if have_exec:
        for line in open(exec_path):
            if line.strip():
                e = json.loads(line)
                for t in e["record"].get("tests", []):
                    arm = valid_keys.get((e["callId"], t["index"]))
                    if arm and t.get("outcome") in ("PASS", "FAIL"):
                        executed[arm] += 1
    out = {}
    for arm, a in sorted(calls.items()):
        t1 = a["schema_valid"] / a["calls"]
        t2 = a["valid"] / a["tests"] if a["tests"] else 0.0
        t3 = executed[arm] / a["valid"] if (have_exec and a["valid"]) else None
        res = {"T1": f"{a['schema_valid']}/{a['calls']} = {100 * t1:.1f}%",
               "T2": f"{a['valid']}/{a['tests']} = {100 * t2:.1f}%",
               "T3": f"{executed[arm]}/{a['valid']} = {100 * t3:.1f}%" if t3 is not None else "pending (no exec file)",
               "pass": {"T1": t1 >= BARS["T1"], "T2": t2 >= BARS["T2"], "T3": None if t3 is None else t3 >= BARS["T3"]},
               "fresh_call_s": {"median": round(statistics.median(a["ms"]) / 1000, 1), "mean": round(statistics.mean(a["ms"]) / 1000, 1),
                                "n": len(a["ms"])} if a["ms"] else None}
        out[arm] = res
    done = all(r["pass"]["T3"] is not None for r in out.values())
    out["G3"] = ("PASS" if all(all(r["pass"].values()) for k, r in out.items() if k != "G3") else "FAIL") if done else "pending"
    return out


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("work")
    ap.add_argument("--label", default="day5")
    a = ap.parse_args()
    print(json.dumps(g3(a.work, a.label), indent=2))
