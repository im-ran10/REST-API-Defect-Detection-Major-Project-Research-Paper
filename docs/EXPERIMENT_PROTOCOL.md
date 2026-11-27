# Experiment Protocol

> **Status: METHODOLOGY PROVISIONALLY APPROVED (16 Nov 2026) — NOT FROZEN.** No experiment has been run and there are no results.
>
> - **Freeze point:** the protocol is frozen at the end of the development phase, before any holdout deployment (Handover §19; D11).
> - **Undecided values:** items marked **TBD** are deliberately undecided — do not fill them with guesses.
> - **Decision status:** items tagged D# carry the status shown in [DECISIONS.md](DECISIONS.md).
> - **Full reasoning:** `docs/proposals/2026-11-16_final_methodology.md`, as amended by `docs/proposals/2026-11-16_methodology_revision_2.md` (the latter takes precedence).
>
> **Amended 23 Nov 2026 — ten-day design (D23–D43).** Scale reduced for a 10-day first draft; design, arms, detection protocol and safeguards unchanged. Reasoning: [`proposals/2026-11-23_ten_day_design_and_qc.md`](proposals/2026-11-23_ten_day_design_and_qc.md). Where a section below carries an *Amendment 23 Nov 2026* note, the note takes precedence over the earlier text, which is kept for history. D30 (people) is still PROPOSED; all other D23–D43 rows are PROVISIONAL.

## 1. Research questions

Main question (unchanged, Handover §2.2):

> Can selecting and prioritizing test-generation strategies using an empirical taxonomy of REST API defects improve the detection of real-world REST API defects compared with ordinary LLM-generated tests under the same testing budget?

Operationalised as:

- **RQ1 (selection effect):** does Arm B detect more real-world defects than Arm A under equal budgets?
- **RQ2 (taxonomy effect):** does Arm C detect more than Arm B under the same selector, budget and prompts?
- **RQ3 (descriptive):** which defects and detection mechanisms does each arm find, at what false-positive and cost levels?

RQ1 and RQ2 are the two pre-registered primary comparisons; C vs A is secondary. These are hypotheses, not findings.

## 2. Arms (D12, PROVISIONAL)

| | A — plain | B — general principles | C — taxonomy-derived |
|---|---|---|---|
| Planner | *s* slots per operation, no strategies | shared selector + `catalogue_B` | shared selector + `catalogue_C` |
| Prompt task section | "write *s* tests likely to reveal defects" | one slot per selected B strategy (description + oracle template) | one slot per selected C strategy (taxonomy-derived manifestation + oracle template) |

**Held identical across arms:**
- the project-level spec and the exploration feature record;
- the LLM provider, model version and settings;
- one generation call per operation;
- *s* slots per operation and the request caps;
- the JSON test DSL and the system prompt;
- deployments, replay, fixtures and verdict rules.

**Catalogues (D3.1).** Draft entries are in the proposals (final methodology §4.3; revision 2 §1).
- **B** = Schemathesis/RESTler checks + Segura et al.'s six metamorphic output patterns + RFC 9110 semantics.
- **C** = taxonomy-derived; every entry cites a subtype or L-class.
- **Membership:** a strategy belongs to B only if it appears in B's named sources.
- **Overlap:** a frozen overlap table records which strategy families have a counterpart in the other catalogue.

**Two-arm fallback.** At the freeze, compute *catalogue divergence* on the development subjects: the share of (operation, slot) assignments whose strategy family has no counterpart in the other catalogue. If it is < 20%, run A vs C only and report RQ2 as not testable.

**Amendment 23 Nov 2026 (D34, D35, D36, D41).**
- **B2** carries the Vieira et al. (DSN 2007) input-mutation operators by data type as its operator detail, disambiguated for HTTP (key absent / value empty / value null); no new B entry; B's provenance rule gains this source.
- **Entry format (B and C):** name; applicability; how to probe; oracle template; one worked example on a small **fictional** API (never a development or held-out project); source. Mean tokens per entry within ±20% between B and C, measured and reported. The shared system prompt (identical for A, B, C) contains one JSON-format example.
- **Mapping artefact:** each trigger rule records ID, observable predicate, strategy, applicability, source, rationale, oracle template, leakage control and validation status (Illustrative → Development-checked → Held-out evaluated).
- **Predicate corrections (D41):** filter names (`filter`, `filters`, `sqlfilters`, `search`, `q`, `query`, `where`) → C2/B12; pagination/projection/sort names (`limit`, `offset`, `page`, `per_page`, `fields`, `sort*`, `order*`) → C8/B11/B15; C6 = action sub-resource or create–read–(update or delete) lifecycle; C10 = multipart bodies only; C7 only where the generic fixture provides a second role.
- **Divergence (D31):** Div_C = share of C's (operation, slot) assignments, over development operations, *s* slots and the 5 seed offsets, with no B counterpart; Div_B reported; the overlap table is committed before the first computation; the fallback triggers if Div_C < 20%.

*T8 implementation (27 Nov 2026, DRAFT until the freeze):* catalogues and overlap table in `system/src/main/resources/catalogues/`; mapping artefact `docs/frozen/CATALOGUES.md`; selector `restdd.select` (features, predicates, round-robin allocation, shared slot template; sources not shown in prompts); `generate --arms A,B,C` logs every slot choice with its predicate and features to `selection.jsonl` before any model call; `select-report` prints firing counts, assignments and Div_C/Div_B (counts only for held-out projects). Choices and the start-index issue: D56 (PROPOSED).

