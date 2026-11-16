# Project Status

**Last updated:** 16 Nov 2026 (owner environment recorded; push access verified)

## Current stage

**Research validation and Phase 1 (environment setup).** No application code exists. No experiment has been run. There are **no measured results**.

## What has actually been done

| Item | Evidence |
|---|---|
| Research direction, title and research question selected | [DECISIONS.md D0](DECISIONS.md) |
| Handover document prepared (10 Nov 2026) | `REST_API_Project_Handover_v2_Complete.docx` (claude.ai Project) |
| Benchmark counts, taxonomy, CLI and prerequisites verified from primary sources | [RESEARCH_LOG.md E1–E5](RESEARCH_LOG.md) |
| Published four-tool baseline numbers and failure causes recorded | E6, E7 |
| Static check of restcountries#235 buggy vs patched data | E8 (static only) |
| Pilot subjects **proposed** (RESTCountries, Dolibarr) | D7 (PROPOSED) |
| Documentation system created and pushed to `main` | Owner asked for review-then-push on 16 Nov 2026; **owner's own review still to come** |
| Owner environment stated: **Windows**, **IntelliJ IDEA** (has VS Code too; more experienced with IntelliJ) | Owner, 16 Nov 2026 |
| WSL2 present: `wsl --status` reports Default Version 2; `wsl -l -v` lists `docker-desktop` (default) and `Ubuntu`, both WSL version 2 and Stopped | Owner-reported output, 16 Nov 2026 |
| Push access from the Claude workspace verified (a throwaway branch was pushed to GitHub) | See Open questions item 3 |

## What has NOT been done

- No development environment installed or confirmed.
- Defects4REST not installed; no defect deployed or reproduced.
- No spec-variant check on Dolibarr.
- No code, schema, prompts or generated tests.
- No focused novelty check.
- Taxonomy and strategy set not frozen; experiment protocol not frozen.
- Decisions D2–D7 and D10 are PROPOSED, i.e. **not yet agreed by the owner**.

## Phase 1 checklist (carried over from the decision log)

- [x] Verify benchmark counts, taxonomy, CLI, prerequisites from primary sources
- [x] Choose pilot subjects (proposed D7)
- [x] Confirm dev OS/IDE — Windows + IntelliJ IDEA (owner, 16 Nov 2026). Host requirement D9 is therefore Windows + WSL2 + Docker Desktop (WSL integration)
- [ ] Verify WSL2 setup: Ubuntu (WSL2) is installed. Still to confirm: Ubuntu starts and works, Docker Desktop is installed and running, and its WSL integration is enabled for Ubuntu (`docker run hello-world` from inside Ubuntu). Decide where the project and benchmark files live (WSL filesystem vs Windows)
- [ ] Install toolchain: Java 21, Maven, Docker (+ WSL2 if Windows), Python 3.9+ venv, Go 1.16+, PostgreSQL (Docker), Git
- [ ] Install Defects4REST v1.0.0 (pin commit), run `defects4rest --help`
- [ ] Deploy restcountries#235 buggy → reproduce "Kiev"; deploy patched → "Kyiv"
- [ ] Deploy one Dolibarr defect, obtain API key, reproduce with curl/Postman
- [ ] Explain the 5 differing Dolibarr specs (leakage check)
- [ ] Minimal Java 21 program: load spec, list operations, send one request, capture status + body
- [ ] Record exact versions (benchmark commit, Docker images, JDK) in [DECISIONS.md](DECISIONS.md)/[RESEARCH_LOG.md](RESEARCH_LOG.md)

First concrete milestone (Handover §11): a locally running benchmark REST API, a readable OpenAPI spec, and a minimal program that sends a request and captures the response. **Not** a dashboard.

## Roadmap (Handover §10)

| Phase | Scope | Status |
|---|---|---|
| 1 | Environment and benchmark setup | **In progress** |
| 2 | Baseline pipeline (parse spec, ordinary LLM tests, execute, capture) | Not started |
| 3 | Test execution engine (test-case model, state handling, assertions, logs) | Not started |
| 4 | Taxonomy and feature selector (deterministic rules, leakage prevention) | Not started |
| 5 | Proposed method (taxonomy-guided suites under fixed budget) | Not started |
| 6 | Evaluation (controlled runs, validation, statistics) | Not started |
| 7 | Integrated application (DB-backed experiment management; optional React) | Not started |
| 8 | Research paper | Not started |

Handover §9 pre-implementation work: A freeze feasible taxonomy (open), B validate benchmark environment (Phase 1, open), C freeze experiment protocol (draft only), D focused novelty check (open), E pilot (open).

## Open questions / blockers

1. WSL2: Ubuntu is installed; Docker Desktop (currently stopped) and its WSL integration for Ubuntu must be confirmed working (see checklist).
2. Owner decision on PROPOSED items D2–D7, D10.
3. GitHub push: verified working on 16 Nov 2026 by pushing a throwaway branch `claude/push-access-test`. The session cannot delete remote branches (HTTP 403), so that branch **still exists on GitHub and must be deleted by the owner** (GitHub → Branches). Nothing has been pushed to `main`.
4. Whether the 5 differing Dolibarr specs leak defect information (E10).

## Next task

Owner reviews the pushed documentation and requests any changes. Then continue the Phase 1 toolchain checklist: verify Docker Desktop + Ubuntu integration, install the toolchain, then install Defects4REST and reproduce restcountries#235.
