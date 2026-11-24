#!/usr/bin/env bash
# dolibarr_reset.sh — reset adapter for Dolibarr deployments (T5, D28).
#
#   snapshot  save the database and the documents folder of a freshly set-up deployment
#   restore   return the deployment to that snapshot (drop every table, reload the dump, restore documents)
#   check     compare the current database with the snapshot (prints "identical" or "differs")
#
# Run from the prepared Dolibarr folder (the one with docker-compose.yml and docker-compose.override.yml),
# or set "dir" in the target file. Take the snapshot once per deployment, right after dolibarr_setup.sh,
# before any test. Snapshots go to ~/.restdd/snapshots/dolibarr-<checked-out commit> ($SNAP_DIR), never into
# the repository or the Dolibarr clone.
# It contains the database, so it holds the admin user's (hashed) password and API key: keep it private.
#
# Every setting can be overridden with an environment variable of the same name (none clashes with WSL defaults).
set -euo pipefail

COMPOSE="${COMPOSE:-docker compose -f docker-compose.yml -f docker-compose.override.yml}"
DB_NAME="${DB_NAME:-dolibarr}"
DB_USER="${DB_USER:-dolibarr}"
DB_PASS="${DB_PASS:-dolibarr}"
DATA_PARENT="${DATA_PARENT:-/var/www}"
DATA_NAME="${DATA_NAME:-documents}"
# one snapshot per checked-out Dolibarr version, outside the Dolibarr clone (keeps `git status` clean, D19)
SNAP_DIR="${SNAP_DIR:-$HOME/.restdd/snapshots/dolibarr-$(git rev-parse --short=12 HEAD 2>/dev/null || echo unknown)}"

# docker compose prints a harmless warning about the benchmark's compose files on every call; hide only that line
quiet() { "$@" 2> >(grep -v 'attribute .version. is obsolete' >&2); }
db()    { quiet $COMPOSE exec -T db "$@"; }
web()   { quiet $COMPOSE exec -T dolibarr "$@"; }
dump()  { db mysqldump -u"$DB_USER" -p"$DB_PASS" --single-transaction --skip-dump-date --routines "$DB_NAME"; }

case "${1:-}" in
  snapshot)
    umask 077                                   # snapshot files readable by this user only
    mkdir -p "$SNAP_DIR"
    chmod 700 "$HOME/.restdd" "$(dirname "$SNAP_DIR")" "$SNAP_DIR" 2>/dev/null || chmod 700 "$SNAP_DIR"
    dump > "$SNAP_DIR/db.sql.tmp"
    mv "$SNAP_DIR/db.sql.tmp" "$SNAP_DIR/db.sql"
    web tar -C "$DATA_PARENT" -cf - "$DATA_NAME" > "$SNAP_DIR/documents.tar.tmp"
    mv "$SNAP_DIR/documents.tar.tmp" "$SNAP_DIR/documents.tar"
    sha256sum "$SNAP_DIR/db.sql" | cut -c1-16 > "$SNAP_DIR/db.sha256"
    echo "snapshot: $(wc -c < "$SNAP_DIR/db.sql") bytes of SQL, $(wc -c < "$SNAP_DIR/documents.tar") bytes of documents, db sha256 $(cat "$SNAP_DIR/db.sha256")"
    ;;
  restore)
    [ -s "$SNAP_DIR/db.sql" ] || { echo "no snapshot in $SNAP_DIR (run: $0 snapshot)" >&2; exit 2; }
    drops=$(db mysql -u"$DB_USER" -p"$DB_PASS" -N -e \
      "SELECT CONCAT('DROP TABLE IF EXISTS \`', table_name, '\`;') FROM information_schema.tables WHERE table_schema='$DB_NAME'")
    { echo "SET FOREIGN_KEY_CHECKS=0;"; echo "$drops"; echo "SET FOREIGN_KEY_CHECKS=1;"; } \
      | db mysql -u"$DB_USER" -p"$DB_PASS" "$DB_NAME"
    db mysql -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" < "$SNAP_DIR/db.sql"
    # empty the folder instead of removing it: it is usually a mounted volume, which cannot be deleted
    web sh -c "mkdir -p '$DATA_PARENT/$DATA_NAME' && find '$DATA_PARENT/$DATA_NAME' -mindepth 1 -delete"
    web tar -C "$DATA_PARENT" -xf - < "$SNAP_DIR/documents.tar"
    echo "restored: $(echo "$drops" | grep -c DROP) tables dropped and reloaded; documents restored"
    ;;
  check)
    [ -s "$SNAP_DIR/db.sql" ] || { echo "no snapshot in $SNAP_DIR" >&2; exit 2; }
    now=$(dump | sha256sum | cut -c1-16)
    if [ "$now" = "$(cat "$SNAP_DIR/db.sha256")" ]; then echo "identical ($now)"; else echo "differs (now $now, snapshot $(cat "$SNAP_DIR/db.sha256"))"; exit 1; fi
    ;;
  *)
    echo "usage: $0 snapshot|restore|check" >&2
    exit 2
    ;;
esac
