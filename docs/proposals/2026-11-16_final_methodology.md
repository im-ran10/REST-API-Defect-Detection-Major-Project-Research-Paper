# Final methodology proposal (checkpoint before implementation)

**Status: PROPOSAL FOR OWNER REVIEW.** No decision status is changed by this document. Nothing has been implemented, set up, committed or pushed.
**Date:** 16 Nov 2026. **Builds on:** `2026-11-16_D2-D7_review.md` (sources [P], [BR], [RP], [RQ3R], [RC], [M22], [G20], [TV26], [RTB] defined there).

**Labels:** **[V]** verified against the named source · **[A]** assumption or judgement · **[R]** recommended methodology (a design choice, not evidence).

**Additional sources used here**

| Key | Source |
|---|---|
| [SCH] | Schemathesis documentation, "checks" reference (schemathesis.readthedocs.io) |
| [RLC] | RESTler user guide, `docs/user-guide/Checkers.md` (github.com/microsoft/restler-fuzzer) |
| [ICST20] | Atlidakis, Godefroid, Polishchuk, *Checking Security Properties of Cloud Service REST APIs*, ICST 2020 |
| [SEG18] | Segura, Parejo, Troya, Ruiz-Cortés, *Metamorphic Testing of RESTful Web APIs*, IEEE TSE 44(11), 2018, DOI 10.1109/TSE.2017.2764464 |
| [LA25] | Zhang et al., *LogiAgent*, arXiv 2503.15079 |
| [LRT25] | Kim, Sinha, Orso, *LlamaRestTest*, FSE 2025, DOI 10.1145/3715737 |
| [N2R23] | Kim et al., *Enhancing REST API Testing with NLP Techniques* (NLPtoREST), ISSTA 2023, DOI 10.1145/3597926.3598131 |
| [K22] | Kim, Xin, Sinha, Orso, *Automated Test Generation for REST APIs: No Time to Rest Yet*, ISSTA 2022, DOI 10.1145/3533767.3534401 |
| [ART] | Kim et al., AutoRestTest (ICSE 2025), and *AutoRestTest at the SBFT 2026 Tool Competition*, arXiv 2607.01063 |
| [RB26] | *RESTberus*, arXiv 2611.04769 (only its citation of Defects4REST is used here) |

New scripts (metadata-only, no README reading): `analysis/benchmark_audit/holdout_meta.py`.

---

## 1. The research contribution, concisely

**What we claim we will contribute (if results support it):**
1. **A controlled empirical study.** It asks whether *deterministic, feature-conditioned selection of test strategies* improves the detection of **real-world** REST API defects by LLM-generated tests under equal budgets. It also asks whether a strategy catalogue **derived from an empirical REST defect taxonomy** (Defects4REST) adds detection beyond a catalogue built from established tool checks. Both questions are evaluated with a buggy-vs-patched differential oracle on **held-out projects**.
2. **A frozen, traceable artefact:** the taxonomy-derived strategy catalogue with feature triggers. Every entry cites the subtype or limitation it comes from.
3. **A documented benchmark-validity audit** of Defects4REST, already partly done: label inconsistencies, ground-truth problems, leakage paths in specs. This is a secondary, factual contribution.

**What we do not claim:** the LLM generator, the taxonomy, multi-agent/agentic testing, oracle generation or metamorphic relations as such.

### Closest work and how ours differs

| Work | What it does [V from source] | What it does not do, relative to us |
|---|---|---|
| Tigulla & Vieira [TV26] | Embeds a **mutation** taxonomy (Vieira et al. 2007) in LLM prompts. Robustness testing of two microservice systems; oracle = HTTP ≥ 500. | Runs every prompt strategy on every system, with no feature-based selection. Not an empirical defect taxonomy. No real-world defect ground truth. |
| LogiAgent [LA25] | LLM multi-agent "logical testing" of REST systems, with **logical oracles** grounded in business logic, beyond 5xx. | Not taxonomy-guided; not evaluated against a real-defect benchmark with a buggy/patched oracle. |
| RESTestBench [RTB] | Benchmark of 3 services with NL requirements and requirement-aligned mutations. Finds that interaction with a faulty implementation can make generated tests adapt to incorrect behaviour. | Requirements-based and mutation-based, not real-world defects; no strategy selection. |
| Defects4REST RQ3 [P] | 4 existing tools on 30 real defects: 3/30 detected, plus root-cause classes L1–L9. | No LLM strategy selection; a lenient manual detection criterion. |
| Marculescu et al. [M22] | Taxonomy of faults found by EvoMaster in 8 case studies. | Describes found faults; does not use a taxonomy to guide generation. |
| NLPtoREST [N2R23], LlamaRestTest [LRT25] | Extract rules, inter-parameter dependencies and input values from spec text and server responses. | Feature/rule extraction for *input validity*; not strategy selection for defect classes. |
| AutoRestTest [ART] | MARL + dependency graph + LLM-generated values; ranked first in fault detection at SBFT 2026. | Reports faults (5xx-centred); no taxonomy-guided oracles. |
| Segura et al. [SEG18] | Metamorphic relation output patterns for REST APIs. | A source of oracle *techniques* (we reuse the idea); not taxonomy-driven selection. |