**Development effort parity.** A, B and C each get the same number of logged revision rounds on the development set.

**External tools.** Schemathesis, RESTler, EvoMaster and AutoRestTest are not run in the initial plan (scope). Their published RQ3 results for 6 development defects (E20) are context only, not comparable baselines.

## 3. Selection (D3.1, D6.1)

- **Features:** deterministic, from the project-level spec and exploration shapes (final methodology §5.1).
- **Triggering:** each catalogue entry has a boolean trigger predicate over those features.
- **Allocation:** per operation, triggered strategies in catalogue order fill *s* slots round-robin, starting at index (seed mod length); default strategy if nothing triggers.
- *Amendment 27 Nov 2026 (D56, owner; pre-freeze, coverage counts only):* the start index is **(seed × *s*) mod length** (TILE) for B and C, so consecutive seeds take consecutive blocks of *s* entries. Under the original rule (MOD) 35 fired (operation, strategy) pairs were never assigned on the development operations; under TILE 0. Div_C is 34.8% under both (E69): **three arms**; Div_B 47.3% (MOD) → 40.7% (TILE).
- **No priors.**
- **Logging:** each choice is logged with its predicate and feature values.

## 4. Budgets and settings

| Item | Value |
|---|---|
| Slots per operation *s* | **TBD** (proposal 3; calibrated on dev) |
| Generation seeds *k* | 5; raise to 10 for all arms **only if** > 30% of dev defects show intermediate detection rates and the budget allows (decided before the holdout) |
| Execution repetitions *r* | 3 (flakiness classification only) |
| Temperature / provider seed | fixed non-zero temperature; provider seed where available; **TBD** values |
| Request caps (per test, per suite) | **TBD** |
| Token budget (per call, per arm, total) | **TBD**, pinned before the holdout (D15) |
| LLM provider/model | **TBD** |

**Amendment 23 Nov 2026 (D26, D27, D39).** Values above that conflict with this note are superseded:
- **LLM:** local Ollama (native `/api/chat`, JSON-schema `format`, `think` off, fixed `num_ctx`, fixed seed list), model selected on development data only: Qwen3 4B Instruct-2507 if it passes, else Gemma 3 4B. Model digest, quantisation, Ollama version, GPU driver and all decoding settings are recorded and frozen. No paid APIs, no fine-tuning.
- **Model test (G2):** fixed sample of 30 development operations (RESTCountries 10 + 20 Dolibarr operations chosen by the defect-blind cap rule) × 2 seeds = 60 calls per model, neutral Arm A prompt, provisional *s* = 3. Gate 0: no truncated prompt. T1 schema-valid calls ÷ 60 ≥ 95%; T2 spec-valid tests ÷ tests in schema-valid calls ≥ 80%; T3 executable tests (no ERROR-TEST/ERROR-INFRA) ÷ spec-valid tests ≥ 70%; T4 tests with a non-trivial assertion beyond the status code ÷ executable tests ≥ 60% (automatic classification, checked on 20 random tests). Plus 10 strategy-slot calls, which feed the mandatory adherence check (D39, superseded by D43).
- ***k*** = 5 seeds; **40-hour rule:** projected held-out generation time (actual held-out prompts token-counted before the freeze, per-arm rates from the Day 5 development runs, +15%) above 40 h → 3 seeds for all arms, then a lower operation cap. Provisional at G1, binding at the freeze. The *raise to 10* clause is superseded.
- **No retries:** exactly one call per (operation, arm, seed); a schema-invalid response = zero tests for that call.
- *G1 result, 23 Nov 2026 (E43; configuration D44, PROPOSED):* `num_ctx` **16384**, Ollama flash attention on with a q8_0 KV cache, `num_predict` 2048, D41 compaction rule; balanced power mode, plugged in, PCIe link-state power management off. Largest held-out prompt 8,973 tokens, fully processed; provisional projection 17.8 h (five seeds). **Reproducibility statement:** generation on this GPU is not bit-reproducible for a fixed seed (R36), so every raw model output is archived (call log + cache) and all verdicts are recomputable from it; seeds are independent draws. The `systemctl show ollama -p Environment` line is recorded with each run.
- *D44 approved, 23 Nov 2026 (conditions):* every raw model response (including rejected ones), exact prompt, sampling configuration, seed, model details and validation result is archived with the run; GPU temperature and power are logged per call and generation pauses above a temperature limit; runs are resumable. The configuration applies to Qwen3 4B Instruct-2507; if G2 selects Gemma 3 4B, G1 is repeated for it before the freeze.
- *Strategy adherence (D45, PROVISIONAL, owner 23 Nov 2026):* 60 tests per arm from the Day 5 development runs, stratified by strategy; a test is Adherent if its requests carry out the assigned strategy's probe action **and** an assertion checks its oracle (per-entry rule written at T8); gate B ≥ 50% and C ≥ 50%; owner codes all, a second person independently codes 30% without seeing the owner's codes, κ reported; the rubric is final before any test is judged. On failure: one symmetric revision round on development data, re-measure on fresh seeds, then the fallback model, then escalation; no silent threshold change.
- **Operation cap** per project, fixed on Day 2; selection by path group, proportional share, hash order; identical for all arms.
- *D47 (owner, 24 Nov 2026):* Podman all 165, nocodb all 126, RESTCountries all 10; Dolibarr 20 (model test) and 60 (development runs). Section = first path segment, largest-remainder shares, SHA-256-of-key order within a section. Lists and their SHA-256 frozen before the experiment; never changed on the basis of observed results.
- *Final generation configuration (D44, D41, D48; owner-approved 23–24 Nov 2026; identical for all three arms, one options object; frozen at the freeze):*

  | Setting | Value |
  |---|---|
  | Model | `qwen3:4b-instruct-2507-q4_K_M`, digest `0edcdef34593…` (D27; Gemma 3 4B only if G2 selects it, then G1 repeated) |
  | Thinking mode | **non-thinking** — the model has no thinking mode; `think` omitted; every response checked for reasoning output and flagged |
  | Server | Ollama 0.40.17, flash attention on, KV cache q8_0 (systemd drop-in), GPU-only (preflight refuses partial offload) |
  | Context / output | `num_ctx` 16384, `num_predict` 2048, `truncate` false, `shift` false (no silent truncation) |
  | Sampling | temperature 0.7, top-p 0.8, top-k 20, repeat penalty 1.0, **presence penalty 1.5** (D52, from 25 Nov 2026), seed per call (D26) |
  | Output format | JSON schema with exactly *s* tests (`SuiteSchema`), *s* = 3 provisional; text values capped at 200 characters (titles 150, paths 300, file content 2,000; D51); per-type assertion alternatives (D53, PROPOSED) |
  | Prompt | shared system prompt (SHA-256 in each manifest), compacted operation slice (D41), related operations, exploration shapes (T7), fixture names; arms differ only in the task section |
  | Machine | balanced power mode, plugged in, PCIe link-state power management off; GPU paused at ≥ 85 °C until ≤ 75 °C |
