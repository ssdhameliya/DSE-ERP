@echo off
setlocal
cd /d "%~dp0"

echo Verifying DSE ERP...
call mvn -B -ntp clean verify
if errorlevel 1 (
    echo.
    echo Build failed. Review the Maven output above.
    exit /b 1
)

echo.
echo Build successful: %~dp0target\DSE_Final.jar
echo To create the Windows installer, run package-windows.ps1.
endlocal
