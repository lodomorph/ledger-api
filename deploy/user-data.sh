#!/usr/bin/env bash
# =============================================================================
# EC2 User Data — Ledger API single-server bootstrap
# Tested on: Ubuntu 22.04 LTS / 24.04 LTS (x86_64 and arm64)
# Runs as root automatically when used as EC2 User Data.
#
# What this does:
#   1. Installs Java 17, PostgreSQL 15, Redis, Caddy
#   2. Configures PostgreSQL (creates DB + user)
#   3. Writes /etc/ledger-api.env (app environment)
#   4. Writes systemd units for ledger-api and caddy
#   5. Enables all services so they survive reboots
#   6. Adds a 512 MB swap file (safety net for small instances)
#
# After this runs, deploy the JAR once with deploy-app.sh.
# =============================================================================
set -euo pipefail

# ── CONFIGURATION (env vars take precedence, then these defaults) ─────────────
DB_NAME=ledger
DB_USER=ledger
DB_PASSWORD=${DB_PASSWORD:-ledger}
CADDY_DOMAIN=${CADDY_DOMAIN:-":80"}

# ── CONSTANTS ────────────────────────────────────────────────────────────────
CADDY_VERSION=2.9.1
APP_DIR=/opt/ledger-api
APP_PORT=8080

# ── SWAP (safety net for t4g.micro / t3.micro) ───────────────────────────────
if [ ! -f /swapfile ]; then
    fallocate -l 512M /swapfile
    chmod 600 /swapfile
    mkswap /swapfile
    swapon /swapfile
    echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

# ── PACKAGES ─────────────────────────────────────────────────────────────────
export DEBIAN_FRONTEND=noninteractive
apt-get update -y
apt-get install -y \
    openjdk-17-jre-headless \
    postgresql \
    postgresql-contrib \
    redis-server \
    curl \
    tar

# ── POSTGRESQL ───────────────────────────────────────────────────────────────
# Ubuntu auto-initializes PostgreSQL on install; just ensure it's running
systemctl enable --now postgresql

# Switch local connections to md5 auth so the app can connect with a password
PG_HBA=$(find /etc/postgresql -name pg_hba.conf 2>/dev/null | head -1)
# Replace peer → md5 for local socket, and ident → md5 for loopback
sed -i \
    -e 's/^\(local[[:space:]]\+all[[:space:]]\+all[[:space:]]\+\)peer/\1md5/' \
    -e 's/^\(host[[:space:]]\+all[[:space:]]\+all[[:space:]]\+127\.0\.0\.1\/32[[:space:]]\+\)ident/\1md5/' \
    -e 's/^\(host[[:space:]]\+all[[:space:]]\+all[[:space:]]\+::1\/128[[:space:]]\+\)ident/\1md5/' \
    "$PG_HBA"

systemctl reload postgresql

# Idempotent: create DB user and database only if missing
sudo -u postgres psql -tc "SELECT 1 FROM pg_roles WHERE rolname='${DB_USER}'" \
    | grep -q 1 \
    || sudo -u postgres psql -c "CREATE USER ${DB_USER} WITH PASSWORD '${DB_PASSWORD}';"

sudo -u postgres psql -tc "SELECT 1 FROM pg_database WHERE datname='${DB_NAME}'" \
    | grep -q 1 \
    || sudo -u postgres psql -c "CREATE DATABASE ${DB_NAME} OWNER ${DB_USER};"

# ── REDIS ────────────────────────────────────────────────────────────────────
# Bind to loopback only
sed -i 's/^bind .*/bind 127.0.0.1 -::1/' /etc/redis/redis.conf
systemctl enable --now redis-server

# ── CADDY ────────────────────────────────────────────────────────────────────
if ! command -v caddy &>/dev/null; then
    ARCH=$(uname -m)
    if [ "$ARCH" = "aarch64" ]; then CADDY_ARCH=arm64; else CADDY_ARCH=amd64; fi
    curl -fsSL \
        "https://github.com/caddyserver/caddy/releases/download/v${CADDY_VERSION}/caddy_${CADDY_VERSION}_linux_${CADDY_ARCH}.tar.gz" \
        | tar xz -C /usr/local/bin caddy
    chmod +x /usr/local/bin/caddy
fi

id caddy &>/dev/null \
    || useradd --system --create-home --home-dir /var/lib/caddy \
               --shell /usr/sbin/nologin caddy

mkdir -p /etc/caddy /var/log/caddy
chown caddy:caddy /var/log/caddy

cat > /etc/caddy/Caddyfile <<CADDY
# Replace ":80" with your domain for automatic Let's Encrypt HTTPS.
# Example:  api.example.com { ... }

${CADDY_DOMAIN} {
    reverse_proxy 127.0.0.1:${APP_PORT}

    log {
        output file /var/log/caddy/access.log
    }
}
CADDY

cat > /etc/systemd/system/caddy.service <<'UNIT'
[Unit]
Description=Caddy reverse proxy
Documentation=https://caddyserver.com/docs/
After=network-online.target
Wants=network-online.target

[Service]
Type=notify
User=caddy
Group=caddy
ExecStart=/usr/local/bin/caddy run --environ --config /etc/caddy/Caddyfile
ExecReload=/usr/local/bin/caddy reload --config /etc/caddy/Caddyfile --force
TimeoutStopSec=5s
LimitNOFILE=1048576
PrivateTmp=true
ProtectSystem=full
AmbientCapabilities=CAP_NET_BIND_SERVICE

[Install]
WantedBy=multi-user.target
UNIT

# ── LEDGER API ───────────────────────────────────────────────────────────────
id ledger &>/dev/null \
    || useradd --system --create-home --home-dir ${APP_DIR} \
               --shell /usr/sbin/nologin ledger

mkdir -p ${APP_DIR}
chown ledger:ledger ${APP_DIR}

# Secrets file — readable only by the ledger user
cat > /etc/ledger-api.env <<ENV
DB_HOST=127.0.0.1
DB_PORT=5432
DB_NAME=${DB_NAME}
DB_USER=${DB_USER}
DB_PASSWORD=${DB_PASSWORD}
REDIS_HOST=127.0.0.1
REDIS_PORT=6379
ENV
chmod 640 /etc/ledger-api.env
chown root:ledger /etc/ledger-api.env

# Placeholder so the unit file is valid before the real JAR lands
touch ${APP_DIR}/app.jar
chown ledger:ledger ${APP_DIR}/app.jar

cat > /etc/systemd/system/ledger-api.service <<'UNIT'
[Unit]
Description=Ledger API (Spring Boot)
After=network.target postgresql.service redis-server.service
Requires=postgresql.service redis-server.service

[Service]
User=ledger
Group=ledger
EnvironmentFile=/etc/ledger-api.env
WorkingDirectory=/opt/ledger-api
ExecStart=/usr/bin/java \
  -Xms64m -Xmx384m \
  -XX:MaxMetaspaceSize=128m \
  -Djava.security.egd=file:/dev/./urandom \
  -jar /opt/ledger-api/app.jar
Restart=on-failure
RestartSec=15
SuccessExitStatus=143
StandardOutput=journal
StandardError=journal
SyslogIdentifier=ledger-api

[Install]
WantedBy=multi-user.target
UNIT

# ── ENABLE EVERYTHING (survive reboots) ──────────────────────────────────────
systemctl daemon-reload
systemctl enable postgresql redis-server caddy ledger-api

systemctl start caddy
# ledger-api will be started by deploy-app.sh after the real JAR is copied

echo ""
echo "======================================================"
echo " Bootstrap complete."
echo " Next step: run deploy-app.sh from your workstation."
echo "======================================================"
