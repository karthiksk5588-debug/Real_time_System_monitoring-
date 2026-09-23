@echo off
setlocal EnableDelayedExpansion
title NeuroSys Agent - Stop

:: 1. Check Administrator Privileges & Elevate if Needed
net session >nul 2>&1
if %errorlevel% neq 0 (
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Start-Process '%~f0' -Verb RunAs" >nul 2>&1
    if %errorlevel% equ 0 exit /b
)

cd /d "%~dp0"

if not exist "%~dp0cache" mkdir "%~dp0cache" >nul 2>&1
echo stopped > "%~dp0cache\stopped.flag"
if exist "%~dp0cache\agent-status.json" del /f /q "%~dp0cache\agent-status.json" >nul 2>&1

echo ===================================================
echo  NeuroSys Agent Control - Stop
echo ===================================================
echo.

:: 2. Stop and Unregister Scheduled Task
powershell -NoProfile -ExecutionPolicy Bypass -Command "if (Get-ScheduledTask -TaskName 'NeuroSysAgent' -ErrorAction SilentlyContinue) { Stop-ScheduledTask -TaskName 'NeuroSysAgent' -ErrorAction SilentlyContinue; Unregister-ScheduledTask -TaskName 'NeuroSysAgent' -Confirm:$false -ErrorAction SilentlyContinue }" >nul 2>&1

:: 3. Remove Windows Startup Shortcuts
powershell -NoProfile -ExecutionPolicy Bypass -Command "$u = [Environment]::GetFolderPath('Startup'); $uL = Join-Path $u 'NeuroSysAgent.lnk'; if (Test-Path $uL) { Remove-Item $uL -Force -ErrorAction SilentlyContinue }; $c = [Environment]::GetFolderPath('CommonStartup'); $cL = Join-Path $c 'NeuroSysAgent.lnk'; if (Test-Path $cL) { Remove-Item $cL -Force -ErrorAction SilentlyContinue }" >nul 2>&1

:: 4. Terminate Parent Batch Loop (cmd.exe) and VBScript launcher (wscript.exe)
powershell -NoProfile -ExecutionPolicy Bypass -Command "Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -like '*Run-NeuroSys-Agent*' -or $_.CommandLine -like '*Run-Silent*' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }" >nul 2>&1

:: 5. Terminate Agent Java Process (javaw.exe / java.exe)
powershell -NoProfile -ExecutionPolicy Bypass -Command "Get-CimInstance Win32_Process | Where-Object { ($_.Name -eq 'java.exe' -or $_.Name -eq 'javaw.exe') -and $_.CommandLine -like '*neurosys-agent*' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }" >nul 2>&1

ping -n 3 127.0.0.1 >nul

echo.
echo ===================================================
echo  NeuroSys Agent status: STOPPED
echo ===================================================
echo.
ping -n 3 127.0.0.1 >nul
