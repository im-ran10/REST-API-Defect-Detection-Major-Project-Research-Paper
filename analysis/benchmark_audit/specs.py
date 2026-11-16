import os, sys, json, hashlib, re, collections
try:
    import yaml
except ImportError:
    yaml = None
br = os.path.join(sys.argv[1], 'bug_replication')
out = collections.defaultdict(list)
def load(p):
    txt = open(p, encoding='utf-8', errors='replace').read()
    if p.endswith('.json'):
        return json.loads(txt), txt
    return yaml.safe_load(txt), txt
def trig(readme):
    s = open(readme, encoding='utf-8', errors='replace').read()
    m = re.search(r'triggering endpoints?\**:?\s*(.*?)(\n#|\*\*Step|## \*\*Trig|## Trig)', s, re.I | re.S)
    if not m: return []
    return sorted(set(re.findall(r'/[A-Za-z0-9_{}./\-]+', m.group(1))))
for p in sorted(os.listdir(br)):
    pd = os.path.join(br, p)
    if not os.path.isdir(pd): continue
    for d in sorted(os.listdir(pd)):
        dd = os.path.join(pd, d)
        if not os.path.isdir(dd): continue
        specs = [f for f in os.listdir(dd) if 'spec' in f]
        if not specs:
            out[p].append((d, None, None, None, None, None)); continue
        sp = os.path.join(dd, specs[0])
        try:
            obj, txt = load(sp)
        except Exception as e:
            out[p].append((d, specs[0], 'PARSE-ERR ' + str(e)[:40], None, None, None)); continue
        ver = obj.get('openapi') or obj.get('swagger')
        paths = obj.get('paths') or {}
        h = hashlib.sha256(json.dumps(obj, sort_keys=True).encode()).hexdigest()[:10]
        leak_words = re.findall(r'(?i)\b(bug|issue\s*#?\d+|defect|buggy|patched|fix(?:ed)?)\b', txt)
        t = trig(os.path.join(dd, 'README.md')) if os.path.exists(os.path.join(dd, 'README.md')) else []
        out[p].append((d, specs[0], ver, len(paths), h, (sorted(set(w.lower() for w in leak_words))[:6], t)))
for p, rows in out.items():
    hashes = collections.Counter(r[4] for r in rows if r[4])
    print(f"== {p}: {len(rows)} folders, distinct specs={len(hashes)}, sizes={sorted(set(r[3] for r in rows if r[3] is not None))}")
    for r in rows:
        if r[1] is None:
            print("   ", r[0], "NO SPEC"); continue
        extra = r[5]
        print("   ", r[0], r[2], "paths=", r[3], "hash=", r[4], "shared=" + str(hashes[r[4]]), "words=", extra[0] if extra else '', "trig=", (extra[1] if extra else '')[:4])
