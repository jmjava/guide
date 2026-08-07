#!/usr/bin/env bash
# Idempotently ensure the Docker daemon is running in the Cloud Agent VM.
#
# The Cloud Agent VM is a nested container with no systemd (PID 1 is tini),
# so `systemctl start docker` does not work. We launch dockerd directly with
# the fuse-overlayfs storage driver, which works over the VM's overlay rootfs.
set -euo pipefail

if sudo docker info >/dev/null 2>&1; then
  sudo chmod 666 /var/run/docker.sock 2>/dev/null || true
  exit 0
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

# Let the daemon socket be used without sudo inside this dev VM.
sudo chmod 666 /var/run/docker.sock 2>/dev/null || true

if ! sudo docker info >/dev/null 2>&1; then
  echo "ERROR: dockerd failed to start. Recent log:" >&2
  tail -30 /tmp/dockerd.log >&2 || true
  exit 1
fi

echo "Docker is ready."
