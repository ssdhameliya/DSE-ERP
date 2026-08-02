@echo off
REM === Generate timestamp in format YYYYMMDD_HHMMSS ===
for /f "tokens=1-4 delims=/ " %%a in ('date /t') do (
    set yyyy=%%d
    set mm=%%b
    set dd=%%c
)
for /f "tokens=1-2 delims=: " %%a in ('time /t') do (
    set hh=%%a
    set min=%%b
)
set timestamp=%yyyy%%mm%%dd%_%hh%%min%

REM === Define source and destination ===
set src=D:\JavaProject\DSE_Final\src
set dest=D:\Backups\DSE_Final_Backup_%timestamp%\src

REM === Perform backup ===
xcopy "%src%" "%dest%" /E /H /C /I

echo Backup completed: %dest%
pause
