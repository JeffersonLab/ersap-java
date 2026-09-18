#!/usr/bin/env bash
# =============================================================================
# deploy.sh — install ERSAP monitoring config files into $HOME
#
# Run once on the Perlmutter login node before submitting any Slurm job, and
# re-run any time you edit a file under perlmutter-setup/.
#
# What it copies:
#   perlmutter-setup/prometheus/ersap.yml          -> $HOME/prometheus/ersap.yml
#   perlmutter-setup/grafana/conf/custom.ini        -> $HOME/grafana/conf/custom.ini
#   perlmutter-setup/grafana/provisioning/...       -> $HOME/grafana/provisioning/...
#   perlmutter-setup/grafana/dashboards/...         -> $HOME/grafana/dashboards/...
#
# Files containing the placeholder __HOME__ have it replaced with the real
# $HOME path at copy time, so the configs work for any user account.
#
# This script does NOT install Prometheus or Grafana binaries.
# See HOWTO-perlmutter.md § 0 for the one-time binary installation steps.
# =============================================================================

set -euo pipefail

REPO_DIR="$(cd "$(dirname "$0")" && pwd)"

# -----------------------------------------------------------------------------
# Helpers
# -----------------------------------------------------------------------------
log()  { printf '  %s\n' "$*"; }
ok()   { printf '  \033[32m✓\033[0m  %s\n' "$*"; }
warn() { printf '  \033[33m!\033[0m  %s\n' "$*"; }
fail() { printf '  \033[31m✗\033[0m  %s\n' "$*" >&2; }

# copy_file <src> <dst>
# Copies src to dst, substituting __HOME__ → $HOME in the content.
# Prints whether the file was created, updated, or unchanged.
copy_file() {
    local src="$1" dst="$2"
    local dst_dir
    dst_dir="$(dirname "$dst")"
    mkdir -p "$dst_dir"

    # Substitute __HOME__ placeholder, write to a temp file for comparison.
    local tmp
    tmp="$(mktemp)"
    sed "s|__HOME__|${HOME}|g" "$src" > "$tmp"

    if [[ ! -f "$dst" ]]; then
        cp "$tmp" "$dst"
        ok "created  $dst"
    elif diff -q "$tmp" "$dst" >/dev/null 2>&1; then
        log "unchanged $dst"
    else
        cp "$tmp" "$dst"
        ok "updated  $dst"
    fi
    rm -f "$tmp"
}

# -----------------------------------------------------------------------------
# Pre-flight: verify source files exist
# -----------------------------------------------------------------------------
printf '\ndeploying ERSAP monitoring configs to %s\n\n' "$HOME"

missing=0
for src in \
    "$REPO_DIR/prometheus/ersap.yml" \
    "$REPO_DIR/grafana/conf/custom.ini" \
    "$REPO_DIR/grafana/provisioning/datasources/datasource.yml" \
    "$REPO_DIR/grafana/provisioning/dashboards/dashboards.yml" \
    "$REPO_DIR/grafana/dashboards/ersap-overview.json"
do
    if [[ ! -f "$src" ]]; then
        fail "missing source file: $src"
        missing=1
    fi
done
if (( missing )); then
    printf '\naborting: source files missing from %s\n' "$REPO_DIR" >&2
    exit 1
fi

# -----------------------------------------------------------------------------
# Create directory structure
# -----------------------------------------------------------------------------
mkdir -p \
    "$HOME/prometheus/data" \
    "$HOME/grafana/conf" \
    "$HOME/grafana/data" \
    "$HOME/grafana/logs" \
    "$HOME/grafana/plugins" \
    "$HOME/grafana/dashboards" \
    "$HOME/grafana/provisioning/datasources" \
    "$HOME/grafana/provisioning/dashboards"

# -----------------------------------------------------------------------------
# Copy files
# -----------------------------------------------------------------------------
printf 'Prometheus\n'
copy_file "$REPO_DIR/prometheus/ersap.yml" \
          "$HOME/prometheus/ersap.yml"

printf '\nGrafana\n'
copy_file "$REPO_DIR/grafana/conf/custom.ini" \
          "$HOME/grafana/conf/custom.ini"
copy_file "$REPO_DIR/grafana/provisioning/datasources/datasource.yml" \
          "$HOME/grafana/provisioning/datasources/datasource.yml"
copy_file "$REPO_DIR/grafana/provisioning/dashboards/dashboards.yml" \
          "$HOME/grafana/provisioning/dashboards/dashboards.yml"
copy_file "$REPO_DIR/grafana/dashboards/ersap-overview.json" \
          "$HOME/grafana/dashboards/ersap-overview.json"

# -----------------------------------------------------------------------------
# Binary checks
# -----------------------------------------------------------------------------
printf '\nbinary check\n'
for bin in "$HOME/prometheus/prometheus" "$HOME/grafana/bin/grafana-server"; do
    if [[ -x "$bin" ]]; then
        log "found    $bin"
    else
        warn "missing  $bin  (see HOWTO-perlmutter.md § 0 for install steps)"
    fi
done

# -----------------------------------------------------------------------------
# Hot-reload Prometheus if it is already running
# -----------------------------------------------------------------------------
printf '\n'
PROM_PORT="${PROM_PORT:-9090}"
if curl -sf --max-time 2 "http://localhost:${PROM_PORT}/-/ready" >/dev/null 2>&1; then
    printf 'Prometheus is running — sending hot-reload\n'
    if curl -sf --max-time 5 -X POST "http://localhost:${PROM_PORT}/-/reload" >/dev/null 2>&1; then
        ok "reloaded  http://localhost:${PROM_PORT}"
    else
        warn "reload request failed — check http://localhost:${PROM_PORT}/-/ready"
    fi
else
    log "Prometheus not running — config will be loaded on next job start"
fi

# -----------------------------------------------------------------------------
# Done
# -----------------------------------------------------------------------------
printf '\ndone.\n\n'
printf 'Next steps:\n'
printf '  sbatch slurm/monitor-longrun.slurm    # production\n'
printf '  sbatch slurm/monitor.slurm            # ad hoc / debug\n'
printf '\nTo add an external scrape target, edit:\n'
printf '  %s/prometheus/ersap.yml\n' "$REPO_DIR"
printf 'then re-run this script.\n\n'
