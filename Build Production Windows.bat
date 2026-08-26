@echo off
setlocal EnableExtensions
cd /d "%~dp0"

echo.
echo ==========================================
echo   DSE ERP 9.0.15 - PRODUCTION WINDOWS BUILD
echo ==========================================
echo.
echo This builds the customer Windows installer.
echo It does not start the source JavaFX application.
echo.

where mvn >nul 2>nul
if errorlevel 1 (
  echo ERROR: Maven is required on the RELEASE BUILD MACHINE.
  echo Production customers do not need Maven.
  pause
  exit /b 1
)

where java >nul 2>nul
if errorlevel 1 (
  echo ERROR: JDK is required on the RELEASE BUILD MACHINE.
  echo Production customers receive a bundled Java runtime.
  pause
  exit /b 2
)

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\package-windows.ps1"
set "EXIT_CODE=%ERRORLEVEL%"

if not "%EXIT_CODE%"=="0" (
  echo.
  echo Production build failed with exit code %EXIT_CODE%.
  pause
  exit /b %EXIT_CODE%
)

echo.
echo Production installer created under:
echo   target\windows-installer
echo.
pause
exit /b 0
