#!/usr/bin/env python3
from pathlib import Path
import hashlib, sys
root=Path(__file__).resolve().parents[1]
checks=[]
def check(name, ok, detail=""):
    checks.append((name,bool(ok),detail)); print(("PASS" if ok else "FAIL")+" "+name+(" :: "+detail if detail else ""))
def text(path): return (root/path).read_text(encoding="utf-8")
def sha(path): return hashlib.sha256((root/path).read_bytes()).hexdigest()

wm=text("desktop/src/main/java/org/example/config/WorkspaceStorageManager.java")
ws=text("desktop/src/main/java/org/example/config/WorkspaceManager.java")
managed=text("desktop/src/main/java/org/example/service/ManagedInvoicePdfService.java")
maint=text("server/src/main/java/org/example/server/support/StorageMaintenanceService.java")
support=text("server/src/main/java/org/example/server/support/SupportService.java")
fxml=text("desktop/src/main/resources/fxml/pages/settings/WorkspaceSettingsPanel.fxml")
report=text("server/src/main/java/org/example/server/reporting/ReportScheduleService.java")

protected={
 "desktop/src/main/java/org/example/documentstudio/service/DocumentOutputService.java":"5d84c57c22299bfedcc969512b33f2a8cd0371455918ef82f71037827ee2686c",
 "desktop/src/main/java/org/example/service/InvoicePdfService.java":"ddc9bd1120388058ae60742f343553c6c0de2634e885360deef8e31298033fa8",
 "desktop/src/main/java/org/example/invoice/service/SalesTaxInvoiceService.java":"27eb0498f015a410b60aa86f71c8bced4e0ff0e45f8a7e0207b7be9a7ce74082",
}

check("storage-manager-present", "class WorkspaceStorageManager" in wm)
check("financial-year-storage", "financialYear" in wm and '"Sales"' in wm and '"Purchase"' in wm)
check("workspace-structure-folders", all(x in ws for x in ["Documents/Sales","Reports/Scheduled","Exports/Diagnostics","Logs/Desktop","Logs/Server","Logs/PostgreSQL","Imports/Results"]))
check("protected-pdf-generators-unchanged", all(sha(Path(p))==h for p,h in protected.items()))
check("managed-pdf-storage-facade", "class ManagedInvoicePdfService" in managed and "WorkspaceStorageManager.documentFile" in managed and "InvoicePdfService." in managed)
callers=list((root/"desktop/src/main/java/org/example/controller").rglob("*.java"))
check("business-ui-uses-managed-pdf-facade", any("ManagedInvoicePdfService." in p.read_text(errors="ignore") for p in callers))
legacy_allowed=set(protected)
legacy_violations=[]
for p in (root/"desktop/src/main/java").rglob("*.java"):
    rel=str(p.relative_to(root)).replace('\\','/')
    if rel in legacy_allowed: continue
    if 'getConfigFolder().resolve("Documents")' in p.read_text(errors="ignore"):
        legacy_violations.append(rel)
check("no-new-generic-config-documents", not legacy_violations, ",".join(legacy_violations))
check("scheduled-reports-organized", 'resolve("Reports").resolve("Scheduled")' in report and "financialYear(today)" in report)
check("storage-settings-server-authoritative", 'k.startsWith("storage.")' in support)
check("storage-persistence-jpa-owned", "JpaNativeRepository" in maint and "JdbcTemplate" not in maint and "org.springframework.jdbc" not in maint)
check("safe-cleanup-documents-excluded", 'cleanupTree(workspace.resolve("Documents")' not in maint)
check("safe-cleanup-attachments-excluded", 'cleanupTree(workspace.resolve("Attachments")' not in maint)
check("safe-cleanup-backups-excluded", 'cleanupTree(workspace.resolve("Backups")' not in maint)
check("safe-cleanup-database-excluded", 'cleanupTree(workspace.resolve("Database")' not in maint)
check("operational-cleanup-covered", all(x in maint for x in ['workspace.resolve("Reports")','workspace.resolve("Exports")','workspace.resolve("Imports/Results")','workspace.resolve("Temp")','cleanupLogs(workspace.resolve("Logs")']))
check("admin-cleanup-only", "Storage cleanup can be run only by an administrator" in maint)
check("retention-ui", all(x in fxml for x in ["txtLogRetentionDays","txtReportRetentionDays","txtExportRetentionDays","txtImportResultRetentionDays","txtTempRetentionDays","Clean Now","Preview Cleanup"]))
check("open-folder-ui", all(x in fxml for x in ["Open Documents","Open Reports","Open Logs"]))
check("two-css-only", len(list((root/"desktop/src/main/resources/css").glob("*.css")))==2, str([p.name for p in (root/"desktop/src/main/resources/css").glob("*.css")]))

failed=[c for c in checks if not c[1]]
print(f"PHASE4B_STORAGE_RETENTION_AUDIT {'PASS' if not failed else 'FAIL'} checks={len(checks)} failed={len(failed)}")
sys.exit(1 if failed else 0)
