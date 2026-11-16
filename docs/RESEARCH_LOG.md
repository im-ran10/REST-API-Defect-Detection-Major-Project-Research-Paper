# Research Log and Evidence Table

Carries forward, without loss, the evidence table, taxonomy mapping, risks and Phase 1 findings from `Decision_Log_and_Evidence_Table.md` (claude.ai Project, last updated 16 Nov 2026), and the literature notes from `REST_API_Project_Handover_v2_Complete.docx` §7–8 (10 Nov 2026).

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

## 5. Novelty check status

**Not done.** The handover (§9-D) requires a *focused, narrow* prior-art search for work combining an empirical REST API defect taxonomy with feature-conditioned / prioritised LLM test-strategy selection, evaluated on real-world defects. Until it is done and recorded here, **no novelty claim may be made** in any document or in the paper.

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
