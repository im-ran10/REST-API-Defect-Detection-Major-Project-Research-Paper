# Ten-day design, literature integration and final quality control (23 Nov 2026)

Status: **reasoning document** for decisions D23–D43 ([DECISIONS.md](../DECISIONS.md) is authoritative). It consolidates three working documents reviewed by the owner on 22–23 Nov 2026 (claude.ai docs "Local-LLM Feasibility Assessment and Validation Plan", "Revised Methodology and 10-Day Execution Plan", "Literature Integration and Novelty Assessment") and records the final quality-control pass the owner requested on 23 Nov 2026. Immutable once approved; later proposals supersede it.

Tags: **[V]** verified from a primary source or a measurement in this repository; **[A]** assessment; **[R]** recommendation.

---

## 1. Why the design changed

- The owner fixed a **10-day deadline for a complete first draft with measured results** (Day 1 = Mon 23 Nov 2026, Day 10 = Wed 2 Dec 2026) and a **zero budget** for the LLM (no paid APIs, no keys).
- At the approved scale (3 arms × 5 seeds × all operations of 6 held-out projects) generation alone is about 38,000 calls, roughly two weeks of GPU time on the owner's RTX 3050 6 GB laptop GPU, before building the pipeline and running the tests [A]. The full design therefore cannot finish in 10 days.
- The owner authorised a **scale reduction only**: research question, contribution, all three arms, benchmark, detection protocol, held-out separation and leakage safeguards are unchanged.

## 2. Final experimental design [R, owner-reviewed]

| Element | Design |
|---|---|
| Research question, RQ1 (B vs A), RQ2 (C vs B), RQ3 | Unchanged (EXPERIMENT_PROTOCOL §1) |
| Arms | A plain; B feature-conditioned general-principles catalogue; C feature-conditioned taxonomy-derived catalogue (D12, including its two-arm rule) |
| Arm B content | B1–B9 tool checks + B10–B15 Segura et al. metamorphic patterns + RFC 9110 semantics; **B2 gains the Vieira et al. (DSN 2007) input-mutation operators by data type, disambiguated for HTTP** (key absent / value empty / value null), as operator detail inside B2, not a new entry (D34) |
| Strategy entry format | Every B and C entry: name; applicability; how to probe; oracle template; one worked example against a small **fictional** API; source. Mean tokens per entry within ±20% between B and C. One JSON-format example in the shared system prompt for all arms (D35) |
| Feature-to-strategy mapping | Released research artefact; rule fields: ID, observable predicate, strategy, applicability, source, rationale, oracle template, leakage control, validation status (Illustrative → Development-checked → Held-out evaluated). Claims stay at catalogue level (D36) |
| Development | RESTCountries: 15 usable defects, 3 arms × 2 seeds, one logged revision round per arm. Dolibarr R14: generation validity, B/C trigger coverage, catalogue divergence, #23179 sanity; no Dolibarr per-defect runs (D24) |
| Held-out | **Podman (23 defects) + nocodb (6; 5 in the primary analysis) = 28 primary**, before post-freeze attrition. Rule: Podman = only held-out project verified deployable (X5); second = more defects among projects deployable from one prebuilt image (nocodb, flowable-engine); flowable-engine if nocodb's reference version is not deployed within 4 h on Day 2. awx, mastodon, netbox reserved and firewalled (D25) |
| Model | Local Ollama, native chat API, JSON-schema output, thinking off, truncation check on every call. Qwen3 4B Instruct-2507 if it passes Gate 0 + T1–T4 on a fixed 60-call development sample, else Gemma 3 4B (D27). Plus 10 extra strategy-slot calls, reported only (D39) |
| Budgets | One call per (operation, arm, seed), **no retries**; *k* = 5, reduced to 3 for all arms only if projected held-out generation exceeds **40 h** at the freeze; pre-declared operation cap with deterministic, defect-blind selection; *s* calibrated on development (D26) |
| Execution | Screening run per suite; full re-runs (2 more) for suite runs with a candidate; isolated re-runs; D4.1 unchanged (D28) |
| Review | Validator (X6/X7, attribution; post-freeze only) and two arm-blind reviewers named before the freeze; blinded export; κ reported; unresolved disagreements → conservative verdict in the primary analysis (D30) |
| Statistics | Defined in §5 of this document (D29) |
| Catalogue divergence | Div_C on development data; overlap table committed before first computation; refinement only from development evidence inside the logged revision round; fallback unchanged (D31) |
| Supplementary | Development-only C∅ selector ablation (D37); analyses A1–A4 (D38); post-draft frozen-system extension to flowable-engine (D33) |

