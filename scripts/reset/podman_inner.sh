# podman_inner.sh — runs as root inside the Podman distribution; called by podman_reset.sh through wsl.exe.
# Arguments: snapshot|restore|check FIXTURE_IMAGE
set -euo pipefail
ACTION="$1"
IMAGE="$2"
SNAP=/root/restdd-snapshot

counts() {
  echo "pods=$(podman pod ps -q | wc -l) containers=$(podman ps -aq | wc -l) volumes=$(podman volume ls -q | wc -l)" \
       "images=$(podman images -q | wc -l) secrets=$(podman secret ls -q 2>/dev/null | wc -l)"
}

case "$ACTION" in
  snapshot)
    # Podman built from source (benchmark install) ships without containers-common's default config; without a
    # trust policy it can neither pull nor load images. Generic defaults, created only if missing, same for every
    # version and arm (recorded in RESEARCH_LOG).
    mkdir -p /etc/containers
    if [ ! -f /etc/containers/policy.json ]; then
      echo '{"default":[{"type":"insecureAcceptAnything"}]}' > /etc/containers/policy.json
      echo "created default /etc/containers/policy.json (was missing)"
    fi
    if [ ! -f /etc/containers/registries.conf ]; then
      echo 'unqualified-search-registries = ["docker.io"]' > /etc/containers/registries.conf
      echo "created default /etc/containers/registries.conf (was missing)"
    fi
    umask 077
    mkdir -p "$SNAP"
    podman image exists "$IMAGE" || podman pull -q "$IMAGE" > /dev/null
    podman save -q -o "$SNAP/fixture.tar" "$IMAGE"
    podman image inspect --format '{{.Id}}' "$IMAGE" > "$SNAP/fixture.id"
    echo "snapshot: fixture image $IMAGE ($(cut -c1-12 "$SNAP/fixture.id")), $(wc -c < "$SNAP/fixture.tar") bytes saved"
    ;;
  restore)
    [ -s "$SNAP/fixture.tar" ] || { echo "no snapshot (run: podman_reset.sh snapshot)" >&2; exit 2; }
    podman pod rm -a -f > /dev/null 2>&1 || true
    podman stop -a -t 0 > /dev/null 2>&1 || true
    podman rm -a -f > /dev/null 2>&1 || true
    podman volume rm -a -f > /dev/null 2>&1 || true
    podman secret ls -q 2>/dev/null | xargs -r podman secret rm > /dev/null 2>&1 || true
    podman network prune -f > /dev/null 2>&1 || true
    podman rmi -a -f > /dev/null 2>&1 || true
    podman load -q -i "$SNAP/fixture.tar" > /dev/null
    id=$(podman image inspect --format '{{.Id}}' "$IMAGE")
    if [ "$id" != "$(cat "$SNAP/fixture.id")" ]; then
      echo "restore check failed: fixture image id $id differs from the snapshot" >&2
      exit 1
    fi
    echo "restored: $(counts)"
    ;;
  check)
    echo "now: $(counts)"
    if podman image exists "$IMAGE" && [ "$(podman image inspect --format '{{.Id}}' "$IMAGE")" = "$(cat "$SNAP/fixture.id" 2>/dev/null)" ]; then
      echo "fixture image present and identical to the snapshot"
    else
      echo "fixture image missing or different"
      exit 1
    fi
    ;;
  *)
    echo "usage: snapshot|restore|check" >&2
    exit 2
    ;;
esac
