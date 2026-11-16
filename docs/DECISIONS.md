# Decision Log

Project: *Improving Real-World REST API Defect Detection Through Taxonomy-Guided LLM Test Generation* (working title, provisional)

This is the permanent decision log. It carries forward, without loss, the decision list in `REST_API_Project_Handover_v2_Complete.docx` (§3, 10 Nov 2026) and `Decision_Log_and_Evidence_Table.md` (claude.ai Project document, last updated 16 Nov 2026). Evidence IDs (E1–E10) refer to [RESEARCH_LOG.md](RESEARCH_LOG.md).

## Status key

| Status | Meaning |
|---|---|
| **FINAL** | Settled. Do not reopen without a clear technical or research reason, recorded here. |
| **PROVISIONAL** | Adopted for now. Revisit at the stated trigger. |
| **PROPOSED** | Recommended but **not yet agreed** by the project owner. Do not treat as settled; do not build irreversible work on it. |

Rules for editing this file are in [CLAUDE.md](../CLAUDE.md). Never delete or silently rewrite a decision: supersede it with a new entry and keep the old one.

## Decisions

### D0 — Decisions carried over from the handover (10 Nov 2026) — FINAL

Source: Handover §3 (and §12, §19). Expanded here into individually citable items so they are not lost inside a single row.

| ID | Decision | Status |
|---|---|---|
| D0.1 | Project direction is LLM-assisted REST API testing, narrowed to taxonomy-guided test generation and real-world defect detection. | FINAL |
| D0.2 | Working title and research question are as recorded in [PROJECT_OVERVIEW.md](PROJECT_OVERVIEW.md). The **title stays provisional** until a focused novelty check and a pilot experiment support it. | FINAL (title itself PROVISIONAL) |
| D0.3 | Build our **own implementation**, informed by papers and tools. Do not simply reproduce one base paper. | FINAL |
| D0.4 | Do **not** start with a dashboard, elaborate multi-agent architecture, reinforcement learning, custom model training or a vector database. | FINAL |
| D0.5 | Start with a small end-to-end pipeline: parse OpenAPI → generate requests/tests → execute against a running API → record results. | FINAL |
| D0.6 | Use a **deterministic** taxonomy strategy selector initially. It may use only public API specifications and requirements. It must **not** use hidden defect labels, issue descriptions, patches or benchmark ground truth (leakage rule). | FINAL |
| D0.7 | Do not claim as novel: generic business-logic testing, workflow generation, feedback loops, test-oracle generation, or LLM-based test generation by themselves. | FINAL |
| D0.8 | The project stays on REST API defect detection. Do not switch to the other directions considered earlier (AI for Indian public services, security vulnerability detection/fixing) unless the owner explicitly asks. | FINAL (Handover §16) |

### D1–D9 — Phase 1 decisions (16 Nov 2026)

