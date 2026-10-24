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

# Prepare new Caddy block or update existing block with brace-balanced block inspection
GEN_OUTPUT=$(sudo python3 - << 'PY'
import sys

config_path = "/etc/caddy/Caddyfile"
with open(config_path, "r", encoding="utf-8") as f:
    content = f.read()

target_domain = "erp-uat.jasviindustries.in"
target_port = "8081"

target_block = """# DSE ERP — UAT Web Application (Pure Java Vaadin UI)
erp-uat.jasviindustries.in {
    reverse_proxy 127.0.0.1:8081 {
        header_up Host {host}
        header_up X-Real-IP {remote_host}
        header_up X-Forwarded-For {remote_host}
        header_up X-Forwarded-Proto {scheme}
    }
    encode gzip zstd
}
"""

def extract_blocks(text):
    blocks = []
    i = 0
    n = len(text)
    while i < n:
        brace_pos = text.find("{", i)
        if brace_pos == -1:
            break
        k = brace_pos - 1
        while k >= i and text[k] in ' \t\r\n':
            k -= 1
        line_start = text.rfind("\n", i, k + 1)
        start = line_start + 1 if line_start != -1 else i
        header = text[start:brace_pos].strip()

        depth = 1
        pos = brace_pos + 1
        while pos < n and depth > 0:
            ch = text[pos]
            if ch == "{":
                depth += 1
            elif ch == "}":
                depth -= 1
            pos += 1
        
        blocks.append((header, start, pos, text[start:pos]))
        i = pos
    return blocks

blocks = extract_blocks(content)
target_match = None
for h, s, e, blk in blocks:
    parts = [p.strip() for p in h.replace(",", " ").split() if p.strip()]
    if target_domain in parts:
        target_match = (h, s, e, blk)
        break

if target_match:
    h, s, e, blk = target_match
    has_file_server = "file_server" in blk or "root " in blk
    has_api_only = "handle /api" in blk or "handle_path /api" in blk
    has_proxy = f"reverse_proxy 127.0.0.1:{target_port}" in blk or f"reverse_proxy localhost:{target_port}" in blk
    
    if has_proxy and not has_file_server and not has_api_only:
        print("ALREADY_CONFIGURED")
        sys.exit(0)
    
    new_content = content[:s] + target_block.strip() + content[e:]
else:
    new_content = content.rstrip() + "\n\n" + target_block.strip() + "\n"

tmp_path = "/tmp/Caddyfile.candidate"
with open(tmp_path, "w", encoding="utf-8") as f:
    f.write(new_content)

print("CANDIDATE_GENERATED")
PY
)

echo "$GEN_OUTPUT"

if [[ "$GEN_OUTPUT" == *"ALREADY_CONFIGURED"* ]]; then
  echo "UAT Caddy configuration already proxies erp-uat to 8081."
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
echo "UAT_CADDY_CONFIGURED_OK: erp-uat.jasviindustries.in -> 127.0.0.1:8081"
