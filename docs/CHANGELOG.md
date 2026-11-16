# Changelog

All notable changes to project documentation, decisions, design and code. Newest first. Dates are the date of the change. Add an entry in the same change as every meaningful modification (see [CLAUDE.md](../CLAUDE.md)).

## [Unreleased]

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
