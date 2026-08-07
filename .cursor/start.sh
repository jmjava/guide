#!/usr/bin/env bash
# Per-boot startup for the Embabel Guide Cloud Agent environment.
#
# Reconciles the infrastructure the app depends on and then returns:
#   * dockerd (no systemd in the VM)
#   * the Neo4j graph database (default `neo4j` Spring profile -> bolt://localhost:7687)
#
# The Guide Spring Boot app itself runs as a visible `terminals` entry
# (see .cursor/environment.json) so its logs stay inspectable. Daemon-side
# docker calls use sudo (the docker group is not active in this process).
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

# 1. Ensure the Docker daemon is up (idempotent).
bash "$REPO_ROOT/.cursor/ensure-docker.sh"

# 2. Start Neo4j (idempotent: compose reuses the existing container).
echo "Starting Neo4j..."
sudo docker compose up neo4j -d

# 3. Wait for Neo4j to report healthy so the app can connect on boot.
status="unknown"
for _ in $(seq 1 60); do
  status="$(sudo docker inspect --format '{{.State.Health.Status}}' embabel-neo4j 2>/dev/null || echo none)"
  if [ "$status" = "healthy" ]; then
    break
  fi
  sleep 3
done
echo "Neo4j health: $status"

if [ "$status" != "healthy" ]; then
  echo "WARN: Neo4j did not become healthy in time; check 'sudo docker logs embabel-neo4j'." >&2
fi

echo "start.sh complete."