## 3. Final quality control (owner's four checks, 23 Nov 2026)

Method: a temporary researcher-side script (not committed, not importable by the system) computed **aggregate** feature and predicate counts per project from the development specs, nocodb's official spec (`scripts/sdk/swagger.json` at `cea2b4ad7348`, E33) and the largest Podman benchmark spec. Only counts were printed; no operation names, README content, defect rows or triggering endpoints were opened for held-out projects (contamination ledger entry added).

### 3.1 Check 1 — Can every C strategy be executed in our environments? [V counts, A assessment]

Predicate counts (operations on which the predicate fires; strict observable rules from §3.2):

| Feature / predicate | RESTCountries (10 ops) | Dolibarr R14 (392) | nocodb official (126) | Podman largest spec (173) |
|---|---|---|---|---|
| C2 filter-like query parameter on GET | 0 | 62 | 4 | 15 |
| C3 write + GET on the same item | 0 | 64 | 11 | **0** |
| C4 collection + item GET pair | 1 | 38 | 2 | 3 |
| C6 action sub-resource or create–read–update/delete lifecycle | 0 | 214 | 35 | 53 |
| C7 security scheme defined (project level) | no | yes (390 ops declare it) | yes (defined, no operation declares it) | no |
| C10 multipart body | 0 | 0 | 2 | **0** (3 operations take binary/tar bodies only) |
| C8 pagination / projection / sort parameters | 10 | 68 | 11 | 4 |
| B6 DELETE + GET on the same item | 0 | 36 | 8 | 3 |

Assessment per strategy:

| Strategy | Executable? | Minimal correction |
|---|---|---|
| C1 schema/payload validation | Yes, all projects | — |
| C2 filter/search semantics | Yes; tests must create their own data first (DSL multi-step) | Filter-name list split from pagination names (see 3.2) |
| C3 persistence round-trip | Yes on Dolibarr and nocodb; **never fires on Podman** (non-REST naming such as `/containers/create`) | None: an API-specific predicate would be speculative. Report per-project family coverage |
| C4 cross-view consistency | Yes | — |
| C5 semantic plausibility | Yes (GET + assertions); risk of speculative oracles is filtered by the differential protocol | Report false-positive rate per arm (already in §10) |
| C6 workflow / state transition | Yes; **Podman needs a container image available offline** | Podman fixture pre-loads one small image before the snapshot; Podman reset removes containers, pods, volumes, networks and secrets but keeps the fixture image |
| C7 session/token lifecycle, roles | Only where the generic fixture provides ≥ 2 roles. Dolibarr: second user via `POST /users` + login; nocodb: second user via its documented sign-up/invite API; Podman: no auth, never fires | Second-role fixture time-boxed (2 h per project, from public documentation only); if not built, C7 inactive for that project and reported |
| C8 representation variation | Yes | — |
| C9 runtime error surface | Yes | — |
| C10 file/storage handling | Multipart only (nocodb 2 operations); binary/tar bodies cannot be built by the DSL | Predicate restricted to multipart operations; DSL supports one small inline text file part. C10 does not fire on Podman |

