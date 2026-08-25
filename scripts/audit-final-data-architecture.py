#!/usr/bin/env python3
from pathlib import Path
import sys
root=Path(__file__).resolve().parents[1]
desktop=root/'desktop'
server=root/'server'
errors=[]
# Desktop must remain database-driver/JDBC free.
for p in [desktop/'pom.xml', *(desktop/'src/main').rglob('*')]:
    if not p.is_file() or p.suffix.lower() not in {'.java','.xml','.properties','.fxml'}:
        continue
    text=p.read_text(errors='ignore').lower()
    for token in ('org.postgresql.','drivermanager.getconnection','hikaridatasource','hikariconfig','java.sql.'):
        if token in text:
            errors.append(f'{p.relative_to(root)} contains forbidden desktop DB token {token}')
# Production desktop data/authentication must use APIs.
cm=(desktop/'src/main/java/org/example/config/ConfigManager.java').read_text(errors='ignore')
if 'public static boolean isApiAuthenticationEnabled() { return true; }' not in cm:
    errors.append('authentication is not locked to Spring API')
if 'public static boolean isApiDataEnabled() { return true; }' not in cm:
    errors.append('business data is not locked to Spring API')
# Server persistence must be JPA/Hibernate owned, not Spring JDBC/raw JDBC.
for p in (server/'src/main/java').rglob('*.java'):
    text=p.read_text(errors='ignore')
    rel=p.relative_to(root)
    for token in ('JdbcTemplate','NamedParameterJdbcTemplate','org.springframework.jdbc','java.sql.','DriverManager.getConnection','PreparedStatement','ResultSet'):
        if token in text:
            errors.append(f'{rel} contains forbidden server persistence token {token}')
# REST controllers must not execute persistence directly.
for p in (server/'src/main/java').rglob('*Controller.java'):
    text=p.read_text(errors='ignore')
    if 'JpaNativeRepository' in text or 'EntityManager' in text:
        errors.append(f'{p.relative_to(root)} accesses persistence directly; use a service')
# Spring component scan must not contain duplicate default bean names.
# Java permits the same simple class name in different packages, but Spring's
# default AnnotationBeanNameGenerator does not: both become the same bean name.
import re
from collections import defaultdict
component_annotations = ('Component','Service','Repository','Controller','RestController','Configuration','ControllerAdvice','RestControllerAdvice')
bean_names = defaultdict(list)
for p in (server/'src/main/java').rglob('*.java'):
    text=p.read_text(errors='ignore')
    if not any(re.search(r'@'+name+r'\b', text) for name in component_annotations):
        continue
    m=re.search(r'\b(?:public\s+)?(?:final\s+)?(?:class|record|interface|enum)\s+(\w+)', text)
    if not m:
        continue
    cls=m.group(1)
    bean=cls[:1].lower()+cls[1:]
    bean_names[bean].append(p.relative_to(root))
for bean, paths in bean_names.items():
    if len(paths) > 1:
        joined=', '.join(str(path) for path in paths)
        errors.append(f'duplicate Spring default bean name {bean}: {joined}')

# Every release migration file (except the bootstrap schema applied by Spring SQL init)
# must be registered in the runtime migration runner. A packaged SQL file that is
# omitted from this list is dead code and can cause later migrations to run against
# tables/columns that were never created on an upgraded database.
migration_dir=server/'src/main/resources/db/migration'
runner_path=server/'src/main/java/org/example/server/runtime/SecurityFinancialMigrationRunner.java'
if runner_path.exists():
    runner_text=runner_path.read_text(errors='ignore')
    migration_stems=[p.stem for p in sorted(migration_dir.glob('*.sql')) if p.stem!='V5_1_2__server_owned_schema']
    for migration in migration_stems:
        if migration not in runner_text:
            errors.append(f'runtime migration runner omits packaged migration {migration}')
    ordered=('V8_5_1__role_master_lookup_authority','V8_5_5__permission_matrix_authority','V8_5_8__purchase_recon','V9_0_0__multi_user_audit_versioning','V9_0_0_1__persistent_auth_sessions','V9_0_0_2__signed_auth_sessions')
    positions=[runner_text.find(m) for m in ordered]
    if all(p >= 0 for p in positions) and positions != sorted(positions):
        errors.append('runtime migration order must be V8_5_1 -> V8_5_5 -> V8_5_8 -> V9_0_0 -> V9_0_0_1 -> V9_0_0_2')
else:
    errors.append('SecurityFinancialMigrationRunner is missing')

# Authentication sessions must be server-authoritative and restart-safe.
token_service=server/'src/main/java/org/example/server/security/TokenService.java'
if token_service.exists():
    token_text=token_service.read_text(errors='ignore')
    if 'auth_session' not in token_text or 'token_hash' not in token_text:
        errors.append('TokenService does not retain the hashed PostgreSQL auth_session registry')
    if 'SignedTokenCodec' not in token_text or 'auth_version' not in token_text or 'auth_token_revocation' not in token_text:
        errors.append('TokenService is not using signed bearer tokens with PostgreSQL revocation/version authority')
    if re.search(r'FROM\s+auth_session\s+WHERE\s+token_hash\s*=\s*\?\s+AND\s+expires_at\s*>\s*CURRENT_TIMESTAMP', token_text, re.IGNORECASE):
        errors.append('TokenService still treats auth_session registry presence as the sole authentication proof')
    if 'ConcurrentHashMap' in token_text:
        errors.append('TokenService still uses process-local in-memory sessions')
else:
    errors.append('TokenService is missing')
if 'getCanonicalApiBaseUrl()' not in cm or 'public static String getAuthApiBaseUrl() {\n        return getCanonicalApiBaseUrl();' not in cm or 'public static String getDataApiBaseUrl() {\n        return getCanonicalApiBaseUrl();' not in cm:
    errors.append('desktop authentication and business data are not locked to one canonical Spring endpoint')

# JPA/Hibernate foundation must exist.
repo=server/'src/main/java/org/example/server/persistence/JpaNativeRepository.java'
if not repo.exists():
    errors.append('JpaNativeRepository is missing')
else:
    text=repo.read_text(errors='ignore')
    if 'EntityManager' not in text or '@Repository' not in text:
        errors.append('JpaNativeRepository is not EntityManager/@Repository backed')
if errors:
    print('FINAL DATA ARCHITECTURE: FAIL')
    for e in errors: print(' -',e)
    sys.exit(2)
print('FINAL DATA ARCHITECTURE: PASS')
print('JavaFX uses typed APIs; desktop has no direct database boundary.')
print('Spring controllers delegate to services; server persistence is JPA/Hibernate owned.')
print('No Spring JDBC/raw JDBC execution path remains in production source.')
