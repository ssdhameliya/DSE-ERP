from pathlib import Path
import sys
r=Path(__file__).resolve().parents[1]
checks=[]
def need(path,text):
 s=(r/path).read_text(encoding="utf-8")
 checks.append((f"{path}: {text}", text in s))
need("pom.xml","<version>9.0.52</version>")
need("server/src/main/resources/db/migration/V9_0_48__release_gate_2_3_hardening.sql","ck_payment_record_positive_amount")
need("server/src/main/resources/db/migration/V9_0_48__release_gate_2_3_hardening.sql","row_version BIGINT NOT NULL DEFAULT 0")
need("server/src/main/java/org/example/server/persistence/entity/PaymentRecordEntity.java","@Version")
need("server/src/main/java/org/example/server/persistence/entity/BankStatementTransactionEntity.java","@Version")
need("server/src/main/java/org/example/server/persistence/entity/BankStatementImportEntity.java","@Version")
need("server/src/main/java/org/example/server/support/PaymentIntegrityService.java","FOR UPDATE")
need("server/src/main/java/org/example/server/support/PaymentIntegrityService.java","row_version=row_version+1")
need("desktop/src/main/java/org/example/backup/BackupManager.java","createSafetyBackup")
need("desktop/src/main/java/org/example/backup/BackupManager.java","validatePostgresBackup")
failed=[x for x,ok in checks if not ok]
for x,ok in checks: print(("PASS " if ok else "FAIL ")+x)
if failed: sys.exit(1)
print("9.0.49 Gate 2/3 contract PASSED")
