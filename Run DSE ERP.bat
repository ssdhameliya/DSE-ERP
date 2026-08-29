@echo off
setlocal EnableExtensions
cd /d "%~dp0"

echo.
echo ========================================
echo   DO NOT USE THIS SCRIPT TO BUILD THE CUSTOMER INSTALLER
echo ========================================
echo   DSE ERP 9.0.36 - DEVELOPMENT / INTELLIJ ONLY
echo ========================================
echo.

where mvn >nul 2>nul
if errorlevel 1 (
  echo ERROR: Maven was not found on PATH.
  echo Use IntelliJ's Maven terminal or add Maven to PATH on the development machine.
  pause
  exit /b 1
)

set "DSE_POSTGRES_MODE=managed"

if not defined DSE_POSTGRES_HOME if not defined DSE_POSTGRES_RUNTIME_DIR (
  if exist "%CD%\runtime\postgresql\bin\initdb.exe" set "DSE_POSTGRES_RUNTIME_DIR=%CD%\runtime\postgresql"
)
if not defined DSE_POSTGRES_HOME if not defined DSE_POSTGRES_RUNTIME_DIR (
  if exist "D:\PostgreSQL\18\pgsql\bin\initdb.exe" set "DSE_POSTGRES_RUNTIME_DIR=D:\PostgreSQL\18\pgsql"
)
if not defined DSE_POSTGRES_HOME if not defined DSE_POSTGRES_RUNTIME_DIR (
  if exist "C:\Program Files\PostgreSQL\18\bin\initdb.exe" set "DSE_POSTGRES_RUNTIME_DIR=C:\Program Files\PostgreSQL\18"
)

if defined DSE_POSTGRES_RUNTIME_DIR echo PostgreSQL runtime: %DSE_POSTGRES_RUNTIME_DIR%
if defined DSE_POSTGRES_HOME echo PostgreSQL runtime: %DSE_POSTGRES_HOME%

if not defined DSE_POSTGRES_HOME if not defined DSE_POSTGRES_RUNTIME_DIR (
  echo.
  echo ERROR: PostgreSQL 18 runtime payload was not found for this SOURCE/IntelliJ run.
  echo The customer EXE/DMG bundles this runtime automatically; no customer installation is required.
  echo For development, place the PostgreSQL 18 runtime under runtime\postgresql or set
  echo DSE_POSTGRES_RUNTIME_DIR to the extracted PostgreSQL 18 runtime root.
  echo.
  pause
  exit /b 2
)

echo [1/3] Cleaning stale desktop resources/classes...
call mvn -pl desktop -am clean -DskipTests
if errorlevel 1 (
  echo ERROR: Clean failed.
  pause
  exit /b 3
)

echo [2/3] Installing shared contracts...
call mvn -pl shared install -DskipTests
if errorlevel 1 (
  echo.
  echo ERROR: Shared-contract build failed. Fix the build error above before starting DSE ERP.
  pause
  exit /b 3
)

echo.
echo [3/3] Starting JavaFX full stack...
echo       JavaFX will start managed PostgreSQL, build a uniquely named current
echo       Spring Boot backend JAR, verify its exact version, then open the UI.
echo.
call mvn -f desktop\pom.xml org.openjfx:javafx-maven-plugin:0.0.8:run
set "EXIT_CODE=%ERRORLEVEL%"

if not "%EXIT_CODE%"=="0" (
  echo.
  echo DSE ERP exited with code %EXIT_CODE%.
  pause
)
exit /b %EXIT_CODE%
