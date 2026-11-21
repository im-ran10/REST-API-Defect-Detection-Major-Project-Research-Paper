# Benchmark audit scripts (analysis only — not part of the testing system)

Scripts used for the D2–D7 review (`docs/proposals/2026-11-16_D2-D7_review.md`). They read the
Defects4REST repository and replication package and print findings. They are **researcher-side
analysis tools**: they read hidden ground truth (replication READMEs, defect CSVs) and must never be
imported or called by the strategy selector or test generator.

> **Firewall warning (CLAUDE.md):** `specs.py`, `trigcov.py` and `labels.py` print triggering endpoints or per-defect labels for **all** projects, and `rcdiff.py` reads ground truth. Until the methodology freeze, do not run them on holdout projects. For holdout projects use only `holdout_meta.py`, `spec_provenance.py` and `holdout_refver.py`, which print aggregates or version metadata.

Inputs (clone them yourself; nothing is committed here):

| Source | Commit used on 16 Nov 2026 |
|---|---|
| https://github.com/ANSWER-OSU/Defects4REST | `265db1165e54b01a0698af0fd28d345169a5449f` (pyproject version 1.0.0; the repo has no git tags) |
| https://github.com/ANSWER-OSU/Defects4REST-ReplicationPackage | `60ae979efe1fca20b7553bed6ea9b4b55bc28e71` |
| https://github.com/apilayer/restcountries (+ commit `01769a8` fetched from https://github.com/plokhotnyuk/restcountries) | full history |

| Script | Usage | What it reports |
|---|---|---|
| `inventory.py` | `python3 -I inventory.py <Defects4REST>` | listed defects vs replication folders, missing specs/READMEs, CSV row counts |
| `labels.py` | `python3 -I labels.py <Defects4REST> <ReplicationPackage>` | label agreement: top-level index vs per-project READMEs vs `607_defects_types.csv` |
| `specs.py` | `python3 -I specs.py <Defects4REST>` | per-defect spec version, path count, identity across defects, suspicious words, triggering endpoints |
| `trigcov.py` | `python3 -I trigcov.py <Defects4REST>` | heuristic: are README triggering endpoints present in the provided spec |
| `rcdiff.py` | `python3 -I rcdiff.py <restcountries-git> <Defects4REST>/defects4rest/data/defect_data/restcountries_info.csv` | per defect: fields changed between buggy and first patch SHA; border-reciprocity violations |
| `holdout_meta.py` | `python3 -I holdout_meta.py <Defects4REST> <ReplicationPackage>` | per-project aggregates only (counts, spec identity/size, auth scheme, aggregate type labels, RQ3 overlap, setup-hook counts); never prints README content, endpoints or per-defect labels |
| `holdout_refver.py` | `python3 -I holdout_refver.py <Defects4REST> <clones-dir> [project ...]` (blobless clones of the upstream repos; add fork remotes from the deploy scripts) | per project: reference (earliest-buggy) defect, SHA, date and Docker tag; counts of patches dated after / descending from / contained in the reference; buggy-version date span; whether the reference defect has a setup hook. Reads only `issue_no`, `buggy_sha`, `patch_sha`, `buggy_docker_version`; patch SHAs are never printed |
| `spec_provenance.py` | `python3 -I spec_provenance.py <Defects4REST>` | per project: spec titles/versions, union vs common operations, operations present in only one defect's spec (names printed only for dev projects) |
