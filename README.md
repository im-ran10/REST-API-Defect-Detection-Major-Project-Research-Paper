# REST API Defect Detection — Major Project & Research Paper

**Working title (provisional):** *Improving Real-World REST API Defect Detection Through Taxonomy-Guided LLM Test Generation*

Final-year B.Tech IT major project and intended IEEE conference paper.

## Research question

> Can selecting and prioritizing test-generation strategies using an empirical taxonomy of REST API defects improve the detection of real-world REST API defects compared with ordinary LLM-generated tests under the same testing budget?

## Current status

**Methodology provisionally approved (16 Nov 2026); Phase 1 (environment and feasibility checks) is next.** There is no application code yet and **no experimental results**. The novelty of the approach has **not** been established. See [docs/PROJECT_STATUS.md](docs/PROJECT_STATUS.md) for exactly what is and is not done.

## Approach (provisional)

Our own pipeline: project-level OpenAPI/Swagger spec + GET-only exploration → deterministic feature extractor → planner → LLM test generator → replay against buggy and patched versions → differential verdicts.

Three arms run under equal budgets:

| Arm | Test generation |
|---|---|
| A | plain LLM test generation |
| B | feature-conditioned strategy selection from a catalogue of established testing principles |
| C | the same selector with a catalogue derived from the Defects4REST defect taxonomy |

Evaluation:
- **Benchmark:** **Defects4REST** (pinned commit `265db11`; not yet reproduced locally).
- **Development projects:** RESTCountries and Dolibarr.
- **Final evaluation:** pre-registered, project-level held-out projects.

Details: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md), [docs/EXPERIMENT_PROTOCOL.md](docs/EXPERIMENT_PROTOCOL.md).

Planned stack: Java 21, Spring Boot, PostgreSQL, Docker, Python (analysis).

## Setup and usage

Not available yet — there is nothing to build or run. This section will be updated when the toolchain and first code are added. Planned prerequisites for the benchmark (Defects4REST v1.0.0): Python 3.9+, Docker + Compose, Git, Go 1.16+; on Windows, WSL2 with Docker Desktop WSL integration.

## Documentation

| Document | Purpose |
|---|---|
| [CLAUDE.md](CLAUDE.md) | Mandatory rules for Claude Code sessions (documentation, honesty, research integrity) |
| [docs/PROJECT_OVERVIEW.md](docs/PROJECT_OVERVIEW.md) | Title, research question, contribution, scope, definitions |
| [docs/PROJECT_STATUS.md](docs/PROJECT_STATUS.md) | Current stage, checklist, roadmap, next task |
| [docs/DECISIONS.md](docs/DECISIONS.md) | Decision log with FINAL / PROVISIONAL / PROPOSED status |
| [docs/RESEARCH_LOG.md](docs/RESEARCH_LOG.md) | Evidence table, benchmark findings, risks, literature, novelty-check status |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Provisional system design and stack |
| [docs/EXPERIMENT_PROTOCOL.md](docs/EXPERIMENT_PROTOCOL.md) | Draft (not frozen) experiment design |
| [docs/CHANGELOG.md](docs/CHANGELOG.md) | Dated history of changes |

## Repository conventions

Docs are updated together with every meaningful change (rules in [CLAUDE.md](CLAUDE.md)). Decisions are never deleted, only superseded.
