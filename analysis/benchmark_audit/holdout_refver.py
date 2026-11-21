# Metadata-only reference-version check for holdout projects (D13, PROJECT_STATUS Phase 1).
#
# For each project: picks the earliest-buggy version (by committer date) among the project's
# executable defects that have a replication folder and a spec, then checks that every patch SHA is
# dated after it and is a git descendant of it. Also reports whether a per-issue setup hook exists
# for the reference defect (function NAMES only; hook bodies are never read).
#
# Reads only these CSV columns: issue_no, buggy_sha, patch_sha, buggy_docker_version.
# Never prints titles, descriptions, URLs, patched files, labels, README content or endpoints.
# Prints: counts, the reference defect's issue number, the reference SHA, its date and docker tag.
#
# Usage: python3 -I holdout_refver.py <Defects4REST> <clones-dir> [project ...]
#   <clones-dir>/<repo-name> must be a (blobless) clone of the upstream repository; forks named in
#   the deploy scripts may be added as extra remotes and fetched.
import csv, os, re, subprocess, sys

HOLDOUT = ['awx', 'flowable-engine', 'kafka-rest', 'mastodon', 'netbox', 'nocodb', 'podman']
if len(sys.argv) < 3: sys.exit('usage: python3 -I holdout_refver.py <Defects4REST> <clones-dir> [project ...]')
root, clones = sys.argv[1], sys.argv[2]
projects = sys.argv[3:] or HOLDOUT
br = os.path.join(root, 'bug_replication')
dd = os.path.join(root, 'defects4rest', 'data', 'defect_data')
hookdir = os.path.join(root, 'defects4rest', 'src', 'api_dep_setup')

listed = {}
for line in open(os.path.join(br, 'README.md'), encoding='utf-8'):
    m = re.match(r'\|\s*\d+\s*\|\s*([\w\-]+)\s*\|\s*\[#(\d+)\]', line)
    if m: listed.setdefault(m.group(1).lower(), set()).add(m.group(2))

def folder_has_spec(proj, issue):
    for pd in os.listdir(br):
        if pd.lower() != proj: continue
        # folder names are not consistently cased (e.g. netBox#18669), so match case-insensitively
        for sub in os.listdir(os.path.join(br, pd)):
            if sub.lower() == f'{proj}#{issue}':
                d = os.path.join(br, pd, sub)
                return os.path.isdir(d) and any('spec' in f for f in os.listdir(d))
        return False
    return False

def git(repo, *a):
    r = subprocess.run(['git', '-C', repo, *a], capture_output=True, text=True)
    return r.returncode, r.stdout.strip()

def have(repo, sha):
    if git(repo, 'cat-file', '-e', sha + '^{commit}')[0] == 0: return True
    for rem in git(repo, 'remote')[1].split():
        subprocess.run(['git', '-C', repo, 'fetch', '-q', '--filter=blob:none', rem, sha], capture_output=True)
        if git(repo, 'cat-file', '-e', sha + '^{commit}')[0] == 0: return True
    return False

def date(repo, sha):  # (unix committer time, ISO string); compare the integer, never the string
    t, iso = git(repo, 'show', '-s', '--format=%ct %cI', sha)[1].split()
    return int(t), iso

def hook_issues(proj):
    names = set()
    for f in os.listdir(hookdir):
        if f.endswith('.py') and f[:-3].replace('_', '-').lower().startswith(proj.split('-')[0]):
            names |= set(re.findall(r'^def issue_(\d+)', open(os.path.join(hookdir, f)).read(), re.M))
    return names

for proj in projects:
    repo = os.path.join(clones, proj)
    rows = [r for r in csv.DictReader(open(os.path.join(dd, f'{proj}_info.csv'), newline='', encoding='utf-8'))
            if r['issue_no'].strip() in listed.get(proj, set()) and folder_has_spec(proj, r['issue_no'].strip())]
    missing, dated = [], []
    for r in rows:
        b = r['buggy_sha'].strip()
        if not have(repo, b): missing.append('buggy'); continue
        dated.append((date(repo, b), r))
    if not dated:
        print(f'== {proj}: no buggy SHA found in clone'); continue
    dated.sort(key=lambda x: x[0])
    ref_date, ref = dated[0]
    ref_sha = ref['buggy_sha'].strip()
    shared = sum(1 for d, r in dated if r['buggy_sha'].strip() == ref_sha)
    n_p = after = desc = notfound = in_ref = 0; skew = []
    for r in rows:
        for p in [s.strip() for s in r['patch_sha'].split('|') if s.strip()]:
            n_p += 1
            if not have(repo, p): notfound += 1; continue
            pd_ = date(repo, p)
            if pd_ > ref_date: after += 1
            else: skew.append(pd_)
            if git(repo, 'merge-base', '--is-ancestor', ref_sha, p)[0] == 0: desc += 1
            # the decisive check: is the fix commit already contained in the reference version?
            if git(repo, 'merge-base', '--is-ancestor', p, ref_sha)[0] == 0: in_ref += 1
    b_desc = sum(1 for d, r in dated if git(repo, 'merge-base', '--is-ancestor', ref_sha, r['buggy_sha'].strip())[0] == 0)
    hooks = hook_issues(proj)
    ref_hooked = [r['issue_no'].strip() for d, r in dated if r['buggy_sha'].strip() == ref_sha and r['issue_no'].strip() in hooks]
    print(f"== {proj}: defects={len(rows)} buggy_not_found={len(missing)}")
    print(f"   reference: #{ref['issue_no'].strip()} sha={ref_sha[:12]} date={ref_date[1]} docker={ref.get('buggy_docker_version', '').strip() or '-'} defects_sharing_ref_sha={shared}")
    print(f"   patches={n_p} not_found={notfound} dated_after_ref={after} descendants_of_ref={desc} contained_in_ref={in_ref}")
    if skew: print(f"   patches dated on/before ref: {len(skew)} (earliest {min(skew)[1]})")
    print(f"   buggy versions descending from ref: {b_desc}/{len(dated)}; buggy-version date span: {dated[0][0][1][:10]} .. {dated[-1][0][1][:10]}")
    print(f"   per-issue hooks in project: {len(hooks)}; hook for reference defect: {'yes' if ref_hooked else 'no'}")
