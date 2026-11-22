# Experiment Protocol

> **Status: METHODOLOGY PROVISIONALLY APPROVED (16 Nov 2026) — NOT FROZEN.** No experiment has been run and there are no results.
>
> - **Freeze point:** the protocol is frozen at the end of the development phase, before any holdout deployment (Handover §19; D11).
> - **Undecided values:** items marked **TBD** are deliberately undecided — do not fill them with guesses.
> - **Decision status:** items tagged D# carry the status shown in [DECISIONS.md](DECISIONS.md).
> - **Full reasoning:** `docs/proposals/2026-11-16_final_methodology.md`, as amended by `docs/proposals/2026-11-16_methodology_revision_2.md` (the latter takes precedence).

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

**Development effort parity.** A, B and C each get the same number of logged revision rounds on the development set.

**External tools.** Schemathesis, RESTler, EvoMaster and AutoRestTest are not run in the initial plan (scope). Their published RQ3 results for 6 development defects (E20) are context only, not comparable baselines.

## 3. Selection (D3.1, D6.1)

- **Features:** deterministic, from the project-level spec and exploration shapes (final methodology §5.1).
- **Triggering:** each catalogue entry has a boolean trigger predicate over those features.
- **Allocation:** per operation, triggered strategies in catalogue order fill *s* slots round-robin, starting at index (seed mod length); default strategy if nothing triggers.
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
- **Expected evaluable holdout:**

  | Scenario | Expected evaluable defects |
  |---|---|
  | Podman infeasible, X8 removes nothing | ≈ 22–27 |
  | Worst case (Podman infeasible, X8 removes nocodb, flowable-engine, kafka-rest) | ≈ 16 |
  | Podman feasible | ≈ 40–48 |

  *Update 21 Nov 2026 (estimates, before X6/X7 attrition):* X8 removed only kafka-rest; 24 primary-analysis defects without Podman, about 47 with Podman. The scenario rows above are kept as the original planning estimates.

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

## 9. Outcomes, detection and attribution (D4.1)

| Class | Definition | Counts toward detection? |
|---|---|---|
| PASS | all assertions hold in all *r* runs | — |
| FAIL | an assertion fails in all *r* runs with valid exchanges | candidate input |
| FLAKY | verdicts differ across runs | never |
| ERROR-INFRA | deploy or health failure, connection error, timeout, gateway error; redeploy ≤ 2×, then *not evaluable* | never |
| ERROR-TEST | malformed or unexecutable test, or its own setup fails on both versions | never |

**Candidate:** FAIL on buggy and PASS on patched, with no ERROR-INFRA, and reproduced in isolation.

**Attribution to the defect's own fix-commit delta:**
- **RESTCountries — mechanical:** the failing assertion must read a (country, field) changed by *d*'s own fix commit. Example: a "UKR capital = Kyiv" test failing on `aadbd07` and passing on #199's patch is **not** a #199 detection (E15).
- **Dolibarr — reviewer-judged** against the fix commit and README. `#24661` is flagged (E19).

**Blinded confirmation (arm labels hidden):**
- **Verdicts:** Confirmed / Related / Unrelated / Spurious.
- **Evidence recorded:** defect, test hash, both exchanges, failing assertion, rationale.
- **Who and when:** reviewer, date.
- **Reviewers:** two independent reviewers preferred; otherwise ≥ 30% double-coded with κ reported.

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

**Claim discipline:**
- Inference covers the sampled defects and projects only; no per-type significance tests.
- If fewer than 20 evaluable holdout defects remain, report descriptively.
- No post-hoc subgroup hunting.

## 11. Reproducibility record (per run)

Benchmark commit (`265db11`), patch indices, Docker image IDs/digests, JDK and tool versions, LLM provider/model/version and date, prompts, decoding settings, seeds, spec hash, feature-record hash, catalogue versions, budget counters, code commit.

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

## 13. Freeze checklist (all before the first holdout deployment)

- [ ] Catalogues B and C, the overlap table, predicates, feature extractor, DSL, prompts, budgets (*s*, *k*, tokens, caps) frozen and git-tagged
- [ ] Owner check: no catalogue entry, predicate or prompt mentions holdout resources or endpoints
- [ ] Catalogue divergence computed → three-arm or two-arm design fixed
- [ ] Power/precision estimate from dev variance recorded
- [ ] X5 and X8 outcomes recorded; holdout project list fixed
- [ ] D16.1 applied and recorded (if Dolibarr was infeasible)
- [ ] Generic fixtures per holdout project written from public docs
- [ ] Reference-version checks and hook rule applied per holdout project
- [ ] Reviewers named; confirmation sheet and arm-anonymisation script ready
- [ ] Focused novelty check completed (R19)

## 14. Post-freeze change log

Any change after the freeze is recorded here (date, change, reason) and forces all arms to be rerun.

| Date | Change | Reason | Rerun done |
|---|---|---|---|
| — | — | — | — |
