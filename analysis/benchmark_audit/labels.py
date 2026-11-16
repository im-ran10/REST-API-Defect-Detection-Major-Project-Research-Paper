import re, os, csv, sys, glob, collections
d4r, rp = sys.argv[1], sys.argv[2]
br = os.path.join(d4r, 'bug_replication')
def norm(u): return u.strip().rstrip('/').lower()
top = {}
for line in open(os.path.join(br, 'README.md')):
    m = re.match(r'\|\s*\d+\s*\|\s*([\w\-]+)\s*\|\s*\[#(\d+)\]\(([^)]*)\)\s*\|\s*([^|]+)\|\s*([^|]+)\|', line)
    if m:
        proj, iid, url, t, st = m.groups()
        top[(proj.lower(), iid)] = dict(url=norm(url), type=t.strip(), sub=st.strip())
proj_lbl = {}
for f in glob.glob(os.path.join(br, '*', 'README.md')):
    proj = os.path.basename(os.path.dirname(f)).lower()
    for line in open(f, encoding='utf-8', errors='replace'):
        cells = [c.strip() for c in line.strip().strip('|').split('|')]
        if len(cells) >= 4:
            m = None
            for i, c in enumerate(cells[:3]):
                m = re.match(r'\[#?(\d+)\]\(', c)
                if m: break
            if m:
                proj_lbl[(proj, m.group(1))] = dict(type=cells[i+1], sub=cells[i+2])
ref = {}
with open(os.path.join(rp, 'RQ2/patch_type_analysis/607_defects_types.csv'), newline='', encoding='utf-8', errors='replace') as fh:
    for r in csv.DictReader(fh, delimiter='\t'):
        ref[norm(r['url'])] = r['defect type'].strip()
same_sub = diff_proj = 0; rows = []
for k, v in sorted(top.items()):
    p = proj_lbl.get(k)
    r = ref.get(v['url'])
    if r is None:
        # try alternate issue url forms
        alt = [u for u in ref if u.endswith('/issues/' + k[1]) and k[0].split('-')[0] in u]
        r = ref[alt[0]] if len(alt) == 1 else None
    flag_proj = (p is None) or (p['sub'] != v['sub']) or (p['type'] != v['type'])
    flag_ref = (r != v['sub'])
    rows.append((k, v['type'], v['sub'], p, r, flag_proj, flag_ref))
print("top-level entries:", len(top), " project-README entries:", len(proj_lbl), " ref rows:", len(ref))
print("mismatch top vs project README:", sum(1 for x in rows if x[5]))
print("mismatch top subtype vs 607 csv:", sum(1 for x in rows if x[6]))
for k, t, s, p, r, fp, fr in rows:
    if fp or fr:
        print(f"{k[0]}#{k[1]} | TOP: {s} | PROJ: {(p or {}).get('type','-')} / {(p or {}).get('sub','-')} | 607csv: {r}" + ("  <PROJ>" if fp else "") + ("  <CSV>" if fr else ""))