- *Model test procedure and metric definitions (T6, 24 Nov 2026):* `generate` (Arm A, seeds 1 and 2, *s* = 3, D44 configuration) → `run-suites` against the development deployments (reset before each suite) → `model-test-report`. **Spec-valid** = structurally valid (operations in the spec, references defined, assertions well formed); type/required-parameter conformance is reported, not gated. **Executable** = run outcome PASS or FAIL on that deployment (INVALID or SETUP_ERROR count as ERROR-TEST, INFRA_ERROR as ERROR-INFRA). **Non-trivial** = automatic class MEANINGFUL (an assertion beyond the status code), checked by hand on 20 random executable tests. The 10 strategy-slot calls run after T8, when catalogue entries exist.
- *T4 rubric R-T4 (D49, owner 24 Nov 2026; used unchanged for every T4 check, automatic and manual):* a test is non-trivial if at least one assertion is. Trivial: any status check; on the whole body `$`, exists / notExists / isEmpty / isNotEmpty / isType; header exists / notExists; compare of a value with itself. Non-trivial: on `$`, equals / contains (a generic word counts) / matches / in / length / number comparisons; any operator on a named part (`$.id`, `$[0]`, `$[*].x`), including field existence or absence; header value checks; responseSchema conformsToSpec; compare of two different values. Assertions are judged by what they state, not by whether they are correct.
- *T4 measurement (D50, owner 24 Nov 2026):* T4 = share of **all** executable tests of the G2 run that are non-trivial under R-T4 (classifier = rubric); threshold 60%; the manual 20-test check verifies the classifier (more than 1 disagreement → classifier corrected, report recomputed, both numbers reported).
- *G2 sequence (D49):* first attempt failed T4 (E49, 10/20); diagnostic re-run with seeds 3–4 after the shared-prompt rule (not an approval); **final G2 with the full experimental configuration including T7 shapes**; Gemma (D27) if Qwen fails the final G2; threshold unchanged.
- **Context (D41):** G1 uses the largest held-out prompt; one context window for all calls, or an arm-independent slice-compaction rule fixed before the freeze.
- **Mandatory feasibility checks (D43)** before the model configuration is frozen: full-prompt context test (zero truncation; truncation always fails the call, never silent); JSON validity (T1 ≥ 95%, T2–T4 as above); strategy adherence of B and C on the Day 5 development sample (≥ 50% per arm, default); per-arm generation time and the binding 40-hour projection. The 10 strategy-slot calls of the model test feed the adherence check (D39 superseded).

## 5. Inputs, spec source and leakage control (D5.1)

**Spec source, in order of preference:**
1. The product-generated/official spec of the project's reference version, generated with a documented, defect-independent configuration. For Dolibarr this is `/api/index.php/explorer/swagger.json` with all API-capable modules enabled: the 28 modules selected by Dolibarr's own API-discovery rule at `9d4007e` (`scripts/dolibarr/modules_r14.txt`, D22). The **same** module list is used for every Dolibarr buggy and patched deployment; each deployment records its enabled-module fingerprint (E36).
2. Otherwise, a benchmark spec that is identical across the project's defects, or differs only by version with zero single-defect operations; use the reference defect's copy.
3. **Never** a union, and never a per-defect spec.

