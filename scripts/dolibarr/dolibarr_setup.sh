#!/usr/bin/env bash
# dolibarr_setup.sh — unattended first-time setup of a freshly started Dolibarr deployment
# (Phase 1, D17). Replaces the manual browser steps of E29:
#   install wizard → enable API + Products modules → obtain the admin API key.
#
# How it works (all through Dolibarr's own code, no direct table edits):
#   1. writes Dolibarr's documented forced-install file (install/install.forced.php) into the
#      container: database settings, documents directory, admin login and the modules to enable;
#   2. opens and submits the same installer pages the browser does
#      (check → fileconf → step1 → step2 → step5; check.php creates conf/conf.php from the example);
#   3. step5 creates the admin user (the installer enables only the API module);
#   4. removes the forced-install file, then enables the requested modules with Dolibarr's own
#      activateModule() and the application fully loaded (activate_modules.php), recording failures;
#   5. asks Dolibarr's login API (POST /api/index.php/login) for the admin's API key, so the key is
#      created and stored the way this Dolibarr version expects (plain or encrypted);
#   6. checks the key with GET /api/index.php/status.
#
# Run it from the prepared Dolibarr folder (the one with docker-compose.yml and
# docker-compose.override.yml), after `docker compose ... up -d --build` on a FRESH deployment
# (`down -v` first if Dolibarr was installed before).
#
# Output: progress lines only. The API key and admin password are never printed; they are written
# to $CRED_FILE (default ~/.dolibarr_credentials, permissions 600) as exported shell variables,
# so that child processes (e.g. the Java probe) see them too:
#   export DOLIBARR_API_KEY=...   export DOLIBARR_ADMIN_PASSWORD=...
# Use them with:  source ~/.dolibarr_credentials; curl -H "DOLAPIKEY: $DOLIBARR_API_KEY" ...
#
# Every setting below can be overridden with an environment variable of the same name.
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
COMPOSE="${COMPOSE:-docker compose -f docker-compose.yml -f docker-compose.override.yml}"
WEB_EXEC="${WEB_EXEC-$COMPOSE exec -T dolibarr}"        # how to run a shell command in the web container
ACTIVATE_EXEC="${ACTIVATE_EXEC-$COMPOSE exec -T -u www-data dolibarr}"  # module activation runs as the web server user
TMP_IN_CONTAINER="${TMP_IN_CONTAINER:-/tmp}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SQL_CMD="${SQL_CMD-$COMPOSE exec -T db mysql -udolibarr -pdolibarr -sN dolibarr}"  # read-only checks
HTDOCS="${HTDOCS:-/var/www/html}"
DATA_DIR="${DATA_DIR:-/var/www/documents}"
MAIN_URL="${MAIN_URL:-$BASE_URL}"
DB_HOST="${DB_HOST:-db}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-dolibarr}"
DB_USER="${DB_USER:-dolibarr}"
DB_PASS="${DB_PASS:-dolibarr}"
ADMIN_LOGIN="${ADMIN_LOGIN:-admin}"
# Modules to enable. Default: the R14 configuration (owner-approved option A, 22 Nov 2026), i.e. every module
# that provides REST API classes at the reference version 9d4007e, frozen in modules_r14.txt and used unchanged
# for spec extraction, buggy and patched deployments. MODULES=modApi,modProduct gives the minimal E29 setup.
MODULES_FILE="${MODULES_FILE:-$SCRIPT_DIR/modules_r14.txt}"
if [ -z "${MODULES:-}" ]; then
  MODULES=$(grep -v '^#' "$MODULES_FILE" | tr -d ' \n')
fi
SETUP_LANG="${SETUP_LANG:-en_US}"
CRED_FILE="${CRED_FILE:-$HOME/.dolibarr_credentials}"
LOG_DIR="${LOG_DIR:-$(mktemp -d /tmp/dolibarr_setup.XXXXXX)}"
WAIT_SECS="${WAIT_SECS:-180}"
mkdir -p "$LOG_DIR"

