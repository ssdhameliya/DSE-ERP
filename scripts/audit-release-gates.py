#!/usr/bin/env python3
"""Single authoritative current DSE ERP release-gate aggregate.

Release-specific file names are intentionally avoided. Historical database migration
versions remain in server/src/main/resources/db/migration because upgrade history is
part of the runtime contract.
"""
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
CHECKS = [
    "audit-desktop-jdbc.py",
    "audit-phase2-data-boundary.py",
    "audit-postgres-only.py",
    "audit-final-data-architecture.py",
    "audit-auth-shortcut-ui-contract.py",
    "audit-global-ui-contract.py",
    "audit-import-contract.py",
    "audit-bank-reconciliation-contract.py",
    "audit-reconciliation-navigation-ui-contract.py",
    "audit-business-integrity-contract.py",
    "audit-pdf-studio-contract.py",
    "audit-corrective-contract.py",
    "audit-sales-payment-compatibility.py",
    "audit-register-login-history-contract.py",
    "audit-quotation-register-contract.py",
    "audit-reference-item-import-contract.py",
    "audit-quotation-source-sale-link-contract.py",
    "audit-financial-multi-user-contract.py",
    "audit-registration-security-contract.py",
    "audit-customer-360-contract.py",
    "audit-gate2-gate3-contract.py",
    "audit-project-execution-removal-contract.py",
    "audit-session-focus-stock-contract.py",
    "audit-stability-contract.py",
    "audit-architecture-refactor.py",
]

failed = []
for name in CHECKS:
    path = ROOT / "scripts" / name
    print(f"\n=== {name} ===")
    result = subprocess.run([sys.executable, str(path)], cwd=ROOT)
    if result.returncode:
        failed.append(name)

# Small cross-cutting release invariants that are intentionally centralized here.
def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8", errors="replace")

extra = []
calc = text("shared/src/main/java/org/example/shared/DocumentCalculationEngine.java")
ops = text("server/src/main/java/org/example/server/operations/BusinessOperationsService.java")
auth = text("server/src/main/java/org/example/server/auth/AuthService.java")
pay = text("server/src/main/java/org/example/server/support/PaymentIntegrityService.java")
css = sorted(p.name for p in (ROOT / "desktop/src/main/resources/css").glob("*.css"))
if "BigDecimal qty = quantityDecimal" not in calc:
    extra.append("authoritative calculation is not BigDecimal internally")
if "requestedTotals=documentTotals" not in ops:
    extra.append("sale/purchase update guard still trusts client totals")
if "stored.equals(raw)" in auth:
    extra.append("plaintext password comparison remains enabled")
if "FOR UPDATE" not in pay or "effectivePaid" not in pay:
    extra.append("payment serialization/authoritative paid guard missing")
if css != ["dark-theme.css", "light-theme.css"]:
    extra.append(f"central two-theme CSS contract changed: {css}")


# Current focused production corrections.
focused = [
    ("server/src/main/java/org/example/server/reporting/ReportingService.java", "OUTPUT RETURN"),
    ("server/src/main/java/org/example/server/reporting/ReportingService.java", "Return GST"),
    ("server/src/main/java/org/example/server/auth/AuthService.java", "ADMIN_CONTROLLED"),
    ("server/src/main/java/org/example/server/admin/AdminService.java", "effectiveMfa"),
    ("server/src/main/resources/db/migration/V9_0_58__mfa_policy.sql", "security.auth.mfa.policy"),
]
for path, token in focused:
    if token not in text(path):
        extra.append(f"focused contract missing: {path} -> {token}")

# KPI containment is deliberately opt-in. No other screen may inherit this rule.
bounded=[]
for f in (ROOT / "desktop/src/main/resources/fxml/pages").rglob("*.fxml"):
    if "erp-kpi-bounded" in f.read_text(encoding="utf-8",errors="ignore"):
        bounded.append(f.name)
if bounded:
    extra.append(f"rolled-back bounded KPI class remains: {sorted(bounded)}")

# Packaging/source cleanliness: current audit scripts are generic; historical SQL migrations are retained.
versioned_audits=[p.name for p in (ROOT/"scripts").glob("audit-*.py") if __import__("re").search(r"audit-\d+\.\d+",p.name)]
if versioned_audits:
    extra.append(f"version-numbered audit scripts remain: {versioned_audits}")

if extra:
    print("\n=== RELEASE-GATE EXTRA CHECKS ===")
    for item in extra:
        print("FAIL -", item)
    failed.extend(extra)
else:
    print("\nRELEASE_GATE_EXTRA_OK bigdecimal=yes server_totals=yes plaintext_compare=no payment_lock=yes css=2")

if failed:
    print("\nRELEASE_GATES_FAIL")
    for item in failed:
        print(" -", item)
    sys.exit(1)

print("\nRELEASE_GATES_CURRENT_OK version=9.0.77")
