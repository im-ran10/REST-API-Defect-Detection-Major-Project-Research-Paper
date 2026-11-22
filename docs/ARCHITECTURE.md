# Architecture

> **Status: PROVISIONAL DESIGN (methodology provisionally approved 16 Nov 2026). Nothing described here has been implemented.** No application code exists in this repository. Update this document whenever the design changes (see [CLAUDE.md](../CLAUDE.md)).
>
> **History:** the handover (§5–6) proposed a single pipeline: spec parser → feature extractor → taxonomy selector → LLM generator → executor → reporting. The 16 Nov 2026 checkpoint kept that pipeline and added the stages, switches and safeguards below. The earlier single-arm description is superseded, not discarded: it is still the core path of Arms B/C.

## Design principles

**FINAL — D0.3–D0.6:**
- our own implementation;
- a small end-to-end pipeline first;
- a deterministic, explainable selector;
- no dashboard, multi-agent orchestration, RL, custom model training or vector database until a measured need exists;
- no benchmark ground truth enters the system.

**PROVISIONAL — methodology checkpoint:**
- **No feedback or refinement loop in v1.** Single-shot generation keeps the budget comparable across arms and avoids tests adapting to faulty behaviour (RESTestBench).
- **One codebase, three arms**, differing only in planner configuration and catalogue file (D12).
- **System side vs researcher side:** researcher-side tools may read benchmark ground truth; system-side code must never import them.

## Pipeline (PROVISIONAL)

```
SYSTEM SIDE — allow-listed inputs only; every input hashed into a run manifest
 0 Input loader (allow-list; fails the run on violation) + run manifest writer
 1 Spec normaliser ─► one sanitised project-level spec (D5.1 preference order; never a union)
 2 Explorer ─► GET-only, shape-only observations of the project's reference version (D13)
 3 Feature extractor (deterministic) ─► feature record per operation/resource (hashed, shared by all arms)
 4 Planner ─ Arm A: s slots per operation, no strategies
           └ Arms B/C: shared selector + catalogue_B | catalogue_C ─► (operation, strategy) slots + reasons
 5 LLM generator ─► one call per operation; same model, settings and system prompt; JSON test DSL
 6 Static validator ─► well-formedness only (no repair, no regeneration)
 7 Replayer ─► per defect: buggy then patched; snapshot restore; r repetitions; isolated re-runs
 8 Verdict engine ─► outcome classes (PASS/FAIL/FLAKY/ERROR-INFRA/ERROR-TEST), differential candidates
 9 Results store ─► runs, tests, exchanges, verdicts, budgets, versions (PostgreSQL)

RESEARCHER SIDE — may read ground truth; never imported by system code
 · benchmark validation (X6/X7) · fix-delta attribution · blinded confirmation sheet · statistics
 · analysis/benchmark_audit/ scripts
```

## Components

| # | Component | Responsibility | Status |
|---|---|---|---|
| 0 | Input loader + manifest | Single file-reading path; allow-list; SHA-256 of every input; full prompts and responses; model metadata; code and benchmark commits | Not started |
| 1 | Spec normaliser | Parse Swagger 2.0 and OpenAPI 3.x (D8: swagger-parser v3); neutral name; fix servers; keyword scan; hash | Not started |
| 2 | Explorer | GET-only HTTP wrapper; ≤ 2 requests per GET operation; deterministic ID choice; double run; shape inference; values never exported | Not started |
| 3 | Feature extractor | Operation/resource features (methods, params, filter-like params, CRUD sets, action sub-resources, auth), plus shape features | Not started |
| 4 | Planner / selector | Arm A slot allocation; B/C trigger predicates, catalogue order, seed-rotated round-robin, reason log; catalogue files versioned | Not started |
| 5 | LLM generator | Provider-independent client; fixed system prompt; arm-specific task section; token accounting | Not started |
| 6 | Static validator | Schema validation of the DSL; rejects, never repairs | Not started |
| 7 | Replayer | Drives `defects4rest checkout` sequentially; health check; generic fixture; snapshot/restore (Dolibarr DB dump); fixed test order; isolated candidate re-runs | Not started |
| 8 | Verdict engine | Outcome classes; differential candidates; flaky detection | Not started |
| 9 | Results store | PostgreSQL schema for runs, tests, exchanges, verdicts, budgets | Not started |
| R | Researcher tools | Attribution (RESTCountries fix-delta script), confirmation sheet with arm anonymisation, statistics (Python) | Partly: audit scripts exist in `analysis/benchmark_audit/` |

## Test DSL (to be specified before any arm is run)

JSON test case:
- **steps:** method, path template, parameters, body, symbolic auth role, captures;
- **assertions:** status in a set; JSON-path equals/contains/matches/compare; cross-step comparisons.

The DSL is the same for all arms. Its assertion vocabulary must be fixed **before** seeing arm outputs, so that expressiveness limits apply equally.

## Technology stack (PROVISIONAL, Handover §6)

| Area | Choice |
|---|---|
| Backend/orchestration | Java 21, Spring Boot |
| Spec parsing | swagger-parser v3 (D8) |
| LLM integration | Provider-independent client; provider/model **TBD** |
| HTTP execution | RestAssured or Java HTTP Client — **TBD** |
| Persistence | PostgreSQL (Docker) |
| Benchmark | Defects4REST CLI, pinned commit `265db11` (D1.1) |
| Deployment | Docker; host = Windows + WSL2 (Ubuntu) + Docker Desktop (D9) |
| Analysis | Python (statistics, plots, researcher-side scripts) |
| Frontend | React — optional, postponed (Phase 7) |

## Data model

Not designed yet. Expected entities: Project, SpecVersion (hash), FeatureRecord (hash), Catalogue (version), Arm, Seed, Suite, TestCase (hash), Deployment (defect, version, image digests), Execution, Exchange, Verdict, Candidate, Confirmation, BudgetCounter. To be designed in Phases 2–3.

## Open design questions

1. HTTP client choice; DSL details; how captures and cross-step comparisons are expressed.
2. LLM provider/model; whether the provider supports seeds; token accounting.
3. Automating the Dolibarr installer, modules and API key (R13). Go/no-go outcome: conditional go, with scripting time-boxed to two working days (D17). The replayer needs it. Solved: `scripts/dolibarr/dolibarr_setup.sh` (E34), driven through Dolibarr's own installer and login API; validated on the real containers (~45 s per deployment).
4. Product-generated spec configuration ("all API modules") for Dolibarr (R14). Confirmed module-dependent: API + Products gives 75 paths (E29).
5. Snapshot mechanism per project (DB dump vs volume copy).
6. Deployment handling the replayer must include: root-owned build files (R25), recipe decay such as the Debian 11 workaround (R26), avoiding the per-deploy 1.7 GB Dolibarr re-clone (R27), database host `db` in the Dolibarr install. Policies decided: D18 (base images), D19 (prepared clones with equivalence checks).
7. The spec parser must also read **OpenAPI 3.1** (nocodb's official spec, E33). Whether swagger-parser v3 (D8) handles it adequately is not yet verified.
