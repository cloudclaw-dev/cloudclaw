@echo off
REM CloudClaw Start Script (Windows)
REM Usage: start.bat [standalone|cluster]

setlocal

set "PROFILE=%~1"
if "%PROFILE%"=="" set "PROFILE=standalone"

echo Starting CloudClaw (%PROFILE% mode)...
echo Logs: logs\cloudclaw.out
echo Access: http://localhost:8080

if not exist logs mkdir logs

start /b java %JAVA_OPTS% -Dfile.encoding=UTF-8 -Xms256m -Xmx512m ^
    -jar cloudclaw-app-*.jar ^
    --spring.profiles.active=%PROFILE% ^
    --spring.config.additional-location=file:./config/ ^
    > logs\cloudclaw.out 2>&1

echo Started.
endlocal
