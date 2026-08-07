#!/usr/bin/env bash
# Start Neo4j Community as a native process (no Docker).
# Used when nested Cloud Agent VMs cannot run Docker containers
# (missing CAP_NET_ADMIN / /dev/fuse).
set -euo pipefail

NEO_VERSION="${NEO4J_VERSION_NATIVE:-2025.10.1}"
NEO_HOME="${NEO4J_HOME_NATIVE:-/opt/neo4j}"
NEO_PASSWORD="${NEO4J_PASSWORD:-brahmsian}"
NEO_USER="${NEO4J_USERNAME:-neo4j}"

install_neo4j() {
  if [[ -x "${NEO_HOME}/bin/neo4j" ]]; then
    return 0
  fi
  echo "Installing Neo4j Community ${NEO_VERSION} to ${NEO_HOME}..."
  local tmp
  tmp="$(mktemp -d)"
  curl -fsSL -o "${tmp}/neo4j.tar.gz" \
    "https://dist.neo4j.org/neo4j-community-${NEO_VERSION}-unix.tar.gz"
  sudo mkdir -p "$(dirname "${NEO_HOME}")"
  sudo rm -rf "${NEO_HOME}"
  sudo tar -xzf "${tmp}/neo4j.tar.gz" -C "$(dirname "${NEO_HOME}")"
  sudo mv "$(dirname "${NEO_HOME}")/neo4j-community-${NEO_VERSION}" "${NEO_HOME}"
  sudo chown -R "$(id -u):$(id -g)" "${NEO_HOME}"
  rm -rf "${tmp}"
}

configure_neo4j() {
  cat > "${NEO_HOME}/conf/neo4j.conf" <<EOF
server.default_listen_address=0.0.0.0
server.bolt.listen_address=:7687
server.http.listen_address=:7474
server.directories.data=data
server.directories.logs=logs
server.memory.heap.initial_size=512m
server.memory.heap.max_size=1G
dbms.security.auth_enabled=true
EOF
  # Only effective before first start of a fresh data dir.
  "${NEO_HOME}/bin/neo4j-admin" dbms set-initial-password "${NEO_PASSWORD}" 2>/dev/null || true
}

wait_healthy() {
  local i
  for i in $(seq 1 60); do
    if "${NEO_HOME}/bin/cypher-shell" -u "${NEO_USER}" -p "${NEO_PASSWORD}" \
      'RETURN 1;' >/dev/null 2>&1; then
      echo "Native Neo4j healthy (bolt://localhost:7687)."
      return 0
    fi
    sleep 2
  done
  echo "WARN: native Neo4j did not become healthy in time." >&2
  return 1
}

install_neo4j
configure_neo4j

if "${NEO_HOME}/bin/neo4j" status >/dev/null 2>&1; then
  echo "Native Neo4j already running."
else
  "${NEO_HOME}/bin/neo4j" start
fi
wait_healthy