**Gap (stated narrowly):** among the works checked, none evaluates *feature-conditioned selection of LLM test strategies* — with the strategy catalogue derived from an empirical REST defect taxonomy and compared against a general-principles catalogue — on real-world defects with a differential oracle and project-level holdout.

**Caveat:** this is a targeted search, **not** the systematic novelty check, which remains pending. [RB26] shows Defects4REST is already being cited (Nov 2026), so new overlapping work may appear; repeat the check before submission.

---

## 2. Revised research questions [R]

The title and main question stay as recorded. They are refined into three sub-questions:

- **RQ1 — selection effect.** Under equal budgets, does deterministic feature-conditioned strategy selection with a general-principles catalogue (Arm B) detect more real-world defects than plain LLM test generation (Arm A)?
- **RQ2 — taxonomy effect.** Under the same selector, budget and prompts, does a catalogue derived from the Defects4REST taxonomy and its documented tool limitations (Arm C) detect more defects than the general-principles catalogue (Arm B)?
- **RQ3 — characterisation (descriptive).** Which defects and detection mechanisms does each arm detect, at what false-positive and cost levels?

RQ1 and RQ2 are the two pre-registered primary comparisons. C vs A is secondary.

---

## 3. Final proposed experimental architecture [R]

```
        ┌──────────── researcher side (may read ground truth; never feeds the system) ────────────┐
        │ benchmark validation · fixtures · attribution · blinded confirmation · statistics        │
        └──────────────────────────────────────────────────────────────────────────────────────────┘
SYSTEM SIDE (allow-listed inputs only; every input hashed into a run manifest)
 1 Spec normaliser ──► project-level API description (sanitised, neutral filename)
 2 Explorer (GET-only, shape-only, on the project's reference version)  ──► observed response shapes
 3 Feature extractor (deterministic) ──► feature record per operation / resource
 4 Planner ── Arm A: equal slots per operation
            └ Arms B/C: same selector code + catalogue file (B or C) ──► (operation, strategy) slots + reasons
 5 LLM generator (same model, settings, output schema; one call per operation) ──► test suite (JSON DSL)
 6 Static validator (well-formedness only; no repair, no regeneration)
 7 Replayer: per defect, buggy then patched; fixture; r repetitions ──► raw results
 8 Verdict engine: outcome classes, differential candidates
```

**Deliberately excluded [R]:**
- feedback/refinement loops ([RTB] reports they can adapt tests to faulty behaviour, and they would complicate budget parity);
- multi-agent orchestration, RL, RAG, fine-tuning;
- prevalence priors (D6);
- any UI.

Each of these can be a later experiment if justified.

---

## 4. Specification of the three arms

### 4.1 Held identical across arms [R]

| Aspect | Rule |
|---|---|
| API information | Same project-level description (§6.5) + same exploration shapes (§6). The LLM never sees a defect ID, label or triggering endpoint. |
| LLM | Same provider, model version, temperature, max tokens; same k generation seeds (proposal k = 5, matching the 5 seeds per tool in [P] RQ3 [V]). |
| Calls | Exactly one generation call per operation per (arm, seed). |
| Test budget | Same **s slots per operation** (s to be calibrated on dev; proposal s = 3). Same cap on HTTP requests per test and per suite. |
| Output | Same JSON test DSL: steps, symbolic auth role, captures, assertions from a fixed vocabulary (status, JSON-path equals/contains/matches/compare, cross-step comparisons). The DSL is fixed before any arm is run. |
| Prompt skeleton | Identical system prompt (DSL definition, rules, operation context). Only the *task section* differs (below). |
| Execution | Same replayer, fixture, repetitions, verdict rules; arms are run against the **same deployments**. |
| Reporting | Tokens in/out, requests executed and wall-clock time are recorded per arm, because prompt lengths differ slightly. |

### 4.2 What differs

| | **A: plain LLM** | **B: feature-conditioned, general-principles catalogue** | **C: feature-conditioned, taxonomy-derived catalogue** |
|---|---|---|---|
| Implementation | Planner assigns s slots per operation; no selector | Selector code S + catalogue file `catalogue_B` | Same selector code S + catalogue file `catalogue_C` |
| Strategy selection | none | Feature predicates in `catalogue_B` decide which strategies apply to the operation; s slots are filled from them in a fixed order, rotated by seed | Same mechanism with `catalogue_C` |
| Prompt task section | "Write s test cases for this operation that are likely to reveal defects, with assertions." | "Write one test per slot. Slot i: strategy *Bx* — {generic check description + oracle template}." | "Write one test per slot. Slot i: strategy *Cx* — {defect-class manifestation from the taxonomy + oracle template}." |
| Output | test suite + empty selection log | test suite + selection log (strategy, triggering features, reason) | same as B |
| Provenance | — | Every entry cites an established tool check or protocol rule | Every entry cites a Defects4REST subtype (Table 4) and/or limitation L1–L9 [P] |