Consequence [A]: C's distinct families are unevenly available. On **Podman**, C's distinct content is mainly C6 (workflow) and C5 (plausibility), with small C4 coverage; C3, C7 and C10 do not fire. On **RESTCountries** (read-only) no write family fires, so the Day 5 floor check (G3) exercises only C4/C5/C8 there; executability of C3/C6/C7 is checked on Dolibarr without defects. This is reported, not engineered away.

### 3.2 Check 2 — Are all rules based on observable evidence? [A]

Two draft predicates contained assumptions and are corrected:

1. **Filter vs pagination.** The draft "filter-like" name list mixed filters (`filter`, `sqlfilters`, `search`, `q`, `where`) with pagination and projection (`limit`, `offset`, `page`, `fields`, `sort*`, `order*`). Paging is not filtering. Correction: `filter`, `filters`, `sqlfilters`, `search`, `q`, `query`, `where` → C2 / B12; `limit`, `offset`, `page`, `per_page`, `fields`, `sort*`, `order*` → C8 / B11 / B15. Generic names such as `name` or `status` are **not** treated as filters.
2. **Workflow trigger.** "≥ 3 methods on a resource" does not imply a workflow. Correction: C6 fires on an action sub-resource (`POST|PUT|PATCH /x/{id}/<verb>`) or on the create operation of a resource with an observable create–read–(update or delete) lifecycle.

Every other predicate reads only spec structure (methods, paths, parameter locations and names, content types, declared security) or exploration shapes (D13.1). `has_reference_array` uses observed values inside the extractor only; values are never passed to the generator. C7 eligibility uses the project-level security scheme plus fixture configuration, as §5.1 of the final methodology already states.

### 3.3 Check 3 — Can the 4B model handle the complete catalogues? [V sizes, A otherwise]

Cannot be verified before Ollama is installed (Day 1–2). What can be checked now: estimated spec-slice size per operation (resolved `$ref`s, depth ≤ 6, ≈ 3.5 characters per token):

| Project | Median | p95 | Max | Operations > 2,000 tokens |
|---|---|---|---|---|
| RESTCountries | 113 | 135 | 142 | 0 |
| Dolibarr R14 | 135 | 358 | 882 | 0 |
| nocodb | 129 | 255 | 331 | 0 |
| Podman (largest spec) | 190 | 784 | **7,514** | **2** (1 above 4,000) |

With the system prompt, schema, format example and *s* strategy entries (about 2–3k tokens), the largest Podman prompt is about 10k tokens. Default Ollama context (4,096 for GPUs under 24 GB) would truncate it silently.

Rule added (D41): Stage 2a (G1) is run with the **actual largest held-out prompt** (largest Podman slice + longest B/C task text). If it fits at a context window that still loads fully on the GPU, that window is used for every call of every arm. If not, a deterministic, arm-independent **slice-compaction rule** (drop schema descriptions and examples beyond a fixed length, then cap nesting depth) is applied to all operations' slices, fixed before the freeze; it applies identically to all arms because the slice is shared input. Generation time per arm is then re-measured on Day 5 for the binding 40-hour rule. Gates G1–G3 remain the checks that decide feasibility.

### 3.4 Check 4 — Is the statistical analysis appropriate for about 28 defects in two projects? [A]

- The primary test (two-sided paired sign-flip, Holm over RQ1 and RQ2) is appropriate: exact, no distributional assumptions, unit = defect.
- **Minimum needed for significance:** the smallest possible exact two-sided p-value with *n* defects whose paired difference is non-zero is 2/2^*n*. Holm's first step needs p ≤ 0.025, so at least **7** defects with a non-zero difference all in one direction (2/128 = 0.016; 6 gives 0.031). Defects that no arm detects contribute nothing. Given that existing tools detected 3 of 30 Defects4REST defects (E6), many held-out defects may be all-zero, so a **descriptive outcome is likely** and is pre-stated.
- **Statistically supported conclusions:** only a Holm-significant RQ1 or RQ2 result, and only as a statement about these 28 defects in Podman and nocodb.
- **Descriptive only:** per-project results, the project-balanced mean, C vs A, per-strategy attribution, assertion types (A1), status-only sensitivity (A2), cross-seed diversity (A3), strategy adherence (A4), the C∅ ablation, RQ3, and everything if fewer than 20 defects are evaluable (D11).
- Confidence intervals are defect-level bootstrap intervals within these two projects; two projects cannot estimate between-project variation, and Podman supplies 23 of 28 defects.

