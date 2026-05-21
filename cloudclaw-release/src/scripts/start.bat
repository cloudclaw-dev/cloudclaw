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

start /b java %JAVA_OPTS% -Xms256m -Xmaxh512m ^
    -jar cloudclaw-app-1.0.0.jar ^
    --spring.profiles.active=%PROFILE% ^
    --spring.config.additional-location=file:./config/ ^
    > logs\cloudclaw.out 2>&1

echo Started.
endlocal
