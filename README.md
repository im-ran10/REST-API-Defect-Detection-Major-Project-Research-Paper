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
It runs Dolibarr's own installer (forced-install file + the browser's installer pages), then enables the **R14 module configuration** (D22): the 28 API-capable modules listed in `scripts/dolibarr/modules_r14.txt`, one at a time with Dolibarr's own `activateModule()`. It records activation failures and the enabled-module list with a fingerprint in its log folder, gets the admin API key from Dolibarr's login API and checks it. It refuses to run on an already-installed database. Optional settings: `LOG_DIR=<folder>` keeps the logs there; `SPEC_OUT=<file>` also saves `/api/index.php/explorer/swagger.json`; `MODULES=modApi,modProduct` gives the earlier minimal setup. The module list itself is regenerated only with `php scripts/dolibarr/list_api_modules.php <htdocs>` at the reference version.

**Local LLM (D27; Day 1 of the ten-day plan).** Ollama runs as a service inside the main WSL Ubuntu (not the separate Podman distribution):
```bash
sudo apt-get install -y zstd                       # the Ollama installer needs it
curl -fsSL https://ollama.com/install.sh | sh      # installs to /usr/local, service on 127.0.0.1:11434
ollama pull qwen3:4b-instruct-2507-q4_K_M
ollama pull gemma3:4b
ollama ps                                          # PROCESSOR must read "100% GPU"
```
Models are stored by the service under `/usr/share/ollama/.ollama/models`, outside the repository. In WSL the GPU can fail to initialise within Ollama's 30-second discovery window right after a boot (the service then silently uses the CPU); `sudo systemctl restart ollama` fixes it. The client refuses to run unless the model is fully on the GPU.

Run configuration (D44, PROPOSED; measured in G1, E43). On the 6 GB laptop GPU the model fits fully at `num_ctx` 16384 only with flash attention and an 8-bit KV cache, set for the service with a systemd drop-in (undo: delete the file, `daemon-reload`, restart):
```bash
sudo mkdir -p /etc/systemd/system/ollama.service.d
printf '[Service]\nEnvironment="OLLAMA_FLASH_ATTENTION=1"\nEnvironment="OLLAMA_KV_CACHE_TYPE=q8_0"\n' | sudo tee /etc/systemd/system/ollama.service.d/kv-cache.conf
sudo systemctl daemon-reload && sudo systemctl restart ollama
systemctl --no-pager show ollama -p Environment     # record this line with every run
```
Before every long model run (R35): Windows **balanced** power mode, plugged in; PCIe link-state power management off on AC (once, in an administrator PowerShell: `powercfg /setacvalueindex SCHEME_CURRENT SUB_PCIEXPRESS ASPM 0`, then `powercfg /setactive SCHEME_CURRENT`); and a GPU log in a separate PowerShell window: `nvidia-smi --query-gpu=timestamp,temperature.gpu,power.draw,clocks.gr,utilization.gpu,memory.used --format=csv -l 5 -f $HOME\gpu-log.csv`.

**System CLI** (`system/`, plain Java 21 + Maven, swagger-parser 2.1.48), built to `target/restdd.jar`:
```bash
cd ~/projects/rest-api-defect-detection/system
mvn package                                        # builds target/restdd.jar and runs the unit tests
java -jar target/restdd.jar normalise --spec <spec file> [--out <dir>] [--compact cap=200,drop-examples,drop-schema-descriptions,resp-depth=2]
java -jar target/restdd.jar sanitise  --spec <spec file>                 # D5.1 keyword hits for owner review
java -jar target/restdd.jar llm-check --spec <spec file> --model qwen3:4b-instruct-2507-q4_K_M [--num-ctx 8192]
java -jar target/restdd.jar g1 --spec <spec>[,<spec>] --model <model> --num-ctx 12288,16384 --compact <rule>   # G1 test
```
`g1` (D41/D43) builds the real prompt (shared system prompt + operation context + a task section padded to the expected longest B/C size) for the largest operation over the given specs and a median one, and for each context window checks that the model loads fully on the GPU, processes the largest prompt without truncation, and how fast it runs; it prints a projection for the 40-hour rule. It prints sizes and timings only, never operation names.

**Running a suite against a deployment (T5).** A target file (kept outside the repository; examples in `scripts/targets/`) gives the base URL, the auth environment variable and the reset commands:
```bash
mkdir -p ~/restdd-targets && cp ~/projects/rest-api-defect-detection/scripts/targets/*.example.json ~/restdd-targets/   # once; edit paths if needed
java -jar target/restdd.jar exec --target <target.json> --spec <spec> --suite <suite.json> [--snapshot] [--label buggy] [--only 2] [--out runs/x.json]
```
`--snapshot` takes the deployment's snapshot first (once per deployment, right after setup); every run then restores it before the tests. Each test prints PASS / FAIL / SETUP_ERROR / INFRA_ERROR / INVALID with a reason; the full record (requests, responses, assertion results, validation result) goes to `--out`, with the API key replaced by `<redacted>`. Dolibarr reset by hand: `bash scripts/reset/dolibarr_reset.sh snapshot|restore|check` from the Dolibarr folder (snapshots in `~/.restdd/snapshots/`, private: they contain the database).

**nocodb reference deployment (Day 2, D25; T5).** The reference version 0.90.3 runs from the official image, with no benchmark command and no defect-specific hook (firewall). Scripts in `scripts/nocodb/`:
```bash
bash scripts/nocodb/nocodb_start.sh            # container restdd-nocodb on 127.0.0.1:8090, SQLite in volume restdd-nocodb-data; private ~/.nocodb_credentials and ~/.nocodb_env created once
source ~/.nocodb_credentials && python3 scripts/nocodb/nocodb_setup.py   # admin user, fixture project/table/3 rows, writes ~/restdd-targets/nocodb.json
source scripts/nocodb/nocodb_env.sh            # exports NOCODB_TOKEN for the runner (never printed)
bash scripts/reset/nocodb_reset.sh snapshot|restore|check
```
Official spec (D5.1 input): `curl -sL -o ~/tools/nocodb_cea2b4ad7348_swagger.json https://raw.githubusercontent.com/nocodb/nocodb/cea2b4ad7348/scripts/sdk/swagger.json`; SHA-256 `234bfa530c0c0860e83689f0b8752eff254c85f2c96d29c6d10ef5e9480696f1`.

`llm-check` prints the Ollama version and model digest, verifies the model is 100% on the GPU with the requested context window, makes one structured-output call for the spec's first operation and validates it. Its cache and call log go to `./llm-check/` (not committed).

**Phase 1 probe** (now the `probe` subcommand). Loads a Swagger 2.0 / OpenAPI 3.x spec, lists its operations, sends one GET and prints status and body:
```bash
source ~/.dolibarr_credentials
java -jar target/restdd.jar probe \
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
