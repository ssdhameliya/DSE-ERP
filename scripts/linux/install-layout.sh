#!/usr/bin/env bash
set -euo pipefail

if ! id dseerp >/dev/null 2>&1; then
  sudo useradd --system --home /opt/dse-erp --shell /usr/sbin/nologin dseerp
fi

sudo install -d -o dseerp -g dseerp \
  /opt/dse-erp/uat/releases /opt/dse-erp/prod/releases \
  /srv/dse-erp/uat /srv/dse-erp/prod \
  /srv/dse-erp/uat/Backups/PreUpgrade /srv/dse-erp/prod/Backups/PreUpgrade \
  /etc/dse-erp
sudo install -m 0644 "$(dirname "$0")/dse-erp@.service" /etc/systemd/system/dse-erp@.service
sudo systemctl daemon-reload

echo 'Layout ready.'
echo 'Next: copy uat.env/prod.env to /etc/dse-erp/{uat,prod}.env, chmod 600, and replace CHANGE_ME.'
