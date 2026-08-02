@echo off
setlocal
cd /d "%~dp0"

if exist "release\DSE-ERP\DSE-ERP.exe" (
    start "DSE ERP" "release\DSE-ERP\DSE-ERP.exe"
    exit /b 0
)

if exist "target\DSE_Final.jar" (
    start "DSE ERP" javaw -Dprism.order=sw -jar "target\DSE_Final.jar"
    exit /b 0
)

echo DSE ERP has not been built yet.
echo Run build.bat first, then try again.
pause
exit /b 1
