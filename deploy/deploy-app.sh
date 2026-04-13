#!/usr/bin/env bash
# =============================================================================
# deploy-app.sh — Full deploy: bootstrap server (first run) + build + ship JAR
#
# Usage:
#   ./deploy/deploy-app.sh ubuntu@<EC2-IP>
#
# Environment variables (all optional):
#   SSH_KEY       Path to .pem file   (e.g. ~/Downloads/key.pem)
#   DB_PASSWORD   Database password   (default: ledger)
#   CADDY_DOMAIN  Domain for Caddy    (default: ":80" — plain HTTP)
#                 Set to your domain for automatic HTTPS, e.g. "api.example.com"
#
# Examples:
#   SSH_KEY=~/Downloads/key.pem ./deploy/deploy-app.sh ubuntu@1.2.3.4
#
#   SSH_KEY=~/Downloads/key.pem \
#   DB_PASSWORD=secret \
#   CADDY_DOMAIN=api.example.com \
#   ./deploy/deploy-app.sh ubuntu@1.2.3.4
# =============================================================================
set -euo pipefail

SSH_TARGET=${1:?Usage: $0 ubuntu@<EC2-IP>}
SSH_OPTS=(-o StrictHostKeyChecking=accept-new -o ServerAliveInterval=10 -o ServerAliveCountMax=6)
if [ -n "${SSH_KEY:-}" ]; then
    SSH_OPTS+=(-i "$SSH_KEY")
fi

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
PROJECT_DIR=$(cd "${SCRIPT_DIR}/.." && pwd)
APP_DIR=/opt/ledger-api

# ── STEP 1: BOOTSTRAP (skipped if already done) ──────────────────────────────
echo "==> Checking if server needs bootstrapping..."
BOOTSTRAPPED=$(ssh "${SSH_OPTS[@]}" "${SSH_TARGET}" \
    'test -d /opt/ledger-api && systemctl is-enabled ledger-api &>/dev/null && echo yes || echo missing')

if [ "${BOOTSTRAPPED:-missing}" != "yes" ]; then
    echo "==> Server not bootstrapped — running setup (this takes ~5 minutes)..."

    scp "${SSH_OPTS[@]}" "${SCRIPT_DIR}/user-data.sh" "${SSH_TARGET}:/tmp/bootstrap.sh"

    ssh "${SSH_OPTS[@]}" "${SSH_TARGET}" \
        "chmod +x /tmp/bootstrap.sh && sudo DB_PASSWORD='${DB_PASSWORD:-ledger}' CADDY_DOMAIN='${CADDY_DOMAIN:-:80}' bash /tmp/bootstrap.sh"

    echo "==> Bootstrap complete."
else
    echo "==> Server already bootstrapped, skipping."
fi

# ── STEP 2: BUILD ─────────────────────────────────────────────────────────────
echo "==> Building fat JAR (skipping tests)..."
cd "${PROJECT_DIR}"
mvn -q clean package -DskipTests

JAR=$(ls target/ledger-api-*.jar | grep -v '\.original$' | head -1)
echo "==> Built: ${JAR}"

# ── STEP 3: UPLOAD + INSTALL ──────────────────────────────────────────────────
echo "==> Uploading to ${SSH_TARGET}:/tmp/app.jar (gzip pipe)..."
gzip -c "${JAR}" | ssh "${SSH_OPTS[@]}" "${SSH_TARGET}" "gunzip > /tmp/app.jar"

echo "==> Installing JAR and restarting ledger-api..."
ssh "${SSH_OPTS[@]}" "${SSH_TARGET}" \
    "sudo mv /tmp/app.jar ${APP_DIR}/app.jar && sudo chown ledger:ledger ${APP_DIR}/app.jar && sudo systemctl restart ledger-api"

# ── STEP 4: HEALTH CHECK ──────────────────────────────────────────────────────
echo "==> Waiting for health check (up to 60s)..."
for i in $(seq 1 12); do
    sleep 5
    STATUS=$(ssh "${SSH_OPTS[@]}" "${SSH_TARGET}" \
        "curl -sf http://127.0.0.1:8080/actuator/health | grep -o '\"status\":\"[A-Z]*\"' || true")
    echo "    [${i}/12] ${STATUS:-no response yet}"
    if echo "${STATUS}" | grep -q '"UP"'; then
        echo ""
        echo "==> Deployment successful. App is UP."
        exit 0
    fi
done

echo ""
echo "==> Health check did not return UP within 60s."
echo "    Check logs: ssh ${SSH_TARGET} 'sudo journalctl -u ledger-api -n 50'"
exit 1
