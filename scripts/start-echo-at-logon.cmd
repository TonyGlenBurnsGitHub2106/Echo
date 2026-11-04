@echo off
rem Story 5653: what the Startup shortcut (Echo.lnk) and the installer's first start run. Windows never
rem elevates a sign-in start, so this one does NOT require administrator -- start-echo.cmd, run by hand,
rem does. Echo started this way runs as the signed-in person, so any window can stop it.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0Start-Echo.ps1" %*
if errorlevel 1 pause
