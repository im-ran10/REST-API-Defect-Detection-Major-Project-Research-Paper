# Changelog

All notable changes to project documentation, decisions, design and code. Newest first. Dates are the date of the change. Add an entry in the same change as every meaningful modification (see [CLAUDE.md](../CLAUDE.md)).

## [Unreleased]

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
