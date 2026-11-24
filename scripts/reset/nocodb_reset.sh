#!/usr/bin/env bash
# nocodb_reset.sh — reset adapter for the nocodb deployment started by scripts/nocodb/nocodb_start.sh (T5, D28).
#
#   snapshot  stop nocodb, save its data volume (SQLite meta and data), start it again
#   restore   stop nocodb, replace the volume contents with the snapshot, start it, wait until it answers
#   check     stop nocodb, compare the SQLite file with the snapshot's, start it ("identical" / "differs").
#             Informational only: nocodb writes to its database at every start, so after a restart it differs.
#             Every restore verifies the restored file against the snapshot before nocodb starts.
#
# Snapshots go to ~/.restdd/snapshots/nocodb-<image tag> (private; they contain the user table).
set -euo pipefail

IMAGE="${RESTDD_NOCODB_IMAGE:-nocodb/nocodb:0.90.3}"
CONTAINER="${RESTDD_NOCODB_CONTAINER:-restdd-nocodb}"
VOLUME="${RESTDD_NOCODB_VOLUME:-restdd-nocodb-data}"
PORT="${RESTDD_NOCODB_PORT:-8090}"
SNAP_DIR="${RESTDD_NOCODB_SNAP_DIR:-$HOME/.restdd/snapshots/nocodb-${IMAGE##*:}}"

# a throw-away container of the same image, with the data volume and the snapshot folder mounted
helper() { docker run --rm --entrypoint sh -v "$VOLUME:/data" -v "$SNAP_DIR:/snap" "$IMAGE" -c "$1"; }
OWNER="$(id -u):$(id -g)"

wait_up() {
  for i in $(seq 1 180); do
    code=$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:$PORT/api/v1/db/meta/nocodb/version" || true)
    case "$code" in 200|401|403) echo "nocodb up after ${i} s"; return 0 ;; esac
    sleep 1
  done
  echo "nocodb did not come up within 180 s" >&2
  return 1
}

dbsum() { helper "sha256sum /data/noco.db 2>/dev/null | cut -c1-16 || echo none"; }

case "${1:-}" in
  snapshot)
    umask 077
    mkdir -p "$SNAP_DIR"
    chmod 700 "$HOME/.restdd" "$(dirname "$SNAP_DIR")" "$SNAP_DIR" 2>/dev/null || chmod 700 "$SNAP_DIR"
    docker stop "$CONTAINER" > /dev/null
    helper "tar -cf /snap/data.tar -C /data . && chmod 600 /snap/data.tar && chown $OWNER /snap/data.tar"
    dbsum > "$SNAP_DIR/db.sha256"
    docker start "$CONTAINER" > /dev/null
    wait_up
    echo "snapshot: $(wc -c < "$SNAP_DIR/data.tar") bytes; noco.db sha256 $(cat "$SNAP_DIR/db.sha256")"
    ;;
  restore)
    [ -s "$SNAP_DIR/data.tar" ] || { echo "no snapshot in $SNAP_DIR (run: $0 snapshot)" >&2; exit 2; }
    docker stop "$CONTAINER" > /dev/null
    helper "find /data -mindepth 1 -delete && tar -xf /snap/data.tar -C /data"
    # verify before starting: nocodb writes to its database at every start, so a later comparison proves nothing
    restored=$(dbsum)
    if [ "$restored" != "$(cat "$SNAP_DIR/db.sha256")" ]; then
      echo "restore check failed: noco.db is $restored, snapshot $(cat "$SNAP_DIR/db.sha256")" >&2
      exit 1
    fi
    docker start "$CONTAINER" > /dev/null
    wait_up
    echo "restored the data volume; noco.db identical to the snapshot before start ($restored)"
    ;;
  check)
    [ -s "$SNAP_DIR/db.sha256" ] || { echo "no snapshot in $SNAP_DIR" >&2; exit 2; }
    docker stop "$CONTAINER" > /dev/null
    now=$(dbsum)
    docker start "$CONTAINER" > /dev/null
    wait_up > /dev/null
    if [ "$now" = "$(cat "$SNAP_DIR/db.sha256")" ]; then echo "identical ($now)"; else echo "differs (now $now, snapshot $(cat "$SNAP_DIR/db.sha256"))"; exit 1; fi
    ;;
  *)
    echo "usage: $0 snapshot|restore|check" >&2
    exit 2
    ;;
esac
