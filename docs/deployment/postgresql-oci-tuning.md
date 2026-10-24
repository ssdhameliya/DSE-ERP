# DSE ERP — PostgreSQL OCI Tuning Guide

> Apply these settings on the OCI VM running PostgreSQL.
> **Never expose port 5432 publicly** — loopback-only is enforced.

---

## UAT VM (3 GB RAM · 1 OCPU)

### `postgresql.conf` changes

```conf
# Memory
shared_buffers       = 512MB       # 1/6 of 3 GB RAM
effective_cache_size = 1536MB      # 1/2 of 3 GB RAM (planner hint)
work_mem             = 4MB         # Per sort/hash operation
maintenance_work_mem = 64MB        # VACUUM / CREATE INDEX

# Connections
max_connections      = 50          # HikariCP pool=10 + headroom

# WAL / Checkpoints
wal_buffers          = 16MB
checkpoint_completion_target = 0.9
min_wal_size         = 80MB
max_wal_size         = 1GB

# Logging (UAT — verbose for debugging)
log_min_duration_statement = 500   # Log queries > 500ms
log_checkpoints            = on
log_connections            = on
log_disconnections         = on

# Timezone
timezone             = 'UTC'
log_timezone         = 'UTC'
```

### `pg_hba.conf` — loopback only

```conf
# TYPE   DATABASE   USER        ADDRESS         METHOD
local    all        postgres                    peer
local    dse_erp    dse_erp_app                 md5
host     dse_erp    dse_erp_app 127.0.0.1/32   scram-sha-256
# NO IPv4/IPv6 wildcard entries — port 5432 must be closed in OCI Security List
```

---

## PROD VM (9 GB RAM · 2 OCPU)

### `postgresql.conf` changes

```conf
# Memory
shared_buffers       = 2560MB      # ~28% of 9 GB RAM
effective_cache_size = 6GB         # ~66% of 9 GB RAM
work_mem             = 16MB        # More RAM allows larger in-memory sorts
maintenance_work_mem = 256MB

# Connections
max_connections      = 100         # HikariCP pool=30 + admin headroom

# WAL / Checkpoints
wal_buffers          = 64MB
checkpoint_completion_target = 0.9
min_wal_size         = 256MB
max_wal_size         = 4GB

# Parallel query (2 OCPU)
max_worker_processes    = 4
max_parallel_workers    = 2
max_parallel_workers_per_gather = 1

# Logging (PROD — minimal)
log_min_duration_statement = 2000  # Log queries > 2s only
log_checkpoints            = off
log_connections            = off

# Timezone
timezone             = 'UTC'
log_timezone         = 'UTC'
```

### `pg_hba.conf` — loopback only (same rule as UAT)

```conf
local    all        postgres                    peer
local    dse_erp    dse_erp_app                 md5
host     dse_erp    dse_erp_app 127.0.0.1/32   scram-sha-256
```

---

## OCI Security List Rules (both VMs)

| Direction | Protocol | Port  | Action  | Reason |
|-----------|----------|-------|---------|--------|
| Inbound   | TCP      | 22    | ALLOW   | SSH admin |
| Inbound   | TCP      | 80    | ALLOW   | Caddy HTTP → HTTPS redirect |
| Inbound   | TCP      | 443   | ALLOW   | Caddy HTTPS |
| Inbound   | TCP      | 5432  | **DENY**| PostgreSQL — loopback only |
| Inbound   | TCP      | 8080  | **DENY**| Spring Boot — behind Caddy only |

---

## Deployment Commands (OCI VM)

```bash
# 1. Copy JAR and service file
scp target/dse-erp-server.jar   dse@<OCI_IP>:/opt/dse-erp/
scp docs/deployment/portal.service dse@<OCI_IP>:/tmp/

# 2. Install systemd unit
sudo cp /tmp/portal.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable portal
sudo systemctl start portal

# 3. Check status
sudo systemctl status portal
sudo journalctl -u portal -f

# 4. Install Caddy
sudo apt install -y caddy
sudo cp docs/deployment/Caddyfile /etc/caddy/Caddyfile
sudo systemctl enable --now caddy
```

---

## Environment Variables (set in `/etc/systemd/system/portal.service.d/env.conf`)

```ini
[Service]
Environment="DSE_DB_URL=jdbc:postgresql://localhost:5432/dse_erp"
Environment="DSE_DB_USERNAME=dse_erp_app"
Environment="DSE_DB_PASSWORD=<STRONG_PASSWORD>"
Environment="DSE_SPRING_PROFILE=prod"
Environment="DSE_WORKSPACE_PATH=/opt/dse-erp/workspace"
Environment="DSE_SMTP_HOST=smtp.example.com"
Environment="DSE_SMTP_PORT=587"
Environment="DSE_SMTP_EMAIL=erp@yourdomain.com"
Environment="DSE_SMTP_PASSWORD=<SMTP_PASSWORD>"
```

> **Never put credentials in the `.service` file itself — use the `.d/env.conf` override.**
> Secure with: `sudo chmod 600 /etc/systemd/system/portal.service.d/env.conf`
