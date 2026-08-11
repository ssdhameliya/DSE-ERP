@echo off
setlocal
cd /d "%~dp0.."
echo Building DSE ERP backend...
call mvn -pl server -am package -DskipTests
if errorlevel 1 exit /b 1
echo Starting packaged DSE ERP backend on http://localhost:8080 ...
java -jar server\target\dse-erp-server.jar
endlocal
