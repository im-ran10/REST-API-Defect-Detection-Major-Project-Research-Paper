import os, sys, json, re, yaml
br = os.path.join(sys.argv[1], 'bug_replication')
def norm(p):
    p = p.strip().rstrip('/').rstrip('`')
    p = re.sub(r'\{[^}]*\}?', '{}', p)
    return p.lower()
def trig(readme):
    s = open(readme, encoding='utf-8', errors='replace').read()
    m = re.search(r'triggering endpoints?\**:?\s*(.*?)(\n#|\*\*Step|## \*\*Trig|## Trig)', s, re.I | re.S)
    return sorted(set(re.findall(r'/[A-Za-z0-9_{}./\-]+', m.group(1)))) if m else []
tot = full = part = none = notrig = 0
for p in sorted(os.listdir(br)):
    pd = os.path.join(br, p)
    if not os.path.isdir(pd): continue
    for d in sorted(os.listdir(pd)):
        dd = os.path.join(pd, d)
        if not os.path.isdir(dd): continue
        specs = [f for f in os.listdir(dd) if "spec" in f]
        rd = os.path.join(dd, 'README.md')
        if not specs: continue
        f = os.path.join(dd, specs[0]); t = open(f, encoding='utf-8', errors='replace').read()
        o = json.loads(t) if f.endswith('json') else yaml.safe_load(t)
        sp = [norm(x) for x in o['paths']]
        tr = trig(rd) if os.path.exists(rd) else []
        tot += 1
        if not tr:
            notrig += 1; print(f"{d}: no triggering endpoint parsed"); continue
        hits = [x for x in tr if any(s == norm(x) or s.endswith(norm(x)) or norm(x).endswith(s) for s in sp)]
        if len(hits) == len(tr): full += 1
        elif hits: part += 1; print(f"{d}: PARTIAL {len(hits)}/{len(tr)} missing={[x for x in tr if x not in hits]}")
        else: none += 1; print(f"{d}: NONE in spec; trig={tr}")
print(f"total={tot} all-trig-in-spec={full} partial={part} none={none} unparsed={notrig}")
