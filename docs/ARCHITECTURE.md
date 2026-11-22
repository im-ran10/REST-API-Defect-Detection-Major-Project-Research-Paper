# Architecture

> **Status: PROVISIONAL DESIGN (methodology provisionally approved 16 Nov 2026). The pipeline below is not implemented.** The only system-side code is the Phase 1 probe in `system/` (see "Implemented so far"). Update this document whenever the design changes (see [CLAUDE.md](../CLAUDE.md)).
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
| 1 | Spec normaliser | Parse Swagger 2.0 and OpenAPI 3.x (D8: swagger-parser v3); neutral name; fix servers; keyword scan; hash; count operations/attributes before and after parsing (R29) | Not started (parsing and operation listing exist in the Phase 1 probe) |
| 2 | Explorer | GET-only HTTP wrapper; ≤ 2 requests per GET operation; deterministic ID choice; double run; shape inference; values never exported | Not started |
| 3 | Feature extractor | Operation/resource features (methods, params, filter-like params, CRUD sets, action sub-resources, auth), plus shape features | Not started |
| 4 | Planner / selector | Arm A slot allocation; B/C trigger predicates, catalogue order, seed-rotated round-robin, reason log; catalogue files versioned | Not started |
| 5 | LLM generator | Provider-independent client; fixed system prompt; arm-specific task section; token accounting | Not started |
| 6 | Static validator | Schema validation of the DSL; rejects, never repairs | Not started |
| 7 | Replayer | Drives `defects4rest checkout` sequentially; health check; generic fixture; snapshot/restore (Dolibarr DB dump); fixed test order; isolated candidate re-runs | Not started |
| 8 | Verdict engine | Outcome classes; differential candidates; flaky detection | Not started |
| 9 | Results store | PostgreSQL schema for runs, tests, exchanges, verdicts, budgets | Not started |
| R | Researcher tools | Attribution (RESTCountries fix-delta script), confirmation sheet with arm anonymisation, statistics (Python) | Partly: audit scripts exist in `analysis/benchmark_audit/` |

## Implemented so far (Phase 1 probe, 22 Nov 2026)

`system/`: a Maven project (plain Java 21, no Spring Boot), package `restdd.probe`, built to `system/target/probe.jar`. It is a feasibility probe, **not** the D5.1 input loader: it writes no manifest and enforces no allow-list.

| Class | Does |
|---|---|
| `SpecLoader` | Loads Swagger 2.0 / OpenAPI 3.x with swagger-parser 2.1.48 (`OpenAPIParser`, `resolve` + `resolveFully`); reports the declared version and parser messages; flags unresolved path-item `$ref`s (E35) |
| `OperationLister` | (method, path, operationId), sorted by path then method; matches a concrete path against path templates |
| `HttpProbe` | One GET with `java.net.http.HttpClient` (30 s timeouts, no redirects); optional auth header; header values never logged |
| `ProbeMain` | CLI: `--spec --base-url --get [--auth-header --auth-env] [--max-body]`; base URL from the runner, never from the spec's servers/host |

Unit tests use three hand-written fixtures (Swagger 2.0, OpenAPI 3.0.3, OpenAPI 3.1.0) with the same operations. Using `java.net.http` here does **not** settle the HTTP-client question below.

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

1. HTTP client choice (the Phase 1 probe uses `java.net.http`; not a decision); DSL details; how captures and cross-step comparisons are expressed.
2. LLM provider/model; whether the provider supports seeds; token accounting.
3. Automating the Dolibarr installer, modules and API key (R13). Go/no-go outcome: conditional go, with scripting time-boxed to two working days (D17). The replayer needs it. Solved: `scripts/dolibarr/dolibarr_setup.sh` (E34), driven through Dolibarr's own installer and login API; validated on the real containers (~45 s per deployment).
4. Product-generated spec configuration ("all API modules") for Dolibarr (R14). Confirmed module-dependent: API + Products gives 75 paths (E29).
5. Snapshot mechanism per project (DB dump vs volume copy).
6. Deployment handling the replayer must include: root-owned build files (R25), recipe decay such as the Debian 11 workaround (R26), avoiding the per-deploy 1.7 GB Dolibarr re-clone (R27), database host `db` in the Dolibarr install. Policies decided: D18 (base images), D19 (prepared clones with equivalence checks).
7. The spec parser must also read **OpenAPI 3.1** (nocodb's official spec, E33). **Partly answered (E35):** swagger-parser 2.1.48 parses a hand-written 3.1 fixture with no messages and keeps 3.1-only constructs, but with `resolve=true` alone it silently drops path items referenced from `components/pathItems`; `resolveFully=true` fixes that. nocodb's actual spec is not yet parsed (holdout; do it in the spec normaliser with before/after operation counts, R29).
8. Dolibarr's spec uses the non-standard parameter attribute `defaultValue` (100 of 304 parameters), which the parser drops (E35, R29). Whether the normaliser maps it to `default` is a spec-normalisation choice under D5.1, to be decided in Phase 2.
9. **How the Phase 2 spec normaliser resolves `$ref`s is open.** The probe uses `resolveFully=true` only to list operations reliably (E35). It is **not** adopted for the normaliser: full inlining enlarges the spec text that reaches prompts, which matters for the equal token budgets across arms (D12), and it has to cope with circular schemas. Options (e.g. resolving only path-item `$ref`s and keeping schema `$ref`s) are to be decided in Phase 2.
