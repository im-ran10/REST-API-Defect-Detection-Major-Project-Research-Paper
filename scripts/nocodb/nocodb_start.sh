#!/usr/bin/env bash
# nocodb_start.sh — start the nocodb reference version (0.90.3, E32) from its official image, with SQLite in a
# named volume. No benchmark command and no defect-specific setup hook runs (firewall). Day 2 of D25.
#
# Creates once, private (chmod 600): ~/.nocodb_credentials (admin e-mail and a random password, for
# nocodb_setup.py) and ~/.nocodb_env (a random JWT secret passed to the container, so tokens survive
# restarts and restores). Neither is ever printed.
set -euo pipefail

IMAGE="${IMAGE:-nocodb/nocodb:0.90.3}"
NAME="${NAME:-restdd-nocodb}"
VOLUME="${VOLUME:-restdd-nocodb-data}"
PORT="${PORT:-8090}"
CRED="${CRED:-$HOME/.nocodb_credentials}"
ENVF="${ENVF:-$HOME/.nocodb_env}"

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

if docker ps -a --format '{{.Names}}' | grep -qx "$NAME"; then
  docker start "$NAME" > /dev/null
  echo "started existing container $NAME"
else
  docker run -d --name "$NAME" -p "127.0.0.1:$PORT:8080" -v "$VOLUME:/usr/app/data" --env-file "$ENVF" "$IMAGE" > /dev/null
  echo "created container $NAME from $IMAGE (port $PORT, volume $VOLUME)"
fi

for i in $(seq 1 180); do
  code=$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:$PORT/api/v1/db/meta/nocodb/version" || true)
  case "$code" in
    200|401|403) echo "nocodb is up after ${i} s (version endpoint answered $code)"; exit 0 ;;
  esac
  sleep 1
done
echo "nocodb did not come up within 180 s; last 30 log lines:" >&2
docker logs --tail 30 "$NAME" >&2
exit 1
