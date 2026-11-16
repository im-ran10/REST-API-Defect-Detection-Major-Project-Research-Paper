# CLAUDE.md — Project rules for Claude Code

This file is binding for every session. Read it fully before changing anything.

## Project in one paragraph

Final-year IT major project and IEEE paper. Working title (provisional): *Improving Real-World REST API Defect Detection Through Taxonomy-Guided LLM Test Generation*. We build our own REST API testing system and test whether taxonomy-guided LLM test generation detects more real-world defects than ordinary LLM-generated tests under equal budgets. Stack (provisional): Java 21, Spring Boot, OpenAPI, PostgreSQL, Docker, Python for analysis. Details: `docs/PROJECT_OVERVIEW.md`.

## Start of every session (mandatory, before any change)

1. Read this file.
2. Read `docs/PROJECT_STATUS.md` (current stage, open items, next task).
3. Skim `docs/DECISIONS.md` for FINAL / PROVISIONAL / PROPOSED status, and `git log --oneline -10` plus `git status`.
4. Read `docs/ARCHITECTURE.md` or `docs/EXPERIMENT_PROTOCOL.md` if the task touches design or experiments.
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

## Honesty rules (mandatory)

- **Never invent** implementation progress, experiment results, defect counts, benchmark compatibility, citations or performance numbers. Unknown is written as "TBD" or "not yet verified".
- Keep status labels truthful: **FINAL** (settled), **PROVISIONAL** (revisit at a trigger), **PROPOSED** (not yet agreed by the owner). Do not promote a status without the owner's agreement or a recorded verification.
- Mark design and protocol content that is not implemented/frozen as **PROVISIONAL** or **DRAFT**.
- **No novelty claim** anywhere until the focused prior-art check is done and recorded in `docs/RESEARCH_LOG.md`.
- Verify papers and benchmark statistics against **primary sources** (the paper, the artifact/repository) and record source plus date in `docs/RESEARCH_LOG.md`. Literature entries marked "verification pending" must not be cited as verified.
- Report measured results exactly as measured, including negative or inconclusive ones.

## Research-integrity rules (mandatory)

- **No leakage.** The strategy selector and LLM generator must never see benchmark ground truth: bug-replication READMEs, defect CSVs, `defects4rest info` output, issue titles/bodies/URLs, patches, or per-defect labels. Only the OpenAPI/Swagger spec and genuinely public docs (D0.6, D5).
- **Equal budgets** between baseline and proposed arms; record model/provider/version, prompts, seeds, benchmark commit, Docker images and JDK version.
- A **detection** requires validation (D4); a failing request alone is not a defect.
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
docs/DECISIONS.md            decision log (FINAL / PROVISIONAL / PROPOSED)
docs/RESEARCH_LOG.md         evidence table, risks, literature, novelty-check status
docs/ARCHITECTURE.md         provisional design and stack
docs/EXPERIMENT_PROTOCOL.md  draft experiment design (not frozen)
docs/CHANGELOG.md            dated history of changes
```

## Source of truth

The GitHub repository is the source of truth for project documentation (D10, PROPOSED). The original handover `.docx` and `Decision_Log_and_Evidence_Table.md` live in the claude.ai Project as historical sources.

## End of every session

Summarise: what changed, which docs were updated, what is still open, the exact next task. Make sure `docs/PROJECT_STATUS.md` matches that summary.
