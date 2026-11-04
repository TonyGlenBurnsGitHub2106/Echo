@echo off
rem Story 5653: fail fast -- stop here, before anything runs, unless this window is administrator.
call "%~dp0scripts\require-admin.cmd" backup-echo.cmd || exit /b 1
rem Echo (Story 5653): runs scripts\Backup-EchoDatabase.ps1 -- PowerShell scripts need no signing when started this way.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\Backup-EchoDatabase.ps1" %*
set "ECHO_RC=%errorlevel%"
rem Story 5653: always wait for a key, so the last message can be read before the window closes.
echo.
pause
exit /b %ECHO_RC%
