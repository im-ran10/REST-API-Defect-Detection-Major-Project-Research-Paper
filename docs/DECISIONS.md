# Decision Log

Project: *Improving Real-World REST API Defect Detection Through Taxonomy-Guided LLM Test Generation* (working title, provisional)

This is the permanent decision log. It carries forward, without loss, the decision list in `REST_API_Project_Handover_v2_Complete.docx` (§3, 10 Nov 2026) and `Decision_Log_and_Evidence_Table.md` (claude.ai Project document, last updated 16 Nov 2026). It also records the methodology checkpoint of 16 Nov 2026, whose full reasoning is in `docs/proposals/`. Evidence IDs (E#) and risk IDs (R#) refer to [RESEARCH_LOG.md](RESEARCH_LOG.md).

## Status key

| Status | Meaning |
|---|---|
| **FINAL** | Settled. Do not reopen without a clear technical or research reason, recorded here. |
| **PROVISIONAL** | Adopted for now, with the owner's agreement. Revisit at the stated trigger. Work may build on it, but must not make it hard to change. |
| **PROPOSED** | Recommended but **not yet agreed** by the owner. Do not treat as settled; do not build irreversible work on it. |
| **SUPERSEDED** | Replaced by a later decision (named). Kept for history; no longer in force. |

Rules for editing this file are in [CLAUDE.md](../CLAUDE.md). Never delete or silently rewrite a decision: add a new entry, mark the old one SUPERSEDED, and record the change in the status history (§5).

---

## 1. Decisions in force — summary

| ID | Topic | Status |
|---|---|---|
| D0.1–D0.8 | Handover decisions (direction, own implementation, scope guard, leakage rule, no novelty claims, no direction change) | FINAL |
| D1, D1.1 | Defects4REST as primary benchmark, pinned at commit `265db11` | FINAL (owner-approved 20 Nov 2026) |
| D2.1 | Taxonomy = reference vocabulary and derivation source of catalogue C | PROVISIONAL |
| D3.1 | Types vs strategies; catalogues B and C; shared deterministic selector | PROVISIONAL |
| D4.1 | Differential detection protocol | PROVISIONAL |
| D5.1 | Leakage prevention and spec-source rules | PROVISIONAL |
| D6.1 | No prevalence priors initially | PROVISIONAL |
| D7.1 | Development set: RESTCountries + Dolibarr | PROVISIONAL |
| D8 | Swagger 2.0 + OpenAPI 3.x parsing | PROVISIONAL |
| D9 | Host: Linux or Windows + WSL2 + Docker Desktop | FINAL |
| D10 | Repository as documentation source of truth | FINAL (owner-approved 16 Nov 2026) |
| D11 | Project-level held-out evaluation | PROVISIONAL |
| D12 | Three experimental arms with a two-arm fallback | PROVISIONAL |
| D13.1 | Minimal GET-only, shape-only exploration (reference-version check amended) | PROVISIONAL (delegated, 21 Nov 2026) |
| D14 | Exclusion rules X1–X8 | PROVISIONAL |
| D15 | Quality over arbitrary deadlines; explicit resource budgets | FINAL (owner directive) |
| D16.1 | Development-set fallback order if Dolibarr is infeasible (no holdout project moved) | PROVISIONAL |
| D17 | Dolibarr Phase 1 go/no-go: conditional go (2-day time box for setup automation, else D16.1) | PROVISIONAL (owner, 21 Nov 2026) |
| D18 | End-of-life base images: minimal, generic repository fix, recorded as a deviation | PROVISIONAL (delegated, 21 Nov 2026) |
| D19 | Reuse one prepared clone per project instead of the CLI's per-deploy re-clone, with equivalence checks | PROVISIONAL (delegated, 21 Nov 2026) |
| D20 | kafka-rest excluded from the holdout (X8; reference version not deployable) | PROVISIONAL (delegated, 21 Nov 2026) |
| D21 | Defects whose operations lie outside the project-level spec stay in the primary analysis; covered by sensitivity analysis S1 | PROVISIONAL (delegated, 21 Nov 2026) |
| D2–D7 (original) | Phase 1 proposals of 16 Nov 2026 | SUPERSEDED by D2.1–D7.1 |
| D16 (original options) | Fallback options (a)/(b)/(c) | SUPERSEDED by D16.1 |
| D13 (original) | Exploration with strict ancestry check | SUPERSEDED by D13.1 |

---

## 2. Original decisions (kept verbatim)

### D0 — Decisions carried over from the handover (10 Nov 2026) — FINAL

Source: Handover §3 (and §12, §19). Expanded here into individually citable items so they are not lost inside a single row.

| ID | Decision | Status |
|---|---|---|
| D0.1 | Project direction is LLM-assisted REST API testing, narrowed to taxonomy-guided test generation and real-world defect detection. | FINAL |
| D0.2 | Working title and research question are as recorded in [PROJECT_OVERVIEW.md](PROJECT_OVERVIEW.md). The **title stays provisional** until a focused novelty check and a pilot experiment support it. | FINAL (title itself PROVISIONAL) |
| D0.3 | Build our **own implementation**, informed by papers and tools. Do not simply reproduce one base paper. | FINAL |
| D0.4 | Do **not** start with a dashboard, elaborate multi-agent architecture, reinforcement learning, custom model training or a vector database. | FINAL |
| D0.5 | Start with a small end-to-end pipeline: parse OpenAPI → generate requests/tests → execute against a running API → record results. | FINAL |
| D0.6 | Use a **deterministic** taxonomy strategy selector initially. It may use only public API specifications and requirements. It must **not** use hidden defect labels, issue descriptions, patches or benchmark ground truth (leakage rule). | FINAL |
| D0.7 | Do not claim as novel: generic business-logic testing, workflow generation, feedback loops, test-oracle generation, or LLM-based test generation by themselves. | FINAL |
| D0.8 | The project stays on REST API defect detection. Do not switch to the other directions considered earlier (AI for Indian public services, security vulnerability detection/fixing) unless the owner explicitly asks. | FINAL (Handover §16) |

### D1–D9 — Phase 1 decisions (16 Nov 2026)

Rows are unchanged except the Status cell, which now records supersession where it applies.

| # | Date | Decision | Reason | Evidence | Status |
|---|---|---|---|---|---|
| D1 | 16 Nov 2026 | Use **Defects4REST v1.0.0** as the primary real-world defect benchmark. | Only public executable benchmark of real-world REST API defects; single-command buggy/patched checkout; per-bug OpenAPI specs. | E1–E5 | **FINAL** (owner-approved 20 Nov 2026 after restcountries#235 was reproduced locally, E27; was PROVISIONAL until one defect was reproduced); refined by D1.1 |
| D2 | 16 Nov 2026 | **Adopt the Defects4REST taxonomy (6 types / 13 subtypes)** as the empirical taxonomy instead of constructing our own four-category list. | Empirically derived (607 defects, 50 projects), peer-reviewed (ICSE 2026), and the same label space as the benchmark, so results are interpretable. Handover candidates map onto it (see [RESEARCH_LOG.md §3](RESEARCH_LOG.md)). | E1, E6 | **SUPERSEDED by D2.1** (was PROPOSED) |
| D3 | 16 Nov 2026 | **Separate "defect type" from "test strategy".** Taxonomy = what goes wrong (Defects4REST types). Strategies = how a test probes for it (e.g. boundary/invalid input, filter/query probing, multi-step stateful workflow, credential/role variation, semantic/factual response assertion). The selector maps *observable API features* → strategies, prioritised using taxonomy information. | The handover's four candidates mixed both levels: "stateful workflows" and "response/behaviour checks" are strategies, not defect types. Defects4REST RQ3 failure causes (L1 semantic, L2 stateless, L3 auth) are naturally strategies. | E6, E7 | **SUPERSEDED by D3.1** (was PROPOSED) |
| D4 | 16 Nov 2026 | **Detection oracle = differential + manual confirmation.** A defect counts as detected only if a generated test (a) fails on the buggy checkout, (b) passes on the patched checkout, and (c) a human confirms post hoc that the failure corresponds to the documented defect. | Stronger and more objective than Defects4REST's own criterion (manual log inspection, "matched or closely resembled", any of 5 seeds). Buggy/patched checkouts make this possible. Consistent with Handover §8.3 ("a failing request alone is not a defect detection"). | E5, E8 | **SUPERSEDED by D4.1** (was PROPOSED) |
| D5 | 16 Nov 2026 | **Leakage firewall.** Selector and generator may read only `<project>#<issue>_spec.json/yaml` and genuinely public API docs. They must **never** read `bug_replication/**/README.md`, `defects4rest/data/defect_data/*.csv`, `defects4rest info` output, issue URLs/titles, patches, or per-defect taxonomy labels. Enforce in code (path allow-list) and log every input file per run. | Benchmark folders hold issue text and triggering endpoints alongside the spec. Operationalises D0.6. | E9 | **SUPERSEDED by D5.1** (was PROPOSED). Note: allowing per-defect spec files was later found to be a leak (E16, E17). |
| D6 | 16 Nov 2026 | Taxonomy **prevalence priors** (if used for prioritisation) must be computed from the 607-defect set **excluding evaluation projects**, or the selector uses feature rules only with uniform priors. | The published 42% / 27% / 10% … distribution includes the evaluation defects → circularity. | E1 | **SUPERSEDED by D6.1** (was PROPOSED) |
| D7 | 16 Nov 2026 | **Pilot subjects: RESTCountries (16 defects) + Dolibarr (25 listed).** Fallbacks: NetBox (6), NocoDB (6). | RESTCountries: lightest build (Java 8 WAR on Tomcat), one spec for all 16 bugs, OpenAPI 3.0.3, 10 paths. Dolibarr: largest validation set (16 T2), plus auth/storage/config defects, API-key auth, MariaDB. Together: 41 listed defects and 8 of the paper's 30 RQ3 defects (published tool results available as reference baselines). | E3, E4, E7, E10 | **SUPERSEDED by D7.1** (was PROPOSED). Fallbacks NetBox/NocoDB withdrawn because they are now holdout candidates (see D16.1). |
| D8 | 16 Nov 2026 | Spec parser must support **both Swagger 2.0 and OpenAPI 3.x** (use swagger-parser v3, which converts 2.0 → 3.0). | Dolibarr specs are Swagger 2.0; RESTCountries is OpenAPI 3.0.3. | E10 | **PROVISIONAL** |
| D9 | 16 Nov 2026 | Development host must be **native Linux, or Windows + WSL2 + Docker Desktop (WSL integration)**. | Defects4REST prerequisites; the Podman subject requires Linux. | E2 | **FINAL** (benchmark requirement) |

### D10 — Documentation system (16 Nov 2026)

| # | Date | Decision | Reason | Evidence | Status |
|---|---|---|---|---|---|
| D10 | 16 Nov 2026 | The **GitHub repository is the single source of truth** for project documentation. `docs/` holds the status, decisions, research log, architecture, experiment protocol and changelog; `CLAUDE.md` defines mandatory update rules. The claude.ai Project documents (`Decision_Log_and_Evidence_Table.md`, the handover `.docx`) are historical sources and are **not edited further** once this set is accepted. | Sessions do not share memory; cloud workspace and local folder do not sync. One authoritative, versioned location keeps every future session consistent. | Owner request, 16 Nov 2026 | **FINAL** (owner-approved 16 Nov 2026; was PROPOSED) |

---

## 3. Methodology checkpoint decisions (16 Nov 2026)

**Owner's approval (16 Nov 2026).** The owner provisionally approved the revised methodology: three arms with the 20% divergence fallback; the exploration and leakage safeguards; the statistical protocol; Podman and official-spec availability as Phase 1 feasibility checks; all limitations preserved.

The full reasoning is in:
- `docs/proposals/2026-11-16_D2-D7_review.md`
- `docs/proposals/2026-11-16_final_methodology.md`
- `docs/proposals/2026-11-16_methodology_revision_2.md` — this one takes precedence where they differ.

**Revisit trigger for all PROVISIONAL items below:** the methodology freeze (end of development phase, before any holdout deployment), or earlier if a Phase 1 feasibility check fails.

| # | Decision | Reason | Evidence | Status |
|---|---|---|---|---|
| D1.1 | Pin Defects4REST by commit **`265db1165e54b01a0698af0fd28d345169a5449f`** (27 Sep 2026; `pyproject.toml` version 1.0.0). The repository has no git tags. | "v1.0.0" is not a reproducible identifier on its own. | E21, E27 | **FINAL** (owner-approved 20 Nov 2026; was PROVISIONAL until one defect was reproduced locally) |
| D2.1 | The Defects4REST taxonomy is the **reference vocabulary** for stratification and reporting, and the **derivation source of catalogue C**; every C entry cites a subtype or limitation class (L1–L9). Authoritative per-defect labels: `bug_replication/README.md` at the pinned commit (identical to the replication package's `607_defects_types.csv`). Per-defect labels are never a system input. Adopting the taxonomy is not claimed as a contribution. | Labels describe the dominant reported symptom/fix area, not a detection mechanism; per-project READMEs disagree for 22/110 defects. | E11, E12, E13, E25 | PROVISIONAL |
| D3.1 | Defect types and test strategies are separate. There are two versioned catalogues with trigger predicates, oracle templates and provenance:<br>• **B** = established tool checks (Schemathesis, RESTler), the six metamorphic output patterns of Segura et al. (TSE 2018), and RFC 9110 semantics;<br>• **C** = strategies derived from the Defects4REST taxonomy and its documented tool limitations.<br>Membership follows a stated provenance rule. One shared deterministic selector allocates *s* slots per operation, without priors, and logs reasons. Catalogues are frozen before the holdout. | Isolates catalogue provenance as the only B/C difference. | E25; literature §6 | PROVISIONAL |
| D4.1 | A **detection** requires a frozen test that:<br>• is stably FAIL on buggy and PASS on patched (recorded patch index) over r = 3 runs from identical snapshots;<br>• is reproduced in isolation;<br>• is attributable to the defect's **own fix-commit delta**;<br>• is Confirmed by arm-blind review: two independent reviewers preferred, otherwise ≥ 30% double-coded with Cohen's κ.<br>Infrastructure, invalid and flaky outcomes never count. Defects failing pre-run validation are excluded and listed. False positives = wrong-oracle failures on patched versions. | Co-resident defects and large patch deltas exist (e.g. RESTCountries #199's patched version also contains the #235 fix). | E14, E15, E19, E21 | PROVISIONAL |
| D5.1 | System inputs are limited to:<br>(1) one sanitised, neutrally named, hashed **project-level spec** — preference order: product-generated/official spec of the reference version; else a benchmark spec that is identical across the project's defects (or differs only by version, with zero single-defect operations); **never a union, never a per-defect spec**;<br>(2) the shared exploration feature record (D13);<br>(3) runner-supplied base URL and symbolic roles.<br>Enforced by an allow-listed input loader that fails on violation, run manifests (file hashes, full prompts, model metadata) and a per-batch prompt audit. Suites are generated per project, never per defect. | Spec filenames contain issue numbers; per-defect spec variants and unions steer generation toward specific defects. | E9, E16, E17, E23 | PROVISIONAL |
| D6.1 | **No prevalence priors** in the initial system. Feature-triggered strategies use a fixed order and an equal slot split. Prevalence weighting (computed excluding evaluation projects) is a possible later ablation. | Simplest defensible design; priors are over types while strategies cut across types. | E26 | PROVISIONAL |
| D7.1 | **Development set:** RESTCountries (15 usable: #237 excluded; #199 uses patch index 2; #202 flagged as contested) and Dolibarr (18 usable + 4 needing a fixture; #23345, #26307, #29372 excluded), subject to deployment and validation checks. Development results are reported separately and never as main evidence. | Pilot ground truth has been studied; it cannot serve as unbiased evaluation. | E13–E15, E18–E20 | PROVISIONAL |
| D11 | **Project-level held-out evaluation** on projects passing X1–X5 and X8; per-defect rules X6–X7 after the freeze. Described as *pre-registered and design-frozen*, **not blind**, with the contamination ledger published. If fewer than 20 evaluable holdout defects remain, results are reported descriptively. | Prevents tuning on evaluation defects; honest about partial researcher exposure. | E24; RESEARCH_LOG §7 | PROVISIONAL |
| D12 | **Three arms:**<br>• A — plain LLM generation;<br>• B — feature-conditioned, general-principles catalogue;<br>• C — feature-conditioned, taxonomy-derived catalogue.<br>They differ only in planner/catalogue and the prompt task section, under equal information, model, calls, slots and request caps. **Two-arm fallback (A vs C)** if *catalogue divergence* on the development set is < 20%. Divergence = the share of (operation, slot) assignments whose strategy family has no counterpart in the other catalogue, computed with a frozen overlap table and no ground truth. | Without B, a C > A result cannot be attributed to the taxonomy. | Proposal revision 2 §1 | PROVISIONAL |
| D13 | **Minimal exploration** in the initial system: GET-only, shape-only exploration of the **earliest-buggy reference version**, subject to:<br>• per-project date and ancestry checks;<br>• excluding the reference defect from the primary analysis if a setup hook ran for it;<br>• an isolated, discarded deployment with fixed limits;<br>• two runs for reproducibility;<br>• one hashed feature record shared by all arms.<br>Fallback per project: spec plus official-documentation examples. Spec-only is a later ablation. | Development specs lack response structure; earliest-buggy predates all fixes on both development subjects. | E16, E21, E22 | **SUPERSEDED by D13.1** (was PROVISIONAL) |
| D14 | **Exclusion rules** (ground-truth-free unless marked):<br>• X1 no folder or no spec;<br>• X2 needs an external personal account/device (signal-cli-rest-api);<br>• X3 per-defect curated specs or spec text naming a bug (enviroCar-server);<br>• X4 project spec documents < 5 operations (seaweedfs);<br>• X5 reference version not deployable on the owner's host within one working day (**Podman** attempted);<br>• X6 (after freeze) benchmark validation fails;<br>• X7 (after freeze) needs environment changes beyond checkout + generic fixture (sensitivity subset);<br>• X8 benchmark specs contain single-defect operations **and** no product-generated/official reference spec is obtainable (at risk: nocodb, flowable-engine, kafka-rest).<br>The owner accepted X2–X4 provisionally. | Keeps exclusions independent of defect ground truth where possible. | E17, E24 | PROVISIONAL |
| D15 | Scientific quality is not compromised to meet an arbitrary implementation deadline. Realistic resource and execution budgets (time, tokens, deployments, reviewer effort) are set explicitly and recorded before the holdout. | Owner directive, 16 Nov 2026. | — | FINAL (owner directive) |
| D16 | **Development-set fallback if Dolibarr fails its Phase 1 feasibility check.** The original fallbacks (NetBox, NocoDB) are now holdout candidates and cannot be used without shrinking and contaminating the holdout. Options:<br>(a) develop on RESTCountries only (weak: one detection mechanism);<br>(b) move one eligible holdout project to development, chosen by a pre-registered rule before any holdout inspection — suggested rule: the eligible project with the fewest defects that passes X5/X8;<br>(c) use an excluded project (e.g. seaweedfs) for pipeline development only.<br>**Recommendation:** (b). | Consistency check found a conflict with the original D7 fallbacks. | — | **SUPERSEDED by D16.1** (was PROPOSED) |
| D16.1 | **Development-set fallback order (owner decision, 16 Nov 2026):**<br>1. Attempt RESTCountries and Dolibarr as planned.<br>2. If Dolibarr proves infeasible, investigate an already-excluded project such as **SeaweedFS** as an additional development subject.<br>3. If that is also infeasible, use **RESTCountries alone** for initial pipeline development.<br>4. **Do not** move a designated holdout project into the development set unless there is a compelling methodological justification, recorded as a new decision with the resulting contamination documented in RESEARCH_LOG §9.<br>Remaining holdout projects stay independent. | Protects holdout independence. Note: SeaweedFS was excluded from the holdout by X4 (3-operation spec), so it can mainly exercise the pipeline, not strategy diversity. | E24 | **PROVISIONAL** (revisit if Dolibarr fails its Phase 1 go/no-go) |

### D17 — Phase 1 Dolibarr go/no-go (21 Nov 2026)

| # | Date | Decision | Reason | Evidence | Status |
|---|---|---|---|---|---|
| D17 | 21 Nov 2026 | **Conditional go for Dolibarr.**<br>1. Dolibarr stays in the development set (D7.1 unchanged).<br>2. Scripting the per-deployment setup (install wizard, API module, Products module, admin API key) for one version is time-boxed to **two working days** of effort.<br>3. If it is not achieved within the time box, apply D16.1 step 2 (investigate SeaweedFS as an additional development subject).<br>4. The Debian bullseye workaround (E30) is a recorded deviation from the benchmark recipe; how to handle it permanently was decided separately in D18. | dolibarr#23179 reproduces on buggy and not on patched (E29), so Dolibarr is deployable. Manual setup took ~15 min the first time and ~7 min the second, about 5 hours over ~44 deployments, and is error-prone (E29), so R13 stays open. | E29, E30; R13 | **PROVISIONAL** (owner agreed 21 Nov 2026; revisit at the end of the time box) |

Notes:
- The time box was first agreed as one working day and changed by the owner to **two working days** on the same day (21 Nov 2026), matching the earlier plan.
- The earlier plan's fallback was "keep Dolibarr with fewer defects" (`docs/proposals/2026-11-16_final_methodology.md` F1). D17 replaces it with D16.1 step 2. The proposals stay unchanged as historical documents; D17 governs.

### D13.1, D18–D21 — Decisions delegated by the owner (21 Nov 2026)

Decided by the drafting assistant under the owner's explicit delegation (21 Nov 2026); the owner may revisit any of them. Each follows from Phase 1 evidence; none was informed by holdout ground truth (only the metadata in E32/E33).

| # | Date | Decision | Reason | Evidence | Status |
|---|---|---|---|---|---|
| D13.1 | 21 Nov 2026 | D13 unchanged except the **reference-version check**. A project's reference version (earliest-buggy) is acceptable if:<br>(1) every patch commit is dated after it (committer timestamps); and<br>(2) **no patch commit is contained in it** (no patch is a git ancestor of the reference).<br>Whether each patch is a git descendant is **reported, not required**. Otherwise the D13 fallback applies. | The check exists so that exploration never observes a fixed version. (1)+(2) test that directly; "descendant" additionally fails on fixes merged on release branches, which carry no leakage. Applied uniformly; RESTCountries and Dolibarr pass both forms. **Disclosure:** amended after seeing holdout version metadata (E32), before any holdout deployment or ground truth. | E22, E32; R28 | **PROVISIONAL** (delegated) |
| D18 | 21 Nov 2026 | **End-of-life base images.** When a benchmark image fails to build only because its distribution's package repositories have been retired, apply the smallest generic fix that restores the original packages: drop the retired repository (Dolibarr: `bullseye-security`, E30) or, if needed later, point apt to `archive.debian.org`. **Never** change the base image, language runtime or application version. The fix is identical for every defect of a project, is recorded per subject (EXPERIMENT_PROTOCOL §11), and is reported in the paper. Other subjects are checked from build files only when they are first deployed. | Restores the benchmark's intended environment with the least change; upgrading the base image would change the system under test. | E30; R26 | **PROVISIONAL** (delegated) |
| D19 | 21 Nov 2026 | **Prepared clones.** The replayer may keep one clone per project and switch versions with `git checkout <sha>` instead of the CLI's full re-clone, provided that before each deployment: `HEAD` equals the target SHA; `git status` shows no tracked changes; the generated build files are byte-identical to what the CLI writes; and all containers and volumes of the previous version are removed (`down -v`). Recorded as a deviation. | Same source tree and build files as the CLI produces, without ~1.7 GB per deploy (≈ 75 GB over ~44 Dolibarr deployments) or the network failures that come with it. | E29, E30; R27 | **PROVISIONAL** (delegated) |
| D20 | 21 Nov 2026 | **kafka-rest is excluded from the holdout** (3 defects). | Two independent, ground-truth-free reasons: (a) X8 — its benchmark specs contain single-defect operations and no official spec exists for its 2015 reference version (E33); (b) the reference version maps to image `confluentinc/cp-kafka-rest:0`, which does not exist on Docker Hub (owner check, 21 Nov 2026, E32), so the reference cannot be deployed as the benchmark specifies. kafka-rest stays under the ground-truth firewall. | E17, E32, E33; D14 | **PROVISIONAL** (delegated) |
| D21 | 21 Nov 2026 | **Defects whose operations are outside the project-level spec** (e.g. one nocodb defect, one flowable-engine defect, E33) **stay in the primary analysis**. They are analysed by the existing sensitivity analysis S1 (triggering operation in spec vs not), decided by the validator after the freeze. | Excluding them now would select defects using per-defect spec content, which sits close to ground truth. In the paired per-defect comparison a defect no arm can reach adds a zero difference and does not bias the arm contrast; it only lowers absolute detection rates, which are reported as such. | E33; EXPERIMENT_PROTOCOL §10 | **PROVISIONAL** (delegated) |

---

## 4. Superseded or reopened decisions

| Old | New | Date | What changed |
|---|---|---|---|
| D2 | D2.1 | 16 Nov 2026 | Taxonomy used as reference vocabulary and catalogue-C source; never a system input. |
| D3 | D3.1 | 16 Nov 2026 | Two catalogues (B, C) with provenance rules; prioritisation by taxonomy information removed (no priors). |
| D4 | D4.1 | 16 Nov 2026 | Repetitions, snapshots, isolation re-runs, fix-delta attribution, blinded review, outcome classes. |
| D5 | D5.1 | 16 Nov 2026 | Per-defect spec files no longer allowed; project-level spec preference order; no unions. |
| D6 | D6.1 | 16 Nov 2026 | Priors excluded from the initial system. |
| D7 | D7.1 | 16 Nov 2026 | Pilot subjects become the development set with usable-defect lists; NetBox/NocoDB fallbacks withdrawn (see D16.1). |
| D16 | D16.1 | 16 Nov 2026 | Owner chose: RESTCountries + Dolibarr → else add SeaweedFS → else RESTCountries alone; no holdout project moved without documented justification. |
| D13 | D13.1 | 21 Nov 2026 | Reference-version check: "every patch is a descendant" replaced by "no patch is contained in the reference" (descendant status reported only). Delegated decision. |

---

## 5. Status history

| Date | ID | From → To | Basis |
|---|---|---|---|
| 16 Nov 2026 | D2–D7 | PROPOSED → SUPERSEDED | Replaced by D2.1–D7.1 after the methodology checkpoint |
| 16 Nov 2026 | D1.1, D2.1–D7.1, D11–D14 | new → PROVISIONAL | Owner's provisional approval of methodology revision 2 (16 Nov 2026) |
| 16 Nov 2026 | D15 | new → FINAL | Owner directive |
| 16 Nov 2026 | D16 | new → PROPOSED | Raised by the consistency check |
| 16 Nov 2026 | D10 | PROPOSED → FINAL | Owner approval, 16 Nov 2026 |
| 20 Nov 2026 | D1, D1.1 | PROVISIONAL → FINAL | Trigger met: restcountries#235 reproduced locally on buggy ("Kiev") and patched ("Kyiv") versions (E27); owner approval, 20 Nov 2026. Exclusion of individual holdout projects (D14) is unaffected. |
| 16 Nov 2026 | D16 | PROPOSED → SUPERSEDED | Replaced by D16.1 (owner's chosen fallback order) |
| 16 Nov 2026 | D16.1 | new → PROVISIONAL | Owner decision, 16 Nov 2026 |
| 21 Nov 2026 | D17 | new → PROVISIONAL | Owner chose the conditional go for Dolibarr after dolibarr#23179 was reproduced on buggy and patched versions (E29) |
| 21 Nov 2026 | D13 | PROVISIONAL → SUPERSEDED | Replaced by D13.1 |
| 21 Nov 2026 | D13.1, D18, D19, D20, D21 | new → PROVISIONAL | Owner delegated these choices to the drafting assistant (21 Nov 2026) |
| 22 Nov 2026 | D17 | no status change | **Condition met:** Dolibarr setup scripted and validated on the real containers in ~1.5 h of the 2-day time box (E34). Dolibarr stays in the development set; the D16.1 fallback is not triggered. |

## How to add a decision

Append an entry with the next free number (or `Dn.k` for a revision) giving date, decision, reason, evidence and status. If it changes an earlier decision:
1. mark the old entry SUPERSEDED with a pointer to the new one;
2. add a row to §4 and to §5;
3. update [CHANGELOG.md](CHANGELOG.md) in the same change.

Never promote a status without the owner's agreement or a recorded verification.
