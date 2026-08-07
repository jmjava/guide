#!/usr/bin/env bash
# One-time, idempotent bootstrap for the Embabel Guide Cloud Agent environment.
#
# Runs after the repository is checked out. Prepares durable, source-derived
# state that is captured in the environment snapshot:
#   * Docker (Testcontainers-backed tests + local Neo4j via compose)
#   * Warmed Maven / Gradle caches and the Drivine KSP-generated sources
#   * The Neo4j image pre-pulled so `start` is fast on every boot
#
# Per-boot runtime (starting dockerd, Neo4j, the app) lives in start.sh /
# environment.json terminals, not here.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

# 1. System packages: Docker + nested-container dependencies (idempotent).
if ! command -v docker >/dev/null 2>&1; then
  echo "Installing Docker and dependencies..."
  sudo apt-get update -qq
  sudo DEBIAN_FRONTEND=noninteractive apt-get install -y -qq \
    docker.io docker-compose-v2 fuse-overlayfs iptables uidmap
fi
sudo usermod -aG docker "$USER" 2>/dev/null || true

# 2. Bring the Docker daemon up so we can warm image + build caches.
bash "$REPO_ROOT/.cursor/ensure-docker.sh"

# 3. Warm Maven + Gradle caches: run the Drivine KSP codegen and compile main
#    and test sources. -U refreshes SNAPSHOT dependencies, matching CI.
export JAVA_HOME="${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")}"
echo "Using JAVA_HOME=$JAVA_HOME"
./mvnw -B -U compile test-compile

# 4. Pre-pull the Neo4j image referenced by compose.yaml (best effort).
docker compose --profile neo4j pull neo4j \
  || docker pull neo4j:2025.10.1-community-bullseye \
  || echo "WARN: could not pre-pull Neo4j image; start.sh will pull on demand."

echo "install.sh complete."
