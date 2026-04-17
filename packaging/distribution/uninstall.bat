@echo off
setlocal EnableExtensions
chcp 65001 >nul 2>&1
cd /d "%~dp0"

echo ========================================
echo Jangbogo Integrated Uninstall
echo ========================================

REM -- Admin privilege check --
NET SESSION >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Administrator privileges required.
    echo Right-click this file and choose "Run as administrator".
    pause
    exit /b 1
)

echo.
echo [1/4] Stop and uninstall Windows service...
if exist "service\jangbogo-service.exe" (
    pushd service
    jangbogo-service.exe stop >nul 2>&1
    jangbogo-service.exe uninstall >nul 2>&1
    jangbogo-service.exe status
    popd
) else (
    echo [WARN] service\jangbogo-service.exe not found. Skipping service uninstall.
)

echo.
echo [2/4] Close tray app and Jangbogo app processes...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$tray = Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -like '*Jangbogo-Tray.ps1*' }; foreach ($p in $tray) { Invoke-CimMethod -InputObject $p -MethodName Terminate | Out-Null }; $app = Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -like '*jangbogo-*.jar*' }; foreach ($p in $app) { Invoke-CimMethod -InputObject $p -MethodName Terminate | Out-Null }; Write-Host '[INFO] Related processes terminated.'"

echo.
echo [3/4] Delete desktop/start-menu shortcuts...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$desktop = [Environment]::GetFolderPath('Desktop'); $programs = [Environment]::GetFolderPath('Programs'); $targets = @((Join-Path $desktop 'Jangbogo Tray.lnk'), (Join-Path $desktop 'Jangbogo Dashboard.url'), (Join-Path $programs 'Jangbogo Tray.lnk')); foreach ($t in $targets) { if (Test-Path $t) { Remove-Item $t -Force; Write-Host ('[DEL] ' + $t) } else { Write-Host ('[SKIP] ' + $t) } }"

echo.
echo [4/4] Finalize...
echo [OK] Uninstall completed.
echo [INFO] Runtime data (db, logs, exports) was preserved.
echo [INFO] If needed, delete this folder manually.
pause
