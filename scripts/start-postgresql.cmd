@echo off
for /f "tokens=2 delims==" %%V in ('findstr /b /c:"-Drevision=" "%~dp0..\.mvn\maven.config"') do set "DSE_VERSION=%%V"
setlocal
set "DSE_POSTGRES_MODE=managed"
if not defined DSE_POSTGRES_HOME (
  if exist "D:\PostgreSQL\18\pgsql\bin\initdb.exe" set "DSE_POSTGRES_HOME=D:\PostgreSQL\18\pgsql"
)
if not defined DSE_POSTGRES_HOME (
  echo Set DSE_POSTGRES_HOME to your PostgreSQL 18 runtime for development.
  exit /b 1
)
echo DSE ERP %DSE_VERSION% uses application-managed PostgreSQL.
echo Runtime: %DSE_POSTGRES_HOME%
echo Start the JavaFX desktop; it will initialize/start PostgreSQL automatically.
endlocal
