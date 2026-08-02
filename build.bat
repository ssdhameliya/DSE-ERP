@echo off
setlocal
cd /d "%~dp0"

echo Building DSE ERP...
call mvn package
if errorlevel 1 exit /b 1

echo Creating portable application image...
if exist "target\package-input" rmdir /s /q "target\package-input"
mkdir "target\package-input"
copy /Y "target\DSE_Final.jar" "target\package-input\DSE_Final.jar" >nul
if not exist "release" mkdir "release"
if exist "release\DSE-ERP" rmdir /s /q "release\DSE-ERP"
jpackage --input "target\package-input" --dest release --name DSE-ERP --main-jar DSE_Final.jar --main-class org.example.app.Launcher --type app-image --app-version 1.0.0
if errorlevel 1 exit /b 1
copy /Y "JavaAppERP.db" "release\DSE-ERP\JavaAppERP.db" >nul

echo.
echo Build complete: %~dp0release\DSE-ERP\DSE-ERP.exe
echo Application data: %%APPDATA%%\DSE ERP
endlocal
