# Changelog

All notable changes to project documentation, decisions, design and code. Newest first. Dates are the date of the change. Add an entry in the same change as every meaningful modification (see [CLAUDE.md](../CLAUDE.md)).

## [Unreleased]

### 2026-11-28 — E80: D45 second measurement fails; escalation (D60 proposed)

**Added**
- `analysis/stats/coder_prompt_D45.txt` (the fixed coder prompt, verbatim).
- RESEARCH_LOG E80; DECISIONS D60 (PROPOSED); EXPERIMENT_PROTOCOL note; PROJECT_STATUS (STOP).

### 2026-11-28 — E79: second-round execution, G3 PASS for B and C

**Added**
- RESEARCH_LOG E79; PROJECT_STATUS.

### 2026-11-28 — E78: D59 second-round generation

**Added**
- RESEARCH_LOG E78; PROJECT_STATUS.

### 2026-11-28 — E77: second-round generation on the wrong spec (not used)

**Added**
- RESEARCH_LOG E77; DECISIONS D59, EXPERIMENT_PROTOCOL and PROJECT_STATUS point the second measurement to `dev3-dolibarr` with the R14 spec; CATALOGUES.md note on the version label.

### 2026-11-28 — D59: D45 revision round 1

**Changed**
- `Selector.task`: each slot also shows the entry's adherence rule (`Required requests:`, `Required assertion: … A status code alone does not satisfy this.`), identical for B and C.
- Catalogues B8 and C7: auth written as the step field (`"auth": "none"`, `"auth": "secondary"`) in probe and example; catalogues T8-2 (B `6de856de…`, C `b473cc39…`).
- `SelectorTest`: checks the adherence lines; parity and leakage checks cover all prompt-visible entry text (C/B 1.080).

**Added**
- DECISIONS D59; RESEARCH_LOG E75 (diagnosis, spot check) and E76; EXPERIMENT_PROTOCOL note; `docs/frozen/CATALOGUES.md` (T8-2, slot presentation, parity); ARCHITECTURE component 4; PROJECT_STATUS.

### 2026-11-28 — E74: D45 adherence gate fails

**Added**
- RESEARCH_LOG E74; PROJECT_STATUS.

### 2026-11-28 — D57 adopted

**Changed**
- DECISIONS D57 → PROVISIONAL (unchanged bars); PROJECT_STATUS.

### 2026-11-28 — E73: G3 passes; A5 per arm

**Added**
- RESEARCH_LOG E73; PROJECT_STATUS.

### 2026-11-28 — D58: two AI coders for adherence

**Added**
- `adherence.py score --two-coders` (both-agree rule, κ over all tests) with a test; DECISIONS D58; PROJECT_STATUS.

### 2026-11-27 — E72: Day-5 generation; per-arm G3 report

**Added**
- `analysis/stats/g3.py` (per-arm T1–T3 and timing) with a test; RESEARCH_LOG E72; PROJECT_STATUS.

### 2026-11-27 — D57 proposed (G3 bars) before the Day-5 runs

**Added**
- DECISIONS D57 (PROPOSED); PROJECT_STATUS (T12 approval and conditions).

### 2026-11-27 — T11: analysis scripts

**Added**
- `analysis/stats/` (`build_y.py`, `detection.py`, `assertion_quality.py`, `adherence.py`, `test_stats.py`); RESEARCH_LOG E71; EXPERIMENT_PROTOCOL §10 implementation note; README.

### 2026-11-27 — T10: verdict engine

**Added**
- `restdd.verdict` (`Verdict`, `VerdictEngine`); `verdicts` CLI; `run-suites --only` (whole-suite and isolated single-test re-runs, resumable); `VerdictEngineTest` (3), `VerdictsFlowTest` (2); RESEARCH_LOG E70; EXPERIMENT_PROTOCOL §9 implementation note; ARCHITECTURE component 8; README.

### 2026-11-27 — D56 approved with TILE

**Changed**
- `generate` default start rule TILE; DECISIONS D56 → PROVISIONAL (pre-freeze amendment, MOD figures kept); EXPERIMENT_PROTOCOL §3 amendment; RESEARCH_LOG E69 note; CATALOGUES.md allocation section; PROJECT_STATUS.

### 2026-11-27 — E69: development divergence (Div_C 34.8%)

**Added**
- RESEARCH_LOG E69; PROJECT_STATUS.

### 2026-11-27 — E68: first development selector report; C10 fix; both start rules reported

**Fixed**
- C10 multipart reading (body offered also as JSON is not multipart).

**Added**
- `Selector.Start` (MOD, TILE); `select-report` prints both with never-assigned counts; `generate --start`; tests; RESEARCH_LOG E68; DECISIONS D56(5)–(6).

### 2026-11-27 — T8: strategy catalogues, predicates and the shared selector

**Added**
- `restdd.select` (features, predicates, catalogue loader, selector, slot template); catalogues B (15), C (10), overlap table; `select-report`; `generate --arms A,B,C` with `selection.jsonl` and `--ignore-predicates` (development only); `SelectorTest` (3); `docs/frozen/CATALOGUES.md`; DECISIONS D56 (PROPOSED); RESEARCH_LOG E67.

