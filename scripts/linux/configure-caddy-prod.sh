#!/usr/bin/env bash
set -euo pipefail

# ==============================================================================
# DSE ERP — Safe PROD Caddy Configuration for Pure Java Vaadin Web UI
# Targets: erp.jasviindustries.in -> 127.0.0.1:8080
# ==============================================================================

CONFIG="/etc/caddy/Caddyfile"
BACKUP="/etc/caddy/Caddyfile.pre-vaadin-web"
TMP="/tmp/Caddyfile.candidate"

echo "Configuring Caddy for Pure Java Web UI on PROD..."

if ! sudo -n test -r "$CONFIG"; then
  echo "Caddyfile missing or not readable through sudo: $CONFIG" >&2
  exit 2
fi

# Backup existing config once
if [[ ! -e "$BACKUP" ]]; then
  sudo cp -a "$CONFIG" "$BACKUP"
  echo "Backup created at $BACKUP"
fi

# Prepare new Caddy block or update existing block with precise block inspection
GEN_OUTPUT=$(sudo python3 - << 'PY'
import re, sys

config_path = "/etc/caddy/Caddyfile"
with open(config_path, "r", encoding="utf-8") as f:
    content = f.read()

target_block = """# DSE ERP — Production Web Application (Pure Java Vaadin UI)
erp.jasviindustries.in {
    reverse_proxy 127.0.0.1:8080 {
        header_up X-Real-IP {remote_host}
        header_up X-Forwarded-For {remote_host}
        header_up X-Forwarded-Proto {scheme}
    }
}
"""

pattern = r'(?ms)^[ \t]*erp\.jasviindustries\.in\b[^{]*\{.*?\n\}'
match = re.search(pattern, content)
if match and "reverse_proxy" in match.group(0) and "8080" in match.group(0):
    print("ALREADY_CONFIGURED")
    sys.exit(0)

if match:
    new_content = content[:match.start()] + target_block.strip() + content[match.end():]
else:
    new_content = content.rstrip() + "\n\n" + target_block

tmp_path = "/tmp/Caddyfile.candidate"
with open(tmp_path, "w", encoding="utf-8") as f:
    f.write(new_content)

print("CANDIDATE_GENERATED")
PY
)

echo "$GEN_OUTPUT"

if [[ "$GEN_OUTPUT" == *"ALREADY_CONFIGURED"* ]]; then
  echo "PROD Caddy configuration already proxies erp to 8080."
  sudo caddy validate --config "$CONFIG"
  exit 0
fi

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
echo "PROD_CADDY_CONFIGURED_OK: erp.jasviindustries.in -> 127.0.0.1:8080"
