import csv, json, subprocess, sys
repo, csvp = sys.argv[1], sys.argv[2]
def load(sha):
    out = subprocess.run(['git', '-C', repo, 'show', f'{sha}:src/main/resources/countriesV2.json'], capture_output=True)
    if out.returncode: return None
    return json.loads(out.stdout.decode('utf-8'))
def recip(data):
    by = {c['alpha3Code']: c for c in data}
    v = set()
    for c in data:
        for b in c.get('borders') or []:
            if b not in by: v.add((c['alpha3Code'], b, 'unknown-code'))
            elif c['alpha3Code'] not in (by[b].get('borders') or []): v.add((c['alpha3Code'], b, 'non-reciprocal'))
    return v
def diff(a, b):
    A = {c['alpha3Code']: c for c in a}; B = {c['alpha3Code']: c for c in b}
    ch = []
    for k in sorted(set(A) | set(B)):
        if k not in A or k not in B: ch.append((k, 'added/removed')); continue
        for f in sorted(set(A[k]) | set(B[k])):
            if A[k].get(f) != B[k].get(f): ch.append((k, f))
    return ch
for r in csv.DictReader(open(csvp, newline='')):
    bsha, psha = r['buggy_sha'].strip(), r['patch_sha'].split('|')[0].strip()
    a, b = load(bsha), load(psha)
    if a is None or b is None:
        print(r['issue_no'], 'cannot load', bsha[:7] if a is None else '', psha[:7] if b is None else ''); continue
    d = diff(a, b)
    ra, rb = recip(a), recip(b)
    fixed = ra - rb; introduced = rb - ra
    print(f"#{r['issue_no']} buggy={bsha[:7]} patch={psha[:7]} changed={d[:6]}{' …' if len(d)>6 else ''} | reciprocity violations buggy={len(ra)} patched={len(rb)} removed_by_patch={sorted(fixed)[:4]}")
