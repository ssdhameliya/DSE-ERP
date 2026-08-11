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
