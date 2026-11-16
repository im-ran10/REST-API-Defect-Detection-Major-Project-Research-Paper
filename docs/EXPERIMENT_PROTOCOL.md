# Experiment Protocol

> **Status: DRAFT — NOT FROZEN. No experiment has been run and there are no results.** Handover §19 requires a frozen protocol before any comparative run, and forbids tuning the proposed method on hidden test defects. Items marked **TBD** are deliberately undecided; do not fill them with guesses. Items tagged D# come from [DECISIONS.md](DECISIONS.md) and carry that decision's status.

## 1. Question and hypothesis

Research question: see [PROJECT_OVERVIEW.md](PROJECT_OVERVIEW.md). Working hypothesis: taxonomy-guided strategy selection detects more real-world defects than ordinary LLM-generated tests under the same budget. This is a hypothesis, not a finding.

## 2. Arms

| Arm | Description | Status |
|---|---|---|
| **Baseline** | Ordinary LLM-generated API tests from the same spec and public docs, **without** taxonomy-guided strategy selection | Required (Handover §8.2) |
| **Proposed** | Taxonomy-guided strategy selection, then LLM test generation | Required |
| External reference tools | Schemathesis, RESTler, EvoMaster, AutoRestTest or a benchmark-recommended tool, where feasible and reproducible | Optional; choose by subject compatibility. The paper's published results (E6) are reference points only because the protocol differs |

## 3. Budget parity (rules; values TBD)

Control or explicitly report: model/provider/version, generation token or call budget, request budget, execution time, and other constraints. The same model is used in both arms (see risks R2 and R3 in [RESEARCH_LOG.md](RESEARCH_LOG.md)). **Numeric budget values: TBD.**

## 4. Benchmark and subjects

- Primary: **Defects4REST v1.0.0** (D1, PROVISIONAL until one defect is reproduced locally). Pin the exact commit. 607 = curated dataset; 110 = reproducible subset; expect ~108 folders actually present (E3).
- Pilot subjects: **RESTCountries (16) and Dolibarr (25 listed)**; fallbacks NetBox (6), NocoDB (6) (D7, PROPOSED).
- Pilot size: Handover §8.1 suggests roughly 10–15 defects as a planning suggestion, **not a final sample-size decision**. Final defect sample: **TBD**.
- Inclusion/exclusion: defects needing infrastructure or external conditions out of reach of black-box spec-based testing (E7 L4, L7, L8, L9) are to be excluded or reported separately. Exact rule: **TBD**.
- Fallback if real defects prove infeasible: a controlled mutation benchmark such as RESTestBench (check scope and artifacts first; Handover §8.1, §12).

## 5. Inputs and leakage control (D0.6 FINAL; D5 PROPOSED)

Allowed inputs: `<project>#<issue>_spec.json/yaml` and genuinely public API documentation. Forbidden: bug-replication READMEs, defect CSVs, `defects4rest info`, issue URLs/titles, patches, per-defect labels. Log every input file per run. Do not tune the selector or prompts on the defects used for final evaluation; define a separate development set (**TBD**).

## 6. Definition of "detected" (D4, PROPOSED)

A defect is detected only if a generated test:
1. fails on the **buggy** checkout,
2. passes on the **patched** checkout, and
3. a human confirms post hoc that the failure corresponds to the documented defect.

A failing request alone is not a detection (Handover §8.3). Record the confirmation procedure and who confirmed (**TBD**). Differs from the paper's lenient criterion (E6), so cross-study comparison needs caveats.

## 7. Metrics

Primary: real-world defects detected; detection rate/recall over the selected reproducible set.
Supporting: false-positive rate or manually validated failure precision (also measured by running tests on patched versions); endpoint/operation/parameter/behaviour coverage where measurable; generated and executed request counts; generation and execution time; LLM token usage and estimated cost; repeatability across runs/seeds where practical.

## 8. Repetitions and statistics

Number of runs/seeds, tests for significance, effect sizes and handling of per-type small samples: **TBD**. Constraint already known: Auth/Session (4) and Distributed/Cluster (3) are too small for per-type significance claims (E4, R4).

## 9. Reproducibility record (per run)

Benchmark commit, Docker images, JDK, tool versions, LLM provider/model/version and date, prompts, decoding settings, random seeds, input file list, budget counters.

## 10. Threats to validity (known so far)

- Label–mechanism mismatch between taxonomy and detectability (R1).
- Semantic oracles rely on the LLM's world knowledge (R2).
- Possible training-data contamination from public issues (R3).
- Small per-type samples (R4).
- Prevalence-prior circularity (D6).
- Artifact gaps (108 vs 110 folders) and the unexplained Dolibarr spec variants (E3, E10).
- Defects that are not black-box testable (E7).

## 11. Freeze checklist (all must be done before the first comparative run)

- [ ] Taxonomy/strategy set frozen and mapped to observable evidence and benchmark defects (Handover §9-A)
- [ ] D2–D7 reviewed by the owner
- [ ] One defect reproduced locally (D1 → FINAL)
- [ ] Defect sample, development/evaluation split and exclusion rules fixed
- [ ] Budget values fixed and shown equal across arms
- [ ] Oracle and confirmation procedure fixed
- [ ] Statistical plan fixed
- [ ] Focused novelty check completed (Handover §9-D)
- [ ] Protocol committed and tagged before running