**Sanitisation:**
1. Neutral filename `spec.json`; replace servers/host.
2. Keyword scan over all text fields: `bug`, `issue`, `#\d+`, `fix`, `buggy`, `patched`, `workaround`, `regression`, `TODO`, `FIXME`, GitHub URLs.
3. **The owner** reviews the hits; each edit is logged.
4. SHA-256 recorded. Every arm/seed manifest must reference the same hash.

**Forbidden inputs to the system:**
- replication READMEs and images;
- defect CSVs;
- `defects4rest info` and checkout output;
- deploy and setup-hook code;
- cloned upstream history;
- the replication package;
- issue trackers;
- per-defect spec files.

**Enforcement:**
- a single allow-listed input loader that fails the run on any violation;
- a run manifest (file hashes, full prompts and responses, model metadata, code commit, benchmark commit);
- a per-batch prompt audit for defect IDs, issue URLs and unexplained triggering-endpoint strings — any hit voids the batch.

Suites are generated **per project**, never per defect.

## 6. Exploratory API analysis (D13.1, PROVISIONAL)

**Reference version:** the buggy version of the project's earliest-buggy defect. Two checks per project (D13.1); if either fails, use the fallback below:
- every patch commit is dated after the reference (committer timestamps);
- no patch commit is contained in the reference (no patch is a git ancestor of it).

Whether each patch is a git descendant of the reference is reported but not required (D13.1 supersedes the stricter D13 check).

The checks pass for RESTCountries (`aadbd07`) and Dolibarr (`9d4007e`) (E22).

Holdout (E32, metadata only, 21 Nov 2026): the D13.1 checks pass for all remaining holdout projects (awx, flowable-engine, mastodon, netbox, nocodb, podman). Mastodon, netbox and nocodb have some patches that are not descendants of the reference (reported). The hook rule applies to the reference defects of netbox and nocodb.

**Hook rule:** if the reference defect has a setup hook (checked by function name only), that defect is excluded from the primary analysis and included in a sensitivity analysis.

**Execution:**
- **Isolation:** a dedicated deployment, discarded afterwards.
- **Requests:** GET only (client wrapper rejects other methods; no redirects); ≤ 2 requests per GET operation (collection + one item, with the ID chosen by code); 10 s timeout; 15 min per project.
- **Order:** operations sorted by path, then method.
- **Reproducibility:** run twice; on a difference, keep the intersection of fields and report it.

**Extraction:** shape only — keys, JSON types, nesting, format tags — plus code-computed booleans (`has_id_like_field`, `has_reference_array`). **Values never leave the explorer.**

**Sharing:** one hashed feature record per project, used by every arm and seed.

*Model selection closed (D55, owner, 27 Nov 2026):* neither candidate passed G2 (Qwen T4 54.9% / 52.9%, E60/E65; Gemma T2 5.0%, T4 5/9 on the nine tests that survived T2, E66). Deviation from D27/D43/D49(6) approved under D43's escalation clause: **Qwen3 4B Instruct-2507 with the E65 configuration** (the D44/D48/D51/D52 table above, D53 off, shared system prompt with the D49 and D54 rules, SHA-256 `5b759f15501b8a27…`). Thresholds, R-T4, D45, arms, catalogues, budgets and detection criteria unchanged; no further prompt optimisation; model selection not reopened unless a check fails after the freeze.

*Second Qwen G2 (D54, 26 Nov 2026):* shared-prompt grounding rule; seeds 7–8; thresholds and R-T4 unchanged; Gemma deferred.

*Model selection outcome (26 Nov 2026, E60):* Qwen failed the final G2 on T4; Gemma 3 4B (`gemma3:4b-it-q4_K_M`) gets its own G1 and G2 with the identical configuration except decoding (D48 Gemma clause: temperature 1.0, top-p 0.95, top-k 64, repeat penalty 1.0; no presence penalty). `generate` and `g1` take these from the model name (`ModelDefaults`) and print them.

*Implementation (T7, 25 Nov 2026; PROVISIONAL until the freeze):* `explore` (`restdd.explore`). Identifiers chosen by code: a fixture whose name equals the path parameter, else the first (then second) element of the list operation whose path is the prefix before the parameter (key preference: the parameter's own name, then id, rowid, uuid, code, ref, acronym, key, name, case-insensitive), else (E56) the first observed list (lists of the operation's own first path segment first, E57; then path order) whose elements carry a field named like the parameter (same name, its plural, or a longer name ending in it, e.g. `alpha3Code` for `{code}`); unresolvable → skipped. Required query parameters take the spec's default, enum value or example, else the operation is skipped. Requests go through the T5 runner with 10 s limits; bodies and identifiers stay in memory. Shapes: keys sorted, JSON types, string format tags (date, date-time, uuid, hex-id, integer-/number-strings, email, uri), first 5 array elements merged, objects whose keys are all numeric/UUID/hash-like (even a single key, E57) rendered as `map<string, …>` (the earlier "more than 40 keys" rule was removed, E56: it hid the field names of wide entities), fields seen with a value listed before null-only fields, depth ≤ 6. Two passes, intersection kept, differences listed. Prompt rendering: the operation under test (if an observed GET) and then its related operations, one line each (≤ 600 characters, ≤ 2,500 in total, nesting depth 3).

