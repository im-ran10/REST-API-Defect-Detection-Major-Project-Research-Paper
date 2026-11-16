# Research Log and Evidence Table

Carries forward, without loss, the evidence table, taxonomy mapping, risks and Phase 1 findings from `Decision_Log_and_Evidence_Table.md` (claude.ai Project, last updated 16 Nov 2026), and the literature notes from `REST_API_Project_Handover_v2_Complete.docx` §7–8 (10 Nov 2026).

**Methodology checkpoint (16 Nov 2026).**
- Additions: evidence E11–E26 (§1b), corrections to E3, E4, E8 and E10 (§1a, where the original rows stay unchanged), risks R12–R24, verified literature (§6b), the contamination ledger (§9).
- Reasoning: `docs/proposals/`.
- Reproduction: the scripts in `analysis/benchmark_audit/` reproduce every script-based [V] count.

**Verification labels used below**

- **Verified (primary source)**: checked by us against the paper or the benchmark repository on the date shown.
- **Handover-reported**: recorded in the handover; title, venue, dates, method and results **not yet verified** from the primary paper. Do not cite in the paper until verified.
- **Our tally / our check**: derived by us from repository contents; re-run on the pinned benchmark commit before use.

Decision IDs (D#) refer to [DECISIONS.md](DECISIONS.md).

---

## 1. Research evidence table (verified 16 Nov 2026)

| # | Claim | Source | Exact evidence | Limitation | Effect on our contribution |
|---|---|---|---|---|---|
| E1 | Defects4REST has **607** manually curated REST API defects from **50** projects, organised into **6 types / 13 subtypes**. | Mehta, Katkhede, Motwani, "Defects4REST", ICSE 2026, DOI 10.1145/3744916.3787787 (PDF in `ANSWER-OSU/Defects4REST-ReplicationPackage`). | Abstract and §3. Mined 11,313 closed issues from 51 projects; GPT-4.1-mini classifier (confidence ≥ 0.7) → 908; dual manual validation → 607 (κ = 0.86, 93.8% agreement); one project had 0 defects → 50 projects. | Taxonomy derived from issue text and patched-file types, not from failure mechanism. | Gives an empirical, citable taxonomy (D2). **We must not claim to have built the taxonomy.** |
| E2 | Prerequisites: Python 3.9+, Docker + Compose, Git, Go 1.16+. Windows needs WSL2 + Docker Desktop WSL integration. | Defects4REST repo README (v1.0.0), "Installation → Prerequisites". | — | — | Drives D9. |
| E3 | Executable benchmark v1.0 = **110** defects across **12** projects. | Paper §3.2.2 and repo README. | Paper: "110 defects from the 607 … that we could manually replicate". Repo table: awx 5, dolibarr 25, enviroCar 4, flowable 5, kafka-rest 3, mastodon 5, netbox 6, nocodb 6, podman 23, restcountries 16, seaweedfs 9, signal-cli 3. | **Artifact check:** 108 replication folders found vs 110 advertised; e.g. `dolibarr#26307` has CSV metadata but no folder/spec. | Resolves the handover's "inconsistent counts": **607 = curated dataset, 110 = reproducible subset.** Report exact version (v1.0.0) and note artifact gaps. |
| E4 | Type distribution of the 110 executable defects. | Our tally of `bug_replication/README.md`. | Data Validation & Query 59; Configuration & Environment 25; Integration/Middleware/Runtime 10; Data Storage/Access/Volume 9; Auth/Session 4; Distributed/Cluster 3. Subtypes: Query Filter 35, Schema/Payload POST 25, Env-specific config 19 … | Our count; re-run on the final benchmark commit. | Auth (4) and Distributed (3) are too small for per-type claims; any taxonomy-level result will be dominated by T2 and T1. |
| E5 | CLI deploys buggy or patched version per defect. | Repo README. | `defects4rest checkout -p <project> -i <issue> --buggy / --patched / --clean` | Requires Docker Hub, Maven Central etc. on the host. | Enables the differential oracle (D4). |
| E6 | Published tool baseline on a 30-defect stratified subset: Schemathesis, RESTler, EvoMaster and AutoRestTest **jointly detect 3/30 (10%)**; 9/30 "potentially"; 18/30 "cannot". | Paper §4.3.2, Table 8. | 1 h timeout × 5 seeds × 4 tools × 30 defects = 600 executions; "detected" if a tool hit the triggering endpoint and the error "matched or closely resembled" the incorrect behaviour in any seed. | Manual, lenient criterion; default tool configurations. | Strong motivation; external reference baseline for overlapping defects. **Different protocol → compare with caveats only.** |
| E7 | Root causes of the 18 undetected defects. | Paper §4.3.2. | L1 semantic correctness 8/18; L5 incomplete specs 3/18; L2 stateless 2/18; L3 auth/multi-user 2/18; L7 integration 2/18; L8 external deps 2/18; L4 infra 1/18; L6 business logic 1/18; L9 time-dependent 1/18. | Small sample (18). | Indicates which strategies matter: semantic assertions, stateful sequences, multi-credential tests. L4, L7, L8, L9 are out of reach for any black-box spec-based generator → exclude or report as out of scope. |
| E8 | A defect example has a clean buggy/patched ground truth. | Our check of `apilayer/restcountries`. | `countriesV2.json`, UKR capital = "Kiev" at buggy SHA `aadbd07`, "Kyiv" at patched SHA `01769a8` (restcountries#235). | Static data check only; **HTTP reproduction not yet run.** | Suggests the differential oracle is feasible for this subject. |
| E9 | Benchmark folders contain defect-revealing text next to the spec. | Repo inspection. | Each `bug_replication/<p>/<p>#<id>/README.md` gives description, triggering endpoint, buggy/expected responses; `defect_data/*.csv` holds issue title/body and patch SHAs. | — | Drives the leakage firewall (D5). |
| E10 | Spec formats. | Repo inspection. | RESTCountries: OpenAPI 3.0.3, 10 paths, identical spec for all 16 bugs. Dolibarr: Swagger 2.0, 248–299 paths; 19 of 24 specs identical, 5 variants. | **Why 5 Dolibarr specs differ is unknown** — check they are not bug-specific edits that hint at the defect. | Drives D8; spec-variant check is a Phase 1 task. |

### 1a. Corrections to the rows above (16 Nov 2026, methodology checkpoint)

| Row | Correction | Source |
|---|---|---|
| E3 | "v1.0.0" is the `pyproject.toml` version only; the repo has **no git tags**. Pin commit `265db11`. The two missing folders are `dolibarr#26307` and `seaweedfs#1776`; `flowable-engine#3536` has a folder but no spec. Two folder names are misspelled (`netBox#18669`, `seaweeedfs#913`). | `inventory.py`, `holdout_meta.py` |
| E4 | Counted **by subtype** the 110 are T2 60, T1 25, T4 10, T5 9, T3 4, T6 2. The original tally (T2 59, T6 3) used the type column, which is inconsistent for `kafka-rest#475` (typed T6, subtype ST5). | `labels.py` |
| E8 | `aadbd07` is the buggy version of #177, #199 **and** #235. #235's patch `01769a8` lives in the `plokhotnyuk/restcountries` fork. The static check holds; still no HTTP reproduction. | `rcdiff.py` |
| E10 | Dolibarr has **6** distinct specs across 24 defects (not "19 identical + 5 variants" alone). The `#26066` variant adds 12 paths, including its triggering `/multicurrencies`. One shared spec does not match every deployed version. Superseded in practice by E17/E23 and D5.1. | `specs.py`, `spec_provenance.py` |

### 1b. New evidence (16 Nov 2026, verified unless marked)

| # | Claim | Source | Effect |
|---|---|---|---|
| E11 | Per-project READMEs disagree with the top-level index on type or subtype for **22 of 110** defects, sometimes with names outside the taxonomy. The top-level index equals the replication package's `607_defects_types.csv` for all 110. | `labels.py`; [BR], [RP] | Authoritative labels = index/CSV (D2.1) |
| E12 | See the E4 correction (by-subtype counts). | `labels.py` | Reporting uses by-subtype counts |
| E13 | All 16 RESTCountries patches modify only data files (`countriesV1/V2.json`); the defects are labelled 12 × ST5 and 4 × ST3. | CSV `patched_file_types`; `rcdiff.py` | Direct evidence for R1 |
| E14 | RESTCountries #237's patch `6302b10` adds NPL to **TWN**'s borders; CHN's borders are identical at buggy and patched SHAs. #199's first patch (`b8f9410`, the CLI default) edits only `countriesV1.json`; the v2 fix is patch index 2 (`8ec4d03`). | [RC] git history | #237 excluded; #199 uses patch index 2 |
| E15 | RESTCountries fix sizes:<br>• 13 of 16 patches are 1 commit after their buggy version; #179 and #202 are 2 commits.<br>• #199 (patch index 2) is 17 commits after its buggy version and changes 478 (country, field) pairs, including the separate #235 fix (`71e04cc`, "Updated Kiev to Kyiv").<br>• #199's own fix commit changes only MKD's `name`, `nativeName`, `altSpellings`, `translations`. | [RC] | Fix-delta attribution rule (D4.1) |
| E16 | Spec content:<br>• the RESTCountries spec has 10 GET operations, **no response schemas** and a wrong server URL;<br>• the Dolibarr base spec has 387 operations and 132 definitions, but request bodies are an opaque `request_data` and many GET responses are typed `string`;<br>• every benchmark spec filename contains the issue number;<br>• `enviroCar-server#45_spec.yaml` contains explicit bug text. | `specs.py`; manual inspection | Exploration needed (D13); neutral filenames (D5.1); X3 |
| E17 | Operations present in only one defect's spec, by project:<br>• Dolibarr: 7, all in `#33949`;<br>• nocodb: 9 and 136, in two different defects;<br>• flowable-engine: 13, in one defect, whose spec is a different API ("APP REST" vs "Process REST");<br>• kafka-rest: 2, in one defect;<br>• enviroCar-server: all 4 defects.<br>The specs are identical, or carry no such operations, for RESTCountries, awx, mastodon, netbox, seaweedfs, signal-cli and podman (11 versions; 164/174 operations common). | `spec_provenance.py` | Unions rejected; X8 |
| E18 | Dolibarr setup:<br>• `configure_api_and_modules` in the deploy script is never called, so the installer, API key and module setup are manual;<br>• READMEs hard-code personal API keys;<br>• #29115, #30432 and #32072 use extra fields or settings that their steps never create;<br>• #23345's README is empty;<br>• #29372 reproduces via a UI page;<br>• #26307 has no replication folder or spec. | [BR] code and folders | D7.1 usable lists; R13 |
| E19 | Dolibarr fix sizes: 20 of the 22 usable/fixture defects have a one-commit patch (1–2 files). `#24661`: 819 commits / 324 files between buggy and patch 1 (the fix commit itself touches 4 files). `#26066`: 3 commits. No two share a buggy version. | blobless clone of Dolibarr | Attribution; #24661 flagged |
| E20 | Overlap with the paper's 30-defect RQ3 sample: RESTCountries #201, #235; Dolibarr #30950, #26307, #33949, #26066, #32072, #29372. That is 8, of which 6 are usable. The RQ3 sample also includes 2 defects outside the 110 (`podman#14676`, `signal-cli-rest-api#103`). | [P] Table 8; [RQ3R] | Context baselines only |
| E21 | Benchmark CLI behaviour:<br>• deploys one version at a time on fixed container names and ports;<br>• `--patched` takes an index (default 1);<br>• per-issue setup hooks (`run_issue_hook`) run at checkout for nocodb, netbox, mastodon, kafka-rest and seaweedfs;<br>• the RESTCountries #235 patch is cloned from a fork. | [BR] code | Sequential replay; hook exclusion rule (D13) |
| E22 | Reference versions:<br>• RESTCountries reference `aadbd07` (2019-04-23) and Dolibarr reference `9d4007e` (2022-12-11) are each dated before every patch of their project, and every patch is a git descendant (0 exceptions).<br>• RESTCountries field names are stable across all 28 benchmark versions; the observed type of `gini` differs in 4 versions. | [RC], Dolibarr git | D13 safe on the development set |
| E23 | At the Dolibarr reference commit, `htdocs/api/index.php` serves `/api/index.php/explorer/swagger.json` (product-generated spec). The endpoint set depends on the enabled modules [A]. | Dolibarr source | Preferred spec source (D5.1) |
| E24 | Holdout eligibility (metadata only):<br>• 69 non-development defects in 10 projects;<br>• 67 after X1;<br>• 52 after X2–X4 (29 in awx, flowable-engine, kafka-rest, mastodon, netbox, nocodb + 23 in Podman).<br>Project facts: signal-cli deploy prints a QR device-link URL (needs a real account); Podman builds from source and one defect needs manual rootless mode. | `holdout_meta.py`; [BR] | D11, D14 |
| E25 | [P] §3.3.3: each issue was assigned its **dominant** subtype from the issue report and its patch. [P] Table 7 already maps subtypes to EvoMaster fault classes from [M22]. | [P] | Labels ≠ mechanisms; must cite Table 7 |
| E26 | Excluding RESTCountries (16) and Dolibarr (29) from the 607 changes T2 from 41.7% to 39.7% and T1 from 27.0% to 27.2%. ST5 is 35/110 = 32% of the executable set vs 118/607 = 19% of the dataset. | [RP] CSVs | D6.1 |

## 2. Defects4REST: findings, limitations and open issues (consolidated)

**Findings**
- Dataset vs benchmark: 607 curated defects (E1) vs 110 reproducible defects in v1.0.0 (E3). Always state which one a number refers to.
- Of the 110, defects are dominated by Data Validation & Query (59) and Configuration & Environment (25) (E4).
- Existing tools find little: 3/30 jointly on the paper's stratified subset (E6). The largest cause of misses is semantic correctness (L1, 8/18) (E7).

**Benchmark limitations**
- Replication package gaps: 108 folders found vs 110 advertised (E3).
- Per-type sample sizes are too small for significance claims in Auth/Session (4) and Distributed/Cluster (3) (E4).
- Several defect causes are not black-box testable from a spec (L4, L7, L8, L9) (E7).
- The paper's own detection criterion is lenient (E6); ours is stricter (D4), so numbers are not directly comparable.
- Defect-revealing text sits beside the spec (E9) → leakage risk, mitigated by D5.
- Prevalence figures include the evaluation defects (D6).

**Potential taxonomy-mapping issue (open, untested)** — see Risk R1 below. The Defects4REST label describes *what category of fault the issue text reports*, not *how a test can expose it*. Example: many RESTCountries defects labelled "Query Filter and Search Parameter Handling Errors" are really wrong values in a data file (patched file type `data-file`). If taxonomy labels do not match the mechanism by which a defect is detectable, type-guided prioritisation may fail to beat an ordinary LLM baseline. The pilot must test this directly. This is the main reason D3 separates defect type from test strategy.

**Update (16 Nov 2026):** R1 is now directly evidenced by E13 (RESTCountries) and E25 (labelling method). It is no longer only a hypothesis about the benchmark, but whether it hurts taxonomy-guided generation is still untested.

## 3. Mapping: handover candidate categories → Defects4REST taxonomy

| Handover candidate | Defects4REST type(s) | Level | Note |
|---|---|---|---|
| Input & parameter validation | T2: ST4 Schema/Payload POST, ST5 Query Filter/Search | Defect type | 59/110 executable defects |
| Authentication & authorization | T3: ST6 Auth/Token, ST7 Session/Account lifecycle | Defect type | Only 4/110 → descriptive only |
| Stateful workflows | (cross-cutting) | Strategy | Addresses L2 |
| Response & behaviour checks | (cross-cutting) | Strategy | Addresses L1 (largest gap) |
| — | T1 Config/Env, T4 Middleware/Runtime, T5 Storage/Volume, T6 Distributed | Defect types | Mostly infrastructure-dependent; keep in the taxonomy but expect low black-box testability; report honestly |

The handover's four categories remain **candidates, not a frozen taxonomy** (Handover §4). The mapping above is part of the PROPOSED D2/D3 and is not yet agreed.

## 4. Open risks

Phase 1 risks (16 Nov 2026):

1. **R1 Label–mechanism mismatch** (threat to the hypothesis): described in §2.
2. **R2 Semantic oracles depend on the LLM's own knowledge.** E.g. "Kiev" vs "Kyiv" can only be flagged by an LLM's world knowledge. Both arms use the same model, but absolute detection numbers reflect model knowledge → report as a threat to validity.
3. **R3 Possible training-data contamination.** The GitHub issues are public. Mitigation: same model for both arms; record model version/date; run generated tests on the patched version to measure false positives.
4. **R4 Small per-type samples** (auth 4, distributed 3) → no per-type significance claims.

Carried over from the handover (§12):

5. **R5 Novelty risk.** Related work is extensive. Make a narrow, evidence-backed claim about the exact combination and its measurable effect.
6. **R6 Benchmark risk.** Real-world defects may be hard to reproduce (dependencies, setup drift, unavailable versions). Validate early; keep a fallback controlled mutation benchmark.
7. **R7 Data leakage risk.** Never expose labels, issue text, patches or oracle information to selection/generation (D0.6, D5).
8. **R8 Fairness risk.** Baseline and proposed method need comparable generation and execution budgets.
9. **R9 Oracle risk.** A server error or unexpected response is not automatically a confirmed defect (D4).
10. **R10 Reproducibility risk.** Pin software versions, Docker images, benchmark commits, model/provider/version, prompts, decoding settings and seeds.
11. **R11 Scope risk.** No multi-agent orchestration, RL, complex RAG or UI before the core hypothesis is tested.

Added at the methodology checkpoint (16 Nov 2026) — all **unresolved**:

12. **R12 Holdout size.** X8 may remove nocodb, flowable-engine and kafka-rest (13 defects); Podman may fail X5 (23). Worst case ≈ 16 evaluable defects. Phase 1 checks; small-sample rule in D11.
13. **R13 Dolibarr setup automation.** About 44 deployments need the installer and an API key (E18). Phase 1 go/no-go: script one version.
14. **R14 Product-generated specs depend on enabled modules** (E23). "Enable all API modules" may be heavy or unstable; test in Phase 1.
15. **R15 B/C boundary disputable.** C3 (read-after-write) could be called a general principle. Mitigation: a stated provenance rule and per-strategy results.
16. **R16 Sparse exploration shapes.** Fresh installs may return empty lists. Measure on dev; use a documented generic data fixture or accept unknowns.
17. **R17 LLM cost and time** for large specs (Dolibarr ≈ 490 operations across its specs; netbox 1,156) × 3 arms × ≥ 5 seeds. Calibrate *s* and a token budget on dev; pin a total budget before the holdout (D15).
18. **R18 Researcher contamination.** Endpoint names and labels of holdout defects have been seen (ledger §9). Mitigation: freeze, source-cited catalogue entries, owner reviews spec-scan hits, sensitivity analysis excluding RQ3 defects.
19. **R19 Novelty.** Only a targeted search has been done; the systematic check is pending (§5). Defects4REST is now being cited by new work.
20. **R20 Reviewer availability.** Two independent reviewers preferred; fall back to ≥ 30% double-coding with κ.
21. **R21 Podman feasibility and safety.** It builds from source, runs a service on the host, and its API manipulates host containers. Requires an isolated WSL distribution or VM; one-working-day limit (X5).
22. **R22 Benchmark ground-truth defects** (E14, E18, E19): some patched versions do not show the documented behaviour, have large deltas, or need undocumented setup. Pre-run validation (X6) and fix-delta attribution (D4.1).
23. **R23 Specification leakage** through filenames, per-defect variants and unions (E16, E17). Mitigated by D5.1; sensitivity analyses S1–S2 (EXPERIMENT_PROTOCOL §10).
24. **R24 Development-set fallback.** Resolved by D16.1 (SeaweedFS, else RESTCountries alone). Residual risk: SeaweedFS's 3-operation spec and RESTCountries' single detection mechanism would leave strategy diversity largely untested before the freeze.

## 5. Novelty check status

**Not done.** The handover (§9-D) requires a *focused, narrow* prior-art search for work combining an empirical REST API defect taxonomy with feature-conditioned / prioritised LLM test-strategy selection, evaluated on real-world defects. Until it is done and recorded here, **no novelty claim may be made** in any document or in the paper.

**Update (16 Nov 2026):**
- **What was done:** a *targeted* search during the methodology work. Among the works checked (§6b), none evaluates feature-conditioned selection of LLM test strategies with a catalogue derived from an empirical REST defect taxonomy, against a general-principles catalogue, on real-world defects with a differential oracle and project-level holdout.
- **Closest neighbour:** Tigulla & Vieira (2026) embed a mutation taxonomy in LLM prompts, but without feature-based selection, without an empirical defect taxonomy and with a 5xx oracle.
- **Status:** this is **not** the systematic check, which is still required and must be repeated before submission. The working gap statement is in `docs/proposals/2026-11-16_final_methodology.md` §1.

## 6. Literature notes (Handover-reported — verification pending)

Role each work may play in positioning. Verify title, venue, date, method, benchmark sizes and results from the primary paper before citing.

| Work | Venue / date (as reported) | Possible role | Link (as reported) |
|---|---|---|---|
| DeepREST | ASE 2024 | Related work; possible baseline/reference (RL operation sequencing and parameter generation). Not the project foundation. | https://arxiv.org/abs/2408.08594 ; replication https://zenodo.org/records/11525389 |
| AutoRestTest | ICSE Companion 2025 (demo) | Comparison/reference (multi-agent/RL + LLM, dependency modelling). Multi-agent architecture alone is not a novelty claim. | https://arxiv.org/abs/2501.08600 ; https://github.com/selab-gatech/AutoRestTest |
| Exploring Behaviours of RESTful APIs in an Industrial Setting | Software Quality Journal 2024 | CRUD/state-change behaviour generation; relevant to stateful testing. | https://link.springer.com/article/10.1007/s11219-024-09686-0 |
| LogiAgent | 2025 preprint | Shows generic business-logic and multi-agent REST testing already explored. | https://arxiv.org/abs/2503.15079 |
| RESTifAI | ICSE 2026 Demo (preprint) | Positive/negative test generation; reusable Postman collections. | https://arxiv.org/abs/2512.08706 |
| AGORA+ / test oracle generation for REST APIs | TOSEM 2025 | Oracle generation is an established direction (invariants from request/response observations). | https://homes.cs.washington.edu/~mernst/pubs/rest-oracle-tosem2025-abstract.html |
| SATORI | ASE 2025 | Static oracle generation from OpenAPI specs. | https://conf.researchr.org/details/ase-2025/ase-2025-papers/52/SATORI-Static-Test-Oracle-Generation-for-REST-APIs |
| RESTOR | 2026 preprint | RL-based oracle generation from observed traces. | https://arxiv.org/abs/2607.23963 |
| APITestGenie | AST 2026 | LLM + RAG tests from requirements and specs; requirement-aware LLM test generation is not untouched. | https://arxiv.org/abs/2604.02039 |
| FDRRestTest | ESEM 2026 | Feedback-driven logical testing with dependency evolution and a logical oracle engine. | https://doi.org/10.4230/LIPIcs.ESEM.2026.20 |
| RESTestBench | EASE 2026 | Possible controlled evaluation resource (requirements + mutation benchmark). Check scope and artifact availability. | https://arxiv.org/abs/2604.25862 ; https://github.com/casablancahotelsoftware/RESTestBench |
| Fault Injection in OpenAPI Specifications | July 2026 | Do not claim faulty/incomplete-OpenAPI testing alone as novel. | https://arxiv.org/abs/2607.12101 |
| APIPilot | 2026 preprint | Dependency inference/refinement from runtime feedback; not a novelty claim for us. | https://arxiv.org/abs/2608.17546 |
| **Defects4REST** | ICSE 2026 | Primary real-world defect benchmark. **Verified 16 Nov 2026** — see E1–E10. | https://github.com/ANSWER-OSU/Defects4REST ; DOI 10.1145/3744916.3787787 |
| RESTgym | ICST 2025 | Repeatable tool-comparison infrastructure; useful for evaluation design. | https://orbilu.uni.lu/handle/10993/67043 |

## 6b. Literature checked at the methodology checkpoint (16 Nov 2026)

"Verified" here means title, venue/date and the claim used were checked against the publisher page, arXiv abstract or official documentation. A full-text reading is still needed before citing details in the paper.

| Work | Verified claim used | Role |
|---|---|---|
| Tigulla & Vieira, *LLM-Based Robustness Testing of Microservice Applications*, arXiv 2605.14202 (venue not stated) | Embeds Vieira et al.'s mutation taxonomy in prompts; runs every strategy on every system (no feature-based selection); oracle = HTTP ≥ 500; 2 microservice systems | Closest neighbour; must cite |
| Hangler et al., *RESTestBench*, EASE 2026, arXiv 2604.25862 | 3 services with NL requirements and requirement-aligned mutations; interaction with a faulty implementation can bias generated tests | Related benchmark; motivates no feedback loop in v1 |
| Zhang et al., *LogiAgent*, arXiv 2503.15079 | LLM multi-agent logical testing with business-logic oracles beyond 5xx | Related; not taxonomy-guided |
| Kim, Sinha, Orso, *LlamaRestTest*, FSE 2025 | Fine-tuned small LLMs for inputs and inter-parameter dependencies; 12 services | Related (inputs, not strategies) |
| Kim et al., *NLPtoREST*, ISSTA 2023 | Rules and dependencies extracted from spec text enhance specs | Related (feature extraction) |
| Kim, Xin, Sinha, Orso, *No Time to Rest Yet*, ISSTA 2022 | 10 tools on 20 services; coverage and failures | Empirical background |
| AutoRestTest (ICSE 2025); *AutoRestTest at the SBFT 2026 Tool Competition*, arXiv 2607.01063 | MARL + dependency graph + LLM values; ranked first in fault detection at SBFT 2026 | Related tool |
| Segura et al., *Metamorphic Testing of RESTful Web APIs*, IEEE TSE 44(11) 2018 | Six output patterns: Equivalence, Equality, Subset, Disjoint, Complete, Difference | Part of catalogue B |
| Schemathesis checks reference | `not_a_server_error`, `status_code_conformance`, `response_schema_conformance`, `negative_data_rejection`, `positive_data_acceptance`, `use_after_free`, `ensure_resource_availability`, `ignored_auth` | Part of catalogue B |
| RESTler `Checkers.md`; Atlidakis, Godefroid, Polishchuk, ICST 2020 | Use-after-free, resource-leak, resource-hierarchy and user-namespace rules; invalid-dynamic-object checker | Part of catalogue B |
| Marculescu, Zhang, Arcuri, TOSEM 2022, DOI 10.1145/3491038 | Taxonomy of faults found by EvoMaster | Related; mapped in [P] Table 7 |
| *Differential Regression Testing for REST APIs*, MSR-TR-2019-23 / ISSTA 2020 | Differential testing across REST API versions; must handle non-determinism | Cite for the oracle |
| *RESTberus*, arXiv 2611.04769 | Cites Defects4REST (Nov 2026) | Signals new overlapping work |

The handover-reported entries in §6 keep their "verification pending" label, except where listed here.

## 7. Historical context (why the direction was narrowed)

An earlier, broader concept was contract-aware agentic generation of positive, negative, boundary and authorization tests with execution-feedback refinement, evaluated by coverage, mutation score and bug detection. It was narrowed because generic LLM test generation, agentic orchestration, feedback refinement, workflow testing, authorization testing and oracle generation all have prior work (Handover §16–17). Do not silently widen scope back to it.

## 8. Phase 1 research findings log

| Date | Finding | Evidence |
|---|---|---|
| 16 Nov 2026 | Benchmark counts, taxonomy, CLI and prerequisites verified from the paper and repository. | E1–E5 |
| 16 Nov 2026 | Published four-tool baseline: 3/30 jointly detected; 18/30 undetected, with causes. | E6, E7 |
| 16 Nov 2026 | Static check of restcountries#235 shows the buggy/patched data difference ("Kiev" → "Kyiv"). **No HTTP reproduction run yet.** | E8 |
| 16 Nov 2026 | Leakage hazards identified in the benchmark layout. | E9 |
| 16 Nov 2026 | Spec formats and Dolibarr spec-variant question identified. | E10 |
| 16 Nov 2026 | Label inconsistency, ground-truth defects (#237, #199) and Dolibarr setup gaps found; development set defined. | E11–E15, E18–E20 |
| 16 Nov 2026 | Spec leakage channels found (filenames, enviroCar text, per-defect variants, unions); spec-source rule adopted. | E16, E17, E23 |
| 16 Nov 2026 | Earliest-buggy reference version verified safe for RESTCountries and Dolibarr. | E22 |
| 16 Nov 2026 | Holdout eligibility computed from metadata only. | E24 |
| 16 Nov 2026 | Methodology provisionally approved by the owner (D2.1–D7.1, D11–D14). | DECISIONS.md |

## 9. Researcher contamination ledger

Purpose: the holdout evaluation (D11) is **not blind**. This ledger records exactly what has been seen, so the paper can disclose it.

| Date | Who | Exposure | Scope |
|---|---|---|---|
| 16 Nov 2026 | Drafting assistant (Claude) | Full replication READMEs, CSV rows and patches | **Development projects only** (RESTCountries, Dolibarr) |
| 16 Nov 2026 | Drafting assistant | Triggering-endpoint paths of all spec-bearing defects (printed once by `specs.py`); some missing-endpoint lists (`trigcov.py`) | All projects, **including holdout** |
| 16 Nov 2026 | Drafting assistant | Type/subtype labels of all 110 defects; label mismatches | All projects |
| 16 Nov 2026 | Drafting assistant | [P] Table 8 (labels + tool results of the 30 RQ3 defects; 14 are in eligible holdout projects); the RQ3 report section on `podman#14676` (not in the 110) | Holdout partially |
| 16 Nov 2026 | Drafting assistant | `enviroCar-server#45` spec text (bug note); one Podman defect needs rootless mode (deploy-script constant) | Excluded project / holdout fact |
| 16 Nov 2026 | Owner | The review and methodology documents in `docs/proposals/` (development-project details, aggregate holdout counts) | Development projects; holdout aggregates |

**Not read for any holdout project:** replication READMEs, defect CSV descriptions, patches, setup-hook bodies.

**Rule from now on:** before the freeze, no further holdout ground truth may be opened (see CLAUDE.md, "Benchmark ground-truth firewall"). Any new exposure is appended here.
