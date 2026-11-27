#!/usr/bin/env python3
"""detection.py — primary and secondary detection analysis (T11; EXPERIMENT_PROTOCOL §10 as amended 23 Nov 2026).

Input: a CSV with one row per (project, defect, arm, seed):
    project,defect,arm,seed,y
where y is 1 (the suite has >= 1 Confirmed candidate), 0 (no Confirmed candidate; invalid/empty output and
ERROR-TEST also count as 0) or NA (ERROR-INFRA after 2 redeploys: missing). Build it with build_y.py.

Output (stdout, and --json FILE): per-defect p(d,a), K_d, D1 = p(B)-p(A) (RQ1), D2 = p(C)-p(B) (RQ2);
two-sided sign-flip test on mean D (exact if <= 20 non-zero differences, else 10,000 flips with a recorded seed);
Holm over RQ1 and RQ2 at alpha 0.05; defect-level percentile bootstrap 95% CI; per-project and project-balanced
means; Wilcoxon signed-rank (sensitivity); exact McNemar on detected-by->=1-seed; A3 cross-seed Jaccard.
Under the D12 fallback (two arms) pass --fallback to test C - A alone.
Only a Holm-significant RQ1/RQ2 result is a statistically supported conclusion; everything else is descriptive.
"""
import argparse
import csv
import itertools
import json
import math
import random
import sys
from collections import defaultdict


def load(path):
    rows = list(csv.DictReader(open(path, newline="")))
    data = defaultdict(dict)  # (project, defect) -> {(arm, seed): y or None}
    for r in rows:
        y = r["y"].strip().upper()
        data[(r["project"], r["defect"])][(r["arm"], int(r["seed"]))] = None if y in ("NA", "") else int(y)
    return data


def rates(data, arms, min_k=3):
    """p(d,a) over K_d = seeds with an outcome for every compared arm; defects with |K_d| < min_k are excluded."""
    out, excluded = {}, []
    for key, cells in sorted(data.items()):
        seeds = sorted({s for (_, s) in cells})
        k = [s for s in seeds if all(cells.get((a, s)) is not None for a in arms)]
        if len(k) < min_k:
            excluded.append((key, len(k)))
            continue
        out[key] = {"K": k, **{a: sum(cells[(a, s)] for s in k) / len(k) for a in arms}}
    return out, excluded


def sign_flip(d, n_mc=10000, seed=20261128):
    """Two-sided p for mean(d) = 0 by flipping signs; exact when <= 20 non-zero values."""
    nz = [x for x in d if x != 0]
    obs = abs(sum(d))
    if not nz:
        return 1.0, "exact (all differences zero)"
    eps = 1e-12
    if len(nz) <= 20:
        hits = 0
        for signs in itertools.product((1, -1), repeat=len(nz)):
            hits += abs(sum(s * x for s, x in zip(signs, nz))) >= obs - eps
        return hits / 2 ** len(nz), f"exact over 2^{len(nz)}"
    rnd = random.Random(seed)
    hits = sum(abs(sum(x if rnd.random() < 0.5 else -x for x in nz)) >= obs - eps for _ in range(n_mc))
    return (hits + 1) / (n_mc + 1), f"{n_mc} random flips, seed {seed}"


def wilcoxon(d):
    """Exact two-sided Wilcoxon signed-rank p (average ranks for ties; zeros dropped); sensitivity only."""
    nz = [x for x in d if x != 0]
    if not nz:
        return 1.0
    order = sorted(range(len(nz)), key=lambda i: abs(nz[i]))
    ranks = [0.0] * len(nz)
    i = 0
    while i < len(order):
        j = i
        while j + 1 < len(order) and abs(nz[order[j + 1]]) == abs(nz[order[i]]):
            j += 1
        for k in range(i, j + 1):
            ranks[order[k]] = (i + j) / 2 + 1
        i = j + 1
    w = sum(r for r, x in zip(ranks, nz) if x > 0)
    mean = sum(ranks) / 2
    if len(nz) > 20:
        var = sum(r * r for r in ranks) / 4
        z = (abs(w - mean) - 0.5) / math.sqrt(var)
        return math.erfc(z / math.sqrt(2))
    hits = 0
    for signs in itertools.product((0, 1), repeat=len(nz)):
        hits += abs(sum(r for s, r in zip(signs, ranks) if s) - mean) >= abs(w - mean) - 1e-12
    return hits / 2 ** len(nz)