**Fallback:** spec plus official-documentation examples, for the affected project only.

**Later ablation:** spec-only.

## 7. Development and held-out sets (D7.1, D11, D14)

- **Development:**
  - RESTCountries (15 usable; #237 excluded; #199 uses `--patched 2`; #202 contested);
  - Dolibarr (18 usable + 4 needing a fixture; #23345, #26307, #29372 excluded).
  - Fallback if Dolibarr is infeasible (D16.1): add an already-excluded project such as SeaweedFS; else RESTCountries alone. A holdout project is moved only with a compelling, documented justification.
- **Holdout candidates after X1–X4:** awx, flowable-engine, kafka-rest, mastodon, netbox, nocodb (29 defects) + Podman (23) if X5 passes; X8 may remove nocodb, flowable-engine and kafka-rest.
  - X8 check (21 Nov 2026, E33): official specs found for nocodb and flowable-engine. **kafka-rest excluded** (D20: X8, and its reference image does not exist).
  - Current holdout (21 Nov 2026): awx 5, flowable-engine 4, mastodon 5, netbox 6, nocodb 6 = **26**, plus Podman 23 if X5 passes. Primary analysis excludes the hooked reference defects of netbox and nocodb (−2; sensitivity S3). X6/X7 attrition comes after the freeze.
  - **Holdout list fixed (22 Nov 2026):** Podman passed X5 (E37). Projects: awx, flowable-engine, mastodon, netbox, nocodb, podman = **49 defects**; **47** in the primary analysis. Podman runs in an isolated WSL distribution with Docker Desktop stopped (R21).
- **Expected evaluable holdout:**

  | Scenario | Expected evaluable defects |
  |---|---|
  | Podman infeasible, X8 removes nothing | ≈ 22–27 |
  | Worst case (Podman infeasible, X8 removes nocodb, flowable-engine, kafka-rest) | ≈ 16 |
  | Podman feasible | ≈ 40–48 |

  *Update 21 Nov 2026 (estimates, before X6/X7 attrition):* X8 removed only kafka-rest; 24 primary-analysis defects without Podman, about 47 with Podman. The scenario rows above are kept as the original planning estimates.

- **Amendment 23 Nov 2026 (D24, D25, D33).**
  - **Development runs:** RESTCountries, all 15 usable defects, 3 arms × 2 seeds, one logged revision round per arm. Dolibarr R14: generation validity, trigger coverage, divergence and #23179 only; no Dolibarr per-defect runs.
  - **Held-out evaluation:** Podman (23) + nocodb (6; 5 primary) = 28 primary defects before X6/X7. Fallback for nocodb: flowable-engine (4). awx, mastodon and netbox are reserved and stay behind the firewall.
  - **Extension after the draft:** the unchanged frozen system may run on flowable-engine; reported separately.
- **Per-defect rules after the freeze:** X6 (validation fails), X7 (needs extra environment; sensitivity subset).
- **Fixtures:** the benchmark checkout is treated as a black box; a generic project fixture (credentials, ≥ 2 roles where supported) is written only from public documentation, before any holdout ground truth is opened.
- **Claims:** "pre-registered, project-level, design-frozen held-out evaluation with partial researcher exposure" — **not blind** (ledger: RESEARCH_LOG §9).
- **Fallback if real defects prove infeasible overall** (Handover §8.1, risk R6): a controlled mutation benchmark such as RESTestBench. Check its scope and artifacts first; it is not planned unless Phase 1 fails broadly.

## 8. Deployment and execution (D4.1)

For each defect *d* and each version (buggy; patched at the recorded index):
1. checkout → health check → generic fixture → snapshot (Dolibarr DB dump; RESTCountries none);
2. for each arm × seed × repetition: restore the snapshot → run the frozen suite in a fixed order;
3. shut down.

Notes:
- **Shared buggy versions** are deployed once (RESTCountries `aadbd07` serves #177, #199, #235).
- **Same deployments for all arms:** every arm runs against the same deployments.
- **Test independence:** the DSL requires self-contained tests (own resources, run-scoped unique names, bound IDs, no global counts).
- **Isolated re-runs:** every candidate is re-run alone (restore → single test) *r* times on both versions.

**Amendment 23 Nov 2026 (D28, D32, D41).**
- Step 2 becomes: one **screening** run per arm × seed; every suite run with at least one candidate (FAIL on buggy, PASS on patched) gets 2 more **full** runs from fresh snapshots in the same order; then isolated re-runs. Detections are identical to running every suite *r* = 3 times; the flaky rate is reported for re-run suites.
- **Dolibarr generic fixture (T5, 25 Nov 2026, PROVISIONAL):** `scripts/dolibarr/dolibarr_fixtures.py` creates, through the public API only, one customer, one contact, one product, and one draft proposal, order and invoice with one product line, plus (2-hour box) a second user without rights and its own API key for the `secondary` role; fixture IDs go into the target file and the snapshot is taken afterwards. Identical for every arm; independent of any defect (E56).
- **Reset adapters:** RESTCountries none; Dolibarr DB dump (waits up to 90 s for the database); Podman: fixture pre-loads one small image before the snapshot, reset removes containers, pods, volumes, networks and secrets but keeps it (Docker Desktop stopped, R21); nocodb: container/SQLite restore.
- **Order:** Podman first, then nocodb; never both at once.
- **Execution projection** at the freeze from measured deploy, reset and suite times. Overrun (D32): 5 seeds kept, nocodb finishes late; 3 seeds only if the 40-hour generation rule makes 5 infeasible, recorded with its reason and written into the protocol before any comparative result is examined.

## 9. Outcomes, detection and attribution (D4.1)

| Class | Definition | Counts toward detection? |
|---|---|---|
| PASS | all assertions hold in all *r* runs | — |
| FAIL | an assertion fails in all *r* runs with valid exchanges | candidate input |
| FLAKY | verdicts differ across runs | never |
| ERROR-INFRA | deploy or health failure, connection error, timeout, gateway error; redeploy ≤ 2×, then *not evaluable* | never |
| ERROR-TEST | malformed or unexecutable test, or its own setup fails on both versions | never |

**Candidate:** FAIL on buggy and PASS on patched, with no ERROR-INFRA, and reproduced in isolation.

*Snapshots (D28, restated 23 Nov 2026):* every experimental snapshot is taken from a **fresh deployment** (volumes removed, rebuilt, setup script, fixtures loaded), never from a deployment that has been used before. The T5 smoke snapshot was taken from a previously used development deployment and serves only to check the tooling.

*Implementation note, 23 Nov 2026 (T5; PROVISIONAL until the freeze):* gateway errors are statuses 502, 503 and 504; a response body is read for at most 30 s and 2 MB (streaming endpoints are cut); a test whose `extract` finds nothing is a set-up failure (ERROR-TEST if on both versions, otherwise FAIL on the version where it happened); a structurally invalid test is never sent (ERROR-TEST). Assertion semantics are listed in ARCHITECTURE (T5). Response conformance is checked against the uncompacted project spec.

**Attribution to the defect's own fix-commit delta:**
- **RESTCountries — mechanical:** the failing assertion must read a (country, field) changed by *d*'s own fix commit. Example: a "UKR capital = Kyiv" test failing on `aadbd07` and passing on #199's patch is **not** a #199 detection (E15).
- **Dolibarr — reviewer-judged** against the fix commit and README. `#24661` is flagged (E19).

**Blinded confirmation (arm labels hidden):**
- **Verdicts:** Confirmed / Related / Unrelated / Spurious.
- **Evidence recorded:** defect, test hash, both exchanges, failing assertion, rationale.
- **Who and when:** reviewer, date.
- **Reviewers:** two independent reviewers preferred; otherwise ≥ 30% double-coded with κ reported.

**Amendment 23 Nov 2026 (D30 PROPOSED — owner names the people; D38).** Roles and independence rules: DECISIONS D30. Validator (X6/X7, attribution; post-freeze only; X6/X7 verdicts committed before any candidate is exported) and two arm-blind reviewers are named before the freeze. A script exports each candidate with a random ID, both exchanges, the failing assertion and the fix material; arm, seed and strategy removed; the mapping stays closed until all verdicts are committed. Unresolved disagreements: the conservative verdict counts in the primary analysis, the other in a sensitivity analysis. **Strategy adherence (A4):** on a random sample of B/C tests a reviewer judges whether each test implements its assigned strategy.

*Implementation (T10, 27 Nov 2026; PROVISIONAL until the freeze):* `restdd.verdict.VerdictEngine` (pure rules of this section) and `verdicts --work W --defect D --buggy L --patched L`. Runs are labelled `<L>-s1` (screening), `<L>-f2..f3` (full re-runs, fresh snapshot each) and `<L>-i1..i3` (isolated: restore → single test); `run-suites --only PLAN` executes the plan each stage writes. Readings made explicit: a run missing for a test counts as ERROR-INFRA (not evaluable); FAIL mixed with SETUP_ERROR on one version is FAIL; candidates must FAIL on buggy and PASS on patched in **every** full and isolated run. Output: `candidates-D.jsonl` (with arm, seed, strategy, R-T4 class; analysis), `blinded-D.jsonl` (random ID, test hash, test **without its title** — titles can name the strategy —, failing assertions, both exchanges; export order shuffled) and `mapping-D.jsonl` (closed until all verdicts are committed). Attribution and reviewer verdicts stay researcher-side.

**Detection(d, arm, seed)** ⇔ at least one Confirmed candidate. Related results are reported as a sensitivity count.

**Pre-run validation (X6):** a researcher reproduces the README behaviour on buggy and the expected behaviour on patched. Failures are excluded and listed.

## 10. Metrics and analysis

**Per (defect, arm, seed):**
- detected; candidate and verdict counts;
- patched-version failures, triaged into wrong oracle / other real defect / environment;
- **false-positive rate** = wrong-oracle failures ÷ executed tests;
- buggy-only unattributed failures;
- invalid-test rate; flaky rate;
- tests and requests executed; **operation coverage** (share of spec operations exercised; Handover §8.3);
- tokens in/out; generation and execution time;
- selected strategies (B/C).

**Primary analysis:**
- **Estimand:** mean per-defect difference in detection rate over seeds.
- **Test:** within-defect paired sign-flip permutation test (10,000 permutations); Wilcoxon signed-rank reported alongside; **Holm** correction over RQ1 and RQ2.
- **Effect size:** mean difference with a two-stage project→defect cluster-bootstrap 95% CI, plus a defect-level CI, both reported. With 6–7 clusters the cluster CI will be wide.

**Secondary analyses:**
- binomial GLMM (arm fixed; defect nested in project as random intercepts; odds ratios; refit without project if the fit is singular);
- detected-by-≥ 1-seed with exact McNemar. Illustration: with 10 discordant defects, p < 0.05 needs 9:1; with 20, 15:5.

**Project level:** per-project tables; leave-one-project-out estimates with a forest plot.

**Sensitivity analyses:**
- S1: triggering operation in spec vs not (also covers defects whose operations lie outside the project-level spec, D21);
- S2: spec source (development set only, Dolibarr);
- S3: excluding reference defects that had hooks;
- S4: excluding the 14 holdout defects in the paper's RQ3 sample;
- X7 subset;
- Related counted as detected.

**Amendment 23 Nov 2026 (D29, D37, D38).** Supersedes the primary/secondary lists above where they differ:
- Y(d,a,j) = 1 if the suite has ≥ 1 Confirmed candidate; invalid/empty output and ERROR-TEST count as Y = 0 for that arm. Missing only for ERROR-INFRA after 2 redeploys; K_d = seeds with outcomes for all compared arms; p(d,a) = mean Y over K_d; |K_d| < 3 → defect leaves the primary analysis (listed).
- D1(d) = p(d,B) − p(d,A) (RQ1), D2(d) = p(d,C) − p(d,B) (RQ2); all-zero defects stay in (D = 0), counted.
- **Primary:** two-sided sign-flip on mean D, exact when ≤ 20 non-zero differences, else 10,000 flips (seed recorded); Holm over RQ1 and RQ2 at α = 0.05; under the D12 fallback, C − A alone.
- **Effect size:** mean D with a defect-level percentile bootstrap 95% CI; per-project means; project-balanced mean (sensitivity). Wilcoxon becomes a sensitivity analysis.
- **Dropped at this scale:** binomial GLMM, project-cluster bootstrap, leave-one-project-out (two projects). S2 is development-only and not run.
- **Minimum for significance:** the smallest exact two-sided p is 2/2^*n* for *n* non-zero differences, so Holm's first step needs ≥ 7 consistent non-zero differences; a descriptive outcome is pre-stated as likely.
- **Supported vs descriptive:** only a Holm-significant RQ1/RQ2 result is a statistically supported conclusion, and only for these defects in Podman and nocodb. Per-project results, C vs A, McNemar, per-strategy attribution, A1–A4, the C∅ ablation and RQ3 are descriptive.
- **New analyses (D38):** A1 assertion type of each Confirmed detection (status / body / cross-request); A2 status-only sensitivity; A3 cross-seed diversity (Jaccard of detected-defect sets); A4 strategy adherence.
- **Podman dominance (D42):** per-project results; a Podman-only sensitivity analysis of RQ1/RQ2; strategy applicability by project; explicit limitations from Podman's 23/28 share; no broad generalisation claims.
- **C∅ ablation (D37):** development only; catalogue C with predicates ignored (round-robin over all entries, same seed rotation); RESTCountries + the 20-operation Dolibarr sample × 2 seeds; measures validity, executability, applicability, adherence and meaningful-assertion rates; does not trigger catalogue changes.

**Claim discipline:**
- Inference covers the sampled defects and projects only; no per-type significance tests.
- **A5 (D55, descriptive):** R-T4 non-trivial share of executable tests per arm (development and held-out runs), with class counts (MEANINGFUL / STATUS_ONLY / NONE), from `model-test-report`'s breakdown.
- If fewer than 20 evaluable holdout defects remain, report descriptively.
- No post-hoc subgroup hunting.

## 11. Reproducibility record (per run)

Benchmark commit (`265db11`), patch indices, Docker image IDs/digests, JDK and tool versions, LLM provider/model/version and date, prompts, decoding settings, seeds, spec hash, feature-record hash, catalogue versions, budget counters, code commit.

*Amendment 23 Nov 2026 (D27):* each run also records the Ollama version, model name and digest, quantisation, `num_ctx`, decoding options, seed, `prompt_eval_count`/`eval_count` per call, GPU and driver (RTX 3050 6 GB Laptop GPU, driver 610.60, CUDA 13.3 inside WSL2, Stage 0 on 22 Nov 2026), and the speed log.

**Environment baseline (recorded 20 Nov 2026):**

| Item | Version |
|---|---|
| Host | Windows + WSL2, kernel 6.6.87.2-microsoft-standard-WSL2 |
| Linux | Ubuntu 24.04 |
| Docker Compose | v2.38.2-desktop.1 |
| JDK | OpenJDK 21.0.13 |
| Maven | 3.8.7 |
| Python | 3.12.3 |
| Go | 1.22.2 |
| Git | 2.43.0 |
| RESTCountries build/run images | see RESEARCH_LOG E27 |
| Dolibarr images | base `php:7.4-apache@sha256:c9d7e608f73832673479770d66aacc8100011ec751d1905ff63fae3fe2e0ca6d` (locally built as `dolibarr_local:from_source` with the Debian 11 workaround, E30; local image IDs on 22 Nov 2026: `ef167d0be7cf…` for `61ee3a9`, `a771bfffe4a4…` for `9d4007e`), `mariadb:10.5@sha256:a530aeeefd82f4fa5150f391b6c75462140904780338766f6b03acecb1cca3ce` |
| Java libraries (`system/pom.xml`, 22 Nov 2026) | swagger-parser 2.1.48 (pulls swagger-core 2.2.53), slf4j 2.0.17, JUnit 5.10.2 (tests); build plugins: compiler 3.13.0, surefire 3.2.5, shade 3.6.0 |
| PostgreSQL | `postgres:16@sha256:3950ad7c3148ca2175ef1b6c36c9efbf78957ae8fbe8b3a5d7c35af50500382f` (PostgreSQL 16.16), test container only |
| Dolibarr module configuration | 28 modules (`modules_r14.txt`, D22); enabled-module fingerprint `f4eda97f4d8218cf` on `9d4007e` and `61ee3a9` (E36) |
| Dolibarr project-level spec (pre-sanitisation) | `/api/index.php/explorer/swagger.json` at `9d4007e` under D22: 256 paths / 392 operations; operation-list fingerprint `da2bd2592c1d09ab` (E36); stored outside the repository on the owner's machine |
| Podman (X5, holdout) | Isolated WSL distribution `Ubuntu-24.04`; Go 1.22.2 (Ubuntu package); Podman built from `f65f3320e112` by `defects4rest checkout`, reports 4.0.0-dev, API on `127.0.0.1:8082` (E37) |

**Recorded deviations from the benchmark recipe** (to be reported in the paper's reproducibility section):
- Dolibarr: `bullseye-security` removed from the image's apt sources, because Debian 11 LTS ended on 31 Aug 2026 (E30). Policy: D18 (minimal generic repository fix only).
- Dolibarr: versions switched by `git checkout` and `docker compose` in one prepared clone instead of the CLI's per-deploy re-clone (first used in E29). Policy and equivalence checks: D19.

## 12. Threats to validity (summary; details in RESEARCH_LOG)

- **Construct:**
  - labels ≠ detection mechanisms (R1, E13, E25);
  - flawed ground truth for some defects (R22);
  - semantic oracles depend on the model's world knowledge (R2).
- **Internal:**
  - contamination of the researchers (R18);
  - spec leakage (R23);
  - training-data contamination (R3);
  - manual setup variability (R13);
  - B/C boundary (R15).
- **External:**
  - few projects;
  - T2-dominated sample;
  - RESTCountries has a single detection mechanism.
- **Statistical:** small *n* (R12); no per-type claims.
- *Added 27 Nov 2026 (D55, R37):* the generator missed the pre-declared assertion-quality bar (T4) and is used by an approved deviation; weak success-path assertions lower detection power and may affect arms differently (B and C prescribe checks), assessed with the per-arm R-T4 share (A5); differential execution and blind confirmation (D4.1) reduce, not eliminate, false-detection risk; R-T4 written after the first failure (D49); D45 threshold assistant-set; G2 bars are practical floors.
- *Added 23 Nov 2026:* two held-out projects, Podman dominant (23 of 28); a free 4B model (R30); C's distinct families unevenly applicable per project (R31); RQ1 bundles selection, strategy text and examples; no refinement loop, so tests are never adapted to buggy behaviour (RESTestBench lesson, E39).

## 13. Freeze checklist (all before the first holdout deployment)

- [ ] Catalogues B and C, the overlap table, predicates, feature extractor, DSL, prompts, budgets (*s*, *k*, tokens, caps) frozen and git-tagged
- [ ] Owner check: no catalogue entry, predicate or prompt mentions holdout resources or endpoints
- [ ] Catalogue divergence computed → three-arm or two-arm design fixed
- [ ] Power/precision estimate from dev variance recorded
- [x] X5 and X8 outcomes recorded; holdout project list fixed (22 Nov 2026: X8 → kafka-rest excluded, D20; X5 → Podman passes, E37; 6 projects, 49 defects)
- [x] D16.1 applied and recorded (if Dolibarr was infeasible) — not needed: Dolibarr feasible (D17 met, E34)
- [ ] Generic fixtures per holdout project written from public docs
- [ ] Reference-version checks and hook rule applied per holdout project
- [ ] Reviewers named; confirmation sheet and arm-anonymisation script ready
- [ ] Focused novelty check completed (R19) — *focused check and seven full-text reviews done 23 Nov 2026 (E38, E39); pre-submission recheck still due*
- [ ] *Added 23 Nov 2026:* model digest, quantisation, `num_ctx` (or compaction rule), decoding settings, seed list, *s*, *k*, operation cap and the binding 40-hour check recorded
- [ ] *Added 23 Nov 2026:* overlap table committed before divergence; Div_C computed; catalogue token parity measured
- [ ] *Added 23 Nov 2026:* Podman fixture image and reset script; second-role fixtures (or C7 marked inactive per project)
- [ ] *Added 23 Nov 2026:* execution projection recorded (D32)

## 14. Post-freeze change log

Any change after the freeze is recorded here (date, change, reason) and forces all arms to be rerun.

| Date | Change | Reason | Rerun done |
|---|---|---|---|
| — | — | — | — |