**The only difference between B and C is the catalogue file.** It holds the strategy set, trigger predicates, ordering and description text. Selector code, feature extractor, budget, model and DSL are shared. This is what makes RQ2 an ablation of one factor.

### 4.3 The two catalogues (drafts; frozen before the holdout)

**Catalogue B — general REST testing principles.** Derived only from checks implemented by established black-box tools and from protocol semantics:

| ID | Strategy | Source [V] |
|---|---|---|
| B1 | Server-error robustness (no 5xx for any request) | Schemathesis `not_a_server_error` [SCH]; RESTler 5xx detection [RLC] |
| B2 | Invalid input is rejected | Schemathesis `negative_data_rejection` [SCH] |
| B3 | Valid input is accepted | Schemathesis `positive_data_acceptance` [SCH] |
| B4 | Response conformance (documented status, schema) | Schemathesis `status_code_conformance`, `response_schema_conformance` [SCH] |
| B5 | Invalid/non-existent identifier handling | RESTler invalid-dynamic-object checker [RLC] |
| B6 | Deleted resource is no longer accessible | Schemathesis `use_after_free` [SCH]; RESTler use-after-free [RLC], [ICST20] |
| B7 | Created resource is available | Schemathesis `ensure_resource_availability` [SCH] |
| B8 | Authentication/authorisation enforcement | Schemathesis `ignored_auth` [SCH]; RESTler namespace rule [RLC], [ICST20] |
| B9 | Method semantics (safe GET, idempotent PUT/DELETE) | HTTP semantics (RFC 9110) [A: standard; not re-checked here] |

**Catalogue C — taxonomy-derived.** Each entry traces to [P] Table 4 subtypes or the RQ3 limitation classes:

| ID | Strategy | Derived from |
|---|---|---|
| C1 | Schema/payload validation incl. types, formats, boundaries | ST4 |
| C2 | Query-filter/search semantics: results satisfy the filter, are a subset of the unfiltered list, and are stable under equivalent filters | ST5 |
| C3 | Persistence round-trip: value written = value read, for every writable field | ST4; L1 ("fails to persist") |
| C4 | Cross-view / cross-record consistency (list vs item vs search; symmetric relations) | ST5, ST4; L1 |
| C5 | Domain/semantic plausibility of response values | L1 (semantic correctness) |
| C6 | Stateful workflow / state-transition correctness | ST2; L2 (stateless testing) |
| C7 | Session/token lifecycle and role variation | ST6, ST7; L3 |
| C8 | Environment/representation variation (locale, format, pagination, content negotiation) | ST3 |
| C9 | Runtime error surface (5xx, leaked internals) | ST10 |
| C10 | File/upload/storage handling where multipart or file operations exist | ST11 |

**Overlap [A]:**
- **Shared ground:** C1≈B2, C9≈B1, and C7 partly overlaps B8.
- **Only in B:** B3, B4, B6, B7, B9.
- **Only in C:** C2, C3, C4, C5, C6, C8, C10 — mostly *functional/semantic oracles*.

So C is not "B plus extras": each arm lacks some checks the other has.

### 4.4 Attribution and its honest limits

- **B − A (RQ1):** the effect of conditioning generation on selected strategies versus unguided generation. *Limit:* it bundles "feature-based selection" with "strategy prompting". Separating them needs an extra arm ("apply every catalogue strategy to every operation"). We do **not** propose that arm now; we state the bundling as a limitation.
- **C − B (RQ2):** the effect of *catalogue provenance* — taxonomy-derived versus established-tool-derived — holding the selector, budget and prompts fixed.

**Honest limits of RQ2:**
- **What a C > B result does not show:** it would not prove that only a taxonomy could have produced C's strategies. Several C strategies (C2–C4) can also be motivated from general metamorphic testing [SEG18].
- **What it would show:** that deriving the catalogue systematically from an empirical defect taxonomy produced a measurably more effective catalogue than the state-of-practice tool checks. That is the defensible claim.
- **Strawman criticism:** B could be called a strawman because it lacks functional oracles. Mitigation: B is defined *a priori* by an objective rule (checks implemented in widely used tools), not by us choosing weak strategies; both catalogues get the same development effort on the dev set (§7.6). Per-mechanism results (RQ3) will show where any C advantage comes from.
- **Pre-registered decision rule:** if, after development, C and B differ only cosmetically (e.g. the same strategies with different wording), we drop the three-arm design and report A vs C only, explaining why. We will not keep an artificial arm.

---

## 5. Feature-to-strategy selection methodology [R]

### 5.1 Features (deterministic, logged)

**From the project-level description (spec):**
- **Per operation:** method; path template; path parameters (name, type); query parameters (name, type, enum, required); body present and schema detail; documented status codes (incl. 401, 403, 404); security requirement.
- **Per resource** (grouped by path prefix):
  - `has_collection_and_item` (`/x` and `/x/{id}`);
  - CRUD set present;
  - action sub-resources (`/x/{id}/<verb>`);
  - alternate key lookups (`/x/ref/{ref}`, `/x/code/{code}`);
  - filter-like query parameters (a fixed name list: `filter`, `sqlfilters`, `search`, `q`, `where`, `sort*`, `order*`, `limit`, `offset`, `page`, `fields`);
  - multipart/file operations.
