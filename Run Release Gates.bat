@echo off
setlocal
cd /d "%~dp0"
echo ======================================================
echo   DSE ERP 9.0.73 - RELEASE GATE VERIFICATION
echo ======================================================
python scripts\audit-release-gates.py
if errorlevel 1 goto :fail

echo.
echo Static release gates PASSED.
echo Running Maven automated tests...
call mvnw.cmd test
if errorlevel 1 goto :fail

echo.
echo ======================================================
echo   ALL RELEASE GATES PASSED
 echo ======================================================
exit /b 0
:fail
echo.
echo ======================================================
echo   RELEASE GATE FAILED - review output above
 echo ======================================================
exit /b 1
