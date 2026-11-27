#!/usr/bin/env python3
"""adherence.py — D45 strategy adherence: sample, coding sheets and scores (T11).

sample:  python3 adherence.py sample WORK_DIR --out DIR [--n 60] [--seed 4545]
    From the Day-5 development generation run (arms B and C), draws per arm n tests from schema-valid calls,
    stratified by assigned strategy in proportion to its slot count, excluding default-strategy slots (reported).
    Writes DIR/coding_owner.csv (all 2n), DIR/coding_second.csv (a random 30%, without the owner's codes) and
    DIR/sample_key.json (sample ID -> call, index). Coders see the strategy and its adherence rule (D45: not arm-blind).
score:   python3 adherence.py score DIR [--two-coders]
    --two-coders (D58): coding_owner.csv and coding_second.csv each hold codes for ALL tests from two independent AI
    coders; a test is Adherent only if both say Adherent; kappa over all tests.
    Reads the filled sheets (code column: Adherent / Partial / Not adherent). Per arm: Adherent / n with a 95%
    Wilson interval; gate = B >= 50% and C >= 50% (point estimates); Cohen's kappa on the double-coded subset.
"""
import argparse
import csv
import json
import math
import os
import random
from collections import defaultdict

CODES = ("Adherent", "Partial", "Not adherent")


def catalogue(arm):
    here = os.path.dirname(os.path.abspath(__file__))
    p = os.path.join(here, "..", "..", "system", "src", "main", "resources", "catalogues", f"{arm}.json")
    c = json.load(open(p))
    return c["default"], {e["id"]: e for e in c["entries"]}


def sample(work, out, n, seed):
    rnd = random.Random(seed)
    rows, defaults = [], defaultdict(int)
    by_arm = defaultdict(lambda: defaultdict(list))
    for line in open(os.path.join(work, "calls.jsonl")):
        if not line.strip():
            continue
        c = json.loads(line)
        arm = c.get("arm")
        if arm not in ("B", "C") or not c.get("validation", {}).get("schemaValid"):
            continue
        default, _ = catalogue(arm)
        tests = json.loads(c["response"]).get("tests", [])
        reasons = c.get("selectionReasons", [])
        for i, strat in enumerate(c.get("strategies", [])):
            if i >= len(tests):
                continue
            if strat == default and i < len(reasons) and reasons[i].startswith("default"):
                defaults[arm] += 1
                continue
            by_arm[arm][strat].append((c["callId"], i, tests[i]))
    key = {}
    for arm in ("B", "C"):
        _, entries = catalogue(arm)
        pool = by_arm[arm]
        total = sum(len(v) for v in pool.values())
        if total < n:
            raise SystemExit(f"arm {arm}: only {total} non-default tests, need {n}")
        # largest-remainder allocation in proportion to slot counts
        quota = {s: n * len(v) / total for s, v in pool.items()}
        alloc = {s: int(q) for s, q in quota.items()}
        for s in sorted(quota, key=lambda s: (-(quota[s] - alloc[s]), s))[: n - sum(alloc.values())]:
            alloc[s] += 1
        for s in sorted(pool):
            for call, i, test in rnd.sample(pool[s], alloc[s]):
                sid = f"S{len(rows) + 1:03d}"
                e = entries[s]
                rows.append({"sampleId": sid, "strategy": f"{s} {e['name']}", "probe": e["probe"], "oracle": e["oracle"],
                             "adherence_probe": e["adherence"]["probe"], "adherence_oracle": e["adherence"]["oracle"],
                             "test": json.dumps(test, ensure_ascii=False), "code": "", "note": ""})
                key[sid] = {"arm": arm, "strategy": s, "callId": call, "index": i}
    rnd.shuffle(rows)
    os.makedirs(out, exist_ok=True)
    fields = list(rows[0].keys())
    with open(os.path.join(out, "coding_owner.csv"), "w", newline="") as f:
        w = csv.DictWriter(f, fields); w.writeheader(); w.writerows(rows)
    second = rnd.sample(rows, round(0.3 * len(rows)))
    with open(os.path.join(out, "coding_second.csv"), "w", newline="") as f:
        w = csv.DictWriter(f, fields); w.writeheader(); w.writerows(second)
    json.dump({"seed": seed, "n_per_arm": n, "default_slots_excluded": dict(defaults), "key": key},
              open(os.path.join(out, "sample_key.json"), "w"), indent=2)
    print(f"sampled {len(rows)} tests ({n} per arm), second coder {len(second)}; default slots excluded {dict(defaults)}")


def wilson(k, n, z=1.96):
    if n == 0:
        return (float("nan"), float("nan"))
    p = k / n
    c = (p + z * z / (2 * n)) / (1 + z * z / n)
    h = z * math.sqrt(p * (1 - p) / n + z * z / (4 * n * n)) / (1 + z * z / n)
    return (c - h, c + h)


def kappa(a, b):
    n = len(a)
    if n == 0:
        return None
    po = sum(x == y for x, y in zip(a, b)) / n
    pe = sum((a.count(c) / n) * (b.count(c) / n) for c in CODES)
    return 1.0 if pe == 1 else (po - pe) / (1 - pe)


