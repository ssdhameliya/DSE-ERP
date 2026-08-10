@echo off
setlocal
cd /d "%~dp0.."
echo Starting DSE ERP Spring Boot server on http://localhost:8080 ...
mvn -f server\pom.xml spring-boot:run
endlocal
