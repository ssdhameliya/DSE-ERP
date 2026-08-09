@echo off
"D:\PostgreSQL\18\pgsql\bin\pg_isready.exe" -h localhost -p 5432 >nul 2>&1
if %errorlevel% equ 0 exit /b 0
"D:\PostgreSQL\18\pgsql\bin\pg_ctl.exe" -D "D:\PostgreSQL\18\data" -l "D:\PostgreSQL\18\postgresql.log" -o "-p 5432" start
