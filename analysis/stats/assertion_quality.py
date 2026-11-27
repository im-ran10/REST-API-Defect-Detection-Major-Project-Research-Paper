#!/usr/bin/env python3
"""assertion_quality.py — A5 (D55, descriptive): R-T4 class shares of executable tests per arm.

    python3 assertion_quality.py WORK_DIR [WORK_DIR ...] [--label LABEL]

Reads calls.jsonl (archived R-T4 class per test, computed by the system's classifier) and exec-<label>.jsonl (which
tests executed: PASS or FAIL). Prints per arm: MEANINGFUL / STATUS_ONLY / NONE counts and the non-trivial share.
"""
import argparse
import json
import os
from collections import defaultdict


def shares(work_dirs, label):
    counts = defaultdict(lambda: defaultdict(int))
    for w in work_dirs:
        cls = {}
        for line in open(os.path.join(w, "calls.jsonl")):
            if line.strip():
                c = json.loads(line)
                for t in c.get("validation", {}).get("tests", []):
                    cls[(c["callId"], t["index"])] = (c.get("arm", c["callId"].split("|")[1]), t.get("assertionClass"))
        path = os.path.join(w, f"exec-{label}.jsonl")
        for line in open(path):
            if line.strip():
                e = json.loads(line)
                for t in e["record"].get("tests", []):
                    if t.get("outcome") in ("PASS", "FAIL"):
                        arm, k = cls.get((e["callId"], t["index"]), ("?", t.get("assertionClass")))
                        counts[arm][k] += 1
    out = {}
    for arm, c in sorted(counts.items()):
        n = sum(c.values())
        out[arm] = {"executable": n, **dict(c), "non_trivial_share": c.get("MEANINGFUL", 0) / n if n else None}
    return out


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("work", nargs="+")
    ap.add_argument("--label", default="modeltest")
    a = ap.parse_args()
    print(json.dumps(shares(a.work, a.label), indent=2))
