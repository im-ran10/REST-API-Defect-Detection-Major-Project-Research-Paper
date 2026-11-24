# Architecture

> **Status: PROVISIONAL DESIGN (methodology provisionally approved 16 Nov 2026). The pipeline below is not implemented.** The only system-side code is the Phase 1 probe in `system/` (see "Implemented so far"). Update this document whenever the design changes (see [CLAUDE.md](../CLAUDE.md)).
>
> **History:** the handover (§5–6) proposed a single pipeline: spec parser → feature extractor → taxonomy selector → LLM generator → executor → reporting. The 16 Nov 2026 checkpoint kept that pipeline and added the stages, switches and safeguards below. The earlier single-arm description is superseded, not discarded: it is still the core path of Arms B/C.

> **Amended 23 Nov 2026 (D23–D43; reasoning in [`proposals/2026-11-23_ten_day_design_and_qc.md`](proposals/2026-11-23_ten_day_design_and_qc.md)).** The pipeline is unchanged; the items below fill in components for the 10-day design. Still not implemented.
>
> - **LLM generator (D27):** local Ollama through its native `/api/chat` endpoint (the OpenAI-compatible endpoint ignores `num_ctx`); JSON-schema `format` = the test DSL schema; `think` off; one fixed `num_ctx` for all calls; seed per call; truncation guard comparing `prompt_eval_count` with the counted prompt tokens (fails the call); **no retries** (D26); resumable queue with a cache keyed by prompt hash, model digest, options and seed; manifest records Ollama version, model digest, quantisation, options, token counts and speed. Provider-independent interface kept, but no paid provider is planned.
> - **Catalogue files (D35, D36):** one entry per strategy with name, applicability, how to probe, oracle template, worked example (fictional API), source; one rule per trigger with ID, observable predicate, strategy, applicability, source, rationale, oracle template, leakage control, validation status. B2 carries the Vieira et al. operators (D34). An overlap table file is committed before divergence is computed (D31).
> - **Selector:** unchanged mechanism, plus a development-only flag that ignores predicates (C∅ ablation, D37); the flag cannot be enabled for held-out runs.
> - **Executor/replayer (D28):** screening run, full re-runs for suites with candidates, isolated re-runs. Reset adapters: RESTCountries none; Dolibarr DB dump; Podman fixture image pre-loaded, cleanup reset keeping it, Docker Desktop stopped (R21); nocodb container/SQLite restore. DSL additions: one multipart part with a small inline text file; fixture variables (image name, second-role credentials).
> - **Spec slices (D41):** one shared slice per operation for all arms; if the largest held-out prompt does not fit the context window chosen at G1, a deterministic slice-compaction rule is applied to every slice before the freeze.
> - **Researcher side:** blinded candidate export; adherence sample sheet (A4); analysis scripts for D29 and A1–A4.
>
> **Implemented 23 Nov 2026 (T1–T3; unit tests to be run with Maven on the owner's machine):**
> - `restdd.spec.SpecNormaliser` — one self-contained JSON slice per operation, identical for all arms. Swagger 2.0 converted to OpenAPI 3.0 by swagger-parser; OpenAPI 3.x used as written. **Normalisation choices (answers open questions 8 and 9):** Dolibarr's non-standard `defaultValue` is copied to `default` before conversion when no `default` exists; `$ref`s are inlined **per slice**, and a reference already open on the current path becomes `{"$recursive": "<name>"}`; `x-*` extensions and `servers` dropped; every response code kept with its description, schemas only for 2xx; descriptions and examples kept unless the D41 compaction rule (description cap, drop examples, schema depth) is switched on. Operations ordered by path, then method (same as `OperationLister`).
> - `restdd.spec.SpecSanitiser` — D5.1 keyword scan; reports hits only, the owner reviews.
> - `restdd.dsl.SuiteSchema` — the test format: exactly *s* tests per call; steps (method, path, pathParams, query, headers, auth `default`/`none`/`secondary`, body, multipart text part, extract), assertions `status`/`body`/`header`/`responseSchema`/`compare`; `${var}` and `${fixture.name}` references; paths `$`, `$.a.b`, `$.a[0]`, `$.a[*].b`; at most 6 steps and 6 assertions per step. The same schema is the Ollama `format`.
> - `restdd.dsl.StaticValidator` — schema validity (T1), structural validity (operation exists, path parameters supplied, references defined, assertions well formed; invalid tests are not executed and count as the arm's outcome), spec conformance (T2; reported, not enforced, so negative tests still run), assertion class for T4/A1 (status-only / meaningful / none; trivially true assertions excluded).
> - `restdd.llm.OllamaClient` — native `/api/chat` with JSON-schema `format`, `truncate=false` and `shift=false` (an over-long prompt is an error, never cut), an extra check that prompt + output stay below `num_ctx`, a GPU preflight via `/api/ps` (100% in VRAM, expected context), disk cache keyed by the full request plus model digest (resume), JSONL call log; `think` is sent only when configured; no content retries.
> - `restdd.cli.Main` — `normalise`, `sanitise`, `llm-check`, `g1`, `probe`.
> - **T5 test runner (`restdd.exec`, 23 Nov 2026; branch `t5-exec`, to be built on the owner's machine):**
>   - `TestExecutor` runs one test step by step over HTTP/1.1: path parameters URL-encoded, `${name}` / `${fixture.name}` substitution (a string that is exactly one reference keeps the value's JSON type), auth role `default` / `none` / `secondary`, JSON or multipart body. Redirects are never followed. The response body is read for at most 30 s and 2 MB, so streaming endpoints are cut, not waited for. The run stops at the first failing step.
>   - Run-level outcomes: PASS; FAIL (an assertion failed on a valid exchange); SETUP_ERROR (an `extract` found nothing); INFRA_ERROR (connection error, timeout, status 502/503/504, failed reset); INVALID (structurally invalid, unknown fixture, no second role). The verdict engine (T10) maps them to the protocol classes: SETUP_ERROR on both versions = ERROR-TEST, on one version = FAIL there.
>   - Assertion semantics: lenient scalar equality (`5` = `5.0` = `"5"`); `[*]` must hold for every element and never vacuously (no match fails every operator except `notExists`); `matches` = regex search; `conformsToSpec` = status documented (exact, `nXX` or `default`) and, if a JSON body schema is declared, the body satisfies it (checked against the **uncompacted** spec). The validator now rejects invalid regular expressions statically.
>   - `${fixture.uid}`: a fresh value per test run for run-scoped unique names (test independence).
>   - Every exchange, assertion result, extracted value and the validation result is written to the run record; auth secrets are replaced by `<redacted>` everywhere (D44 archiving).
>   - `SuiteRunner`: a schema-invalid response runs nothing (D26); restore the snapshot, then the structurally valid tests in order; a reset failure makes the tests INFRA_ERROR; a single index gives an isolated re-run.
>   - Reset adapters: `NoReset` (RESTCountries); `CommandReset` runs project scripts plus a health check. `scripts/reset/dolibarr_reset.sh` takes a snapshot of the database and documents folder, restores it (drop all tables, reload, restore documents) and checks it. `scripts/reset/nocodb_reset.sh` stops the nocodb container, swaps its SQLite data volume back to the snapshot and restarts it (branch `t5-nocodb`, 24 Nov 2026). The nocodb reference deployment and its generic fixture (admin user, one project, one 4-column table, 3 rows) come from `scripts/nocodb/` and use only the official spec. `scripts/reset/podman_reset.sh` (branch `t5-podman`) runs from the main Ubuntu and drives the isolated Podman distribution through `wsl.exe` as root: it removes every pod, container, volume, secret, non-default network and image, reloads the generic fixture image (`alpine:3.19`, saved once at snapshot time) and checks its image ID; at snapshot time it creates Podman's standard default `policy.json` (accept anything) and `registries.conf` (`docker.io`) if the source build left them out; the Podman API service runs in its own tab (`sudo podman system service -t 0 tcp:127.0.0.1:8082`, as the benchmark starts it). Docker Desktop stays quit while Podman containers exist (R21). Podman serves its `/libpod/...` endpoints only under a version prefix, so the runner's base URL is `http://127.0.0.1:8082/v<server version>` (reference: `v4.0.0`, from `GET /version`); the spec's paths are unversioned (`basePath: /`). The base URL is runner configuration (D5.1) and identical for all arms.
>   - Targets: a JSON file per deployment (base URL, auth by environment-variable name, fixtures, reset); examples in `scripts/targets/`. CLI: `exec`.
> - **T6 generation (`restdd.gen`, 24 Nov 2026, branch `t6-gen`):** `OperationSelector` (D47 cap rule, list SHA-256); `GenerationRun` (one call per operation × seed × arm, no retries of a model answer; prompts from the compacted spec, validation against the uncompacted spec; each call → `calls.jsonl` with the exact user prompt, raw response, token counts, timings, GPU reading and full validation result; infrastructure errors retried 3 × after 30 s, then the run stops and resumes from the cache); `GpuMonitor` (`nvidia-smi` per fresh call; pauses at ≥ 85 °C until ≤ 75 °C); run manifest (model name/digest, Ollama version and server environment, GPU and driver, options, spec SHA-256, operation list and SHA-256, system-prompt SHA-256, fixture names, git commit and dirty flag, Java version). CLI: `generate`, `run-suites` (executes every schema-valid suite, resumable, `exec-<label>.jsonl`), `model-test-report` (Gate 0, T1–T4, 20-test T4 sample). `OllamaClient.isCached` lets a resumed run skip the GPU check for cached calls. The automatic T4 class follows rubric R-T4 (D49), and the shared system prompt requires at least one specific body or header check per test (D49). Arms B/C plug in through `TaskProvider` (T8); exploration shapes through `--shapes` (T7).
> - *G1 (E43, D44 PROPOSED):* the KV-cache type and flash attention are Ollama **server** settings (systemd drop-in), invisible to the API; they are fixed for the whole study and recorded per run, and are not part of the client's cache key. The preflight's NOT_ON_GPU message distinguishes a CPU fallback from a model that does not fit at the chosen `num_ctx`.
> - `restdd.prompt.PromptBuilder` + `resources/prompts/system.txt` (T4/T6, draft until the freeze): one system prompt for all arms (format, rules, one worked example on a fictional books API, D35, ~1,000 tokens); user prompt = operation slice + **related operations** (method, path, summary ≤ 80 characters of the other operations sharing the first two static path segments, at most 20, deterministic and defect-independent, so tests can create and read back resources) + exploration shapes + fixture names + the arm's task section (Arm A text from final methodology §4.2).
> - **Compaction rule measured (E42, D41):** description cap 200, drop examples, drop schema-internal descriptions, 2xx response schemas to depth 2; applied to every operation of every project for all arms. To be confirmed at G1.

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

`system/`: a Maven project (plain Java 21, no Spring Boot), built to `system/target/restdd.jar` (*renamed 23 Nov 2026; was `probe.jar`*). Package `restdd.probe` is the Phase 1 probe, now the `probe` subcommand. *Added 23 Nov 2026 (T1–T3, see below).* Original text: It is a feasibility probe, **not** the D5.1 input loader: it writes no manifest and enforces no allow-list.

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
| LLM integration | Provider-independent client; provider/model **TBD** → *23 Nov 2026:* local Ollama, Qwen3 4B Instruct-2507 else Gemma 3 4B (D27) |
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
2. LLM provider/model; whether the provider supports seeds; token accounting. *Answered 23 Nov 2026 (D27): local Ollama; seeds and token counts from `/api/chat`.*
3. Automating the Dolibarr installer, modules and API key (R13). Go/no-go outcome: conditional go, with scripting time-boxed to two working days (D17). The replayer needs it. Solved: `scripts/dolibarr/dolibarr_setup.sh` (E34), driven through Dolibarr's own installer and login API; validated on the real containers (~45 s per deployment).
4. Product-generated spec configuration ("all API modules") for Dolibarr (R14). Confirmed module-dependent: API + Products gives 75 paths (E29). **Resolved (D22, E36):** 28 API-capable modules by Dolibarr's own rule, enabled after installation with `activateModule()` (`scripts/dolibarr/activate_modules.php`); 392 operations at `9d4007e`. The replayer uses the same setup script and module list for every Dolibarr deployment.
5. Snapshot mechanism per project (DB dump vs volume copy).
6. Deployment handling the replayer must include: root-owned build files (R25), recipe decay such as the Debian 11 workaround (R26), avoiding the per-deploy 1.7 GB Dolibarr re-clone (R27), database host `db` in the Dolibarr install. Policies decided: D18 (base images), D19 (prepared clones with equivalence checks). Podman (holdout) runs in its own WSL distribution, with Docker Desktop stopped during its runs (R21, E37).
7. The spec parser must also read **OpenAPI 3.1** (nocodb's official spec, E33). **Partly answered (E35):** swagger-parser 2.1.48 parses a hand-written 3.1 fixture with no messages and keeps 3.1-only constructs, but with `resolve=true` alone it silently drops path items referenced from `components/pathItems`; `resolveFully=true` fixes that. nocodb's actual spec is not yet parsed (holdout; do it in the spec normaliser with before/after operation counts, R29).
8. Dolibarr's spec uses the non-standard parameter attribute `defaultValue` (100 of 304 parameters), which the parser drops (E35, R29). Whether the normaliser maps it to `default` is a spec-normalisation choice under D5.1, to be decided in Phase 2.
9. **How the Phase 2 spec normaliser resolves `$ref`s is open.** The probe uses `resolveFully=true` only to list operations reliably (E35). It is **not** adopted for the normaliser: full inlining enlarges the spec text that reaches prompts, which matters for the equal token budgets across arms (D12), and it has to cope with circular schemas. Options (e.g. resolving only path-item `$ref`s and keeping schema `$ref`s) are to be decided in Phase 2.
