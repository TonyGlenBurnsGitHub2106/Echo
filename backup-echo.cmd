@echo off
rem Echo (Story 5653): runs scripts\Backup-EchoDatabase.ps1 -- PowerShell scripts need no signing when started this way.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\Backup-EchoDatabase.ps1" %*
if errorlevel 1 pause