def bootstrap_ci(d, n=10000, seed=20261129):
    if not d:
        return (float("nan"), float("nan"))
    rnd = random.Random(seed)
    means = sorted(sum(rnd.choice(d) for _ in d) / len(d) for _ in range(n))
    return means[int(0.025 * n)], means[int(0.975 * n) - 1]


def holm(pvals, alpha=0.05):
    order = sorted(pvals, key=pvals.get)
    out, stop = {}, False
    for i, name in enumerate(order):
        adj = min(1.0, (len(order) - i) * pvals[name])
        if i:
            adj = max(adj, out[order[i - 1]]["p_holm"])
        stop = stop or pvals[name] > alpha / (len(order) - i)
        out[name] = {"p": pvals[name], "p_holm": adj, "significant": not stop}
    return out


def mcnemar(data, a, b):
    """Exact two-sided McNemar on detected-by->=1-seed (b vs a): discordant counts and binomial p."""
    n_ab = n_ba = 0
    for cells in data.values():
        da = any(v == 1 for (arm, _), v in cells.items() if arm == a)
        db = any(v == 1 for (arm, _), v in cells.items() if arm == b)
        n_ab += db and not da
        n_ba += da and not db
    n = n_ab + n_ba
    if n == 0:
        return {"only_" + b: 0, "only_" + a: 0, "p": 1.0}
    k = min(n_ab, n_ba)
    p = min(1.0, 2 * sum(math.comb(n, i) for i in range(k + 1)) / 2 ** n)
    return {"only_" + b: n_ab, "only_" + a: n_ba, "p": p}


def jaccard_by_arm(data, arm):
    """A3: mean pairwise Jaccard of the sets of defects detected by each seed."""
    seeds = sorted({s for cells in data.values() for (a, s) in cells if a == arm})
    sets = {s: {d for d, cells in data.items() if cells.get((arm, s)) == 1} for s in seeds}
    vals = [len(sets[x] & sets[y]) / len(sets[x] | sets[y]) for x, y in itertools.combinations(seeds, 2) if sets[x] | sets[y]]
    return sum(vals) / len(vals) if vals else None


def analyse(data, fallback=False):
    arms = ["A", "C"] if fallback else ["A", "B", "C"]
    p, excluded = rates(data, arms)
    comps = {"C-A": ("C", "A")} if fallback else {"RQ1 B-A": ("B", "A"), "RQ2 C-B": ("C", "B")}
    res = {"arms": arms, "defects": len(p), "excluded": [f"{k[0]}/{k[1]} (K={n})" for k, n in excluded], "comparisons": {}}
    pvals = {}
    for name, (x, y) in comps.items():
        d = [v[x] - v[y] for v in p.values()]
        pv, how = sign_flip(d)
        pvals[name] = pv
        by_proj = defaultdict(list)
        for (proj, _), v in p.items():
            by_proj[proj].append(v[x] - v[y])
        proj_means = {k: sum(v) / len(v) for k, v in by_proj.items()}
        res["comparisons"][name] = {
            "mean_D": sum(d) / len(d) if d else None, "nonzero": sum(1 for v in d if v), "all_zero_defects": sum(1 for v in d if not v),
            "sign_flip_p": pv, "sign_flip": how, "bootstrap_95ci": bootstrap_ci(d),
            "per_project_mean": proj_means,
            "project_balanced_mean": sum(proj_means.values()) / len(proj_means) if proj_means else None,
            "wilcoxon_p_sensitivity": wilcoxon(d), "mcnemar_detected_by_any_seed": mcnemar(data, y, x)}
    for name, h in holm(pvals).items():
        res["comparisons"][name]["holm"] = h
    res["A3_cross_seed_jaccard"] = {a: jaccard_by_arm(data, a) for a in arms}
    res["detection_rate_by_arm"] = {a: (sum(v[a] for v in p.values()) / len(p) if p else None) for a in arms}
    return res


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("ytable")
    ap.add_argument("--fallback", action="store_true", help="D12 fallback: test C - A alone")
    ap.add_argument("--json")
    a = ap.parse_args(argv)
    res = analyse(load(a.ytable), a.fallback)
    print(json.dumps(res, indent=2, default=str))
    if a.json:
        json.dump(res, open(a.json, "w"), indent=2, default=str)


if __name__ == "__main__":
    sys.exit(main())
