@echo off
rem Echo (Story 5653): runs scripts\Test-Echo.ps1 -- the Spock unit and web specs (no database, no browser).
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\Test-Echo.ps1" %*
set "ECHO_RC=%errorlevel%"
rem Story 5653: always wait for a key, so the last message can be read before the window closes.
echo.
pause
exit /b %ECHO_RC%
