#!/usr/bin/env bash
# podman_reset.sh — reset adapter for the Podman reference deployment (T5, D28, R21).
# Runs from the main Ubuntu; every Podman command runs as root inside the isolated distribution
# ($RESTDD_PODMAN_DISTRO, default Ubuntu-24.04) through wsl.exe, using podman_inner.sh.
#
#   snapshot  create Podman's two standard config files if the source build left them out (image trust
#             policy, registry list; generic, same for every version), pull the generic fixture image once
#             (needs network) and save it inside the Podman distribution
#   restore   remove every pod, container, volume, secret, network (except the default) and image,
#             reload the fixture image from the saved file, wait until the API answers /_ping
#   check     print what exists now (counts) and whether the fixture image is the snapshot's
#
# The Podman API service must already run (separate tab in that distribution):
#   sudo podman system service -t 0 tcp:127.0.0.1:8082
# Quit Docker Desktop while Podman containers exist (R21: shared WSL network stack).
set -euo pipefail

DISTRO="${RESTDD_PODMAN_DISTRO:-Ubuntu-24.04}"
FIXTURE_IMAGE="${RESTDD_PODMAN_FIXTURE_IMAGE:-docker.io/library/alpine:3.19}"
API="${RESTDD_PODMAN_API:-http://127.0.0.1:8082}"
INNER="$(dirname "$(readlink -f "$0")")/podman_inner.sh"

[ -n "${1:-}" ] || { echo "usage: $0 snapshot|restore|check" >&2; exit 2; }
# --cd /: otherwise wsl.exe tries (and fails) to open the current main-Ubuntu folder in the other distribution
wsl.exe -d "$DISTRO" --cd / -u root -- bash -s -- "$1" "$FIXTURE_IMAGE" < "$INNER"

if [ "$1" = "restore" ] || [ "$1" = "snapshot" ]; then
  for i in $(seq 1 60); do
    if [ "$(curl -s "$API/_ping" || true)" = "OK" ]; then echo "Podman API answers /_ping after ${i} s"; exit 0; fi
    sleep 1
  done
  echo "Podman API at $API does not answer (is 'podman system service' running in $DISTRO?)" >&2
  exit 1
fi
