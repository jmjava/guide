#!/usr/bin/env bash
# Idempotently ensure the Docker daemon is running and usable by $USER in the
# Cloud Agent VM.
#
# The Cloud Agent VM is a nested container with no systemd (PID 1 is tini),
# so `systemctl start docker` does not work. We launch dockerd directly with
# the fuse-overlayfs storage driver, which works over the VM's overlay rootfs.
set -euo pipefail

# dockerd creates /var/run/docker.sock as root:docker (0660) and may reset the
# mode while it finishes initializing, so a single chmod can race and be
# reverted. Loop: verify a non-sudo client call works, re-chmod otherwise.
ensure_socket_usable() {
  for _ in $(seq 1 20); do
    if docker info >/dev/null 2>&1; then
      return 0
    fi
    sudo chmod 666 /var/run/docker.sock 2>/dev/null || true
    sleep 1
  done
  docker info >/dev/null 2>&1
}

if sudo docker info >/dev/null 2>&1; then
  if ensure_socket_usable; then
    echo "Docker already running."
    exit 0
  fi
fi

sudo mkdir -p /etc/docker
if [ ! -f /etc/docker/daemon.json ]; then
  echo '{ "storage-driver": "fuse-overlayfs" }' | sudo tee /etc/docker/daemon.json >/dev/null
fi

echo "Starting dockerd..."
sudo nohup dockerd >/tmp/dockerd.log 2>&1 &

for _ in $(seq 1 30); do
  if sudo docker info >/dev/null 2>&1; then
    break
  fi
  sleep 2
done

if ! sudo docker info >/dev/null 2>&1; then
  echo "ERROR: dockerd failed to start. Recent log:" >&2
  tail -30 /tmp/dockerd.log >&2 || true
  exit 1
fi

if ! ensure_socket_usable; then
  echo "ERROR: docker socket is not usable by $USER without sudo." >&2
  ls -la /var/run/docker.sock >&2 || true
  exit 1
fi

echo "Docker is ready."
