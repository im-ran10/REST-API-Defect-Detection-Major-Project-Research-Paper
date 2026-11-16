# Metadata-only eligibility audit. Prints per-project AGGREGATES only.
# Never prints README content, triggering endpoints, descriptions or per-defect labels.
import os, re, sys, json, hashlib, glob, collections, csv
import yaml
root = sys.argv[1]; br = os.path.join(root, 'bug_replication')
src = os.path.join(root, 'defects4rest', 'src')
RQ3 = ["podman#14676","seaweedfs#913","flowable-engine#2584","dolibarr#30950","restcountries#201","dolibarr#26307","podman#23981","dolibarr#33949","restcountries#235","signal-cli-rest-api#654","awx#9222","dolibarr#26066","awx#7243","mastodon#30103","enviroCar-server#45","flowable-engine#3856","mastodon#30039","dolibarr#32072","podman#19368","signal-cli-rest-api#387","podman#18424","nocodb#2776","signal-cli-rest-api#103","podman#15720","flowable-engine#3003","seaweedfs#5864","dolibarr#29372","nocodb#7535","kafka-rest#341","seaweedfs#5213"]
sub2t = {}
listed = collections.defaultdict(list)
for line in open(os.path.join(br, 'README.md')):
    m = re.match(r'\|\s*\d+\s*\|\s*([\w\-]+)\s*\|\s*\[#(\d+)\][^|]*\|\s*([^|]+)\|\s*([^|]+)\|', line)
    if m: listed[m.group(1)].append((m.group(2), m.group(4).strip()))
smap = {}
rp = sys.argv[2]
for r in csv.DictReader(open(os.path.join(rp, 'RQ2/patch_type_analysis/defect_main_sub_counts.csv'), newline='')):
    smap[r['Sub Defect Type'].strip()] = r['Defect Type'].split('(')[-1].rstrip(')').strip() if '(' in r['Defect Type'] else r['Defect Type']
def load(p):
    t = open(p, encoding='utf-8', errors='replace').read()
    return json.loads(t) if p.endswith('.json') else yaml.safe_load(t)
hooks = collections.Counter()
for f in glob.glob(os.path.join(src, 'api_dep_setup', '*.py')):
    hooks[os.path.basename(f)[:-3].replace('_', '-')] = len(re.findall(r'^def issue_\d+', open(f).read(), re.M))
print("project | listed | folders | spec | README>0 | distinct specs | spec ver | paths | auth in spec | types(T1..T6 by subtype) | RQ3 | per-issue hooks | deploy: build-from-source / compose")
for p in sorted(listed):
    ids = [i for i, _ in listed[p]]
    pd = os.path.join(br, p); fol = spe = rd = 0; hs = set(); vers = set(); npaths = []; auth = set()
    for i in ids:
        d = os.path.join(pd, f"{p}#{i}")
        if not os.path.isdir(d): continue
        fol += 1
        r = os.path.join(d, 'README.md')
        if os.path.exists(r) and os.path.getsize(r) > 0: rd += 1
        sp = [f for f in os.listdir(d) if 'spec' in f]
        if sp:
            spe += 1; o = load(os.path.join(d, sp[0]))
            hs.add(hashlib.sha256(json.dumps(o, sort_keys=True, default=str).encode()).hexdigest())
            vers.add(str(o.get('openapi') or o.get('swagger'))); npaths.append(len(o.get('paths') or {}))
            sec = (o.get('components') or {}).get('securitySchemes') or o.get('securityDefinitions') or {}
            auth.add('yes' if sec else 'no')
    tc = collections.Counter(smap.get(s, '?') for _, s in listed[p])
    types = ' '.join(f"{k}:{v}" for k, v in sorted(tc.items()))
    rq = sum(1 for x in RQ3 if x.split('#')[0] == p and x.split('#')[1] in ids)
    ds = glob.glob(os.path.join(src, 'deployment_scripts', f'deploy_{p}.py')) + glob.glob(os.path.join(src, 'deployment_scripts', f"deploy_{p.replace('-', '_')}.py"))
    txt = open(ds[0]).read() if ds else ''
    bfs = 'yes' if re.search(r'docker build|\bbuild\b.*Dockerfile|mvn|gradle|git clone', txt) else 'no'
    comp = 'yes' if 'compose' in txt else 'no'
    hk = hooks.get(p, 0) or hooks.get(p.split('-')[0], 0)
    print(f"{p} | {len(ids)} | {fol} | {spe} | {rd} | {len(hs)} | {','.join(sorted(vers))} | {min(npaths) if npaths else '-'}-{max(npaths) if npaths else '-'} | {','.join(sorted(auth))} | {types} | {rq} | {hk} | {bfs}/{comp} ({os.path.basename(ds[0]) if ds else 'no script'})")
