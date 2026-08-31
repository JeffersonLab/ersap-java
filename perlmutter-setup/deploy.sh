#!/usr/bin/env bash
# Run this once on the Perlmutter login node to install all config files.
# It does NOT install Prometheus or Grafana binaries — do that separately.
set -euo pipefail

REPO_DIR="$(cd "$(dirname "$0")" && pwd)"

# Create directory structure
mkdir -p "$HOME/prometheus/data"
mkdir -p "$HOME/grafana/"{conf,data,logs,plugins,dashboards}
mkdir -p "$HOME/grafana/provisioning/"{datasources,dashboards}

# Copy configs
cp "$REPO_DIR/prometheus/ersap.yml"                                  "$HOME/prometheus/ersap.yml"
cp "$REPO_DIR/grafana/conf/custom.ini"                               "$HOME/grafana/conf/custom.ini"
cp "$REPO_DIR/grafana/provisioning/datasources/datasource.yml"       "$HOME/grafana/provisioning/datasources/datasource.yml"
cp "$REPO_DIR/grafana/provisioning/dashboards/dashboards.yml"        "$HOME/grafana/provisioning/dashboards/dashboards.yml"
cp "$REPO_DIR/grafana/dashboards/ersap-overview.json"                "$HOME/grafana/dashboards/ersap-overview.json"

echo "Config files deployed to \$HOME."
echo "Next: install Prometheus and Grafana binaries if not already done."
