# CLAUDE.md — Project rules for Claude Code

This file is binding for every session. Read it fully before changing anything.

## Project in one paragraph

Final-year IT major project and IEEE paper. Working title (provisional): *Improving Real-World REST API Defect Detection Through Taxonomy-Guided LLM Test Generation*. We build our own REST API testing system and compare three arms under equal budgets (A plain LLM tests; B feature-conditioned general-principles strategies; C feature-conditioned taxonomy-derived strategies) on real-world defects from Defects4REST, with project-level holdout. Stack (provisional): Java 21, Spring Boot, OpenAPI, PostgreSQL, Docker, Python for analysis. Details: `docs/PROJECT_OVERVIEW.md`.

## Start of every session (mandatory, before any change)

1. Read this file.
2. Read `docs/PROJECT_STATUS.md` (current stage, open items, next task).
3. Skim `docs/DECISIONS.md` for FINAL / PROVISIONAL / PROPOSED status, and `git log --oneline -10` plus `git status`.
4. Read `docs/ARCHITECTURE.md` or `docs/EXPERIMENT_PROTOCOL.md` if the task touches design or experiments. Read the ground-truth firewall below **before touching anything in the Defects4REST benchmark**.
5. State briefly what stage the project is in and what you will do. If the repo and the docs disagree, tell the owner and fix the docs.

## Documentation rules (mandatory)

After **every meaningful implementation task**, in the same change set:

| Trigger | Update |
|---|---|
| Any meaningful task | `docs/PROJECT_STATUS.md` (what is done / not done, next task, open blockers, "Last updated") |
| Any task | `docs/CHANGELOG.md` (dated entry; Added / Changed / Fixed / Removed) |
| Architecture, components, interfaces, data model or stack change | `docs/ARCHITECTURE.md` |
| A decision is made, reversed or its status changes | `docs/DECISIONS.md` (new row; supersede, never delete) |
| Setup, install, run or usage changes | `README.md` |
| New evidence, paper check, benchmark fact, risk, or novelty-check result | `docs/RESEARCH_LOG.md` |
| Experiment design, metrics, budget, sample or oracle changes; any run is performed | `docs/EXPERIMENT_PROTOCOL.md` |

A task is not finished until these are updated. If nothing needs updating, say so explicitly in the summary. Never leave docs describing something the code no longer does.

## Benchmark ground-truth firewall (mandatory)

**Development projects:** RESTCountries and Dolibarr. Their ground truth may be read (D7.1). Under D16.1 an already-excluded project (e.g. SeaweedFS) may become an additional development subject if Dolibarr is infeasible. Holdout projects are never moved to development without a recorded decision and a contamination entry.

**Holdout candidates:** awx, flowable-engine, kafka-rest, mastodon, netbox, nocodb, podman.

Until the methodology freeze (EXPERIMENT_PROTOCOL §13), **do not open, print or summarise any of the following for holdout candidates:**
- replication READMEs;
- `defects4rest/data/defect_data/*.csv` rows;
- `api_dep_setup` hook bodies;
- patches or fix commits;
- the replication package's per-defect material (RQ3 report sections, tool logs);
- issue-tracker pages;
- triggering endpoints.

What to do instead:
- Use metadata-only scripts that print aggregates (e.g. `analysis/benchmark_audit/holdout_meta.py`, `spec_provenance.py`).
- After the freeze, only the designated validator opens holdout ground truth, and only for validation (X6/X7) and blinded confirmation.
- Record any exposure in `docs/RESEARCH_LOG.md` §9 (contamination ledger).
- Never describe the holdout evaluation as "blind".

## Freeze rule

- **What is frozen:** at the freeze, the catalogues, predicates, feature extractor, DSL, prompts and budgets are git-tagged.
- **After the freeze:** any change goes into the post-freeze change log in `docs/EXPERIMENT_PROTOCOL.md` §14 and forces all arms to be rerun.

## Honesty rules (mandatory)

