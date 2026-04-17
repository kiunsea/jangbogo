@echo off
setlocal EnableExtensions
chcp 65001 >nul 2>&1
cd /d "%~dp0"

echo ========================================
echo Jangbogo Integrated Install
echo ========================================

set "DASHBOARD_URL=http://127.0.0.1:8282"
set "DASHBOARD_WAIT_SEC=45"
set "SERVICE_PORT=8282"
set "SERVICE_ID=jangbogo"
set "APP_JAR="

REM -- Locate JAR (newest first) --
for /f "delims=" %%F in ('dir /b /o:-d "jangbogo-*.jar" 2^>nul') do (
    if not defined APP_JAR set "APP_JAR=%%F"
)

REM -- Admin privilege check --
NET SESSION >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Administrator privileges required.
    echo Right-click this file and choose "Run as administrator".
    pause
    exit /b 1
)

if not exist "service\jangbogo-service.exe" (
    echo [ERROR] service\jangbogo-service.exe not found.
    echo Please place the WinSW executable first.
    pause
    exit /b 1
)

if not defined APP_JAR (
    echo [ERROR] jangbogo-*.jar not found in current folder.
    pause
    exit /b 1
)

echo [INFO] Application JAR: %APP_JAR%

if not exist "Jangbogo-Tray.ps1" (
    echo [ERROR] Jangbogo-Tray.ps1 not found.
    pause
    exit /b 1
)

echo.
echo [0/5] Unblock downloaded files...
powershell -NoProfile -ExecutionPolicy Bypass -Command "Get-ChildItem -Path . -Recurse -File | Unblock-File -ErrorAction SilentlyContinue"

echo.
echo [1/5] Ensure bundled JRE is available...
if not exist "jre\bin\java.exe" (
    if exist "download-jre.ps1" (
        powershell -NoProfile -ExecutionPolicy Bypass -File ".\download-jre.ps1"
    ) else (
        echo [ERROR] download-jre.ps1 not found.
        pause
        exit /b 1
    )
)

if not exist "jre\bin\java.exe" (
    echo [ERROR] Bundled JRE setup failed. Cannot continue.
    pause
    exit /b 1
)

echo.
echo [INFO] Sync service XML with detected JAR...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$jar='%APP_JAR%'; $path='service\\jangbogo-service.xml'; try { [xml]$xml = Get-Content -Raw -Encoding UTF8 $path; $node = $xml.SelectSingleNode('/service/arguments'); if ($null -eq $node) { throw 'Missing /service/arguments node.' }; $node.InnerText = ('-Xms256m -Xmx1024m -jar \"%%BASE%%\\..\\{0}\" --service' -f $jar); $xml.Save((Resolve-Path $path)); [xml]$verify = Get-Content -Raw -Encoding UTF8 $path; $verifyNode = $verify.SelectSingleNode('/service/arguments'); Write-Host ('[INFO] Service XML sync complete: ' + $verifyNode.InnerText) } catch { Write-Host ('[ERROR] Failed to sync service XML: ' + $_.Exception.Message); exit 1 }"
if errorlevel 1 (
    echo [ERROR] Service XML sync failed. Install aborted.
    pause
    exit /b 1
)

echo.
echo [OPTION] Cleanup existing Jangbogo processes before service start
echo [WARN] If selected, any running Jangbogo app/tray processes will be terminated.
set "CLEANUP_OLD="
set /p CLEANUP_OLD=Do cleanup now? [Y/N, default N]:
if /I "%CLEANUP_OLD%"=="Y" (
    echo [INFO] Stopping existing Jangbogo service if present...
    if exist "service\jangbogo-service.exe" (
        pushd service
        jangbogo-service.exe stop >nul 2>&1
        popd
    )

    echo [INFO] Terminating existing Jangbogo app/tray processes...
    powershell -NoProfile -ExecutionPolicy Bypass -Command "$targets = Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -and ( $_.CommandLine -like '*Jangbogo-Tray.ps1*' -or $_.CommandLine -like '*jangbogo-*.jar*' ) }; if (-not $targets) { Write-Host '[INFO] No existing Jangbogo processes found.'; exit 0 }; foreach ($p in $targets) { try { Invoke-CimMethod -InputObject $p -MethodName Terminate | Out-Null; Write-Host ('[KILL] PID ' + $p.ProcessId) } catch { Write-Host ('[WARN] Failed to terminate PID ' + $p.ProcessId + ': ' + $_.Exception.Message) } }"
) else (
    echo [INFO] Skipping existing process cleanup.
)

