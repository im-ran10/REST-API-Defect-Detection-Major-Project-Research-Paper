#!/usr/bin/env python3
"""build_y.py — the Y table for detection.py (T11; EXPERIMENT_PROTOCOL §10 amendment, D29).

    python3 build_y.py --defects defects.csv --verdicts verdicts.csv [--not-evaluable ne.csv]
                       --arms A,B,C --seeds 1,2,3,4,5 --out y.csv

defects.csv:  project,defect,work      (work = generation folder holding candidates-<defect>.jsonl from `verdicts`)
verdicts.csv: candidateId,verdict      (final reviewer verdict: Confirmed / Related / Unrelated / Spurious; after an
                                        unresolved disagreement, the conservative verdict for the primary analysis)
ne.csv:       defect,arm,seed          (ERROR-INFRA after 2 redeploys: missing, not 0)

Y = 1 if the (defect, arm, seed) suite has >= 1 Confirmed candidate; otherwise 0 (invalid or empty output and
ERROR-TEST count as 0); NA for not-evaluable cells. --related counts Related as detected (sensitivity).
"""
import argparse
import csv
import json
import os
import re


def build(defects, verdicts, not_evaluable, arms, seeds, related=False):
    ok = {"Confirmed", "Related"} if related else {"Confirmed"}
    v = {r["candidateId"]: r["verdict"].strip() for r in csv.DictReader(open(verdicts))}
    ne = set()
    if not_evaluable:
        ne = {(r["defect"], r["arm"], int(r["seed"])) for r in csv.DictReader(open(not_evaluable))}
    rows = []
    for d in csv.DictReader(open(defects)):
        safe = re.sub(r"[^A-Za-z0-9._-]", "_", d["defect"])
        path = os.path.join(d["work"], f"candidates-{safe}.jsonl")
        hit = set()
        if os.path.exists(path):
            for line in open(path):
                if line.strip():
                    c = json.loads(line)
                    if c["candidateId"] not in v:
                        raise SystemExit(f"no reviewer verdict for {c['candidateId']} ({d['defect']})")
                    if v[c["candidateId"]] in ok:
                        hit.add((c["arm"], int(c["seed"])))
        for arm in arms:
            for s in seeds:
                y = "NA" if (d["defect"], arm, s) in ne else int((arm, s) in hit)
                rows.append({"project": d["project"], "defect": d["defect"], "arm": arm, "seed": s, "y": y})
    return rows


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--defects", required=True)
    ap.add_argument("--verdicts", required=True)
    ap.add_argument("--not-evaluable")
    ap.add_argument("--arms", default="A,B,C")
    ap.add_argument("--seeds", default="1,2,3,4,5")
    ap.add_argument("--related", action="store_true")
    ap.add_argument("--out", required=True)
    a = ap.parse_args()
    rows = build(a.defects, a.verdicts, a.not_evaluable, a.arms.split(","), [int(x) for x in a.seeds.split(",")], a.related)
    with open(a.out, "w", newline="") as f:
        w = csv.DictWriter(f, ["project", "defect", "arm", "seed", "y"]); w.writeheader(); w.writerows(rows)
    print(f"wrote {len(rows)} rows to {a.out}")
