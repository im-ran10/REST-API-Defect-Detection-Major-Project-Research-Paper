# Methodology revision 2 (final checkpoint before implementation)

**Status: PROPOSAL FOR OWNER REVIEW.** No decision status is changed. Nothing has been implemented, committed or pushed.
**Date:** 16 Nov 2026. **Amends:** `2026-11-16_final_methodology.md` (§ numbers below refer to that document).
**Labels:** [V] verified against a source or by script · [A] assumption/judgement · [R] recommended design.
**New scripts:** `analysis/benchmark_audit/spec_provenance.py`. The reference-version checks were one-off commands, reproducible from the commands recorded in this document.

---

## 1. Changes to the three arms

### 1.1 Arm B strengthened [R]

B = established tool checks (B1–B9, unchanged) **plus** the six Metamorphic Relation Output Patterns of Segura et al. (TSE 2018) [V]:

| ID | Pattern [V] | Strategy |
|---|---|---|
| B10 | Equivalence | Equivalent requests (e.g. reordered or equivalent parameters) return the same items, in any order. |
| B11 | Equality | Equivalent requests return the same items in the same order (incl. pagination consistency). |
| B12 | Subset | A more restrictive follow-up request (e.g. an added filter) returns a subset of the source result. |
| B13 | Disjoint | Requests over disjoint partitions share no items. |
| B14 | Complete | The union of partition results equals the unpartitioned result. |
| B15 | Difference | Source and follow-up results differ only in the expected properties (e.g. a field projection). |

### 1.2 Overlap reassessed

| C strategy (taxonomy source) | Counterpart in strengthened B | Overlap |
|---|---|---|
| C1 schema/payload validation (ST4) | B2, B3 | high |
| C2 filter/search semantics (ST5) | B12–B14 | **high** (was unique before) |
| C4 cross-view / cross-record consistency (ST4/ST5, L1) | B10/B11 partly | medium |
| C8 environment/representation variation (ST3) | B11/B15 partly (pagination, projection) | medium |
| C9 runtime error surface (ST10) | B1 | high |
| C7 session/token lifecycle, role variation (ST6/ST7, L3) | B8 (access enforcement only) | low–medium |
| **C3 persistence round-trip of written values (ST4, L1)** | none (B7 checks only that a created resource *exists*) | **none** |
| **C5 semantic/domain plausibility of values (L1)** | none | **none** |
| **C6 workflow / state-transition correctness (ST2, L2)** | none (sequences in tools, but only with crash/4xx oracles) | **none** |
| **C10 file/storage handling (ST11)** | none | **none** |

**Assessment [A]:** a meaningful distinction remains, but it is narrower than before. After strengthening B, C's distinct content is four strategy families — persistence (C3), semantic plausibility (C5), workflow state (C6), storage (C10) — plus parts of C7. All four trace to the paper's empirical failure analysis of what existing tools miss (L1 semantic correctness, L2 stateless testing) and to subtypes ST2 and ST11.

RQ2 therefore becomes a precise question: *do strategies derived from the taxonomy's documented failure classes add detection beyond a strong general catalogue (tool checks + metamorphic relations)?*

