# REST API Defect Detection — Major Project & Research Paper

**Working title (provisional):** *Improving Real-World REST API Defect Detection Through Taxonomy-Guided LLM Test Generation*

Final-year B.Tech IT major project and intended IEEE conference paper.

## Research question

> Can selecting and prioritizing test-generation strategies using an empirical taxonomy of REST API defects improve the detection of real-world REST API defects compared with ordinary LLM-generated tests under the same testing budget?

## Current status

**Methodology provisionally approved (16 Nov 2026); Phase 1 (environment and feasibility checks) is in progress.** One defect of each development project has been reproduced locally (restcountries#235, dolibarr#23179). There is no application code yet and **no experimental results**. The novelty of the approach has **not** been established. See [docs/PROJECT_STATUS.md](docs/PROJECT_STATUS.md) for exactly what is and is not done.

## Approach (provisional)

Our own pipeline: project-level OpenAPI/Swagger spec + GET-only exploration → deterministic feature extractor → planner → LLM test generator → replay against buggy and patched versions → differential verdicts.

Three arms run under equal budgets:

| Arm | Test generation |
|---|---|
| A | plain LLM test generation |
| B | feature-conditioned strategy selection from a catalogue of established testing principles |
| C | the same selector with a catalogue derived from the Defects4REST defect taxonomy |

Evaluation:
- **Benchmark:** **Defects4REST** (pinned commit `265db11`).
- **Development projects:** RESTCountries and Dolibarr.
- **Final evaluation:** pre-registered, project-level held-out projects.

Details: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md), [docs/EXPERIMENT_PROTOCOL.md](docs/EXPERIMENT_PROTOCOL.md).

Planned stack: Java 21, Spring Boot, PostgreSQL, Docker, Python (analysis).

## Setup and usage

The only code so far is the Phase 1 probe in `system/` (see below). The verified environment for the benchmark is below, followed by the Phase 1 helper scripts in `scripts/`.

**Environment** (verified 20 Nov 2026; see `docs/EXPERIMENT_PROTOCOL.md` §11):
- Windows with WSL2 running Ubuntu 24.04.
- Docker Desktop with WSL integration enabled for Ubuntu; your Ubuntu user in the `docker` group.
- OpenJDK 21, Maven, Python 3.9+ (with `venv`), Go 1.16+, Git.
- GitHub CLI (`gh auth login`), using HTTPS.

**Benchmark (Defects4REST, pinned commit):**
```bash
mkdir -p ~/tools && cd ~/tools
python3 -m venv defects4rest-env && source defects4rest-env/bin/activate
git clone https://github.com/ANSWER-OSU/Defects4REST.git && cd Defects4REST
git checkout 265db1165e54b01a0698af0fd28d345169a5449f
pip install -e . && defects4rest --help
```

The benchmark lives outside this repository and is never committed. Holdout projects' replication material must not be opened (see `CLAUDE.md`).

Known quirks:
- `--stop` and `--clean` need `--buggy` or `--patched` as well.
- RESTCountries redeploys need `sudo rm -rf ~/tools/Defects4REST/defects4rest/data/temp_project_data/restcountries` first (root-owned build files).
- WSL DNS: if Docker pulls time out on `10.255.255.254`, add `[network]` / `generateResolvConf = false` to `/etc/wsl.conf`, run `wsl --shutdown` (this also stops Docker Desktop; restart it), then replace `/etc/resolv.conf` with `nameserver 1.1.1.1` and `nameserver 8.8.8.8`.
- Dolibarr image build fails on `bullseye-security` (Debian 11 is end-of-life). Workaround (policy D18, a recorded deviation): in the generated `Dockerfile`, add `RUN sed -i "/bullseye-security/d" /etc/apt/sources.list` right after `FROM php:7.4-apache`.
- Dolibarr re-clones ~1.7 GB on every deploy. To switch versions in the prepared folder instead (allowed by D19; check `git log -1` and `git status` first): `docker compose -f docker-compose.yml -f docker-compose.override.yml down -v`, `git checkout <sha>`, then `up -d --build`.
- Dolibarr install wizard (manual fallback): database server `db`, database/login/password `dolibarr`, documents directory `/var/www/documents`; then enable *API / Web services (REST server)* and *Products*, and generate the admin API key (sent as the `DOLAPIKEY` header). Normally use the script below instead.

**Dolibarr unattended setup** (`scripts/dolibarr/dolibarr_setup.sh`, D17). Run it from the prepared Dolibarr folder after a fresh start:
```bash
cd ~/tools/Defects4REST/defects4rest/data/temp_project_data/dolibarr
docker compose -f docker-compose.yml -f docker-compose.override.yml down -v
docker compose -f docker-compose.yml -f docker-compose.override.yml up -d --build
bash ~/projects/rest-api-defect-detection/scripts/dolibarr/dolibarr_setup.sh
source ~/.dolibarr_credentials   # exports DOLIBARR_API_KEY (never printed)
```
A credentials file written before 22 Nov 2026 has no `export` lines; use `set -a; source ~/.dolibarr_credentials; set +a` or rerun the setup.
It runs Dolibarr's own installer (forced-install file + the browser's installer pages), enables the API and Products modules, gets the admin API key from Dolibarr's login API and checks it. It refuses to run on an already-installed database. Settings can be overridden with environment variables (see the script header).

**Phase 1 probe** (`system/`, plain Java 21 + Maven, swagger-parser 2.1.48). Loads a Swagger 2.0 / OpenAPI 3.x spec, lists its operations, sends one GET and prints status and body:
```bash
cd ~/projects/rest-api-defect-detection/system
mvn package                      # builds target/probe.jar and runs the unit tests
source ~/.dolibarr_credentials
java -jar target/probe.jar \
  --spec ~/tools/dolibarr_9d4007e_swagger.json \
  --base-url http://localhost:8080/api/index.php \
  --get /status \
  --auth-header DOLAPIKEY --auth-env DOLIBARR_API_KEY
```
The key is read from the environment variable named by `--auth-env`, sent as a header, and never printed. Without the two auth flags no key is sent (e.g. RESTCountries). `--max-body N` limits the printed body (default 4000; 0 = all). The base URL always comes from `--base-url`, never from the spec.

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
