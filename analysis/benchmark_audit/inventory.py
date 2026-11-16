import re, os, csv, sys, glob
root = sys.argv[1]
br = os.path.join(root, 'bug_replication')
listed = {}
for line in open(os.path.join(br, 'README.md')):
    m = re.match(r'\|\s*(\d+)\s*\|\s*([\w\-]+)\s*\|\s*\[#(\d+)\]\(([^)]*)\)\s*\|\s*([^|]+)\|\s*([^|]+)\|\s*\[Replication steps\]\(([^)]*)\)', line)
    if m:
        sn, proj, iid, url, t, st, link = m.groups()
        listed.setdefault(proj, []).append(dict(id=iid, url=url, type=t.strip(), sub=st.strip(), link=link))
folders = {}
for p in sorted(os.listdir(br)):
    pd = os.path.join(br, p)
    if os.path.isdir(pd):
        folders[p] = {}
        for d in sorted(os.listdir(pd)):
            if os.path.isdir(os.path.join(pd, d)):
                folders[p][d] = sorted(os.listdir(os.path.join(pd, d)))
csvs = {}
for f in glob.glob(os.path.join(root, 'defects4rest/data/defect_data/*_info.csv')):
    with open(f, newline='') as fh:
        rows = list(csv.DictReader(fh))
    csvs[os.path.basename(f)] = rows
tot = 0
for proj in listed:
    ids = [x['id'] for x in listed[proj]]
    tot += len(ids)
    fids = {}
    for d in folders.get(proj, {}):
        m = re.search(r'#(\d+)$', d)
        if m: fids[m.group(1)] = d
    missing = [i for i in ids if i not in fids]
    extra = [i for i in fids if i not in ids]
    nospec = [d for i, d in fids.items() if not any('spec' in f for f in folders[proj][d])]
    noreadme = [d for i, d in fids.items() if 'README.md' not in folders[proj][d]]
    print(f"{proj}: listed={len(ids)} folders={len(fids)} missing={missing} extra={extra} nospec={nospec} noreadme={noreadme}")
print("total listed", tot)
for k, rows in sorted(csvs.items()):
    print(k, len(rows), list(rows[0].keys()) if rows else None)