## 4. Literature integration (summary; detail in RESEARCH_LOG §5–§6b)

Seven papers retrieved in full text on 23 Nov 2026 and examined by targeted extraction (RBCTest and RESTestBench: about 80% of the page). Established and credited, never claimed: LLM REST test generation; taxonomy-guided prompting (Tigulla & Vieira); oracles beyond 5xx (LogiAgent, FDRRestTest, RBCTest); small local models (LlamaRestTest). Not found: deterministic feature-conditioned strategy selection; a catalogue derived from an empirical REST defect taxonomy; evaluation on known defects with a buggy/patched oracle; an ablation separating taxonomy-derived from general guidance. The contribution is framed as a controlled study plus a traceable artefact, not a new technique; no "first" claim (PROJECT_OVERVIEW).

Vieira et al. verified bibliographically: M. Vieira, N. Laranjeiro, H. Madeira, "Assessing Robustness of Web-Services Infrastructures," DSN 2007, pp. 131–136, DOI 10.1109/DSN.2007.16 (IEEE CSDL page, and Tigulla & Vieira's reference list). The original operator table was not accessible (paywalled); the operator list used in B2 comes from Tigulla & Vieira's description of it, with their HTTP disambiguation.

## 5. Statistics (definitions; D29)

- Y(d,a,j) = 1 if the suite for defect *d*, arm *a*, seed *j* has ≥ 1 Confirmed candidate. Invalid or empty output and ERROR-TEST are the arm's own outcome (Y = 0).
- Missing only for ERROR-INFRA after 2 redeploys. K_d = seeds with outcomes for all compared arms; p(d,a) = mean Y over K_d; |K_d| < 3 → defect leaves the primary analysis (listed). X6/X7 exclusions listed, never imputed.
- D1(d) = p(d,B) − p(d,A); D2(d) = p(d,C) − p(d,B). All-zero defects stay (D = 0) and their count is reported.
- Statistic = mean D; two-sided sign-flip, exact when ≤ 20 non-zero differences, else 10,000 random flips (seed recorded); Holm over RQ1 and RQ2 at α = 0.05. Under the D12 fallback, C − A is the single primary test.
- Effect size: mean D with a defect-level percentile bootstrap 95% CI (10,000 resamples); per-project means; project-balanced mean as sensitivity.
- Sensitivity: Wilcoxon signed-rank; S1, S3, S4; Related counted as detected; reviewer-disagreement alternative; A2. Secondary: C vs A; McNemar on detected-by-≥ 1-seed. GLMM, project-cluster bootstrap and leave-one-project-out dropped at this scale.

## 6. Remaining methodological problems (after corrections)

1. C's distinct families are unevenly applicable (§3.1); RQ2 on Podman mainly tests C5/C6. Disclosed, per-project family coverage reported.
2. A significant result needs ≥ 7 defects with consistent non-zero differences (§3.4); a descriptive outcome is likely.
3. The 4B model's ability to follow the full catalogues is unmeasured until Days 2–5 (G1–G3).
4. C7 depends on second-role fixtures that are not yet built (time-boxed).
5. The Vieira operator table is taken from a secondary description (§4).
6. One held-out project (Podman) dominates the sample; generalisation claims are excluded.

## 7. Implementation tasks in dependency order

| # | Task | Depends on | Gate |
|---|---|---|---|
| T0 | Owner: install Ollama in Ubuntu; pull Qwen3 4B Instruct-2507 and Gemma 3 4B | approval | — |
| T1 | Project-level specs for Podman and nocodb (D5.1 preference order, metadata only); spec normaliser: `$ref` resolution, `defaultValue`, OpenAPI 3.1, slice extraction | — | — |
| T2 | Test DSL JSON schema (steps, value binding, assertion types incl. body/header/cross-request, multipart text part, fixture variables) + static validator | T1 | — |
| T3 | LLM client: Ollama native `/api/chat`, schema `format`, `think` off, fixed `num_ctx`, seed, truncation guard (`prompt_eval_count`), resume cache, manifest records | T2 | — |
| T4 | Stage 2a speed/VRAM/truncation test with the largest held-out prompt; context window or compaction rule; operation cap; provisional 40-hour check | T0, T1, T3 | **G1** |
| T5 | Executor: HTTP execution of the DSL, binding, assertions, outcome classes; deploy/reset adapters: RESTCountries (none), Dolibarr (DB dump), Podman (fixture image + cleanup reset, Docker stopped), nocodb (container/SQLite restore); generic fixtures, second roles time-boxed | T2 | — |
| T6 | Shared system prompt with the JSON-format example; Arm A prompt; model quality test (60 calls + 10 strategy-slot calls) | T3, T5 | **G2** |
| T7 | Feature extractor (spec features + GET-only shape exploration, reusing the probe) | T1 | — |
| T8 | Catalogues B and C in the structured format (B2 with Vieira operators; fictional-API examples; length parity); corrected predicates; overlap table committed; selector (seed-rotated, logged); C∅ flag | T7 | — |
| T9 | Divergence (Div_C, Div_B) on development data | T8 | D12 rule |
| T10 | Verdict engine: screening, full re-runs, isolation, differential classes, blinded candidate export | T5 | — |
| T11 | Analysis scripts (§5, A1–A4, C∅) tested on development outputs | T10 | — |
| T12 | Development validation: RESTCountries 15 defects × 3 arms × 2 seeds; Dolibarr breadth runs; C∅ ablation; per-arm timing → binding 40-hour rule and execution projection | T6, T8–T11 | **G3** |
| T13 | Freeze: §13 checklist, manifests, git tag; validator and reviewers named | T12 | **G4** |
| T14 | Held-out generation: Podman, then nocodb | T13 | — |
| T15 | Podman builds and execution (Docker stopped); X6/X7 by the validator | T14 | — |
| T16 | nocodb execution; re-runs, isolation, attribution, arm-blind confirmation | T15 | Day 8 time rule |
| T17 | Analysis and paper results | T16 | D11 rule |

## 8. Decisions that still need the owner

1. D30: name the validator and confirm both reviewers are available on Days 8–9.
2. D32: execution overrun option — (b) let nocodb finish late under the Day 8 time rule (recommended) or (a) 3 seeds.
3. D41: the quality-control corrections in §3 (predicate fixes, executability restrictions, Podman fixture/reset, context/compaction rule, second-role time box).

## 9. Owner decisions on this document (23 Nov 2026, recorded before approval)

- **D41 approved**, with D4.1 and the firewall unchanged.
- **D32 approved, option (b):** keep 5 seeds and let nocodb finish late; reduce to 3 only if the 40-hour rule makes 5 genuinely infeasible, recording the reason and updating the protocol before any comparative result is examined.
- **D30:** responsibilities and independence rules clarified in DECISIONS D30; stays an owner action until the owner names the people; no names or availability assumed.
- **D42 (new):** per-project results, Podman-only sensitivity analysis, strategy applicability by project, explicit Podman-share limitations; no broad generalisation.
- **D43 (new):** full-prompt context test, JSON validity, strategy adherence and generation-time checks are mandatory before the model configuration is frozen; no silent truncation. Supersedes D39's "reported, not gated".