- **Session/role:** security scheme present; the fixture provides ≥ 2 roles (configuration, not spec).

**From exploration (§6), shape only:**
- per GET operation: response kind (object/array); field names and JSON types; nesting; date/time format detected;
- `has_id_like_field`;
- `has_reference_array` — a boolean computed by code: a string array whose values fall in the domain of an ID-like field of the same collection.

No observed values are passed on.

### 5.2 Trigger predicates

Each catalogue entry has a boolean predicate over these features. Examples (draft):
- C2 ← GET collection ∧ filter-like parameter.
- C3 ← (POST ∨ PUT/PATCH) on a resource ∧ GET on the same resource.
- C4 ← `has_collection_and_item` ∨ alternate key ∨ `has_reference_array`.
- C5 ← GET returning objects with ≥ 1 string field.
- C6 ← action sub-resource ∨ ≥ 3 distinct methods on the resource.
- C7 ← security scheme ∧ ≥ 2 fixture roles.
- B6 ← DELETE ∧ GET on the same item.

Predicates read features only. The selector package cannot import any benchmark-metadata reader; a CI test enforces this.

### 5.3 Allocation (no priors, D6)

For each operation, list its triggered strategies in the catalogue's fixed order. Fill its s slots round-robin from that list, starting at index (seed mod length) so that, across seeds, every triggered strategy is used. If nothing is triggered, the operation's slots fall back to the catalogue's designated default strategy (B1 / C9).

Every decision is logged: operation, strategy, predicate, feature values.

### 5.4 Freezing

Both catalogues, predicates, the feature extractor, prompts, DSL and budget are tagged in git **before** any holdout deployment or ground-truth inspection. Any later change is logged in a post-freeze change log and forces all three arms to be rerun.

---

## 6. Exploratory API analysis protocol

### 6.1 Why exploration is needed: spec-only vs spec + exploration [V]

| Subject | What the spec provides | What spec-only extraction cannot see |
|---|---|---|
| RESTCountries | 10 GET operations with parameters; **no response schemas**; wrong server URL | Every response field, so C3/C4/C5 (and B4) cannot trigger from response structure |
| Dolibarr (base spec) | 387 operations, 132 definitions, documented codes; request bodies are an opaque `request_data`; many GET responses typed `string` | Writable fields and response fields |

With spec-only features, the response-based strategies are near-inert on both development subjects. The comparison would then test selection on a crippled feature set.

### 6.2 Recommendation [R]

Include a **minimal** exploration stage in the initial implementation: GET-only requests, shape-only features, a fixed budget, no LLM involvement, and the same output for every arm. Run **spec-only as a later ablation** (cheap once the pipeline exists).

### 6.3 Reference version

The owner suggested a patched version. We recommend against it:
- A patched version contains the fix. In RESTCountries the patch for #199 (patch index 2, `8ec4d03`) is 17 commits after its buggy version and changes 478 (country, field) value pairs, including the separate "Kiev → Kyiv" fix [V]. Exploring it would expose correct values.
- Shape-only extraction blocks values, but a fix can also add or remove *fields*.

**Rule [R]:** per project, the reference version is the **buggy version of the defect with the earliest buggy commit date**, deployed with the benchmark's own `checkout --buggy`.
- It predates every patch in the project (given each patch follows its buggy commit), so it cannot contain fix-introduced structure.
- It is chosen by a mechanical rule from commit dates, without reading any defect description.

