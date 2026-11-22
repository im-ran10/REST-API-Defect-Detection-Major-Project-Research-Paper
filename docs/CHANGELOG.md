# Changelog

All notable changes to project documentation, decisions, design and code. Newest first. Dates are the date of the change. Add an entry in the same change as every meaningful modification (see [CLAUDE.md](../CLAUDE.md)).

## [Unreleased]

### 2026-11-22 — Minimal Java 21 probe (Phase 1 milestone)

Added
- `system/`: Maven project (plain Java 21, swagger-parser 2.1.48, package `restdd.probe`): `SpecLoader`, `OperationLister`, `HttpProbe`, `ProbeMain`; runnable `target/probe.jar`. Loads a spec, lists operations, sends one GET, prints status and body; API key from an environment variable, sent as a header, never printed.
- Unit tests with three hand-written fixtures (Swagger 2.0, OpenAPI 3.0.3, OpenAPI 3.1.0) giving identical operation lists, plus 3.1 feature checks (7 tests).
- `.gitignore` (build output, IDE files, credentials).
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
