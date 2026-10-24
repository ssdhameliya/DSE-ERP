#!/usr/bin/env bash
set -euo pipefail

# ==============================================================================
# DSE ERP — Restore Caddy for Decoupled HTML5/JS Web Admin Portal (PROD)
# Targets: erp.jasviindustries.in -> /srv/dse-erp/web/admin + /api/* to 8080
# ==============================================================================

CONFIG="/etc/caddy/Caddyfile"
BACKUP="/etc/caddy/Caddyfile.pre-vaadin-web"
TMP="/tmp/Caddyfile.candidate"

echo "Restoring Caddy configuration for Decoupled HTML/JS Web Portal on PROD..."

if ! sudo -n test -r "$CONFIG"; then
  echo "Caddyfile missing or not readable through sudo: $CONFIG" >&2
  exit 2
fi

# 1. Check if backup exists and contains static file serving
RESTORED_FROM_BACKUP=0
if sudo -n test -f "$BACKUP"; then
  if sudo grep -q 'srv/dse-erp/web/admin' "$BACKUP" || sudo grep -q 'file_server' "$BACKUP"; then
    echo "Found valid pre-vaadin backup: $BACKUP. Restoring..."
    sudo cp -a "$BACKUP" "$CONFIG"
    RESTORED_FROM_BACKUP=1
  fi
fi

if [ "$RESTORED_FROM_BACKUP" -eq 0 ]; then
  echo "Generating canonical decoupled Caddy configuration for erp.jasviindustries.in..."
  sudo python3 - << 'PY'
import re, sys

config_path = "/etc/caddy/Caddyfile"
with open(config_path, "r", encoding="utf-8") as f:
    content = f.read()

target_domain = "erp.jasviindustries.in"

decoupled_block = """# DSE ERP — Production Decoupled Web Portal & REST API
erp.jasviindustries.in {
    root * /srv/dse-erp/web/admin
    encode gzip zstd
    file_server

    handle /api/* {
        reverse_proxy 127.0.0.1:8080 {
            header_up Host {host}
            header_up X-Real-IP {remote_host}
            header_up X-Forwarded-For {remote_host}
            header_up X-Forwarded-Proto {scheme}
        }
    }

    try_files {path} {path}/ /index.html
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
    new_content = content[:s] + decoupled_block.strip() + content[e:]
else:
    new_content = content.rstrip() + "\n\n" + decoupled_block.strip() + "\n"

tmp_path = "/tmp/Caddyfile.candidate"
with open(tmp_path, "w", encoding="utf-8") as f:
    f.write(new_content)

print("Candidate decoupled Caddy configuration written to /tmp/Caddyfile.candidate")
PY

  echo "Validating candidate Caddyfile..."
  sudo caddy validate --config "$TMP"
  sudo install -o root -g root -m 644 "$TMP" "$CONFIG"
  sudo rm -f "$TMP"
fi

# Final validation & reload
sudo caddy validate --config "$CONFIG"
sudo systemctl reload caddy
sleep 2
sudo systemctl is-active --quiet caddy

echo "PROD Caddy successfully restored and reloaded: erp.jasviindustries.in -> /srv/dse-erp/web/admin + /api/* (8080)"
