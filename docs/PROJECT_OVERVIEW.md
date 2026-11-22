# Project Overview

## Working title (provisional)

**Improving Real-World REST API Defect Detection Through Taxonomy-Guided LLM Test Generation**

The title is provisional (D0.2). Keep it unless implementation evidence or the focused novelty check justifies a change. Do not call the contribution novel or proven.

## Research question

> Can selecting and prioritizing test-generation strategies using an empirical taxonomy of REST API defects improve the detection of real-world REST API defects compared with ordinary LLM-generated tests under the same testing budget?

## Project context

- Final-year B.Tech Information Technology major project (9 credits) with an intended IEEE conference paper.
- Owner's practical background: Java, Spring Boot, REST APIs, PostgreSQL, React, JPA/Hibernate, Postman.
- Priorities: a defensible research contribution, measurable improvement, practical feasibility, and an **original** implementation inspired by papers rather than a reproduction of one paper.
- The owner wants step-by-step guidance and a working system, not an abstract proposal. Build the core engine before optional presentation features.

## Problem framing

REST API testing tools and LLM-based approaches can generate requests and tests, but generated tests may spend effort unevenly across defect types. A generic process may produce many tests without prioritizing the strategies most relevant to the API's observable structure and documented behaviour. This project investigates whether an empirical defect taxonomy can guide strategy selection and improve defect detection under a controlled, comparable budget.

## Proposed contribution (to be validated — not established)

*Original list (handover):*
1. Adopt or construct a practical taxonomy of REST API defect categories that can be operationalised into test-generation strategies.
2. Extract observable features from an API specification and available public documentation.
3. Use those features to select and prioritise taxonomy-guided test strategies with transparent deterministic rules.
4. Generate structured LLM-assisted test cases and assertions for the selected strategies.
5. Evaluate whether this detects more real-world defects than a plain LLM-generated-test baseline under comparable budgets.

*As refined at the methodology checkpoint (16 Nov 2026; D2.1, D3.1, D12, PROVISIONAL):*
- **The taxonomy:** the Defects4REST taxonomy is adopted as reference vocabulary and is the source of a strategy catalogue (C). Adopting it is not our contribution.
- **The study:** a controlled three-arm study under equal budgets, on held-out projects with a buggy-vs-patched differential oracle:
  - **A** — plain LLM generation;
  - **B** — feature-conditioned selection from a general-principles catalogue (established tool checks + metamorphic relations);
  - **C** — the same selector with the taxonomy-derived catalogue.
- **Sub-questions:**
  - RQ1: does B beat A (selection effect)?
  - RQ2: does C beat B (taxonomy effect)?
  - RQ3: which defects and mechanisms does each arm find, and at what cost?
- **Contributions:** the frozen, source-traceable catalogue and a benchmark-validity audit are secondary contributions.

The intended contribution is the **empirical evaluation of this strategy-selection approach**, not the mere use of an LLM, a defect taxonomy, API testing, or multi-agent architecture.

*As refined after the literature integration (23 Nov 2026; D36, D40; wording PROVISIONAL, subject to the pre-submission novelty recheck):*
1. **A pre-registered, three-arm controlled study** on real-world REST API defects (Defects4REST) with a buggy/patched differential oracle, fix attribution and arm-blind confirmation. It tests whether strategy guidance helps (B vs A) and whether strategies derived from an empirical REST defect taxonomy help beyond established general guidance that includes the prior mutation-taxonomy approach (C vs B).
2. **A frozen, traceable feature-to-strategy mapping** (the released artefact): deterministic rules from observable API features to strategies, each citing its taxonomy subtype, tool check or standard, released with all prompts, generated tests and run records.
3. **Evidence from a free, local 4B model.**

**Not ours (credit, never claim):** LLM test generation for REST APIs; taxonomy-guided prompting (Tigulla & Vieira); oracles beyond 5xx (LogiAgent, FDRRestTest, RBCTest); small models for API testing (LlamaRestTest). Never "first"; "to our knowledge, no prior study …" only for the combination, after the recheck (RESEARCH_LOG §5, E38).

**Scale for the first draft (D23–D25):** two held-out projects (Podman, nocodb; 28 primary defects); results are conditional on these projects and this model class.

**Current uncertainty:**
- **Novelty:** the novelty of this exact combination has **not** been established; only a targeted search has been done (see [RESEARCH_LOG.md §5](RESEARCH_LOG.md)). *Update 23 Nov 2026:* focused check and seven full-text reviews done (E38); no study combines our four elements; the saved-string database recheck is still due before submission.
- **Label–mechanism mismatch (risk R1):** defect-type labels demonstrably do not match detection mechanisms in RESTCountries (E13). Whether this hurts taxonomy-guided generation is untested.

## Scope and non-goals

In scope: our own implementation of the pipeline in [ARCHITECTURE.md](ARCHITECTURE.md); controlled comparison per [EXPERIMENT_PROTOCOL.md](EXPERIMENT_PROTOCOL.md); an IEEE-style paper.

Not to start with (D0.4): dashboard/React UI, multi-agent orchestration, reinforcement learning, custom model training, vector database, complex RAG.

Not claimed as novel (D0.7): generic business-logic testing, workflow generation, feedback loops, authorization testing, test-oracle generation, LLM test generation, faulty-spec testing, dependency refinement.

## Relationship to the earlier broad concept

The current hypothesis is a deliberate narrowing of an earlier broad "agentic, contract-aware REST API testing" idea. Do not silently revert to the broad title or treat the earlier feature list as the final contribution.

| Earlier broad concept | Current direction |
|---|---|
| Agentic contract-aware generation of positive, negative, boundary, authorization and workflow tests | Taxonomy-guided selection and prioritisation of strategies from observable API features |
| Execution feedback/refinement as a system feature | Allowed as an implementation aid; not a novelty claim unless a distinct contribution is established |
| Coverage, mutation score and bug detection | Primary: real-world defects detected under equal budgets. Supporting: coverage, false positives, time, request count, LLM cost |
| Potentially complex agentic architecture | Transparent deterministic selector in a simple pipeline; add complexity only if experiments justify it |

## Working definitions

| Term | Meaning |
|---|---|
| API specification | Machine-readable description (e.g. OpenAPI) of endpoints, parameters, schemas and responses |
| Defect taxonomy | Structured classification of defect types, used here to organise and prioritise test strategies |
| Strategy selector | Component mapping observable API features to one or more test strategies |
| Baseline | Ordinary LLM-generated tests without taxonomy-guided strategy selection |
| Proposed method | LLM-generated tests whose strategies are selected/prioritised using the taxonomy and observable API features |
| Defect detection | A test outcome validated as exposing a known or confirmed API defect — **not** merely a failed request (see D4) |

## Research-integrity rules (summary)

Prevent hidden-label leakage (D0.6, D5); keep baseline and proposed budgets comparable; validate every detection; separate measured results from assumptions; never invent results; verify papers and benchmark statistics against primary sources. The binding version of these rules is in [CLAUDE.md](../CLAUDE.md).

## Where things are

| Need | File |
|---|---|
| Current stage and next task | [PROJECT_STATUS.md](PROJECT_STATUS.md) |
| Decisions and their status | [DECISIONS.md](DECISIONS.md) |
| Evidence, risks, literature | [RESEARCH_LOG.md](RESEARCH_LOG.md) |
| Design (provisional) | [ARCHITECTURE.md](ARCHITECTURE.md) |
| Experiment design (draft) | [EXPERIMENT_PROTOCOL.md](EXPERIMENT_PROTOCOL.md) |
| History of changes | [CHANGELOG.md](CHANGELOG.md) |