| # | Date | Decision | Reason | Evidence | Status |
|---|---|---|---|---|---|
| D1 | 16 Nov 2026 | Use **Defects4REST v1.0.0** as the primary real-world defect benchmark. | Only public executable benchmark of real-world REST API defects; single-command buggy/patched checkout; per-bug OpenAPI specs. | E1–E5 | **PROVISIONAL** (until one defect is reproduced locally) |
| D2 | 16 Nov 2026 | **Adopt the Defects4REST taxonomy (6 types / 13 subtypes)** as the empirical taxonomy instead of constructing our own four-category list. | Empirically derived (607 defects, 50 projects), peer-reviewed (ICSE 2026), and the same label space as the benchmark, so results are interpretable. Handover candidates map onto it (see [RESEARCH_LOG.md §3](RESEARCH_LOG.md)). | E1, E6 | **PROPOSED** |
| D3 | 16 Nov 2026 | **Separate "defect type" from "test strategy".** Taxonomy = what goes wrong (Defects4REST types). Strategies = how a test probes for it (e.g. boundary/invalid input, filter/query probing, multi-step stateful workflow, credential/role variation, semantic/factual response assertion). The selector maps *observable API features* → strategies, prioritised using taxonomy information. | The handover's four candidates mixed both levels: "stateful workflows" and "response/behaviour checks" are strategies, not defect types. Defects4REST RQ3 failure causes (L1 semantic, L2 stateless, L3 auth) are naturally strategies. | E6, E7 | **PROPOSED** |
| D4 | 16 Nov 2026 | **Detection oracle = differential + manual confirmation.** A defect counts as detected only if a generated test (a) fails on the buggy checkout, (b) passes on the patched checkout, and (c) a human confirms post hoc that the failure corresponds to the documented defect. | Stronger and more objective than Defects4REST's own criterion (manual log inspection, "matched or closely resembled", any of 5 seeds). Buggy/patched checkouts make this possible. Consistent with Handover §8.3 ("a failing request alone is not a defect detection"). | E5, E8 | **PROPOSED** |
| D5 | 16 Nov 2026 | **Leakage firewall.** Selector and generator may read only `<project>#<issue>_spec.json/yaml` and genuinely public API docs. They must **never** read `bug_replication/**/README.md`, `defects4rest/data/defect_data/*.csv`, `defects4rest info` output, issue URLs/titles, patches, or per-defect taxonomy labels. Enforce in code (path allow-list) and log every input file per run. | Benchmark folders hold issue text and triggering endpoints alongside the spec. Operationalises D0.6. | E9 | **PROPOSED** |
| D6 | 16 Nov 2026 | Taxonomy **prevalence priors** (if used for prioritisation) must be computed from the 607-defect set **excluding evaluation projects**, or the selector uses feature rules only with uniform priors. | The published 42% / 27% / 10% … distribution includes the evaluation defects → circularity. | E1 | **PROPOSED** |
| D7 | 16 Nov 2026 | **Pilot subjects: RESTCountries (16 defects) + Dolibarr (25 listed).** Fallbacks: NetBox (6), NocoDB (6). | RESTCountries: lightest build (Java 8 WAR on Tomcat), one spec for all 16 bugs, OpenAPI 3.0.3, 10 paths. Dolibarr: largest validation set (16 T2), plus auth/storage/config defects, API-key auth, MariaDB. Together: 41 listed defects and 8 of the paper's 30 RQ3 defects (published tool results available as reference baselines). | E3, E4, E7, E10 | **PROPOSED** |
| D8 | 16 Nov 2026 | Spec parser must support **both Swagger 2.0 and OpenAPI 3.x** (use swagger-parser v3, which converts 2.0 → 3.0). | Dolibarr specs are Swagger 2.0; RESTCountries is OpenAPI 3.0.3. | E10 | **PROVISIONAL** |
| D9 | 16 Nov 2026 | Development host must be **native Linux, or Windows + WSL2 + Docker Desktop (WSL integration)**. | Defects4REST prerequisites; the Podman subject requires Linux. | E2 | **FINAL** (benchmark requirement) |

### D10 — Documentation system (16 Nov 2026) — PROPOSED

| # | Date | Decision | Reason | Evidence | Status |
|---|---|---|---|---|---|
| D10 | 16 Nov 2026 | The **GitHub repository is the single source of truth** for project documentation. `docs/` holds the status, decisions, research log, architecture, experiment protocol and changelog; `CLAUDE.md` defines mandatory update rules. The claude.ai Project documents (`Decision_Log_and_Evidence_Table.md`, the handover `.docx`) are historical sources and are **not edited further** once this set is accepted. | Sessions do not share memory; cloud workspace and local folder do not sync. One authoritative, versioned location keeps every future session consistent. | Owner request, 16 Nov 2026 | **PROPOSED** (pending owner review of this documentation set) |

## Superseded or reopened decisions

None yet.

## How to add a decision

Append a row (next free number) with date, decision, reason, evidence IDs, and status. If it changes an earlier decision, add the new row, then mark the old row "Superseded by Dn" in the section above rather than deleting it. Update [CHANGELOG.md](CHANGELOG.md) in the same change.
