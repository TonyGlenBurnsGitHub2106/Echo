@echo off
rem Story 5653: the fail-fast preflight every Echo .cmd calls FIRST, before PowerShell starts and before
rem anything is changed (Tony, 2026-11-04: "They do a ton of stuff, and then abort. Let's fail fast.").
rem   call "%~dp0scripts\require-admin.cmd" install.cmd || exit /b 1
rem Exit 0 when this window has administrator rights. Otherwise it says what to do, waits for a key and
rem exits 1. fltmc answers only an elevated window, and needs no service running (net session needs Server).
fltmc >nul 2>&1
if not errorlevel 1 exit /b 0
echo.
echo %~1 must be run as administrator. Nothing was changed.
echo   Do one of these, then run it again:
echo     - right-click %~1 and choose "Run as administrator", or
echo     - Start menu, type cmd, right-click Command Prompt, choose "Run as administrator",
echo       then cd to the Echo folder and run %~1.
echo.
pause
exit /b 1
