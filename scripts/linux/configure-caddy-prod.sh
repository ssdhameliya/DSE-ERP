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

# Prepare new Caddy block or update existing block with brace-balanced block inspection
GEN_OUTPUT=$(sudo python3 - << 'PY'
import os
import re
import subprocess
import sys

config_path = "/etc/caddy/Caddyfile"
with open(config_path, "r", encoding="utf-8") as f:
    content = f.read()

target_domain = "erp.jasviindustries.in"

# Detect target port dynamically
detected_port = None

# 1. From existing api.jasviindustries.in block in Caddyfile
api_match = re.search(r'api\.jasviindustries\.in\s*\{[^}]*reverse_proxy\s+(?:127\.0\.0\.1|localhost):(\d+)', content)
if api_match:
    detected_port = api_match.group(1)

# 2. From /etc/dse-erp/prod.env
if not detected_port and os.path.exists("/etc/dse-erp/prod.env"):
    try:
        with open("/etc/dse-erp/prod.env", "r", encoding="utf-8") as env_f:
            for line in env_f:
                if line.startswith("DSE_SERVER_PORT="):
                    val = line.strip().split("=", 1)[1].strip().strip('"\'')
                    if val.isdigit():
                        detected_port = val
                        break
    except Exception:
        pass

# 3. From running java process listening ports
if not detected_port:
    try:
        ss_out = subprocess.check_output(["ss", "-tlpn"], text=True)
        for line in ss_out.splitlines():
            if "java" in line:
                m = re.search(r':(\d+)\s', line)
                if m and m.group(1) in ("8080", "8081", "8082"):
                    detected_port = m.group(1)
                    break
    except Exception:
        pass

target_port = detected_port if detected_port else "8080"
print(f"PORT_DETECTED={target_port}")

target_block = f"""# DSE ERP — Production Web Application (Pure Java Vaadin UI)
erp.jasviindustries.in {{
    reverse_proxy 127.0.0.1:{target_port} {{
        header_up Host {{host}}
        header_up X-Real-IP {{remote_host}}
        header_up X-Forwarded-For {{remote_host}}
        header_up X-Forwarded-Proto {{scheme}}
    }}
    encode gzip zstd
}}
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
  echo "PROD Caddy configuration already proxies erp to the correct port."
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
sudo rm -f "$TMP"
echo "PROD_CADDY_CONFIGURED_OK: erp.jasviindustries.in -> 127.0.0.1:8080"