**Honest limit:** read-after-write (C3) can also be argued to be a general principle. Our provenance rule assigns to B only what appears in the named sources (tool check lists, Segura's MROPs, RFC 9110); C3 is not in them. The paper must state this rule explicitly, so readers can judge the boundary themselves.

### 1.3 Recommendation: three-arm design, with a pre-registered fallback [R]

**Recommend three arms.** Without B, a C > A result cannot be attributed to the taxonomy rather than to generic strategy prompting. Arm B is what makes the paper's main claim falsifiable.

**Fallback (no ground truth needed).** At the freeze, compute *catalogue divergence* on the development subjects: the share of (operation, slot) assignments, over all seeds, whose strategy family has no counterpart in the other catalogue (according to the table in §1.2, frozen in advance).
- **If divergence < 20%:** switch to two arms (A vs C) and report RQ2 as not testable.
- **If ≥ 20%:** keep three arms.

Cost of the third arm: +50% generation and execution, and Holm correction over two primary tests (§4).

---

## 2. Revised exploratory protocol

### 2.1 Reference version (provisionally retained)

**Rule:** the buggy version of the defect with the earliest buggy commit date in the project.

**Verified for both development subjects [V]:** no patch in the project is dated before the reference, and every patch commit is a git descendant of it.
- RESTCountries: reference `aadbd07` (#177's buggy version); 0 exceptions.
- Dolibarr: reference `9d4007e` (#23179's buggy version), over the 22 usable/fixture defects; 0 exceptions.

**For holdout projects:** run the same two mechanical checks (date, ancestry) at the freeze. If any patch fails them, the rule is not safe for that project and the fallback applies (§2.4).

### 2.2 Could structure reveal targets? Channels and safeguards

| Channel | Assessment | Safeguard [R] |
|---|---|---|
| Fields or types introduced by a fix | Blocked: the reference precedes and is an ancestor of every patch [V for dev] | Mechanical date + ancestry check per project; fallback if it fails |
| Benchmark setup hooks seed data for the reference defect (nocodb, netbox, mastodon, kafka-rest, seaweedfs run `run_issue_hook` at checkout [V]) | Seeded resources could hint at that one defect's area | If a hook exists for the reference defect (checked by function name only), **exclude that defect from the primary analysis**; include it in a sensitivity analysis |
| Reference defect's own symptom visible in its shape | This is ordinary black-box observation of the system under test; shapes from an older version can only *omit* later structure | None needed; disclose |
| Error bodies (stack traces, SQL text) | Could carry code paths | Shape-only: string contents never leave the explorer; only key names and JSON types do |
| Value leakage | Correct values could appear in later versions | Values never enter features or prompts; only shapes and code-computed booleans |
| Shape/version mismatch with a target version | Causes noise, not leakage: assertions on absent fields fail on both versions | Report the "unmatched field" rate |

### 2.3 Isolation, limits, reproducibility, identical observations [R]

- **Isolation:** a dedicated deployment of the reference version, discarded afterwards and never reused for evaluation. The explorer is the only client.
- **GET-only enforcement:** the HTTP client wrapper rejects any other method and does not follow redirects.
- **Request limits:** ≤ 2 requests per GET operation (collection + one item); item IDs are picked by code (first ID in sorted order); 10 s timeout; 15 min cap per project. All limits are fixed in config and reported.
- **Reproducibility:**
  - fixed operation order (sorted by path, then method);
  - every exchange logged with its hash;
  - run the explorer **twice**: if the two shape records differ, use the intersection of fields and report the difference.
- **Identical observations:** one feature record per project with a SHA-256 hash. Every arm's and seed's run manifest must reference that same hash, or the batch is invalid.

### 2.4 Safer alternative if a check fails

Spec-only features plus response examples from the product's official documentation (no deployment). This weakens response-based triggers, so it is used only for the affected project and disclosed.

---

## 3. Revised leakage-prevention protocol

### 3.1 Specification union rejected [V/R]

A union can carry operations that exist only because one defect's curator included them. Counted by `spec_provenance.py` as operations present in exactly one defect's spec:

| Project | Distinct specs | Operations in only one defect's spec |
|---|---|---|
| Dolibarr (dev) | 6 | 7, all in #33949's spec (e.g. the `/objectlinks` endpoints) |
| nocodb | 3 | two defects, with **9 and 136** such operations |
| flowable-engine | 2 | one defect's spec is a **different API** ("Flowable APP REST API" vs "Process REST API"); 13 such operations |
| kafka-rest | 2 | one defect, 2 operations |
| enviroCar-server (already excluded) | 4 | all four defects, 1–16 each |

Projects whose benchmark specs are identical or contain no such operations: RESTCountries, awx, mastodon, netbox, seaweedfs, signal-cli, and podman (11 versions but 0 such operations; 164 of 174 operations common).

A union would therefore steer test generation toward particular defects' areas — strongest for nocodb and flowable. **§6.5 of the previous document is withdrawn.**

### 3.2 Specification source, in order of preference [R]

1. **Product-generated or official spec of the reference version**, produced with a documented, defect-independent configuration.
   - Dolibarr: at the reference commit, `htdocs/api/index.php` serves `/api/index.php/explorer/swagger.json` [V]. Which endpoints appear depends on the enabled modules [A]. Rule: enable all modules that expose API routes, then generate.
   - Other projects: availability of an official spec for the reference version is to be verified per project in Phase 1 [A].
2. **Benchmark spec** only if it is identical across the project's defects, or differs only by version with zero single-defect operations (§3.1). Use the spec attached to the reference defect.
3. **Never** a union, or a choice of spec per defect.

**New exclusion rule X8:** a project whose benchmark specs contain single-defect operations **and** for which no product-generated/official spec of the reference version can be obtained is excluded.
- At risk: nocodb, flowable-engine, kafka-rest (13 defects). Resolve in Phase 1.
- If all three were excluded, the holdout would drop to awx, mastodon and netbox (16 defects) plus Podman (23). Podman feasibility then becomes decisive.

### 3.3 Sanitisation procedure [R]

1. Neutral filename (`spec.json`); replace `servers`/`host` with the runner's base URL.
2. Automated scan of all text fields (descriptions, summaries, examples, `x-` extensions) for: `bug`, `issue`, `#\d+`, `fix`, `buggy`, `patched`, `workaround`, `regression`, `TODO`, `FIXME`, GitHub URLs.
3. **The owner** reviews the scan hits (the owner has not studied holdout ground truth) and removes only defect-revealing text, logging each edit with its reason.
4. Hash the final file. One file per project; every arm/seed manifest must reference the same hash.

### 3.4 Sensitivity analyses [R]

- **(S1) Endpoint reachability.** After all runs, classify each defect (researcher-side) by whether its triggering operation exists in the primary spec. Report detection separately for "reachable" and "not in spec" defects; all stay in the denominator.
- **(S2) Spec source, development set only.** Dolibarr runs with the product-generated spec vs. the benchmark's reference-defect spec, to show how much the spec source changes results.
- **(S3)** Exclude reference defects that had setup hooks (§2.2).
- **(S4)** Exclude the 14 holdout defects that appear in the paper's RQ3 sample (from the previous document).

---

## 4. Revised statistical analysis plan

### 4.1 Design facts

- The unit is the defect; defects are clustered in 6–7 holdout projects.
- Expected evaluable holdout:

  | Podman | Other projects at risk under X8 (nocodb, flowable-engine, kafka-rest) | Expected evaluable defects |
  |---|---|---|
  | infeasible | kept | ≈ 22–27 |
  | infeasible | all excluded | ≈ 16 |
  | feasible | kept | ≈ 40–48 |

- Power is limited by the number of **defects and projects**, not by seeds or repetitions.

### 4.2 Seeds and repetitions

- **Seeds k = 5** (matches [P]'s 5 seeds per tool [V]).
  - With k = 5 a per-defect detection rate is resolved in steps of 0.2.
  - **Pre-registered rule:** if, on the development set, more than 30% of defects show intermediate rates (0 < rate < 1) and the budget allows, raise k to 10 for **all** arms before the holdout.
  - Sampling uses a fixed non-zero temperature, with the provider's seed where available. All outputs are stored, so replay is exact regardless.
- **Repetitions r = 3** exist only to classify flaky tests; they do not add statistical power. r = 2 would already expose instability; r = 3 is kept because execution is cheap once a version is deployed and snapshotted.

### 4.3 Primary analysis (RQ1: B vs A; RQ2: C vs B)

- **Estimand:** mean over defects of the per-defect difference in detection rate (averaged over seeds).
- **Test:** within-defect **paired randomisation (sign-flip permutation) test** on the per-defect differences (10,000 permutations); Wilcoxon signed-rank reported alongside. Valid for the sampled defects without distributional assumptions; **Holm** correction over the two primary tests.
- **Effect size and uncertainty:** the mean difference with a **two-stage cluster bootstrap** 95% CI (resample projects, then defects within projects). Also report a defect-level bootstrap CI and state that the cluster CI is the honest one for generalising beyond these defects. With 6–7 clusters it will be wide.
- **Secondary model:** binomial GLMM, outcome = detected per (defect, arm, seed); arm as fixed effect; random intercepts for defect nested in project; report odds ratios. If the project variance is not estimable (singular fit), say so and refit without it.
- **Secondary binary outcome:** detected by ≥ 1 seed, with exact McNemar. Required asymmetry (computed): with 10 discordant defects, p < 0.05 needs a 9:1 split (8:2 gives p = 0.109); with 20 discordant defects, 15:5.

### 4.4 Project-level reporting and robustness

- Per project and arm: detected/evaluable defects, false-positive rate, tokens, time.
- Leave-one-project-out estimates for both primary contrasts, with a forest plot.
- Sensitivity analyses S1–S4 (§3.4) and the hook exclusion (§2.2).

### 4.5 Claim discipline

- Inference covers the sampled defects and projects. **No claims about REST APIs in general** and no per-type significance tests.
- If fewer than 20 evaluable holdout defects remain, the holdout is reported **descriptively** (effect sizes and CIs, no hypothesis tests) and the paper says so.
- A non-significant result is reported as such; there will be no post-hoc subgroup hunting.

---

## 5. Updated decision wording (all remain PROPOSED / PROVISIONAL)

| # | Wording | Status |
|---|---|---|
| D2 | Defects4REST taxonomy (6 types / 13 subtypes; labels from `bug_replication/README.md` at `265db11`, identical to the replication package's `607_defects_types.csv`) is the reference vocabulary for stratification and reporting and the derivation source of catalogue C; each C entry cites a subtype or limitation class. Per-defect labels are never a system input. | PROPOSED |
| D3 | Defect types and test strategies are separate. Two frozen catalogues: **B** = established tool checks + Segura et al. metamorphic output patterns + RFC 9110 semantics; **C** = strategies derived from the Defects4REST taxonomy and its documented tool limitations. Membership follows a stated provenance rule. One shared deterministic selector allocates s slots per operation, without priors, logging reasons. | PROPOSED |
| D4 | A detection is a frozen test that is stably FAIL on buggy and PASS on patched (recorded patch index) over r = 3 runs from identical snapshots, reproduced in isolation, attributable to the defect's own fix-commit delta, and Confirmed by arm-blind review — by two independent reviewers, or ≥ 30% double-coded with κ reported. Infrastructure, invalid and flaky outcomes never count. Defects failing pre-run validation are excluded and listed. False positives = wrong-oracle failures on patched versions. | PROPOSED |
| D5 | System inputs: one sanitised, hashed, project-level spec chosen by the preference order of §3.2 (product-generated/official spec of the reference version; else an identical benchmark spec; never a union or a per-defect spec); the shared exploration feature record; runner-supplied base URL and symbolic roles. Enforced by an allow-listed loader, run manifests with hashes and full prompts, and a per-batch prompt audit. Suites are generated per project, never per defect. | PROPOSED |
| D6 | No prevalence priors in the initial system; prevalence weighting is a possible later ablation computed excluding evaluation projects. | PROPOSED |
| D7 | Development set: RESTCountries (15 usable) and Dolibarr (18 usable + 4 with fixture), subject to deployment/validation checks; results reported separately. | PROVISIONAL |
| D11 | Project-level holdout from projects passing X1–X5 and X8; per-defect rules X6–X7 after the freeze. Podman is attempted within a one-working-day feasibility limit. Described as pre-registered and design-frozen, not blind, with the contamination ledger published. If fewer than 20 evaluable defects remain, report descriptively. | PROPOSED |
| D12 | Three arms (A plain; B general-principles catalogue; C taxonomy-derived catalogue) differing only in planner/catalogue and prompt task section, under equal information, model, calls, slots and request caps. Two-arm fallback (A vs C) if catalogue divergence on the development set is < 20%. | PROPOSED |
| D13 | Initial system includes GET-only, shape-only exploration of the earliest-buggy reference version, subject to per-project date/ancestry checks, hook-based exclusion of the reference defect, isolation, fixed limits, double-run reproducibility, and one hashed feature record shared by all arms. Fallback: spec plus official-documentation examples. Spec-only is a later ablation. | PROVISIONAL |

---

## 6. Unresolved risks

| # | Risk | Status / next action |
|---|---|---|
| R-a | Holdout size: X8 may remove nocodb, flowable-engine and kafka-rest; Podman may fail X5. Worst case ≈ 16 defects | Resolve both in Phase 1; small-sample rule (§4.5) applies |
| R-b | Dolibarr install and API key automation (~44 deployments) | Phase 1 go/no-go: script one version |
| R-c | Product-generated specs depend on enabled modules; "all API modules" may be heavy or unstable | Test in Phase 1 on Dolibarr |
| R-d | The B/C boundary (C3 read-after-write) can be disputed | Provenance rule stated in the paper; per-strategy results shown |
| R-e | Exploration on fresh installs may yield few shapes | Measure on dev; documented generic data fixture or accept unknowns |
| R-f | LLM cost and time for large specs (Dolibarr ~490 operations across specs; netbox 1,156 in its spec) × 3 arms × 5 seeds | Calibrate s and a token budget on dev; pin a total budget before the holdout |
| R-g | Researcher contamination (endpoint names, labels already seen) | Ledger, freeze, source-cited catalogue entries, owner review of scan hits, sensitivity S4 |
| R-h | Novelty: the systematic prior-art check is still pending; Defects4REST is now being cited | Do it before writing the related-work section; re-check before submission |
| R-i | Two independent reviewers may not be available | Fall back to ≥ 30% double-coding with κ |

---

## 7. Recommendation

Adopt the **three-arm design**, with arm B strengthened by Segura et al.'s metamorphic patterns, the pre-registered divergence fallback (§1.3), the spec preference order without unions (§3.2), the exploration safeguards (§2), and the analysis plan (§4).

The two remaining items that most affect feasibility — Podman (X5) and specs for nocodb, flowable-engine and kafka-rest (X8) — are **Phase 1 checks**, not further research. With them resolved, the methodology can be frozen and implementation can start.