START=$(date +%s)
say()  { printf '[%4ss] %s\n' "$(( $(date +%s) - START ))" "$*"; }
fail() { say "FAILED: $*"; say "Installer pages saved in $LOG_DIR (no secrets in them)"; exit 1; }
# docker compose repeats a harmless warning about the benchmark's compose files on every call; hide only that line
web()  { ${WEB_EXEC:-} sh -c "$1" 2> >(grep -v 'attribute .version. is obsolete' >&2); }
sql()  { $SQL_CMD -e "$1" 2>/dev/null; }  # read-only checks; errors show up as failed checks

post() {  # post <page> <name=value>... ; saves the returned page, fails on HTTP errors
  local page=$1; shift
  local args=()
  for kv in "$@"; do args+=(--data-urlencode "$kv"); done
  local code
  code=$(curl -sS -o "$LOG_DIR/$page.html" -w '%{http_code}' --max-time 900 \
         -X POST "$BASE_URL/install/$page.php" "${args[@]}") || fail "could not reach $page.php"
  [ "$code" = 200 ] || fail "$page.php returned HTTP $code"
}

get() {  # get <page> ; opens an installer page like the browser does
  local code
  code=$(curl -sS -o "$LOG_DIR/$1.html" -w '%{http_code}' --max-time 120 \
         "$BASE_URL/install/$1.php?selectlang=$SETUP_LANG") || fail "could not reach $1.php"
  [ "$code" = 200 ] || fail "$1.php returned HTTP $code"
}

cleanup_forced() { web "rm -f '$HTDOCS/install/install.forced.php'" >/dev/null 2>&1 || true; }

# ---------------------------------------------------------------- 0. record what is being set up
say "Dolibarr unattended setup (log dir: $LOG_DIR)"
if git rev-parse --git-dir >/dev/null 2>&1; then
  say "Source version: $(git log -1 --format='%h %cs')"
  [ -z "$(git status --porcelain --untracked-files=no)" ] && say "Tracked files: unchanged (D19 check passed)" \
    || say "WARNING: tracked files are modified (D19 requires a clean checkout)"
fi
if [ -f Dockerfile ]; then
  grep -q 'bullseye-security' Dockerfile && say "Dockerfile: Debian 11 workaround present (D18)" \
    || say "Dockerfile: no Debian 11 workaround line"
fi

# ---------------------------------------------------------------- 1. wait for web server and database
say "Waiting for the web server and the database (up to ${WAIT_SECS}s)..."
for ((i = 0; i < WAIT_SECS; i += 3)); do
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$BASE_URL/install/index.php" || true)
  # any 2xx/3xx answer means the web server is up (an installed Dolibarr redirects with 302)
  if [[ "$code" =~ ^[23] ]] && [ "$(sql 'SELECT 1' || true)" = 1 ]; then break; fi
  sleep 3
done
[[ "$code" =~ ^[23] ]] || fail "web server not ready ($BASE_URL/install/index.php → HTTP $code)"
[ "$(sql 'SELECT 1' || true)" = 1 ] || fail "database not reachable"
say "Web server and database are up"

