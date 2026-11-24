#!/usr/bin/env bash
# nocodb_start.sh — start the nocodb reference version (0.90.3, E32) from its official image, with SQLite in a
# named volume. No benchmark command and no defect-specific setup hook runs (firewall). Day 2 of D25.
#
# Creates once, private (chmod 600): ~/.nocodb_credentials (admin e-mail and a random password, for
# nocodb_setup.py) and ~/.nocodb_env (a random JWT secret passed to the container, so tokens survive
# restarts and restores). Neither is ever printed.
set -euo pipefail

IMAGE="${RESTDD_NOCODB_IMAGE:-nocodb/nocodb:0.90.3}"
CONTAINER="${RESTDD_NOCODB_CONTAINER:-restdd-nocodb}"
VOLUME="${RESTDD_NOCODB_VOLUME:-restdd-nocodb-data}"
PORT="${RESTDD_NOCODB_PORT:-8090}"
CRED="${RESTDD_NOCODB_CRED:-$HOME/.nocodb_credentials}"
ENVF="${RESTDD_NOCODB_ENVF:-$HOME/.nocodb_env}"

umask 077
if [ ! -f "$CRED" ]; then
  { echo "export NOCODB_EMAIL='restdd-admin@example.com'"
    echo "export NOCODB_PASSWORD='$(openssl rand -hex 16)'"; } > "$CRED"
  echo "created $CRED (private, not printed)"
fi
if [ ! -f "$ENVF" ]; then
  { echo "NC_AUTH_JWT_SECRET=$(openssl rand -hex 32)"
    echo "NC_DISABLE_TELE=true"; } > "$ENVF"
  echo "created $ENVF (private, not printed)"
fi

if docker ps -a --format '{{.Names}}' | grep -qx "$CONTAINER"; then
  docker start "$CONTAINER" > /dev/null
  echo "started existing container $CONTAINER"
else
  docker run -d --name "$CONTAINER" -p "127.0.0.1:$PORT:8080" -v "$VOLUME:/usr/app/data" --env-file "$ENVF" "$IMAGE" > /dev/null
  echo "created container $CONTAINER from $IMAGE (port $PORT, volume $VOLUME)"
fi

for i in $(seq 1 180); do
  code=$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:$PORT/api/v1/db/meta/nocodb/version" || true)
  case "$code" in
    200|401|403) echo "nocodb is up after ${i} s (version endpoint answered $code)"; exit 0 ;;
  esac
  sleep 1
done
echo "nocodb did not come up within 180 s; last 30 log lines:" >&2
docker logs --tail 30 "$CONTAINER" >&2
exit 1
