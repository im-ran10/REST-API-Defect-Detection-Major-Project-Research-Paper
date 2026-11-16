# Project Status

**Last updated:** 16 Nov 2026 (methodology checkpoint)

## Current stage

**Methodology provisionally approved; Phase 1 (environment setup and feasibility checks) is next.**
- No application code exists.
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

## What has NOT been done

- No development environment installed or confirmed (Docker inside Ubuntu not yet tested).
- Defects4REST not installed; no defect deployed or reproduced (so D1 remains PROVISIONAL).
- No Phase 1 feasibility checks: Dolibarr automation, Podman (X5), official specs (X8).
- No code, DSL, catalogues, prompts or generated tests.
- Systematic novelty check not done (only a targeted search).
- Protocol **not frozen**; budgets (*s*, tokens, caps), LLM model and reviewers TBD.

## Decisions awaiting the owner

None at present. D10 approved (FINAL) and D16.1 approved (PROVISIONAL) on 16 Nov 2026.

## Phase 1 checklist (revised 16 Nov 2026)

Environment
- [x] Verify benchmark counts, taxonomy, CLI and prerequisites from primary sources
- [x] Choose pilot subjects → now the development set (D7.1)
- [x] Confirm dev OS/IDE — Windows + IntelliJ IDEA (D9: Windows + WSL2 + Docker Desktop)
- [ ] Verify WSL2: Ubuntu starts; Docker Desktop running with WSL integration for Ubuntu (`docker run hello-world` inside Ubuntu); decide file locations (WSL filesystem recommended)
- [ ] Install toolchain in Ubuntu: Java 21, Maven, Python 3.9+ venv, Go 1.16+, Git, GitHub CLI; PostgreSQL via Docker
- [ ] Install Defects4REST at commit `265db11` (D1.1); run `defects4rest --help`

Development-set feasibility
- [ ] Deploy restcountries#235 buggy → reproduce "Kiev"; deploy patched → "Kyiv"
- [ ] **Dolibarr go/no-go (R13):** deploy one defect, then script the installer + API key for one version within 2 days; reproduce one defect with curl. If no-go, follow D16.1 (SeaweedFS, else RESTCountries alone)
- [ ] Dolibarr product-generated spec: fetch `/api/index.php/explorer/swagger.json` at the reference version with all API modules enabled (R14)
- [ ] Minimal Java 21 program: load spec, list operations, send one request, capture status + body

Holdout feasibility (**metadata only — do not open holdout ground truth**; see CLAUDE.md)
- [ ] **X5 Podman:** deploy its reference version within one working day, in an isolated environment (R21)
- [ ] **X8 official specs:** for nocodb, flowable-engine and kafka-rest, find a product-generated/official spec of the reference version; record the source or exclude
- [ ] Reference-version date/ancestry checks and hook check (function names only) for each eligible holdout project
- [ ] Record exact versions (benchmark commit, Docker images, JDK)

First concrete milestone (Handover §11, unchanged): a locally running benchmark REST API, a readable OpenAPI spec, and a minimal program that sends a request and captures the response. **Not** a dashboard.

## Roadmap (Handover §10, mapped to the approved methodology)

| Phase | Scope | Status |
|---|---|---|
| 1 | Environment, benchmark setup, feasibility checks (above) | **Next** |
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

1. WSL2/Docker integration not yet verified.
2. If Dolibarr fails its go/no-go, apply D16.1 (try SeaweedFS as an extra development subject, else RESTCountries alone).
3. Feasibility of Dolibarr automation, Podman (X5) and official specs (X8) determines the development and holdout sizes (R12).
4. Budgets and LLM model choice: TBD in Phase 5 calibration, pinned before the holdout (D15).

## Next task

Documentation update committed on a documentation branch, awaiting the owner's merge approval. Then begin Phase 1: verify Docker inside Ubuntu, install the toolchain and Defects4REST, and reproduce restcountries#235.
