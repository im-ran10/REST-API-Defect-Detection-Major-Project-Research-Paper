# Per project: spec metadata and "singleton" operations (present in exactly one defect's spec).
# Prints COUNTS only for non-dev projects; names singleton paths only for dev projects.
import os, sys, json, glob, collections, re, yaml
br = os.path.join(sys.argv[1], 'bug_replication'); DEV = {'restcountries', 'dolibarr'}
def load(p):
    t = open(p, encoding='utf-8', errors='replace').read()
    return json.loads(t) if p.endswith('.json') else yaml.safe_load(t)
for proj in sorted(os.listdir(br)):
    pd = os.path.join(br, proj)
    if not os.path.isdir(pd): continue
    specs = {}
    for d in sorted(os.listdir(pd)):
        f = glob.glob(os.path.join(pd, d, '*spec*'))
        if f: specs[d] = load(f[0])
    if not specs: continue
    meta = collections.Counter((str((s.get('info') or {}).get('title'))[:30], str((s.get('info') or {}).get('version'))[:20]) for s in specs.values())
    ops = {d: {(m.lower(), p) for p, v in (s.get('paths') or {}).items() for m in (v or {}) if m.lower() in ('get','post','put','patch','delete')} for d, s in specs.items()}
    cnt = collections.Counter(o for s in ops.values() for o in s)
    n = len(specs)
    single = {d: sorted(o for o in s if cnt[o] == 1) for d, s in ops.items()} if n > 1 else {}
    with_single = {d: v for d, v in single.items() if v}
    union = len(cnt); inter = sum(1 for o, c in cnt.items() if c == n)
    print(f"== {proj}: specs={n} distinct_meta={dict(meta)} union_ops={union} ops_in_all={inter} defects_with_singleton_ops={len(with_single)} singleton_ops_total={sum(len(v) for v in with_single.values())}")
    if proj in DEV:
        for d, v in with_single.items(): print("   ", d, len(v), [f"{m.upper()} {p}" for m, p in v][:12])
    else:
        print("    singleton counts per defect:", sorted(len(v) for v in with_single.values()))
