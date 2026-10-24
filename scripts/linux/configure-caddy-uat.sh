#!/usr/bin/env bash
set -euo pipefail

# ==============================================================================
# DSE ERP — Safe UAT Caddy Configuration for Pure Java Vaadin Web UI
# Targets: erp-uat.jasviindustries.in -> 127.0.0.1:8081
# ==============================================================================

CONFIG="/etc/caddy/Caddyfile"
BACKUP="/etc/caddy/Caddyfile.pre-vaadin-web"
TMP="/tmp/Caddyfile.candidate"

echo "Configuring Caddy for Pure Java Web UI on UAT..."

if ! sudo -n test -r "$CONFIG"; then
  echo "Caddyfile missing or not readable through sudo: $CONFIG" >&2
  exit 2
fi

# Backup existing config once
if [[ ! -e "$BACKUP" ]]; then
  sudo cp -a "$CONFIG" "$BACKUP"
  echo "Backup created at $BACKUP"
fi

# Check if erp-uat is already proxying to 8081
if sudo grep -q 'erp-uat.jasviindustries.in' "$CONFIG" && sudo grep -q 'reverse_proxy.*8081' "$CONFIG"; then
  echo "UAT Caddy configuration already proxies to 8081."
  sudo caddy validate --config "$CONFIG"
  exit 0
fi

# Prepare new Caddy block or update existing block
# If erp-uat.jasviindustries.in exists, replace it or append safe reverse_proxy block
sudo python3 - << 'PY'
import re, sys

config_path = "/etc/caddy/Caddyfile"
with open(config_path, "r", encoding="utf-8") as f:
    content = f.read()

target_block = """# DSE ERP — UAT Web Application (Pure Java Vaadin UI)
erp-uat.jasviindustries.in {
    reverse_proxy 127.0.0.1:8081 {
        header_up X-Real-IP {remote_host}
        header_up X-Forwarded-For {remote_host}
        header_up X-Forwarded-Proto {scheme}
    }
}
"""

# Replace existing erp-uat block if present
pattern = r'(?ms)erp-uat\.jasviindustries\.in\s*\{.*?\n\}'
if re.search(pattern, content):
    new_content = re.sub(pattern, target_block.strip(), content)
else:
    new_content = content.rstrip() + "\n\n" + target_block

tmp_path = "/tmp/Caddyfile.candidate"
with open(tmp_path, "w", encoding="utf-8") as f:
    f.write(new_content)

print("Candidate Caddy configuration generated at /tmp/Caddyfile.candidate")
PY

echo "Validating candidate Caddyfile..."
sudo caddy validate --config "$TMP"

# Install with automatic rollback on error
rollback() {
  echo "ROLLBACK: Restoring previous Caddyfile..." >&2
  sudo cp -a "$BACKUP" "$CONFIG"
  sudo caddy validate --config "$CONFIG"
  sudo systemctl reload caddy
}

trap 'rc=$?; if [[ $rc -ne 0 ]]; then rollback; fi; exit $rc' EXIT

sudo install -o root -g root -m 644 "$TMP" "$CONFIG"
sudo caddy validate --config "$CONFIG"
sudo systemctl reload caddy
sleep 2
sudo systemctl is-active --quiet caddy

trap - EXIT
rm -f "$TMP"
echo "UAT_CADDY_CONFIGURED_OK: erp-uat.jasviindustries.in -> 127.0.0.1:8081"
