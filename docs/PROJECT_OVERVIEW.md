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

1. Adopt (D2, PROPOSED) or construct a practical taxonomy of REST API defect categories that can be operationalised into test-generation strategies.
2. Extract observable features from an API specification and available public documentation.
3. Use those features to select and prioritise taxonomy-guided test strategies with transparent deterministic rules.
4. Generate structured LLM-assisted test cases and assertions for the selected strategies.
5. Evaluate whether this detects more real-world defects than a plain LLM-generated-test baseline under comparable budgets.

The intended contribution is the **empirical evaluation of this strategy-selection approach**, not the mere use of an LLM, a defect taxonomy, API testing, or multi-agent architecture.

**Current uncertainty:** the novelty of this exact combination has **not** been established (see [RESEARCH_LOG.md §5](RESEARCH_LOG.md)), and it is untested whether defect-type labels correspond to how defects can actually be detected (RESEARCH_LOG risk R1).

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
