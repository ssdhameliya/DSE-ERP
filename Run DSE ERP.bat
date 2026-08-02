@echo off
setlocal
cd /d "%~dp0"

if exist "target\DSE_Final.jar" (
    start "DSE ERP" javaw --enable-preview -Dfile.encoding=UTF-8 -jar "target\DSE_Final.jar"
    exit /b 0
)

echo DSE ERP has not been built yet.
echo Run build.bat first, then try again.
pause
exit /b 1