**Changed**
- `GenerationRun.TaskProvider` receives the seed and returns the slot strategies (logged per call); ARCHITECTURE component 4; EXPERIMENT_PROTOCOL §2 note; README; PROJECT_STATUS.

### 2026-11-27 — D55: model selection closed (Qwen3 4B, E65 configuration)

**Added**
- DECISIONS D55 (deviation documented; owner-approved with three wording corrections); RESEARCH_LOG E65 manual check, E66 (Gemma formal G2), R37; EXPERIMENT_PROTOCOL §4 note, §10 A5 (per-arm R-T4 share), §12 threats.

**Changed**
- D53 → SUPERSEDED (code stays opt-in); PROJECT_STATUS current stage.

### 2026-11-26 — D54: second Qwen G2 (code and docs committed 27 Nov after the owner's review)

**Added**
- Grounding rule in `prompts/system.txt`; `model-test-report` breakdown (class counts, by method, success vs 4xx) and grounding count; DECISIONS D54; RESEARCH_LOG E64.

**Changed**
- D53 schema opt-in (`-Drestdd.d53=true`), default = the E60 schema; EXPERIMENT_PROTOCOL §6 note; PROJECT_STATUS.

### 2026-11-26 — E63: Gemma fails T2; model choice escalated

**Added**
- RESEARCH_LOG E63; PROJECT_STATUS.

### 2026-11-26 — E62: D53 first test; body-path pattern added

**Changed**
- `SuiteSchema.BODY_PATH_PATTERN` on body paths; `StaticValidatorTest.bodyPathPatternMatchesExactlyTheParsedSubset`; DECISIONS D53 amended; RESEARCH_LOG E62; PROJECT_STATUS.

### 2026-11-26 — E61: Gemma G1 and diagnostic; D53 proposed

**Added**
- `SuiteSchema.assertionSchema()` (D53: one `anyOf` alternative per assertion type); `StaticValidatorTest.answerSchemaRequiresPerTypeFields`; RESEARCH_LOG E61; DECISIONS D53 (PROPOSED).

**Changed**
- EXPERIMENT_PROTOCOL §4 output-format row; PROJECT_STATUS.

### 2026-11-26 — G2 classifier check; Gemma path

**Added**
- `restdd.llm.ModelDefaults` (decoding per model family); `generate` prints the decoding settings, accepts `--presence-penalty none`; `g1` uses the family's settings; 1 new test.

**Changed**
- RESEARCH_LOG E60 (classifier check 20/20); DECISIONS §5 D27 outcome; EXPERIMENT_PROTOCOL §6 note; PROJECT_STATUS.

### 2026-11-26 — E60: final G2 for Qwen (T4 54.9%, fail pending classifier check)

**Added**
- RESEARCH_LOG E60; PROJECT_STATUS.

### 2026-11-25 — E59: final G2 part 1 (RESTCountries); reset script works from any folder

**Fixed**
- `dolibarr_reset.sh` changes to the Dolibarr folder itself; database wait uses `mysql`. RESEARCH_LOG E59; PROJECT_STATUS.

### 2026-11-25 — E58: both development projects explored; fixture repair mode

**Changed**
- `dolibarr_fixtures.py`: product looked up by ref before creation; `--fill-missing`. RESEARCH_LOG E58; PROJECT_STATUS.

### 2026-11-25 — E57: Dolibarr re-exploration; two shape/identifier fixes

**Changed**
- `Shapes.isMapLike`: a single data-like key is a map; `Explorer.fromAnyList`: own-section lists first; fixture script tolerates PHP notices; 1 new test; RESEARCH_LOG E57; EXPERIMENT_PROTOCOL §6; PROJECT_STATUS.

### 2026-11-25 — T7 fixes after the first exploration (E56); generic Dolibarr fixture