echo.
echo [INFO] Checking service port availability (%SERVICE_PORT%)...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$p=%SERVICE_PORT%; $listeners = Get-NetTCPConnection -State Listen -LocalPort $p -ErrorAction SilentlyContinue; if ($listeners) { $pids = ($listeners | Select-Object -ExpandProperty OwningProcess -Unique) -join ', '; Write-Host ('[ERROR] Port ' + $p + ' is already in use. PID: ' + $pids); exit 1 } else { Write-Host ('[INFO] Port ' + $p + ' is available.') }"
if errorlevel 1 (
    echo [ERROR] Please stop the process using port %SERVICE_PORT% and run install again.
    pause
    exit /b 1
)

REM -- Prepare runtime directories --
if not exist "db"      mkdir db
if not exist "logs"    mkdir logs
if not exist "exports" mkdir exports

echo.
echo [2/5] Install and start Windows service...
pushd service
jangbogo-service.exe install >nul 2>&1
jangbogo-service.exe start
if errorlevel 1 (
    echo [ERROR] Service start failed.
    popd
    pause
    exit /b 1
)
jangbogo-service.exe status
popd

set "_SERVICE_RUNNING=0"
for /l %%I in (1,1,20) do (
    sc query %SERVICE_ID% | find "RUNNING" >nul
    if not errorlevel 1 (
        set "_SERVICE_RUNNING=1"
        goto :service_running
    )
    timeout /t 1 /nobreak >nul
)

:service_running
if "%_SERVICE_RUNNING%"=="0" (
    echo [ERROR] Service is not RUNNING yet.
    echo [INFO] Current SCM state:
    sc query %SERVICE_ID%

    echo [INFO] Collecting recent logs from service\logs and logs...
    powershell -NoProfile -ExecutionPolicy Bypass -Command "if (Test-Path 'service\\logs') { $files = Get-ChildItem -Path 'service\\logs' -File | Sort-Object LastWriteTime -Descending; if ($files) { foreach ($f in $files | Select-Object -First 5) { Write-Host (''); Write-Host ('[INFO] Service log file: ' + $f.FullName); Get-Content -Path $f.FullName -Tail 80 } } else { Write-Host '[INFO] No files found in service\\logs.' } } else { Write-Host '[INFO] service\\logs folder not found.' }"

    if exist "logs\jangbogo.log" (
        echo.
        echo [INFO] Last application logs:
        powershell -NoProfile -ExecutionPolicy Bypass -Command "Get-Content -Path 'logs\\jangbogo.log' -Tail 80"
    ) else (
        echo [INFO] Application log not found: logs\jangbogo.log
    )

    echo.
    echo [INFO] Tip: verify Java process account permission for this install folder.
    pause
    exit /b 1
)

echo [INFO] Waiting for dashboard endpoint (max %DASHBOARD_WAIT_SEC%s)...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$uri='%DASHBOARD_URL%/'; $deadline=(Get-Date).AddSeconds(%DASHBOARD_WAIT_SEC%); $ok=$false; while ((Get-Date) -lt $deadline) { try { $r = Invoke-WebRequest -UseBasicParsing -Uri $uri -TimeoutSec 2; if ($r.StatusCode -ge 200 -and $r.StatusCode -lt 400) { $ok=$true; break } } catch { }; Start-Sleep -Milliseconds 500 }; if ($ok) { Write-Host '[INFO] Dashboard endpoint is reachable.'; exit 0 } else { Write-Host '[WARN] Dashboard endpoint is not reachable within timeout.'; exit 1 }"
if errorlevel 1 (
    set "_READY=0"
    echo [WARN] Dashboard did not become ready within timeout.
    echo [INFO] Browser auto-open is skipped to avoid 404.
) else (
    set "_READY=1"
    echo [INFO] Dashboard is ready.
)

echo.
echo [3/5] Create desktop/start-menu shortcuts...
if exist "create-shortcuts.ps1" (
    powershell -NoProfile -ExecutionPolicy Bypass -File ".\create-shortcuts.ps1"
) else (
    echo [WARN] create-shortcuts.ps1 not found. Skipping shortcut creation.
)

echo.
echo [4/5] Start tray icon app...
start "Jangbogo Tray" powershell -NoProfile -ExecutionPolicy Bypass -STA -WindowStyle Hidden -File ".\Jangbogo-Tray.ps1"

echo.
echo [5/5] Open management dashboard in browser...
if "%_READY%"=="1" (
    start "" "%DASHBOARD_URL%"
) else (
    echo [INFO] Open manually after startup: %DASHBOARD_URL%
)

echo.
echo [OK] Install completed.
echo - Service: installed and started
echo - Tray icon: launched
echo - Dashboard: opened in browser
echo - User guide: 사용설명서.txt
echo - Install guide: 설치가이드.txt
pause
