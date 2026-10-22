@echo off
rem Echo (Story 5653): runs scripts\Import-Solution.ps1 -- PowerShell scripts need no signing when started this way.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\Import-Solution.ps1" %*
if errorlevel 1 pause