def read_csv(path):
    """Spreadsheet programs (Excel, WPS) may save CSV as UTF-8 with BOM, or in the Windows code page."""
    raw = open(path, "rb").read()
    for enc in ("utf-8-sig", "cp1252", "latin-1"):
        try:
            text = raw.decode(enc)
            break
        except UnicodeDecodeError:
            continue
    # AI chats may wrap the CSV in a code fence or vary the heading (sample_id, SampleId, "Code ")
    lines = [l for l in text.splitlines() if l.strip() and not l.strip().startswith("```")]
    rows = list(csv.DictReader(lines))
    alias = {"sampleid": "sampleId", "sample_id": "sampleId", "sample id": "sampleId", "id": "sampleId",
             "code": "code", "note": "note"}
    out = []
    for r in rows:
        n = {}
        for k, v in r.items():
            if k is None:
                continue
            key = alias.get(k.strip().strip('"').lower(), k.strip())
            n[key] = (v or "").strip()
        out.append(n)
    if out and ("sampleId" not in out[0] or "code" not in out[0]):
        raise SystemExit(f"{os.path.basename(path)}: need columns sampleId and code; found {list(out[0])}. First line: {lines[0][:120]!r}")
    return out


def score(d, two_coders=False):
    key = json.load(open(os.path.join(d, "sample_key.json")))["key"]
    own = {r["sampleId"]: r["code"].strip() for r in read_csv(os.path.join(d, "coding_owner.csv"))}
    norm = {c.lower(): c for c in CODES}
    own = {sid: norm.get(c.lower(), c) for sid, c in own.items()}  # tolerate capitalisation
    bad = {sid: c for sid, c in own.items() if c not in CODES}
    if bad:
        raise SystemExit(f"uncoded or unknown codes: {list(bad)[:5]} ... ({len(bad)})")
    res = {}
    for arm in ("B", "C"):
        ids = [s for s, k in key.items() if k["arm"] == arm]
        k = sum(own[s] == "Adherent" for s in ids)
        by = defaultdict(lambda: [0, 0])
        for s in ids:
            by[key[s]["strategy"]][0] += own[s] == "Adherent"
            by[key[s]["strategy"]][1] += 1
        res[arm] = {"adherent": k, "n": len(ids), "share": k / len(ids), "wilson95": wilson(k, len(ids)),
                    "by_strategy": {s: f"{a}/{n}" for s, (a, n) in sorted(by.items())}}
    res["gate_pass"] = res["B"]["share"] >= 0.5 and res["C"]["share"] >= 0.5
    sec_path = os.path.join(d, "coding_second.csv")
    if two_coders:
        # D58: two independent AI coders code all tests; Adherent only if both say Adherent
        norm = {c.lower(): c for c in CODES}
        sec = {r["sampleId"]: norm.get(r["code"].strip().lower(), r["code"].strip()) for r in read_csv(sec_path)}
        missing = [s_ for s_ in own if sec.get(s_) not in CODES]
        if missing:
            raise SystemExit(f"coder 2 has no valid code for {len(missing)} tests, e.g. {missing[:5]}")
        out = {"rule": "D58: Adherent only if both coders say Adherent"}
        for arm in ("B", "C"):
            ids = [s_ for s_, k in key.items() if k["arm"] == arm]
            both = sum(own[s_] == "Adherent" and sec[s_] == "Adherent" for s_ in ids)
            c2 = sum(sec[s_] == "Adherent" for s_ in ids)
            by = defaultdict(lambda: [0, 0])
            for s_ in ids:
                by[key[s_]["strategy"]][0] += own[s_] == "Adherent" and sec[s_] == "Adherent"
                by[key[s_]["strategy"]][1] += 1
            out[arm] = {"adherent_both": both, "n": len(ids), "share": both / len(ids), "wilson95": wilson(both, len(ids)),
                        "coder1_share": res[arm]["share"], "coder2_share": c2 / len(ids),
                        "by_strategy": {k: f"{a_}/{n_}" for k, (a_, n_) in sorted(by.items())}}
        ids = sorted(own)
        out["kappa_all"] = kappa([own[i] for i in ids], [sec[i] for i in ids])
        out["disagreements"] = sum(own[i] != sec[i] for i in ids)
        out["gate_pass"] = out["B"]["share"] >= 0.5 and out["C"]["share"] >= 0.5
        print(json.dumps(out, indent=2))
        return out
    if os.path.exists(sec_path):
        sec = {r["sampleId"]: r["code"].strip() for r in read_csv(sec_path) if r["code"].strip()}
        ids = sorted(sec)
        res["second_coder"] = {"n": len(ids), "kappa": kappa([own[i] for i in ids], [sec[i] for i in ids]),
                               "disagreements": [i for i in ids if own[i] != sec[i]]}
    print(json.dumps(res, indent=2))
    return res


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd", required=True)
    s = sub.add_parser("sample"); s.add_argument("work"); s.add_argument("--out", required=True)
    s.add_argument("--n", type=int, default=60); s.add_argument("--seed", type=int, default=4545)
    t = sub.add_parser("score"); t.add_argument("dir")
    t.add_argument("--two-coders", action="store_true", help="D58: coder 2 coded all tests; Adherent = both agree")
    a = ap.parse_args()
    sample(a.work, a.out, a.n, a.seed) if a.cmd == "sample" else score(a.dir, a.two_coders)