**Verified example (RESTCountries):**
- Earliest buggy commit: `aadbd07` (23 Apr 2019; buggy version of #177, #199 and #235).
- Across the 28 RESTCountries versions in the benchmark, all have the same 24 field names, but the observed *types* differ: in 4 versions (`aadbd07`, `b8f9410`, `01769a8`, `4c30d5a`) `gini` holds only floats/nulls, while in the other 24 some values are integers [V].
- So shapes are stable in field names, but type-level details can drift between versions. An older reference version can only *omit* later structure; it cannot reveal a fix. This is the trade-off: safe but possibly incomplete.

**Caveat [V]:** for nocodb, netbox, mastodon, kafka-rest and seaweedfs, `checkout` runs per-issue setup hooks (`run_issue_hook`), so the reference deployment carries seed data for that one defect. Shape-only extraction limits what this can reveal. We also run a sensitivity analysis that excludes each project's reference defect.

### 6.4 Allowed requests, budget, extraction

| Item | Rule [R] |
|---|---|
| Methods | GET only. No writes, so no state change and no need for valid payloads. |
| Requests | Per GET operation: ≤ 1 request without path parameters (collection) + ≤ 1 item request, using an ID taken from that response by code. No guessed values and no LLM. |
| Budget | ≤ 2 requests per GET operation; 10 s timeout per request; total wall-clock cap 15 min per project; numbers fixed in config and reported. Expected worst cases: RESTCountries ≤ 20 requests; Dolibarr base spec has 178 GET operations [V], so ≤ 356. |
| Authentication | Fixture credentials (admin role) only. |
| Recording | Every request/response is stored in full with SHA-256 in an exploration log (researcher-side). |
| Extraction | Code infers a JSON shape from each response (keys, JSON types, nesting, format tags) and computes the boolean relation features of §5.1. **Only the shape and booleans enter the feature record and prompts; raw values never do.** |
| Sharing | One exploration run per project; its feature record is the single input for A, B and C and all seeds. |
| Leakage barriers | The reference version is chosen mechanically; GET-only; shape-only; no defect identifiers in any input; a prompt audit before each batch (§8.9). |

**Known gap [A]:** a freshly installed Dolibarr may return empty lists, so many shapes stay unknown. Either a defect-independent generic data fixture written from the public documentation fills them, or they stay unknown. This is to be measured in the development phase.

### 6.5 Project-level API description from per-defect specs [R]

1. Take the benchmark specs of all defects in the project. Number of distinct specs [V]: RESTCountries 1, Dolibarr 6, awx 1, flowable 2, kafka-rest 2, mastodon 1, netbox 1, nocodb 3, podman 11.
2. If there is one distinct spec, use it. If there are several, form their **union**: operations keyed by method + path; on conflict, keep the definition from the newest spec version.
   - The union is identical for every defect of the project, so it cannot single out one defect.
   - It may contain operations absent from a given version; those just return 404 on both buggy and patched and cannot create a detection.
3. **Sanitise:**
   - rename the file neutrally (`spec.json`);
   - fix the `servers`/`host` entries;
   - run an automated keyword scan (`bug`, `issue #`, `fix`, `buggy`, `patched`, `workaround`, `regression`) followed by manual review.
4. Hash and record. The Dolibarr union includes the 12 extra paths of the #26066 variant [V]. Disclose this as a mild, project-wide bias, not a per-defect leak.

---

## 7. Development / held-out evaluation split

### 7.1 Development set [R]

RESTCountries (15 usable) and Dolibarr (18 usable now + 4 needing a fixture), as established in the D2–D7 review. Catalogues, prompts, DSL and budget may be tuned here. Results on the development set are reported separately and are never presented as the main evidence.

### 7.2 Holdout eligibility (metadata only) [V]

Counts from `holdout_meta.py`, which never prints README content, triggering endpoints or per-defect labels:

| Project | Listed | With folder + spec | Distinct specs | Ops/paths in spec | Auth scheme in spec | In paper's RQ3 30 | Per-issue setup hooks run at checkout | Notes |
|---|---|---|---|---|---|---|---|---|
| awx | 5 | 5 | 1 | 376 paths | yes | 2 | 0 | compose-based deploy |
| flowable-engine | 5 | 4 (`#3536` has no spec) | 2 | 10–103 | yes | 3 | 0 | |
| kafka-rest | 3 | 3 | 2 | 8–12 | no | 1 | 2 | needs a Kafka cluster [A] |
| mastodon | 5 | 5 | 1 | 159 | yes | 2 | 1 | |
| netbox | 6 | 6 (one folder misspelled `netBox#18669`) | 1 | 297 | yes | 0 | 4 | |
| nocodb | 6 | 6 | 3 | 82–148 | yes | 2 | 6 | every defect has a hook [V] |
| podman | 23 | 23 | 11 | 158–165 | no | 4 | 0 | builds Podman from source; one defect needs manual rootless mode [V] |
| seaweedfs | 9 | 8 (`#1776` missing; one folder misspelled `seaweeedfs#913`) | 1 | **3 paths** | no | 3 | 1 | |
| enviroCar-server | 4 | 4 | **4 (one per defect)** | 7–22 | yes | 1 | 0 | one spec contains bug text [V] |
| signal-cli-rest-api | 3 | 3 | 2 | 40 | no | 2 | 0 | deploy prints a QR device-link URL, so a real Signal account/device is needed [V] |

### 7.3 Pre-registered, ground-truth-free exclusion rules [R]

| Rule | Criterion (decidable without reading defect material) | Excludes |
|---|---|---|
| X1 | no replication folder or no spec | seaweedfs#1776, flowable-engine#3536 |
| X2 | requires an external personal account or device | signal-cli-rest-api (3) |
| X3 | per-defect hand-curated specs (distinct spec per defect *and* < 25 paths), or spec text naming a bug | enviroCar-server (4) |
| X4 | project spec documents < 5 operations (too thin for spec-based testing) | seaweedfs (8) |
| X5 | the project's reference version cannot be deployed on the team's host (Windows 11 + WSL2 + Docker Desktop) within a fixed time box (proposal: 1 working day) | decided by attempt; **Podman** is the main uncertainty |
| X6 (per defect, after freeze) | benchmark validation fails: the defect does not reproduce on buggy, or the documented expected behaviour is absent on patched, or reproduction needs a UI | decided after freeze; counted and reported |
| X7 (per defect, after freeze) | reproduction needs environment changes beyond the benchmark checkout and the generic project fixture | decided after freeze; reported, and analysed as a sensitivity subset if the change is environment-only |

**Expected holdout size:**

| Scenario | Count |
|---|---|
| Listed in the 10 non-pilot projects | 69 |
| After X1 | 67 |
| After X2–X4 (excludes 15) | **52 eligible: 29 in six projects + 23 in Podman** |
| After X6/X7 attrition [A], using development attrition as a guide (RESTCountries 1/16 unusable; Dolibarr 3/25 unusable + 4/25 needing setup) | **≈ 22–27 if Podman is infeasible; ≈ 40–48 if feasible** |

**Diversity [V, aggregate type labels from the index]** — six projects without Podman, counts before X1, so flowable-engine includes its specless defect:

| Project | Type labels |
|---|---|
| awx | T1 2, T2 2, T3 1 |
| flowable-engine | T1 1, T2 2, T4 1, T5 1 |
| kafka-rest | T2 1, T4 1, T6 1 |
| mastodon | T1 1, T2 2, T3 1, T4 1 |
| netbox | T2 5, T4 1 |
| nocodb | T2 4, T4 1, T5 1 |
| podman | T1 9, T2 10, T4 2, T5 2 |

All six types appear, but T2 dominates. Podman would add T1 weight.

### 7.4 Is the sample statistically defensible? [R, with honest limits]

- **Unit of analysis:** the defect.
  - Per (defect, arm): detection rate over k = 5 seeds.
  - Primary tests: paired **Wilcoxon signed-rank** for B vs A (RQ1) and C vs B (RQ2), **Holm-corrected**.
  - Effect sizes: mean difference in detection rate with a **project-clustered bootstrap 95% CI**.
  - Secondary: defects detected by ≥ 1 seed; exact McNemar on that binary.
- **Honest power statement:** with ~25–45 paired defects in 6–7 projects, only large differences will be statistically detectable, and generalisation beyond these projects is limited.
- **Before the holdout:** compute a power estimate from development-set variance and record it. If the estimate is poor, present the study as an exploratory comparison with effect sizes and CIs rather than claim significance.
- **No per-type significance tests** (small strata).

### 7.5 Fixtures without exposing ground truth [R]

1. Deploy each version only through the benchmark's own `checkout`, treating its deploy scripts and hooks as black boxes. Their code is not read for holdout projects.
2. A **generic project fixture** (credentials, ≥ 2 roles where the product supports them) is written **only from the product's public documentation**, before any holdout ground truth is opened. It is identical for every defect of the project.
3. The ground-truth validation (X6/X7) happens **after the freeze**. It is done by a team member following the replication README by hand, and its outcomes are recorded in a validation log.
4. Any environment-only addition found necessary in step 3 is applied to both buggy and patched deployments of that defect, logged, and handled by the X7 sensitivity analysis.

### 7.6 Researcher contamination: ledger and claims [V]

**Already seen by the drafting assistant (16 Nov 2026):**
- triggering-endpoint paths of all spec-bearing defects (printed once by an audit script);
- type/subtype labels of all 110 defects;
- RQ3 Table 8 (labels and tool results for the 30 RQ3 defects, 14 of which are in eligible holdout projects);
- the `enviroCar-server#45` spec text;
- the RQ3 report's analysis of `podman#14676` (not in the 110);
- the fact that one Podman defect requires rootless mode.

**Not read for holdout projects:** replication READMEs, defect CSV descriptions, patches, setup-hook bodies.

**Consequently:**
- the evaluation **must not be called blind**;
- correct wording: *"pre-registered, project-level held-out evaluation; system design frozen before ground-truth validation; researchers had partial metadata exposure (endpoint names, labels), documented in the ledger"*;
- **mitigation:** every catalogue entry must cite a published source (§4.3), and the owner checks before the freeze that no catalogue entry, predicate or prompt mentions holdout resources or endpoints;
- **sensitivity analysis:** exclude the 14 holdout defects that appear in the paper's RQ3 sample (public, detailed information).

### 7.7 Development effort parity

B and C each get the same development budget on the dev set: the same number of revision rounds, logged. Arm A's prompt is also tuned on dev for the same number of rounds. This prevents C from looking better simply because more work went into it.

---

## 8. Revised detection and evaluation protocol (D4)

### 8.1 Verified facts that shape it

- **One version at a time:** `checkout` deploys one version at a time on fixed container names and ports, and stops running services [BR]. Replay is sequential.
- **RESTCountries co-resident defects:** buggy version `aadbd07` is shared by #177, #199 and #235. Patched versions are often the buggy versions of other defects, e.g. `04ca022` [V].
- **RESTCountries fix sizes:** 13 of the 16 patches are 1 commit after their buggy version (#179 and #202: 2). Each changes 1–3 (country, field) pairs. The exception is #199 (patch index 2): 17 commits, 478 changed pairs, including the #235 fix (commit `71e04cc`, "Updated Kiev to Kyiv") [V].
- **Dolibarr fix sizes:** for 20 of the 22 usable or fixture defects, patch 1 is exactly one commit on top of the buggy SHA, touching 1–2 files. Exceptions:
  - `#24661`: 819 commits / 324 files between buggy and patch 1, while the fix commit itself touches 4 files;
  - `#26066`: 3 commits, 1 file.

  No two usable Dolibarr defects share a buggy version [V].
- **State:** Dolibarr keeps state in a MariaDB volume; RESTCountries is read-only static data [V/A].

### 8.2 Deployment and execution plan [R]

**For each defect d, and for each version v ∈ {buggy(d), patched(d) at the recorded patch index}:**
1. `checkout` v → health check, then the generic fixture → **snapshot** (Dolibarr: DB dump; RESTCountries: none needed).
2. For every arm × seed × repetition (r = 3): restore the snapshot → run the frozen suite in its fixed order.
3. Shut down. RESTCountries' shared buggy version `aadbd07` is deployed once and reused by #177, #199 and #235.

Running every arm against the same deployment removes deployment variance between arms.

### 8.3 Test isolation [R]

- The DSL requires each test to create the resources it uses with run-scoped unique names, to bind IDs from responses, and never to rely on global counts or pre-existing objects other than fixture objects.
- The suite runs on a restored snapshot. Every **candidate** detection is then **re-run in isolation** (restore → that test alone) r times on both versions; it stays a candidate only if the isolated re-runs reproduce the verdict.
- This is far cheaper than restoring before every test and still removes order effects from the final result.

### 8.4 Outcome classes (per test, per version)

| Class | Definition | Counts toward detection? |
|---|---|---|
| PASS | all assertions hold in all r runs | — |
| FAIL | an assertion fails in all r runs with valid HTTP exchanges | candidate input |
| FLAKY | verdicts differ across the r runs | never; reported |
| ERROR-INFRA | deploy or health-check failure, connection error, timeout, gateway error | never. Redeploy up to 2×; if it persists, the defect is *not evaluable* for that run |
| ERROR-TEST | malformed or unexecutable test (unresolved capture, invalid DSL, its own setup step fails on **both** versions) | never; reported as invalid-test rate |

### 8.5 Differential candidate and attribution

**Candidate (t, d):** t is FAIL on buggy(d) and PASS on patched(d), with no ERROR-INFRA, and the isolated re-run confirms it.

**Attribution — co-resident defects and large patches:**
- **RESTCountries (mechanical) [R]:** define the target delta of d as the (country, field) pairs changed by **d's own fix commit** (diff of the patch commit against its parent), not buggy → patched.
  - A candidate is attributable only if its failing assertion reads a (country, field) in that target delta.
  - Example: a "UKR capital = Kyiv" test fails on `aadbd07` and passes on #199's patched version, but it is **not** a #199 detection. #199's own fix commit (`8ec4d03`) changes only MKD's `name`, `nativeName`, `altSpellings` and `translations` [V].
- **Dolibarr [R]:** the target delta is the fix commit's files. A reviewer judges whether the failing request exercises the documented behaviour (README + fix commit). `#24661` is flagged in advance because 819 intervening commits make unrelated differences likely.

**Blinded confirmation [R]:** for every candidate, reviewers record:
- **Verdict:** Confirmed / Related / Unrelated / Spurious;
- **Evidence:** defect, test hash, failing exchange on buggy, exchange on patched, failing assertion, rationale;
- **Who and when:** reviewer, date.

Arm labels are hidden. Two reviewers on all candidates, or ≥ 30% double-coded; report Cohen's κ.

**Detection(d, arm, seed)** ⇔ ≥ 1 Confirmed candidate. "Related" results are reported as a sensitivity count.

### 8.6 False positives and unrelated failures

| Metric | Definition |
|---|---|
| Patched-version failures | stable FAIL on patched(d). Triage (all, or a random sample of 50 per arm if more) into *wrong oracle* (e.g. LLM factual error), *other real defect*, *environment*. **False-positive rate = wrong-oracle failures / executed tests.** |
| Buggy-only unattributed failures | FAIL on buggy, PASS on patched, not attributable to d. Reported separately; not counted as detections or false positives. |
| Invalid-test rate | ERROR-TEST / executed tests |
| Flaky rate | FLAKY / executed tests |

### 8.7 Metrics recorded per (defect, arm, seed)

Detected (0/1); candidates; Confirmed/Related/Unrelated/Spurious counts; patched-version failures by triage class; invalid and flaky counts; tests and requests executed; tokens in/out; generation and execution time; selected strategies (B/C).

### 8.8 Pre-run validation per defect

As in X6: before any arm runs, a researcher reproduces the README behaviour on buggy and checks the expected behaviour on patched. Failures are excluded and listed, e.g. RESTCountries #237 [V]. The patch index used is recorded; #199 uses index 2 [V].

### 8.9 Leakage audit per batch

Before results are analysed, a script scans every stored prompt for defect IDs, issue URLs, and triggering-endpoint strings that the project description does not legitimately contain. A hit voids the batch.

---

## 9. Feasibility risks

| # | Risk | Likelihood / impact [A] | Mitigation / go-no-go check |
|---|---|---|---|
| F1 | Dolibarr installer and API key cannot be scripted; manual setup for ~44 deployments | high / high | Dev-phase check: script the install for one version. If not achievable in 2 days, keep Dolibarr dev-only with fewer defects. |
| F2 | Dolibarr deploy cost: full repo clone and image build per checkout | medium / medium | Measure in Phase 1; cache images per SHA if the CLI permits. |
| F3 | Podman not feasible on WSL2, or unsafe: the API manipulates the host's containers | high / high (23 defects) | X5 time box; run inside an isolated WSL distribution or VM. If infeasible, the holdout is ≈ 22–27 defects. |
| F4 | Holdout too small for significance | high / medium | Power estimate from dev before freeze; fall back to effect sizes with CIs; do not overclaim. |
| F5 | Exploration returns empty data on fresh installs | medium / medium | Generic documented data fixture, or accept unknown shapes; measure on dev. |
| F6 | Large Dolibarr spec (387 ops) × 3 arms × 5 seeds makes LLM cost and time high | medium / medium | Calibrate s; cap tokens; restrict to operations under the project's API prefix; pin a cost budget. |
| F7 | C ≈ B after development | medium / medium | Pre-registered rule (§4.4): drop to two arms and explain. |
| F8 | Reviewer workload and blinding with a small team | medium / medium | Confirmation sheet with arms anonymised by script; double-code ≥ 30%. |
| F9 | LLM non-determinism even at fixed settings; model deprecation | medium / low | k seeds; record model version and date; store all outputs. |
| F10 | Benchmark CLI bugs or hard-coded ports (e.g. RESTCountries fork commit) | medium / low | Pin `265db11`; log and report workarounds. |
| F11 | Timeline (final-year schedule) | unknown / high | Owner to provide deadlines; RESTCountries end-to-end first is the earliest useful milestone. |
| F12 | Contamination criticism | certain / medium | Ledger (§7.6), freeze, published-source citations, RQ3-excluded sensitivity analysis. |

---

## 10. Recommended final wording (all remain PROPOSED / PROVISIONAL)

| # | Wording | Status |
|---|---|---|
| D2 | Use the Defects4REST taxonomy (6 types / 13 subtypes; authoritative labels from `bug_replication/README.md` at commit `265db11`, identical to the replication package's `607_defects_types.csv`) as reference vocabulary for stratification and reporting, and as the **derivation source of catalogue C**. Every C entry cites a subtype or limitation class. Per-defect labels are never a system input. | PROPOSED |
| D3 | Defect types and test strategies are separate. Strategies live in versioned catalogues (B: established tool checks; C: taxonomy-derived) with feature predicates, oracle templates and provenance. One shared deterministic selector allocates s slots per operation without priors and logs reasons. Catalogues are frozen before the holdout. | PROPOSED |
| D4 | Detection requires a frozen test that is stably FAIL on buggy and PASS on patched (recorded patch index) over r = 3 runs from identical snapshots, is reproduced in isolation, is attributable to the defect's own fix delta, and is Confirmed by arm-blind review. Infrastructure, invalid and flaky outcomes never count. Defects failing pre-run validation are excluded and listed. False positives = wrong-oracle failures on patched versions. | PROPOSED |
| D5 | System inputs are limited to a sanitised project-level description (union of the project's specs), GET-only shape-only exploration of a mechanically chosen reference version, and runner-supplied base URL and symbolic roles. Enforced by an allow-listed loader that fails on violation, per-run manifests with hashes and full prompts, and a per-batch prompt audit. Suites are generated per project, never per defect. | PROPOSED |
| D6 | No prevalence priors in the initial system; prevalence weighting is a possible later ablation computed excluding evaluation projects. | PROPOSED |
| D7 | RESTCountries (15 usable) and Dolibarr (18 usable + 4 with fixture) form the development set, subject to deployment and validation checks. Development results are reported separately. | PROVISIONAL |
| D11 | Final evaluation uses project-level holdout: eligible projects after pre-registered rules X1–X5 (expected: awx, flowable-engine, kafka-rest, mastodon, netbox, nocodb, and Podman if X5 passes); per-defect rules X6–X7 after the freeze. Described as pre-registered and design-frozen, **not blind**, with the contamination ledger published. | PROPOSED |
| D12 (new) | Three arms (A plain; B feature-conditioned with established-tool catalogue; C feature-conditioned with taxonomy-derived catalogue), differing only in planner/catalogue and prompt task section, under equal information, model, calls, slots and request caps. If B and C cannot be made substantively different on the dev set, report A vs C only. | PROPOSED |
| D13 (new) | Initial implementation includes minimal GET-only, shape-only exploration of the earliest-buggy reference version with a fixed budget, shared by all arms. Spec-only is a later ablation. | PROPOSED |

---

## 11. Owner decisions needed before implementation

1. Accept the B definition: *established tool checks only*, or should it also include general metamorphic principles [SEG18]? Including them makes B stronger but brings C closer to B.
2. Accept earliest-buggy as the reference version instead of a patched version (§6.3).
3. Accept exclusion rules X2–X4 (signal-cli, enviroCar, seaweedfs) and the Podman time box X5.
4. Name the confirmation reviewers (two people), or accept 30% double-coding.
5. Provide the project deadlines (F11), so s, k, r and the holdout size can be fixed realistically.