**Added**
- `scripts/dolibarr/dolibarr_fixtures.py` (generic fixture; updates the target file's fixtures and secondary role); `Explorer.fromAnyList`/`matchingField`; 2 new tests in `ExploreTest`; RESEARCH_LOG E56.

**Changed**
- `Shapes`: map rule limited to data-like keys; non-null fields rendered first. Identifier key order (code/ref before name). `dolibarr_reset.sh` waits for the database. EXPERIMENT_PROTOCOL §6 and §9; README; PROJECT_STATUS.

### 2026-11-25 — T7 exploration; D52 adopted (E55)

**Added**
- `restdd.explore` (`Shapes`, `Explorer`, `FeatureRecord`), CLI `explore`, `ExploreTest` (5 tests); RESEARCH_LOG E55.

**Changed**
- `generate --shapes` takes a feature record and renders per-operation shapes; manifest records `featureRecordSha256`; presence penalty 1.5 by default (D52).
- DECISIONS D52 → PROVISIONAL (verified E55); EXPERIMENT_PROTOCOL §4 table and §6 implementation; ARCHITECTURE; README; PROJECT_STATUS.

### 2026-11-25 — E54; D52 proposed; optional presence penalty

**Added**
- `GenerationOptions.presencePenalty` (sent only when set; `generate --presence-penalty`); `OllamaClientTest.presencePenaltyIsSentOnlyWhenSet`; RESEARCH_LOG E54; DECISIONS D52 (PROPOSED).

### 2026-11-25 — E53 (real D49 diagnostic)

**Added**
- RESEARCH_LOG E53.

### 2026-11-24 — E52; D51 length caps in the answer schema

**Added**
- RESEARCH_LOG E52; DECISIONS D51; `StaticValidatorTest.schemaCapsTextValues`.

**Changed**
- `SuiteSchema`: `maxLength` on text values (D51); EXPERIMENT_PROTOCOL §4 configuration table; PROJECT_STATUS.

### 2026-11-24 — E51; D50 proposed; report re-scores with the current rubric

**Added**
- `StaticValidator.classify` (R-T4 class from a test's assertions); RESEARCH_LOG E51; DECISIONS D50 (PROVISIONAL: owner delegated, adopted as proposed); EXPERIMENT_PROTOCOL §4 T4 measurement.

**Changed**
- `model-test-report` re-scores every executable test with the current rubric and reports how many archived classes differ; the T4 sample shows both classes.

### 2026-11-24 — E50 (unintended old-configuration re-run); test fixed

**Fixed**
- `GenerationTest`: the fake suite uses a named-part assertion (`$[0].text`), non-trivial under R-T4; the outdated expectation had failed the owner's build.

**Added**
- RESEARCH_LOG E50.

### 2026-11-24 — D49: G2 remediation (rubric, shared-prompt rule, aligned classifier)

**Added**
- DECISIONS D49; EXPERIMENT_PROTOCOL rubric R-T4 and G2 sequence; `StaticValidatorTest.t4RubricMatchesTheManualRule`.

**Changed**
- `StaticValidator.isTrivial` = rubric R-T4 (whole-body presence/type checks and header presence are trivial).
- Shared system prompt: one rule requiring a specific body or header check per test (identical for all arms).
- RESEARCH_LOG E49 (approval note; rubric re-applied, still 10/20), PROJECT_STATUS, ARCHITECTURE.

### 2026-11-24 — G2 part 2 (E48) and T4 manual check (E49)

**Added**
- RESEARCH_LOG E48 (execution, T3, automatic T4, GPU peak) and E49 (manual T4 = 50%, G2 fails as configured; fix proposed).

### 2026-11-24 — G2 part 1 (E47); operation lists frozen

**Added**
- `docs/frozen/OPERATION_LISTS.md`, `dolibarr_cap20.txt`, `dolibarr_cap60.txt` (D47 lists and SHA-256, re-verified).
- RESEARCH_LOG E47 (generation part of the model test).

### 2026-11-24 — T6 generation and model-test tooling (branch `t6-gen`); D47 approved, D48 proposed

**Added**
- `restdd.gen`: `OperationSelector`, `GenerationRun`, `GpuMonitor`; CLI `generate`, `run-suites`, `model-test-report`; `OllamaClient.isCached`; `GenerationTest` (4 tests).
- DECISIONS D47 (PROVISIONAL, owner) and D48 (PROVISIONAL, owner: thinking mode fixed and recorded per call).
- Thinking-mode record and check per call (`ChatResult.thinkingDetected`, manifest `thinkingMode`); final generation configuration table in EXPERIMENT_PROTOCOL §4.

**Changed**
- EXPERIMENT_PROTOCOL §4 (D47, D48, model-test procedure and metric definitions), ARCHITECTURE (T6), README (generation commands), PROJECT_STATUS.

### 2026-11-24 — Podman reset verified (E46); `t5-podman` merged

**Added**
- RESEARCH_LOG E46.

**Fixed**
- Podman reset creates missing default `policy.json`/`registries.conf` at snapshot time; `wsl.exe --cd /`; versioned base URL in the Podman target.

### 2026-11-24 — Podman reset scripts (branch `t5-podman`)

**Added**
- `scripts/reset/podman_reset.sh` + `podman_inner.sh`; `scripts/targets/podman.example.json`; `scripts/targets/smoke_podman.json`.

**Changed**
- ARCHITECTURE (Podman reset), README (Podman service and reset), PROJECT_STATUS.

### 2026-11-24 — nocodb deployed and verified (E45); `t5-nocodb` merged

**Added**
- RESEARCH_LOG E45.

**Fixed**
- nocodb scripts: prefixed variables (WSL sets `NAME`); `CommandReset` health check uses HTTP/1.1; snapshot file owned by the user; restore verifies the database file before start.

### 2026-11-24 — nocodb reference deployment scripts (branch `t5-nocodb`)

**Added**
- `scripts/nocodb/nocodb_start.sh`, `nocodb_setup.py`, `nocodb_env.sh`; `scripts/reset/nocodb_reset.sh`; `scripts/targets/smoke_nocodb.json` (validated against the official spec).

**Changed**
- README (nocodb deployment, official-spec download with checksum), ARCHITECTURE (nocodb reset), PROJECT_STATUS.

### 2026-11-24 — D46 approved (review roles without outside human reviewers)

**Changed**
- DECISIONS D46: PROPOSED → PROVISIONAL (owner, 24 Nov 2026); a classmate for the 30% check remains an open option.

**Added**
- DECISIONS D46 (PROPOSED): fresh blind Claude session as reviewer 1, the owner as blind reviewer 2 (all candidates if ≤ 60, else ≥ 30%), the assistant as post-freeze validator; mapping stays on the owner's machine; reporting wording fixed.

### 2026-11-23 (night, 5) — T5 smoke tests passed; `t5-exec` merged

**Added**
- RESEARCH_LOG E44 (T5 smoke tests: Dolibarr 2/2 PASS with the reset verified identical; RESTCountries as expected; no key in saved records).

**Changed**
- `dolibarr_reset.sh`: snapshot files private to the user (umask 077, folders 700); `.gitignore` ignores snapshot folders.
- PROJECT_STATUS: T5 core done; branch `t5-exec` merged into `main`.

### 2026-11-23 (night, 4) — D45 approved; snapshot rule restated

**Changed**
- DECISIONS D45: PROPOSED → PROVISIONAL with the owner's four clarifications; EXPERIMENT_PROTOCOL: experimental snapshots only from fresh deployments; PROJECT_STATUS: Dolibarr smoke 2/2 PASS.
- `dolibarr_reset.sh`: empties the documents volume instead of removing it; a failed reset reports the script's last output.

### 2026-11-23 (night, 3) — T5 test runner (branch `t5-exec`)

**Added**
- `restdd.exec`: `TestExecutor`, `AssertionEvaluator`, `Refs`, `Redactor`, `SuiteRunner`, `ResetAdapter` (+ `NoReset`), `CommandReset`, `TargetConfig`, `ExecConfig`, `AuthConfig`, `Outcome`; CLI `exec`.
- `scripts/reset/dolibarr_reset.sh` (snapshot / restore / check); `scripts/targets/` (example targets, RESTCountries and Dolibarr smoke suites).
- `ExecutorTest` (14 tests against a fake API).

**Changed**
- `StaticValidator`: rejects invalid regular expressions in `matches` assertions; `resolveOperation`, `bodySchema` and a new `schemaIssues` are public for the runner.
- ARCHITECTURE (T5 components and assertion semantics), EXPERIMENT_PROTOCOL §9 implementation note, README (`exec` usage), PROJECT_STATUS.

### 2026-11-23 (night, 2) — D44 approved, D45 proposed, archiving of rejected responses

**Added**
- DECISIONS D45 (PROPOSED): strategy-adherence measurement (unit, rubric, sample, score, coders) and the procedure when schema validity passes but adherence fails.
- `OllamaClientTest`: a rejected (over-long) response is archived and a resumed run reaches the same verdict without a new request.

**Changed**
- DECISIONS D44: PROPOSED → PROVISIONAL (owner, 23 Nov 2026) with archiving, monitoring, resumability and no-early-claims conditions; scope limited to Qwen3 4B (G1 repeated if the fallback model is selected).
- `OllamaClient.chat`: archives every response before the context guard and re-applies the guard to cached entries.
- EXPERIMENT_PROTOCOL, PROJECT_STATUS updated accordingly.

### 2026-11-23 (night) — G1 result, model run configuration, merge of `t4-g1`

**Added**
- RESEARCH_LOG E43 (G1: VRAM fit, largest held-out prompt 8,973 tokens, speeds, 17.8 h projection, non-reproducible outputs, laptop reset); risks R35 (machine stability) and R36 (generation not bit-reproducible).
- DECISIONS D44 (PROPOSED): `num_ctx` 16384, flash attention + q8_0 KV cache, balanced power mode, PCIe link-state power management off.
- README: Ollama KV-cache drop-in, machine settings and GPU temperature log for long runs.

**Changed**
- `OllamaClient.preflight`: the NOT_ON_GPU message now separates a CPU fallback (restart Ollama) from a model that does not fit at the chosen `num_ctx` (lower it or use the q8_0 KV cache).
- EXPERIMENT_PROTOCOL: amendment note with the G1 configuration and the reproducibility statement; ARCHITECTURE: server-side KV-cache setting is part of the recorded configuration, not of the cache key.
- Branch `t4-g1` merged into `main`.

### 2026-11-23 (late) — T4 G1 command and prompt skeleton (branch `t4-g1`)

**Added**
- `restdd.prompt.PromptBuilder`, `resources/prompts/system.txt` (shared system prompt with format, rules and a fictional-API example); `restdd.cli.G1` (`g1` command); `PromptBuilderTest`.
- `SpecNormaliser.Compaction`: `dropSchemaDescriptions`, `responseSchemaDepth`; CLI `--compact` parts `drop-schema-descriptions`, `resp-depth=N`.
- RESEARCH_LOG E42 (build result, spec sizes, compaction trials).

**Changed**
- Branch `t1-t3` merged into `main` after 26/26 tests passed on the owner's machine.

### 2026-11-23 (evening) — T0 local LLM, T1–T3 code (branch `t1-t3`)

**Added**
- `system/`: `restdd.spec` (SpecNormaliser, SpecSanitiser), `restdd.dsl` (SuiteSchema, StaticValidator, JsonPathLite), `restdd.llm` (OllamaClient, GenerationOptions, ChatResult, LlmException), `restdd.cli.Main` (`normalise`, `sanitise`, `llm-check`, `probe`); tests for each, plus a recursive Swagger 2.0 fixture with `defaultValue`.
- README: Ollama setup (incl. the WSL GPU-discovery restart), CLI usage.
- RESEARCH_LOG E40 (Ollama install, GPU discovery, first speeds), E41 (project-level specs; Podman via D5.1 preference 2; workload 4,365 calls); contamination-ledger row.
- `.gitignore`: `llm-check/`, `runs/`.

**Changed**
- Jar renamed `target/probe.jar` → `target/restdd.jar`; the probe is now the `probe` subcommand.
- ARCHITECTURE: implemented components; open questions 8 (`defaultValue`) and 9 (`$ref`) answered by the normaliser's choices.

### 2026-11-23 — Ten-day design, literature integration and final quality control (docs only)

**Added**
- `docs/proposals/2026-11-23_ten_day_design_and_qc.md`: ten-day design, owner's four QC checks, implementation tasks T0–T17.
- DECISIONS D23–D43: PROVISIONAL except D30 (PROPOSED, owner names the people); D32 approved as option (b); D41 approved; D42 Podman-dominance reporting; D43 mandatory model-feasibility checks (supersedes D39).
- RESEARCH_LOG: E38 (focused novelty check, seven full-text reviews), E39 (predicate counts, executability, prompt sizes, deployment mechanisms); risks R30–R34; novelty status update; two contamination-ledger rows (holdout deployment keywords and spec aggregates only).
- EXPERIMENT_PROTOCOL: amendment notes in the header, §2, §4, §7–§13.
- ARCHITECTURE: ten-day amendment (Ollama client, catalogue file schema, C∅ flag, reset adapters, DSL additions).
- PROJECT_OVERVIEW: contribution wording after the literature integration.
- PROJECT_STATUS: ten-day schedule, owner decisions, next task.

**Changed**
- D15 FINAL → SUPERSEDED by D23; D7.1, D11, D12, D3.1 amended or clarified (no status change).
- RESEARCH_LOG §6b: RESTestBench first author corrected to Kogler; DOIs, venues and full/partial-text labels added; FDRRestTest and RESTestBench verified.

**Removed**
- Nothing; earlier text kept and annotated.

### 2026-11-22 — X5 Podman passed; Phase 1 complete

Added
- `RESEARCH_LOG.md`: E37 (X5 run, checks, isolation note), R12 and R21 updates, findings-log row, contamination-ledger rows (holdout deployment metadata only).

Changed
- `EXPERIMENT_PROTOCOL.md`: §7 holdout list fixed (6 projects, 49 defects; 47 primary); §11 Podman environment; §13 X5/X8 and D16.1 items ticked. `ARCHITECTURE.md`: Podman deployment rule (own WSL distribution, Docker Desktop stopped). `PROJECT_STATUS.md`: Phase 1 complete; next = owner checkpoint, then Phase 2 (Arm A).

### 2026-11-22 — R14 Dolibarr module configuration (D22)

Added
- `scripts/dolibarr/list_api_modules.php` (Dolibarr's own API-discovery rule), `modules_r14.txt` (the 28 modules at `9d4007e`, frozen), `activate_modules.php` (`activateModule()` with Dolibarr fully loaded, failures recorded).
- `DECISIONS.md`: D22 (owner approved R14 option A; status history row). `RESEARCH_LOG.md`: E36 (rule, installer crash on `modBom` and the fix, real-container results, coverage against the 24 benchmark specs), R14 resolved, findings-log and contamination-ledger rows (development project only).

Changed
- `scripts/dolibarr/dolibarr_setup.sh`: default module set is now the R14 list; installer enables `modApi` only, then one activation process per module; records failures, the enabled-module list and its fingerprint; optional `SPEC_OUT`.
- `PROJECT_STATUS.md`: R14 done; stale "Current stage" text fixed; next task Podman X5 then a Phase 1 checkpoint; the owner's four Phase 2 completion criteria recorded. `EXPERIMENT_PROTOCOL.md` §5 and §11, `ARCHITECTURE.md` question 4, `README.md`: aligned with D22.

### 2026-11-22 — Probe run against RESTCountries; path-template matching fixed

Fixed
- `system/`: the probe's "in spec" check compared concrete paths with path templates as plain strings (`/v2/capital/tallinn` was reported as not in the spec). `OperationLister.matchesTemplate` now matches segment by segment; the probe prints the matching template. Unit test added (8 tests).

Changed
- `RESEARCH_LOG.md`: E35 extended with the restcountries#235 buggy run (10 operations, 0 parser messages, `/v2/capital/tallinn` 200, unknown capital 404; all 16 RESTCountries specs byte-identical); findings-log row. `PROJECT_STATUS.md`: checklist, milestone note, next task (Dolibarr all-modules spec, R14). `ARCHITECTURE.md`: `OperationLister` row.

### 2026-11-22 — Minimal Java 21 probe (Phase 1 milestone)

Added
- `system/`: Maven project (plain Java 21, swagger-parser 2.1.48, package `restdd.probe`): `SpecLoader`, `OperationLister`, `HttpProbe`, `ProbeMain`; runnable `target/probe.jar`. Loads a spec, lists operations, sends one GET, prints status and body; API key from an environment variable, sent as a header, never printed.
- Unit tests with three hand-written fixtures (Swagger 2.0, OpenAPI 3.0.3, OpenAPI 3.1.0) giving identical operation lists, plus 3.1 feature checks (7 tests).
- `.gitignore` (build output, IDE files, credentials). Credentials rule narrowed from `*credentials*` to `.dolibarr_credentials` and `.env` after review.
- `RESEARCH_LOG.md`: E35 (Dolibarr run, OpenAPI 3.1 result, `defaultValue` loss, credentials `export` issue), R29, findings-log row.

Changed
- `scripts/dolibarr/dolibarr_setup.sh`: writes `export DOLIBARR_API_KEY=…` / `export DOLIBARR_ADMIN_PASSWORD=…` so child processes see the key after `source` (E35).
- `ARCHITECTURE.md`: status note, "Implemented so far" section, component 1, open questions 1 and 7 (partly answered), new questions 8 (`defaultValue`) and 9 (`$ref` resolution in the normaliser is open; `resolveFully` is probe-only). `EXPERIMENT_PROTOCOL.md` §11: Java library and plugin versions. `PROJECT_STATUS.md`: checklist, done/not done, next task (run the probe against RESTCountries). `README.md`: probe build and usage; credentials note.

### 2026-11-22 — Dolibarr setup script validated on the real containers (D17 met)

Changed
- `scripts/dolibarr/dolibarr_setup.sh`: hides only the harmless `version is obsolete` warning from `docker compose`.
- `RESEARCH_LOG.md`: E34 owner-machine results (43 s / 46 s; #23179 reproduced both ways); R13 resolved. `DECISIONS.md` §5: D17 condition met (no status change). `PROJECT_STATUS.md`: checklist, blockers and next task (minimal Java program). `EXPERIMENT_PROTOCOL.md` §11: local Dolibarr image IDs. `ARCHITECTURE.md`: open question 3 solved.

### 2026-11-22 — Dolibarr unattended setup script (Phase 1, D17)

Added
- `scripts/dolibarr/dolibarr_setup.sh`: unattended Dolibarr first-time setup through Dolibarr's forced-install file, installer pages and login API; never prints secrets (credentials written to `~/.dolibarr_credentials`, mode 600).
- `RESEARCH_LOG.md`: E34 (design, local test on three versions, bugs fixed, pending real-container run); R13 update; findings-log row.
- `README.md`: usage of the script. `CLAUDE.md`: `scripts/` added to the file list.

Changed
- `PROJECT_STATUS.md`: next task = validate the script on the owner's machine. `ARCHITECTURE.md`: open question 3 updated.

### 2026-11-21 — Delegated decisions D13.1, D18–D21; branches merged

Added
- `DECISIONS.md`: D13.1 (reference-version check: "no patch contained in the reference" replaces "every patch is a descendant"; disclosed as amended after seeing holdout metadata), D18 (EOL base images: minimal generic repository fix), D19 (prepared clones with equivalence checks), D20 (kafka-rest excluded: X8 and missing reference image), D21 (out-of-spec defects kept; sensitivity S1). All PROVISIONAL, decided by the drafting assistant under the owner's explicit delegation.

Changed
- `DECISIONS.md`: D13 → SUPERSEDED by D13.1; summary, supersession table and status history updated.
- `EXPERIMENT_PROTOCOL.md`: §6 check wording and holdout result; §7 holdout list (26 + Podman 23) and estimate update; §10 S1 scope; §11 deviations now cite D18/D19.
- `RESEARCH_LOG.md`: E32 (kafka-rest reference image confirmed missing), R12, R26, R27, R28 updated.
- `PROJECT_STATUS.md`, `ARCHITECTURE.md`, `README.md`: aligned; no decisions awaiting the owner.

Notes
- `docs/phase1-restcountries-2026-11-20` and `docs/phase1-dolibarr-2026-11-21` merged into `main` at the owner's delegation.

### 2026-11-21 — Phase 1: holdout reference-version and X8 checks; versions recorded

Added
- `analysis/benchmark_audit/holdout_refver.py`: metadata-only reference-version check (validated against E22 on RESTCountries and Dolibarr); listed in the audit README as safe for holdout projects.
- `RESEARCH_LOG.md`: E32 (holdout reference versions, ancestry, hooks), E33 (X8 official specs), R28 (reference-version representativeness), R12 update, findings-log row, contamination-ledger row (holdout metadata only). E29: Dolibarr image digests.
- `EXPERIMENT_PROTOCOL.md`: §6 holdout check results; §7 X8 result; §11 Dolibarr and PostgreSQL digests.
- `ARCHITECTURE.md`: open question 7 (OpenAPI 3.1 parsing).

Changed
- `PROJECT_STATUS.md`: PostgreSQL, X8 and reference-version checklist items done; three new owner decisions listed (reference-version rule for mastodon/netbox/nocodb, kafka-rest X8 exclusion, defects whose API is outside the project spec).

Notes
- No status changed in `DECISIONS.md` for these checks; every resulting exclusion or rule change awaits the owner.

### 2026-11-21 — Phase 1: dolibarr#23179 reproduced; Dolibarr conditional go (D17)

Added
- `DECISIONS.md`: D17 (PROVISIONAL, owner 21 Nov 2026): Dolibarr conditional go; setup scripting time-boxed to two working days (first agreed as one, changed by the owner the same day), else D16.1; note on the changed fallback from the earlier plan. Status history row.
- `RESEARCH_LOG.md`: E29 (dolibarr#23179 buggy/patched reproduction, setup timings and settings, 75-path module-dependent spec); E30 (Debian 11 EOL breaks the Dolibarr image build, with the workaround; 1.7 GB re-clone per deploy; error-handler `TypeError`); E31 (WSL DNS fix); updates to R13 and R14; new R26 (recipe decay) and R27 (deploy cost); findings-log row; contamination-ledger row (development projects only).
- `EXPERIMENT_PROTOCOL.md` §11: Dolibarr images (digests TBD) and recorded deviations from the benchmark recipe.
- `README.md`: Dolibarr and WSL DNS known quirks; manual install settings.

Changed
- `PROJECT_STATUS.md`: Dolibarr reproduction and D17 recorded; checklist updated; decisions awaiting the owner listed; next task = script the Dolibarr setup within the time box.
- `ARCHITECTURE.md`: open design questions 3–4 updated; new item 6 (deployment handling for the replayer).

Fixed
- `PROJECT_STATUS.md`: stale blocker "WSL2/Docker integration not yet verified" marked resolved; Phase 1 roadmap status "Next" → "In progress".
- `README.md`: stale "not yet reproduced locally" and "Phase 1 is next" removed.

Notes
- No application code; no experimental results. Stacked on the unmerged branch `docs/phase1-restcountries-2026-11-20`.

### 2026-11-20 — Phase 1: environment ready, first defect reproduced, D1 final

Added
- `RESEARCH_LOG.md`: E27 (restcountries#235 reproduced: buggy "Kiev", patched "Kyiv"; image digests); E28 (CLI quirks: `--stop`/`--clean` need a version flag; root-owned build files break redeploys); R25; E8 limitation marked resolved (§1a); findings-log row.
- `EXPERIMENT_PROTOCOL.md` §11: environment baseline.
- `README.md`: verified environment, benchmark install steps and known quirks.

Changed
- `DECISIONS.md`: D1 and D1.1 PROVISIONAL → FINAL (trigger met: one defect reproduced locally; owner approval 20 Nov 2026). Status history updated.
- `PROJECT_STATUS.md`: environment and reproduction recorded; checklist ticked; next task = Dolibarr go/no-go.

Notes
- No application code; no experimental results.

### 2026-11-16 — Owner decisions D10 and D16.1

Changed
- `DECISIONS.md`: D10 PROPOSED → FINAL (owner approval). D16 → SUPERSEDED by D16.1 (owner's fallback order: RESTCountries + Dolibarr → add SeaweedFS → RESTCountries alone; no holdout project moved without documented justification). Status history updated.
- `PROJECT_STATUS.md`, `EXPERIMENT_PROTOCOL.md`, `RESEARCH_LOG.md` (R24), `CLAUDE.md`: aligned with D10 and D16.1.

Notes
- Committed on a documentation branch; not merged into `main` pending owner approval.

### 2026-11-16 — Methodology checkpoint: documentation update

Added
- `docs/proposals/`: `2026-11-16_D2-D7_review.md`, `2026-11-16_final_methodology.md`, `2026-11-16_methodology_revision_2.md` (dated, immutable records of the reasoning).
- `analysis/benchmark_audit/`: researcher-side audit scripts (`inventory.py`, `labels.py`, `specs.py`, `trigcov.py`, `rcdiff.py`, `holdout_meta.py`, `spec_provenance.py`) with a README.
- `DECISIONS.md`: D1.1, D2.1–D7.1, D11–D14 (PROVISIONAL, owner's provisional approval); D15 (FINAL, owner directive); D16 (PROPOSED at the time; superseded by D16.1); SUPERSEDED status; supersession table; status history.
- `RESEARCH_LOG.md`: corrections to E3, E4, E8, E10 (original rows kept); evidence E11–E26; risks R12–R24; verified literature (§6b); targeted-search update to the novelty status; researcher contamination ledger (§9).
- `CLAUDE.md`: benchmark ground-truth firewall; freeze rule; proposals and analysis conventions; extended status vocabulary.

Changed
- `EXPERIMENT_PROTOCOL.md`: rewritten to the approved methodology. Adds the three arms with the two-arm fallback, budgets (TBD where undecided), spec-source and leakage rules, exploration, the development/holdout split, the detection/attribution protocol, operation coverage as a metric (Handover §8.3), the statistical plan, sensitivity analyses, a freeze checklist and a post-freeze change log.
- `ARCHITECTURE.md`: pipeline extended (input loader/manifest, spec normaliser, explorer, planner with arm switch, static validator, replayer, verdict engine); system vs researcher side; no feedback loop in v1.
- `PROJECT_STATUS.md`: stage updated; Phase 1 checklist revised with feasibility checks (Dolibarr automation, X5 Podman, X8 official specs); roadmap mapped to the methodology; stale push/branch notes corrected.
- `README.md`, `PROJECT_OVERVIEW.md`: approach and contribution wording aligned with the three-arm design and sub-questions.

Superseded
- D2–D7 (original wording kept) → D2.1–D7.1. D7's NetBox/NocoDB fallbacks withdrawn (now holdout candidates; see D16).
- The project-level spec **union** proposed in the final methodology §6.5 → withdrawn in revision 2 (E17).

Notes
- No application code; no experimental results. Methodology not frozen.

### 2026-11-16 — Self-review, WSL2 update and first push to `main`

Changed
- Fixed an awkward cross-reference in `EXPERIMENT_PROTOCOL.md`.
- `PROJECT_STATUS.md`: WSL2 now shows Ubuntu installed (both distributions WSL version 2, stopped); remaining Docker/WSL-integration check listed; status wording updated now that the docs are pushed.
- Review note: the decision log said E8 "confirms" the differential oracle is feasible; `RESEARCH_LOG.md` E8 says "suggests", because only a static data check has been done. All other carried-over content is unchanged in substance.

Notes
- Documentation set pushed to `main`. The owner's manual review is still pending; D10 and D2–D7 remain PROPOSED.
- Test branch `claude/push-access-test` intentionally left on GitHub at the owner's request.

### 2026-11-16 — Owner environment and push access recorded

Changed
- `PROJECT_STATUS.md`: recorded Windows + IntelliJ IDEA and the WSL2 status (default distribution `docker-desktop`); added a WSL2 verification item; updated the push-access blocker.

Notes
- Push from the Claude workspace was verified with a throwaway branch (`claude/push-access-test`). Deleting it was refused (HTTP 403), so the owner must remove it on GitHub. No commit was made to `main`.

### 2026-11-16 — Documentation system established

Added
- `README.md` (replaced one-line placeholder), `CLAUDE.md`, and `docs/` with `PROJECT_OVERVIEW.md`, `PROJECT_STATUS.md`, `DECISIONS.md`, `RESEARCH_LOG.md`, `ARCHITECTURE.md`, `EXPERIMENT_PROTOCOL.md`, `CHANGELOG.md`.
- Decision D10: GitHub repository is the single source of truth for documentation (PROPOSED).

Carried over (no content dropped)
- Handover §3 decisions → `DECISIONS.md` D0.1–D0.8.
- Decision log D1–D9 → `DECISIONS.md`, statuses unchanged.
- Evidence table E1–E10, taxonomy mapping, risks and Phase 1 checklist → `RESEARCH_LOG.md` / `PROJECT_STATUS.md`.
- Literature notes, benchmark plan, architecture, stack, roadmap, historical context → `RESEARCH_LOG.md`, `EXPERIMENT_PROTOCOL.md`, `ARCHITECTURE.md`, `PROJECT_STATUS.md`, `PROJECT_OVERVIEW.md`.

Notes
- No implementation code and no experimental results exist.
- Handover literature items other than Defects4REST are marked "verification pending".

### 2026-11-16 — Phase 1 research verification (recorded retroactively)

- Verified Defects4REST counts, taxonomy, CLI and prerequisites from primary sources (E1–E5); recorded tool baseline and failure causes (E6, E7); static check of restcountries#235 (E8); identified leakage hazards and spec-format findings (E9, E10); proposed decisions D1–D9. Source: claude.ai Project document `Decision_Log_and_Evidence_Table.md`.

### 2026-11-10 — Handover document prepared (recorded retroactively)

- `REST_API_Project_Handover_v2_Complete.docx` prepared: direction, title, research question, candidate taxonomy, architecture, stack, literature notes, evaluation plan, roadmap, risks.

### 2026-11-16 — Repository

- GitHub repository contained only an initial commit with a one-line `README.md` before this change.
