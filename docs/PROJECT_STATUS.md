# Project Status

**Last updated:** 22 Nov 2026 (Phase 1: R14 Dolibarr module configuration done, D22; Podman X5 is the last Phase 1 item)

## Current stage

**Phase 1 in progress (environment setup and feasibility checks).** The environment is ready; one defect of each development project is reproduced (restcountries#235, dolibarr#23179); Dolibarr's setup is scripted (D17 met) with the approved module configuration (D22, R14); the Phase 1 Java probe runs against both development APIs. The last Phase 1 item is the Podman feasibility check (X5). Methodology provisionally approved 16 Nov 2026.
- The only code is the Phase 1 probe in `system/` (load spec, list operations, one GET). The pipeline is not implemented.
- No experiment has been run.
- There are **no measured results**.

## What has actually been done

| Item | Evidence |
|---|---|
| Research direction, title and research question selected | [DECISIONS.md D0](DECISIONS.md) |
| Handover document prepared (10 Nov 2026) | `REST_API_Project_Handover_v2_Complete.docx` (claude.ai Project) |
| Benchmark counts, taxonomy, CLI and prerequisites verified from primary sources | [RESEARCH_LOG.md E1–E5](RESEARCH_LOG.md) |
| Published four-tool baseline numbers and failure causes recorded | E6, E7 |
| Static check of restcountries#235 buggy vs patched data | E8 (static only) |
| Documentation system created and pushed to `main` (commit `fafe00d`) | CHANGELOG |
| Owner environment: **Windows**, **IntelliJ IDEA**; WSL2 with `docker-desktop` and `Ubuntu` (both stopped when checked) | Owner, 16 Nov 2026 |
| Push access from the Claude workspace verified (throwaway branch `claude/push-access-test`, kept at the owner's request) | CHANGELOG |
| D2–D7 review: label inconsistencies, ground-truth issues, leakage channels, development set defined | `docs/proposals/2026-11-16_D2-D7_review.md`; E11–E21 |
| Final methodology and revision 2: three arms, exploration, spec rules, holdout, detection protocol, statistics | `docs/proposals/2026-11-16_final_methodology.md`, `…_methodology_revision_2.md`; E22–E26 |
| **Owner provisionally approved the methodology** | DECISIONS D1.1, D2.1–D7.1, D11–D14 (PROVISIONAL) |
| Owner approved D10 (repository = source of truth) and the D16.1 development fallback order | DECISIONS D10, D16.1 |
| Researcher-side audit scripts | `analysis/benchmark_audit/` |
| **Environment ready (19–20 Nov 2026):** Ubuntu 24.04 (WSL2); Docker Desktop with WSL integration, user in `docker` group, Compose v2.38.2 (`docker compose` and `docker-compose`); OpenJDK 21.0.13; Maven 3.8.7; Python 3.12.3; Go 1.22.2; Git 2.43.0; GitHub CLI logged in (HTTPS); project repo cloned at `~/projects/rest-api-defect-detection` | Owner's terminal output |
| Defects4REST installed at `265db11` in `~/tools/Defects4REST` (venv `~/tools/defects4rest-env`) | Owner's terminal output |
| **restcountries#235 reproduced locally:** buggy → "Kiev", patched → "Kyiv"; image digests recorded | RESEARCH_LOG E27 |
| D1, D1.1 → FINAL (owner, 20 Nov 2026) | DECISIONS §5 |
| **dolibarr#23179 reproduced locally (20–21 Nov 2026):** buggy → notes `null` after PUT; patched (`61ee3a9`) → notes saved. Manual setup ~15 min first time, ~7 min second time. Buggy spec with API + Products: Swagger 2.0, 75 paths | RESEARCH_LOG E29 |
| Dolibarr deploy issues recorded: Debian 11 EOL breaks the image build (workaround applied, a recipe deviation); 1.7 GB re-clone per deploy; error-handler bug; host DNS fix | E30, E31 |
| **Dolibarr go/no-go → conditional go** (owner, 21 Nov 2026) | DECISIONS D17 (PROVISIONAL) |
| Dolibarr image digests recorded; PostgreSQL 16.16 runs in Docker (throwaway test container) | E29; EXPERIMENT_PROTOCOL §11 |
| **Holdout reference-version checks** (metadata only): no reference version contains a fix; mastodon, netbox, nocodb fail the strict descendant check; reference defects of kafka-rest, netbox, nocodb have setup hooks | E32 (`holdout_refver.py`) |
| **X8 official specs:** found for nocodb (OpenAPI 3.1) and flowable-engine (OpenAPI 3.0); none for kafka-rest (X8 condition met) | E33 |
| **Dolibarr unattended setup script** (22 Nov 2026): `scripts/dolibarr/dolibarr_setup.sh`. Owner's machine: ~45 s per fresh deployment, #23179 reproduces on buggy and patched. **D17 condition met** (~1.5 h of the 2-day box); D16.1 not triggered | RESEARCH_LOG E34; DECISIONS §5 |
| **Minimal Java 21 probe** (22 Nov 2026): `system/` (Maven, swagger-parser 2.1.48). Dolibarr `9d4007e`: 75 paths / 94 operations; `GET /status` 200 with key, 401 without; key never printed. 7/7 unit tests (2.0, 3.0, 3.1 fixtures). Findings: OpenAPI 3.1 parses cleanly except a silently dropped path-item `$ref` under `resolve=true` (fixed with `resolveFully`); Dolibarr's 100 `defaultValue` attributes are dropped (R29); credentials file lacked `export` (setup script fixed). **RESTCountries #235 buggy:** 10 paths / 10 operations, 0 parser messages; `GET /v2/capital/tallinn` 200 (Estonia), unknown capital 404; all 16 RESTCountries specs byte-identical; probe path-template matching bug fixed (8/8 tests) | RESEARCH_LOG E35 |
| **Delegated decisions** (21 Nov 2026): reference-version check amended (D13.1); EOL base-image policy (D18); prepared-clone reuse (D19); kafka-rest excluded (D20); out-of-spec defects kept (D21). Holdout now 26 defects + Podman 23 | DECISIONS |
| **R14 Dolibarr module configuration** (22 Nov 2026, owner approved option A → D22): 28 API-capable modules by Dolibarr's own rule, frozen in `scripts/dolibarr/modules_r14.txt`. Real containers: 28/28 enabled, 0 failures, identical fingerprint on buggy and patched; #23179 reproduces both ways; spec at `9d4007e` = 256 paths / 392 operations, containing all 387 operations of the benchmark's same-version spec | RESEARCH_LOG E36; DECISIONS D22 |

## What has NOT been done

- Two defects reproduced (restcountries#235, dolibarr#23179), one run each; the rest of the development set is not yet validated.
- Not yet done: Podman feasibility check (X5).
- The Dolibarr spec is not yet sanitised (D5.1 keyword scan and owner review happen in Phase 2, in the input loader).
- The probe is not the D5.1 input loader: no allow-list, no manifest, no spec normalisation.
- No pipeline code, DSL, catalogues, prompts or generated tests.
- Systematic novelty check not done (only a targeted search).
- Protocol **not frozen**; budgets (*s*, tokens, caps), LLM model and reviewers TBD.

## Decisions awaiting the owner

None at present. On 21 Nov 2026 the owner delegated the open choices; they are recorded as D13.1 and D18–D21 (PROVISIONAL) with their reasoning, and the owner may revisit any of them. The two docs branches were merged into `main` the same day.

## Phase 1 checklist (revised 16 Nov 2026)

Environment
- [x] Verify benchmark counts, taxonomy, CLI and prerequisites from primary sources
- [x] Choose pilot subjects → now the development set (D7.1)
- [x] Confirm dev OS/IDE — Windows + IntelliJ IDEA (D9: Windows + WSL2 + Docker Desktop)
- [x] Verify WSL2: Ubuntu starts; Docker Desktop running with WSL integration for Ubuntu (`docker run hello-world` inside Ubuntu); files in the WSL filesystem
- [x] Install toolchain in Ubuntu: Java 21, Maven, Python 3.9+ venv, Go 1.16+, Git, GitHub CLI
- [x] PostgreSQL via Docker: `postgres:16` (16.16) runs; digest recorded (21 Nov 2026). Project database set up in Phase 2/3
- [x] Install Defects4REST at commit `265db11` (D1.1); run `defects4rest --help`

Development-set feasibility
- [x] Deploy restcountries#235 buggy → reproduce "Kiev"; deploy patched → "Kyiv" (20 Nov 2026, E27)
- [x] **Dolibarr go/no-go (R13):** deploy dolibarr#23179 buggy and patched; reproduce with curl (E29). Outcome: conditional go (D17, 21 Nov 2026)
- [x] **Script the Dolibarr setup** (install wizard, API + Products modules, admin API key) within two working days (D17): done 22 Nov 2026, validated on both #23179 versions (E34)
- [x] Dolibarr product-generated spec at the reference version with all API-capable modules enabled (R14 → D22, 22 Nov 2026): 392 operations (E36)
- [x] Minimal Java 21 program: load spec, list operations, send one request, capture status + body — done 22 Nov 2026 against Dolibarr (E35)
- [x] Run the probe against RESTCountries — done 22 Nov 2026 on #235 buggy (E35)

Holdout feasibility (**metadata only — do not open holdout ground truth**; see CLAUDE.md)
- [ ] **X5 Podman:** deploy its reference version within one working day, in an isolated environment (R21)
- [x] **X8 official specs:** nocodb and flowable-engine found; kafka-rest none (E33) → excluded (D20)
- [x] Reference-version date/ancestry checks and hook check (function names only) for each eligible holdout project (E32); all remaining projects pass under D13.1
- [ ] Record exact versions (benchmark commit, Docker images, JDK) — done for the environment and RESTCountries (E27, EXPERIMENT_PROTOCOL §11); Dolibarr and PostgreSQL digests recorded (21 Nov 2026); repeat per subject

First concrete milestone (Handover §11, unchanged): a locally running benchmark REST API, a readable OpenAPI spec, and a minimal program that sends a request and captures the response. **Not** a dashboard. Reached for Dolibarr and RESTCountries on 22 Nov 2026 (E35).

## Roadmap (Handover §10, mapped to the approved methodology)

| Phase | Scope | Status |
|---|---|---|
| 1 | Environment, benchmark setup, feasibility checks (above) | **In progress** |
| 2 | Arm A end to end on RESTCountries: input loader + manifest, spec normaliser, explorer, LLM generator, DSL, static validator | Not started |
| 3 | Replayer and verdict engine: snapshots, repetitions, isolated re-runs, outcome classes; results store | Not started |
| 4 | Feature extractor, selector, catalogues B and C (development set only) | Not started |
| 5 | Development runs; calibration of *s*, *k*, tokens; catalogue divergence test; freeze (EXPERIMENT_PROTOCOL §13) | Not started |
| 6 | Holdout evaluation, blinded confirmation, statistics | Not started |
| 7 | Integrated application (DB-backed experiment management; optional React) | Not started |
| 8 | Research paper | Not started |

Handover §9 pre-implementation work:

| Item | Status |
|---|---|
| A — freeze a feasible taxonomy | Replaced by the catalogue design; frozen in Phase 5 |
| B — validate benchmark environment | Phase 1 |
| C — freeze experiment protocol | Methodology approved; freeze in Phase 5 |
| D — focused novelty check | Open |
| E — pilot | Phase 5 development runs |

## Open questions / blockers

1. ~~WSL2/Docker integration not yet verified.~~ Resolved 19–20 Nov 2026 (environment row above).
2. ~~If the Dolibarr setup cannot be scripted within the D17 time box, apply D16.1.~~ Resolved 22 Nov 2026: scripted (E34).
3. Podman feasibility (X5) determines the final holdout size (R12).
4. Budgets and LLM model choice: TBD in Phase 5 calibration, pinned before the holdout (D15).

## Next task

**Podman feasibility check (X5)**, the last Phase 1 item: deploy Podman's reference version (`f65f3320e112`) within one working day, in an isolated environment (R21), metadata only (holdout firewall). Then a **Phase 1 checkpoint** for the owner before Phase 2 starts.

**Phase 2 (owner instruction, 22 Nov 2026): Arm A end to end first; B and C are not implemented in Phase 2.** Phase 2 is complete only when these are also done and recorded:
1. **Statistics:** reconcile the progress report with EXPERIMENT_PROTOCOL §10 and confirm one primary statistical procedure, with justification (owner approval).
2. **Arms B and C:** check that they stay substantively distinct and experimentally fair (D3.1, D12; divergence rule, overlap table, R15).
3. **Spec normalisation:** decide how `$ref`s are resolved (ARCHITECTURE question 9; prompt size under D12) and how Dolibarr's non-standard `defaultValue` is handled (question 8, R29).
4. **Novelty:** the systematic prior-art check (R19) stays an explicit gate. No publication-level novelty claim anywhere until it is done and recorded in RESEARCH_LOG.
