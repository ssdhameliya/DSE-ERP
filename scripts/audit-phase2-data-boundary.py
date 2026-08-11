#!/usr/bin/env python3
from pathlib import Path
import sys
root=Path(__file__).resolve().parents[1]
desktop=root/'desktop'
errors=[]
pom=(desktop/'pom.xml').read_text(errors='ignore')
for token in ('<artifactId>postgresql</artifactId>','spring-boot-starter-data-jpa','HikariCP'):
    if token in pom:
        errors.append('desktop pom still contains database dependency: '+token)
for p in (desktop/'src/main/java').rglob('*.java'):
    text=p.read_text(errors='ignore')
    rel=p.relative_to(root)
    for token in ('org.postgresql.','DriverManager.getConnection','HikariDataSource','HikariConfig','javax.sql.DataSource','java.sql.'):
        if token in text:
            errors.append(f'{rel}: direct database boundary token {token}')
if errors:
    print('DESKTOP DATA BOUNDARY: FAIL')
    for e in errors: print(' -',e)
    sys.exit(2)
print('DESKTOP DATA BOUNDARY: PASS')
print('Desktop has no PostgreSQL driver/pool/raw JDBC implementation.')
print('Business data and authentication are accessed through Spring API clients.')
