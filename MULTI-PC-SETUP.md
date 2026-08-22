# DSE ERP 8.4.1 deployment modes

## Existing single-PC operation

Choose **This PC only**. This remains the default and preserves the 8.3.2 behaviour: the desktop starts its managed PostgreSQL database and Spring service on loopback.

## Company server

Use one designated Windows PC/server with a stable LAN address. Run DSE ERP there in local mode and explicitly bind its Spring service to the LAN interface:

```powershell
$env:DSE_SERVER_ADDRESS = "0.0.0.0"
```

Start DSE ERP from that same environment. Allow only the configured DSE ERP port through the private-network firewall. PostgreSQL port 5432/55432 must remain private and must not be opened to client PCs.

For production, place a trusted TLS reverse proxy in front of the Spring port and give it a stable address such as `https://erp.company.local`. Plain HTTP should be limited to a trusted private LAN during initial rollout.

## Client PCs

On first launch choose **Connect to company server**, enter the server URL, and select **Test Connection**. The desktop saves shared-client mode only when the service identity, API revision, application version and database readiness all match.

Existing installations can change this under **Settings → Workspace & Storage → Deployment**. Restart DSE ERP after saving. The client will not start local PostgreSQL, a local Spring server, database backup or database restore while shared-client mode is active.

Administrators can preconfigure clients with:

```text
DSE_DEPLOYMENT_MODE=SHARED_CLIENT
DSE_SERVER_URL=https://erp.company.local
```

Never give PostgreSQL credentials to client PCs. Each employee signs in with their own DSE ERP user account.

## Server authority in 8.4.1

Local mode preserves the single-PC workflow. In shared-client mode the company server is authoritative for business data, company/payment/invoice settings, PDF and Excel templates, logo/signature/payment-QR assets, business email, reference allocation, timezone/date policy and database backups.

Use `scripts/Enable Multi-User.ps1` for an existing installation. Its staging phase creates a verified local PostgreSQL safety backup and transfers it to the server without changing the desktop mode. Apply the staged restore on the server, restart and verify READY, then run the finalization phase. The client configuration changes only after the administrator explicitly finalizes promotion.

For a dedicated Windows server or Linux VPS, build `scripts/package-company-server.ps1`, configure `dse-erp-server.env.example`, and install either the Windows service or supplied systemd unit. Put a trusted TLS reverse proxy in front of the service.

Run `scripts/test-multi-user-concurrency.ps1` with three authenticated test users before production rollout. It verifies simultaneous server access and collision-free reference allocation.
