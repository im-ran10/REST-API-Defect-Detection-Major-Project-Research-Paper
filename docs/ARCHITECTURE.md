# Architecture

> **Status: PROVISIONAL DESIGN. Nothing described here has been implemented.** No code exists in this repository. This document records the proposed design from Handover §5–6 and Phase 1 decisions, so it can be checked and changed as implementation teaches us things. Update it whenever the design changes (see [CLAUDE.md](../CLAUDE.md)).

## Design principles (FINAL — D0.4, D0.5, D0.6)

- Our own implementation; a small, working end-to-end pipeline first.
- Deterministic, explainable strategy selector first.
- No dashboard, multi-agent orchestration, RL, custom model training or vector database until a measured need emerges.
- The selector and generator see only what a normal tester would have: the OpenAPI/Swagger spec and genuinely public documentation. No hidden labels, issue text, patches or benchmark metadata (D5, PROPOSED, enforcement mechanism below).

## Pipeline (PROVISIONAL)

```
OpenAPI/Swagger spec (+ optional public docs / requirements)
        │
        ▼
1. Specification parser ──► operations, parameters, schemas, constraints,
        │                    response fields, auth info, documented relationships
        ▼
2. API feature extractor ──► observable features per operation
        │
        ▼
3. Taxonomy-guided strategy selector (deterministic rules)   ◄── EXPERIMENTAL ARM ONLY
        │                    selected + prioritised strategies, with recorded reasons
        ▼
4. LLM test generator ─────► structured test cases, request sequences,
        │                    input values, explicit assertions
        ▼
5. Execution & validation engine ─► run requests, manage state, capture
        │                    responses, evaluate assertions, classify outcomes
        ▼
6. Experiment runner & reporting ─► detections, false positives, coverage, request
                             count, time, LLM usage/cost, reproducibility metadata
```

The baseline arm runs the same stages **without stage 3** (ordinary LLM-generated tests from the same spec and public docs, same model, same budget).

Optional, **postponed** until the core pipeline and protocol are stable: React dashboard / experiment-management UI (Handover §5.8, Phase 7).

## Components

| # | Component | Responsibility | Status |
|---|---|---|---|
| 1 | Specification parser | Parse specs; support **Swagger 2.0 and OpenAPI 3.x** (D8, PROVISIONAL: swagger-parser v3, which converts 2.0 → 3.0) | Not started |
| 2 | Feature extractor | Derive observable features relevant to test strategies | Not started |
| 3 | Strategy selector | Map features → strategies by transparent rules; record why each strategy was chosen | Not started |
| 4 | LLM test generator | Provider-independent client; generate structured tests and assertions | Not started |
| 5 | Execution/validation engine | RestAssured or Java HTTP Client; state handling; assertion evaluation; logs | Not started |
| 6 | Experiment runner/reporting | Run both arms under the budget; store results and metadata | Not started |

## Taxonomy vs strategy (PROPOSED — D2, D3)

- **Defect type** = what goes wrong: Defects4REST taxonomy (6 types / 13 subtypes). Used to prioritise.
- **Test strategy** = how a test probes for it, e.g. boundary/invalid input, filter/query probing, multi-step stateful workflow, credential/role variation, semantic/factual response assertion.
- The selector maps observable API features → strategies. The strategy list is **not frozen**; it must be frozen (with observable evidence, assertions and benchmark support for each) before the experiment, per Handover §9-A.
- If prevalence priors are used, they must exclude evaluation projects (D6, PROPOSED).

## Leakage firewall (PROPOSED — D5)

Planned enforcement, not yet built:
- Path allow-list: generator/selector may read only `<project>#<issue>_spec.json/yaml` and public docs.
- Forbidden: `bug_replication/**/README.md`, `defects4rest/data/defect_data/*.csv`, `defects4rest info` output, issue URLs/titles, patches, per-defect taxonomy labels.
- Every input file for every run is logged.
- Open check: the 5 differing Dolibarr specs must be confirmed not to be bug-specific (E10).

## Technology stack (PROVISIONAL, Handover §6)

| Area | Choice |
|---|---|
| Backend/orchestration | Java 21, Spring Boot |
| Spec parsing | swagger-parser v3 (D8); alternatives open |
| LLM integration | Provider-independent client abstraction; provider/model **not yet chosen** |
| HTTP execution | RestAssured or Java HTTP Client — **not yet chosen** |
| Persistence | PostgreSQL (test cases, runs, outcomes, experiment metadata) |
| Deployment | Docker for benchmark services and dependencies |
| Analysis | Python (statistics, plots, benchmark adapters) |
| Frontend | React — optional, postponed |
| Benchmark host | Native Linux, or Windows + WSL2 + Docker Desktop (D9, FINAL) |

## Data model

Not designed yet. To be added with Phase 2–3, informed by what the runner needs to store (runs, test cases, requests/responses, outcomes, budget counters, versions, seeds).

## Open design questions

1. HTTP client choice; test-case representation; how state/dependencies are modelled.
2. LLM provider/model and how budget is defined (tokens, calls, requests, time) — must be equal across arms.
3. How the detection oracle (D4) is automated vs manual.
4. Whether any feedback/refinement loop is used (allowed as an aid, not a novelty claim).
