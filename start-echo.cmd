@echo off
rem Story 5653: fail fast -- stop here, before anything runs, unless this window is administrator.
call "%~dp0scripts\require-admin.cmd" start-echo.cmd || exit /b 1
rem Echo (Story 5653): runs scripts\Start-Echo.ps1 -- PowerShell scripts need no signing when started this way.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\Start-Echo.ps1" %*
if errorlevel 1 pause
