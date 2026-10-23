@echo off
rem Echo (Story 5653): runs scripts\Stop-Echo.ps1 -- PowerShell scripts need no signing when started this way.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\Stop-Echo.ps1" %*
set "ECHO_RC=%errorlevel%"
rem Story 5653: always wait for a key, so the last message can be read before the window closes.
echo.
pause
exit /b %ECHO_RC%
