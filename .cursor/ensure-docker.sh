#!/usr/bin/env bash
# Idempotently ensure the Docker daemon is running in the Cloud Agent VM.
#
# The Cloud Agent VM is a nested container with no systemd (PID 1 is tini),
# so `systemctl start docker` does not work. We launch dockerd directly with
# the fuse-overlayfs storage driver, which works over the VM's overlay rootfs.
#
# install.sh / start.sh drive the daemon with `sudo docker ...` (passwordless
# sudo is available), so this script only needs the daemon reachable via sudo.
# As a convenience we also best-effort relax the socket so tools that run as
# $USER (e.g. Testcontainers via ./mvnw test) can reach it without sudo.
set -euo pipefail

start_dockerd_and_wait() {
  sudo mkdir -p /etc/docker
  if [ ! -f /etc/docker/daemon.json ]; then
    echo '{ "storage-driver": "fuse-overlayfs" }' | sudo tee /etc/docker/daemon.json >/dev/null
  fi
  echo "Starting dockerd..."
  sudo nohup dockerd >/tmp/dockerd.log 2>&1 &
  for _ in $(seq 1 30); do
    if sudo docker info >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done
  return 1
}

if ! sudo docker info >/dev/null 2>&1; then
  if ! start_dockerd_and_wait; then
    echo "ERROR: dockerd failed to start. Recent log:" >&2
    tail -30 /tmp/dockerd.log >&2 || true
    exit 1
  fi
fi

# Best-effort: let $USER use the socket without sudo (for Testcontainers).
# dockerd creates it root:docker 0660; make the runtime dir traversable and
# the socket world-usable. Also add $USER to the docker group for good measure.
# All non-fatal: install.sh / start.sh use sudo regardless.
sudo usermod -aG docker "$USER" 2>/dev/null || true
sudo chmod a+rx /run /var/run 2>/dev/null || true
sudo chmod 666 /var/run/docker.sock 2>/dev/null || true

echo "Docker is ready (server reachable via sudo)."
if docker info >/dev/null 2>&1; then
  echo "Docker socket is usable by $USER without sudo."
else
  echo "NOTE: $USER should use the docker group (fresh login) or sudo for Docker."
fi