- **Never invent** implementation progress, experiment results, defect counts, benchmark compatibility, citations or performance numbers. Unknown is written as "TBD" or "not yet verified".
- Keep status labels truthful:

  | Label | Meaning |
  |---|---|
  | **FINAL** | settled |
  | **PROVISIONAL** | adopted with the owner's agreement; revisit at a trigger |
  | **PROPOSED** | not yet agreed by the owner |
  | **SUPERSEDED** | replaced; kept for history |

  Do not promote a status without the owner's agreement or a recorded verification. Record every status change in `DECISIONS.md` §5.
- Mark design and protocol content that is not implemented/frozen as **PROVISIONAL** or **DRAFT**.
- **No novelty claim** anywhere until the focused prior-art check is done and recorded in `docs/RESEARCH_LOG.md`.
- Verify papers and benchmark statistics against **primary sources** (the paper, the artifact/repository) and record source plus date in `docs/RESEARCH_LOG.md`. Literature entries marked "verification pending" must not be cited as verified.
- Report measured results exactly as measured, including negative or inconclusive ones.

## Research-integrity rules (mandatory)

- **No leakage.** The strategy selector and LLM generator must never see benchmark ground truth: bug-replication READMEs, defect CSVs, `defects4rest info` output, issue titles/bodies/URLs, patches, or per-defect labels. Only the allow-listed inputs of D5.1 (sanitised project-level spec, shared exploration feature record, runner config) (D0.6, D5.1).
- **Equal budgets and information** across all experimental arms (D12); record model/provider/version, prompts, seeds, benchmark commit, Docker images and JDK version.
- A **detection** follows the D4.1 protocol (differential, attributed, blind-confirmed); a failing request alone is not a defect.
- Do not tune on the defects used for final evaluation.
- Do not change the research direction, title or research question without strong evidence, recorded as a decision. Do not suggest unrelated project ideas. Do not restart the literature review.
- Scope guard (D0.4): no dashboard, multi-agent orchestration, RL, custom model training or vector database until a measured need exists.

## Working rules

- Implement step by step; prioritise a working system and measurable results. Keep milestones small and explain setup/implementation choices.
- Own implementation, informed by papers; do not copy a base paper.
- Do not commit secrets or API keys. Do not commit benchmark data or large artifacts; document how to fetch them.
- Do not commit or push unless the owner asks. Show what changed first. Commit messages: short imperative subject; mention the docs updated.
- Do not delete or rewrite history in docs. Supersede and annotate.
- When unsure whether something is decided, check `docs/DECISIONS.md`; if absent, ask.

## Files

```
README.md                    setup, usage, doc index
CLAUDE.md                    this file
docs/PROJECT_OVERVIEW.md     title, research question, contribution, scope, definitions
docs/PROJECT_STATUS.md       current stage, checklist, roadmap, next task
docs/DECISIONS.md            decision log (FINAL / PROVISIONAL / PROPOSED / SUPERSEDED)
docs/RESEARCH_LOG.md         evidence table, risks, literature, novelty-check status
docs/ARCHITECTURE.md         provisional design and stack
docs/EXPERIMENT_PROTOCOL.md  approved-provisional experiment design (not yet frozen)
docs/CHANGELOG.md            dated history of changes
docs/proposals/              dated, immutable reasoning documents (never edited after approval;
                             later proposals supersede earlier ones; DECISIONS.md is authoritative)
analysis/benchmark_audit/    researcher-side scripts; may read ground truth; system code must never import them
scripts/                     deployment helper scripts (e.g. Dolibarr unattended setup); never read benchmark ground truth
```

## Source of truth

The GitHub repository is the single source of truth for project documentation (D10, FINAL). The original handover `.docx` and `Decision_Log_and_Evidence_Table.md` live in the claude.ai Project as historical sources.

## End of every session

Summarise: what changed, which docs were updated, what is still open, the exact next task. Make sure `docs/PROJECT_STATUS.md` matches that summary.
