@echo off
setlocal EnableDelayedExpansion
title NeuroSys Agent - Start

:: 1. Check Administrator Privileges & Elevate if Needed
net session >nul 2>&1
if %errorlevel% neq 0 (
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Start-Process '%~f0' -Verb RunAs" >nul 2>&1
    if %errorlevel% equ 0 exit /b
)

cd /d "%~dp0"

if exist "%~dp0cache\stopped.flag" del /f /q "%~dp0cache\stopped.flag" >nul 2>&1

echo ===================================================
echo  NeuroSys Agent Control - Start
echo ===================================================
echo.

:: 2. Resolve Agent JAR File
set "ACTIVE_JAR="
if exist "%~dp0neurosys-agent-1.0.0-SNAPSHOT-exec.jar" set "ACTIVE_JAR=%~dp0neurosys-agent-1.0.0-SNAPSHOT-exec.jar"
if not defined ACTIVE_JAR if exist "%~dp0neurosys-agent-1.0.0.jar" set "ACTIVE_JAR=%~dp0neurosys-agent-1.0.0.jar"
if not defined ACTIVE_JAR if exist "%~dp0neurosys-agent-1.0.0-SNAPSHOT.jar" set "ACTIVE_JAR=%~dp0neurosys-agent-1.0.0-SNAPSHOT.jar"
if not defined ACTIVE_JAR if exist "%~dp0target\neurosys-agent-1.0.0-SNAPSHOT-exec.jar" set "ACTIVE_JAR=%~dp0target\neurosys-agent-1.0.0-SNAPSHOT-exec.jar"

if not defined ACTIVE_JAR (
    echo [ERROR] NeuroSys Agent installation files are missing!
    echo Please run setup-agent.bat first to install the Agent.
    echo.
    ping -n 4 127.0.0.1 >nul
    exit /b 1
)

:: 3. Register Windows Silent Auto-Boot Scheduled Task & Startup Shortcut Backup
powershell -NoProfile -ExecutionPolicy Bypass -Command "$TaskName = 'NeuroSysAgent'; $ScriptDir = '%~dp0'; $WScriptExe = Join-Path $env:SystemRoot 'System32\wscript.exe'; $SilentVbs = Join-Path $ScriptDir 'Run-Silent.vbs'; $BatLauncher = Join-Path $ScriptDir 'Run-NeuroSys-Agent.bat'; try { if (-not (Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue)) { $Action = New-ScheduledTaskAction -Execute $WScriptExe -ArgumentList \"`\"$SilentVbs`\" `\"$BatLauncher`\"\" -WorkingDirectory $ScriptDir; $TriggerLogon = New-ScheduledTaskTrigger -AtLogon; $Settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable; Register-ScheduledTask -TaskName $TaskName -Action $Action -Trigger $TriggerLogon -Settings $Settings -User $env:USERNAME -Force | Out-Null } } catch {}; try { $StartupFolder = [Environment]::GetFolderPath('Startup'); $ShortcutPath = Join-Path $StartupFolder 'NeuroSysAgent.lnk'; if (-not (Test-Path $ShortcutPath)) { $WScriptShell = New-Object -ComObject WScript.Shell; $Shortcut = $WScriptShell.CreateShortcut($ShortcutPath); $Shortcut.TargetPath = $WScriptExe; $Shortcut.Arguments = \"`\"$SilentVbs`\" `\"$BatLauncher`\"\"; $Shortcut.WorkingDirectory = $ScriptDir; $Shortcut.WindowStyle = 7; $Shortcut.Save() } } catch {}" >nul 2>&1

:: 4. Check if already running
set "ALREADY_RUNNING=0"
for /f "tokens=*" %%P in ('powershell -NoProfile -ExecutionPolicy Bypass -Command "Get-CimInstance Win32_Process | Where-Object { ($_.Name -eq 'java.exe' -or $_.Name -eq 'javaw.exe') -and $_.CommandLine -like '*neurosys-agent*' } | Select-Object -ExpandProperty ProcessId"') do (
    if not "%%P"=="" set "ALREADY_RUNNING=1"
)

if "%ALREADY_RUNNING%"=="1" (
    echo NeuroSys Agent is running silently in the background.
    echo Status: RUNNING
    echo.
    ping -n 3 127.0.0.1 >nul
    exit /b 0
)

:: 5. Start Silent Background Daemon
echo Starting NeuroSys Agent silently in the background...
powershell -NoProfile -ExecutionPolicy Bypass -Command "if (Get-ScheduledTask -TaskName 'NeuroSysAgent' -ErrorAction SilentlyContinue) { Start-ScheduledTask -TaskName 'NeuroSysAgent' } else { wscript.exe '%~dp0Run-Silent.vbs' '%~dp0Run-NeuroSys-Agent.bat' }" >nul 2>&1

:: Wait 3 seconds for JVM startup
ping -n 4 127.0.0.1 >nul

:: 6. Verify status
set "IS_RUNNING=0"
for /f "tokens=*" %%P in ('powershell -NoProfile -ExecutionPolicy Bypass -Command "Get-CimInstance Win32_Process | Where-Object { ($_.Name -eq 'java.exe' -or $_.Name -eq 'javaw.exe') -and $_.CommandLine -like '*neurosys-agent*' } | Select-Object -ExpandProperty ProcessId"') do (
    if not "%%P"=="" set "IS_RUNNING=1"
)

if "%IS_RUNNING%"=="1" (
    echo.
    echo NeuroSys Agent is running silently in the background.
    echo Status: RUNNING
    echo.
    echo (This control window will close automatically. The Agent will continue running silently.)
    echo.
    ping -n 3 127.0.0.1 >nul
    exit /b 0
) else (
    echo.
    echo [FAILED] Could not verify Agent process startup.
    echo Please run setup-agent.bat first.
    echo.
    ping -n 4 127.0.0.1 >nul
    exit /b 1
)