# ---------------------------------------------------------------- 2. refuse to touch an installed Dolibarr
existing=$(sql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DB_NAME'")
if [ "${existing:-0}" -gt 0 ]; then
  fail "the database already has $existing tables (installed before). For a fresh start: $COMPOSE down -v, then up -d --build"
fi

# ---------------------------------------------------------------- 3. forced-install file
trap cleanup_forced EXIT
web "cat > '$HTDOCS/install/install.forced.php'" <<EOF
<?php
// Written by dolibarr_setup.sh; removed again when setup ends.
\$force_install_noedit = 2;
\$force_install_type = 'mysqli';
\$force_install_dbserver = '$DB_HOST';
\$force_install_port = $DB_PORT;
\$force_install_database = '$DB_NAME';
\$force_install_databaselogin = '$DB_USER';
\$force_install_databasepass = '$DB_PASS';
\$force_install_prefix = 'llx_';
\$force_install_createdatabase = false;
\$force_install_createuser = false;
\$force_install_mainforcehttps = false;
\$force_install_main_data_root = '$DATA_DIR';
\$force_install_dolibarrlogin = '$ADMIN_LOGIN';
\$force_install_module = 'modApi';
EOF
say "Forced-install settings written (database host '$DB_HOST'; installer enables modApi only)"

# ---------------------------------------------------------------- 4. installer pages (same forms as the browser)
get check
get fileconf
web "test -f '$HTDOCS/conf/conf.php'" || fail "check.php did not create conf/conf.php (see check.html)"
post step1 testpost=ok action=set selectlang="$SETUP_LANG" \
  main_dir="$HTDOCS" main_data_dir="$DATA_DIR" main_url="$MAIN_URL" \
  db_type=mysqli db_host="$DB_HOST" db_port="$DB_PORT" db_name="$DB_NAME" db_prefix=llx_ \
  db_user="$DB_USER" db_pass="$DB_PASS"
web "grep -q \"dolibarr_main_db_host='$DB_HOST'\" '$HTDOCS/conf/conf.php'" \
  || fail "step1 did not write the database settings into conf/conf.php (see step1.html)"
say "Step 1 done: configuration file written"

say "Step 2: creating tables and loading reference data (can take a few minutes)..."
post step2 testpost=ok action=set selectlang="$SETUP_LANG"
tables=$(sql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DB_NAME'")
[ "${tables:-0}" -gt 100 ] || fail "step2 created only ${tables:-0} tables (see step2.html)"
say "Step 2 done: $tables tables"

ADMIN_PASS=$(openssl rand -hex 12)
post step5 testpost=ok action=set selectlang="$SETUP_LANG" \
  login="$ADMIN_LOGIN" pass="$ADMIN_PASS" pass_verif="$ADMIN_PASS"
[ "$(sql "SELECT COUNT(*) FROM llx_user WHERE login='$ADMIN_LOGIN' AND admin=1")" = 1 ] \
  || fail "step5 did not create the admin user (see step5.html)"
[ "$(sql "SELECT COUNT(*) FROM llx_const WHERE name='MAIN_NOT_INSTALLED'")" = 0 ] \
  || fail "installation not marked complete (see step5.html)"
say "Step 5 done: admin user created"

cleanup_forced
trap - EXIT
say "Forced-install file removed"

# ---------------------------------------------------------------- 4b. enable the requested modules (R14)
# Same function the admin page uses (activateModule), with Dolibarr fully loaded, as the web server user,
# ONE PHP PROCESS PER MODULE in the listed order, like one click per module in the admin page (several
# modules in one process leave Dolibarr's in-memory settings stale and produce spurious dependency errors).
# The installer's own module list is not used for this: in step5.php some modules (e.g. modBom) crash the
# page (E36).
web "cat > '$TMP_IN_CONTAINER/activate_modules.php'" < "$SCRIPT_DIR/activate_modules.php"
: > "$LOG_DIR/activation.log"
IFS=',' read -ra req <<<"$MODULES"
for m in "${req[@]}"; do
  ${ACTIVATE_EXEC:-} php "$TMP_IN_CONTAINER/activate_modules.php" "$HTDOCS" "$m" "$ADMIN_LOGIN" \
    >> "$LOG_DIR/activation.log" 2> >(grep -v 'attribute .version. is obsolete' >&2) \
    || fail "could not run the module activation for $m (see $LOG_DIR/activation.log)"
done
web "rm -f '$TMP_IN_CONTAINER/activate_modules.php'"
say "Module activation: $(grep -c '^OK ' "$LOG_DIR/activation.log") OK, $(grep -c '^FAIL ' "$LOG_DIR/activation.log") reported errors (log: $LOG_DIR/activation.log)"

# Requested modules that did not end up enabled are recorded, not fatal (R14 condition 6); only the API
# module is required, because nothing else works without it.
IFS=',' read -ra mods <<<"$MODULES"
: > "$LOG_DIR/activation_failures.txt"
for m in "${mods[@]}"; do
  const="MAIN_MODULE_$(printf '%s' "${m#mod}" | tr '[:lower:]' '[:upper:]')"
  if [ "$(sql "SELECT value FROM llx_const WHERE name='$const' AND entity IN (0,1) LIMIT 1")" != 1 ]; then
    echo "$m ($const)" >> "$LOG_DIR/activation_failures.txt"
  fi
done
grep -q '^modApi ' "$LOG_DIR/activation_failures.txt" && fail "the API module (modApi) is not enabled"
# The configuration actually in force (requested modules plus any dependencies Dolibarr enabled), and its
# fingerprint, so that spec, buggy and patched deployments can be shown to be identical (R14 condition 5).
sql "SELECT name FROM llx_const WHERE name LIKE 'MAIN_MODULE\\_%' AND name NOT LIKE 'MAIN_MODULE\\_%\\_%' AND value='1' ORDER BY name" \
  > "$LOG_DIR/enabled_modules.txt"
fingerprint=$(sha256sum < "$LOG_DIR/enabled_modules.txt" | cut -c1-16)
nfail=$(wc -l < "$LOG_DIR/activation_failures.txt")
say "Modules: ${#mods[@]} requested, $nfail not enabled"
say "Enabled modules: $(wc -l < "$LOG_DIR/enabled_modules.txt") (list: $LOG_DIR/enabled_modules.txt, fingerprint $fingerprint)"
[ "$nfail" -eq 0 ] || say "Not enabled (recorded in $LOG_DIR/activation_failures.txt): $(tr '\n' ' ' < "$LOG_DIR/activation_failures.txt")"

# ---------------------------------------------------------------- 5. API key through Dolibarr's login API
login_json=$(python3 -c 'import json,sys; print(json.dumps({"login": sys.argv[1], "password": sys.argv[2]}))' \
             "$ADMIN_LOGIN" "$ADMIN_PASS")
resp=$(curl -sS --max-time 60 -X POST "$BASE_URL/api/index.php/login" \
       -H 'Content-Type: application/json' -H 'Accept: application/json' -d "$login_json") \
  || fail "login API not reachable"
API_KEY=$(printf '%s' "$resp" | python3 -c 'import json,sys
try: print(json.load(sys.stdin)["success"]["token"])
except Exception: pass')
[ -n "$API_KEY" ] || fail "login API returned no token (response not printed; it may contain secrets)"

( umask 077; printf 'export DOLIBARR_API_KEY=%s\nexport DOLIBARR_ADMIN_PASSWORD=%s\n' "$API_KEY" "$ADMIN_PASS" > "$CRED_FILE" )
say "API key obtained and saved to $CRED_FILE (not printed)"

# ---------------------------------------------------------------- 6. check the key works
status=$(curl -sS --max-time 30 -w '\n%{http_code}' "$BASE_URL/api/index.php/status" \
         -H "DOLAPIKEY: $API_KEY" -H 'Accept: application/json') || fail "status API not reachable"
code=$(printf '%s' "$status" | tail -1)
[ "$code" = 200 ] || fail "GET /api/index.php/status with the new key returned HTTP $code"
version=$(printf '%s' "$status" | sed '$d' | python3 -c 'import json,sys
try: print(json.load(sys.stdin)["success"]["dolibarr_version"])
except Exception: print("unknown")')
say "API check passed: Dolibarr $version answers with the new key"
# ---------------------------------------------------------------- 7. optional: save the product-generated spec (R14)
if [ -n "${SPEC_OUT:-}" ]; then
  code=$(curl -sS -o "$SPEC_OUT" -w '%{http_code}' --max-time 300 \
         "$BASE_URL/api/index.php/explorer/swagger.json" -H "DOLAPIKEY: $API_KEY") || fail "spec download failed"
  [ "$code" = 200 ] || fail "swagger.json returned HTTP $code"
  counts=$(python3 -c 'import json,sys
d=json.load(open(sys.argv[1])); p=d.get("paths") or {}
print(len(p), sum(1 for v in p.values() for m in v if m.lower() in ("get","post","put","patch","delete")))' "$SPEC_OUT")
  say "Spec saved to $SPEC_OUT: $(echo "$counts" | cut -d' ' -f1) paths, $(echo "$counts" | cut -d' ' -f2) operations, sha256 $(sha256sum < "$SPEC_OUT" | cut -c1-16)"
fi
say "DONE in $(( $(date +%s) - START ))s"
