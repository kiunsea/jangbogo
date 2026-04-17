@echo off
setlocal enabledelayedexpansion
chcp 65001 >nul 2>&1

cd /d "%~dp0"
set "MIN_JAVA_MAJOR=21"

REM -- Locate JAR (newest first) --
set "APP_JAR="
for /f "delims=" %%F in ('dir /b /o:-d "jangbogo-*.jar" 2^>nul') do (
    if not defined APP_JAR set "APP_JAR=%%F"
)

if not defined APP_JAR (
    echo [ERROR] jangbogo-*.jar not found in current folder.
    pause
    exit /b 1
)

echo [INFO] JAR: %APP_JAR%

REM -- Locate Java --
if exist "jre\bin\java.exe" (
    set "JAVA_CMD=jre\bin\java.exe"
    echo [INFO] Using bundled JRE.
) else (
    where java >nul 2>&1
    if errorlevel 1 (
        echo [INFO] Java not found. Downloading bundled JRE...
        if not exist "download-jre.ps1" (
            echo [ERROR] download-jre.ps1 not found. Cannot auto-download JRE.
            pause
            exit /b 1
        )
        powershell -NoProfile -ExecutionPolicy Bypass -File ".\download-jre.ps1"
        if errorlevel 1 (
            echo [ERROR] JRE download failed.
            pause
            exit /b 1
        )
        if not exist "jre\bin\java.exe" (
            echo [ERROR] JRE setup incomplete. jre\bin\java.exe not found.
            pause
            exit /b 1
        )
        set "JAVA_CMD=jre\bin\java.exe"
        echo [INFO] Using downloaded bundled JRE.
    ) else (
        call :resolve_java_major java
        if not defined JAVA_MAJOR (
            echo [ERROR] Failed to detect system Java version.
            pause
            exit /b 1
        )

        if !JAVA_MAJOR! LSS %MIN_JAVA_MAJOR% (
            echo [WARN] System Java !JAVA_MAJOR! is lower than required %MIN_JAVA_MAJOR%.
            if exist "download-jre.ps1" (
                echo [INFO] Downloading bundled JRE for compatibility...
                powershell -NoProfile -ExecutionPolicy Bypass -File ".\download-jre.ps1"
                if errorlevel 1 (
                    echo [ERROR] JRE download failed.
                    pause
                    exit /b 1
                )
                if not exist "jre\bin\java.exe" (
                    echo [ERROR] JRE setup incomplete. jre\bin\java.exe not found.
                    pause
                    exit /b 1
                )
                set "JAVA_CMD=jre\bin\java.exe"
                echo [INFO] Using downloaded bundled JRE.
            ) else (
                echo [ERROR] download-jre.ps1 not found. Install Java %MIN_JAVA_MAJOR%+ or provide bundled jre\bin\java.exe.
                pause
                exit /b 1
            )
        ) else (
            set "JAVA_CMD=java"
            echo [INFO] Using system Java !JAVA_MAJOR!.
        )
    )
)

REM -- Prepare runtime directories --
if not exist "db"      mkdir db
if not exist "logs"    mkdir logs
if not exist "exports" mkdir exports

set "APP_PORT=8282"
call :ensure_port
if not defined APP_PORT exit /b 0

echo ========================================
echo   Jangbogo
echo   http://localhost:%APP_PORT%
echo   Runtime data: db\
echo   Logs: logs\
echo   Press Ctrl+C to stop.
echo ========================================
echo.

"%JAVA_CMD%" -Xms256m -Xmx1024m -jar "%APP_JAR%" --server.port=%APP_PORT%

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] Application exited with an error.
    pause
)

goto :eof

:ensure_port
call :is_port_in_use "%APP_PORT%"
if not defined PORT_IN_USE exit /b 0

echo [WARN] Default port %APP_PORT% is already in use.

:prompt_for_port
set "NEW_PORT="
set /p "NEW_PORT=Enter a new port (1024-65535) or press Enter to cancel: "

if not defined NEW_PORT (
    echo [INFO] Launch canceled.
    set "APP_PORT="
    exit /b 0
)

echo %NEW_PORT%| findstr /r "^[0-9][0-9]*$" >nul
if errorlevel 1 (
    echo [ERROR] Invalid port. Numbers only.
    goto :prompt_for_port
)

set /a PORT_VALUE=%NEW_PORT% >nul 2>&1
if %PORT_VALUE% LSS 1024 (
    echo [ERROR] Invalid port. Use 1024-65535.
    goto :prompt_for_port
)
if %PORT_VALUE% GTR 65535 (
    echo [ERROR] Invalid port. Use 1024-65535.
    goto :prompt_for_port
)

call :is_port_in_use "%NEW_PORT%"
if defined PORT_IN_USE (
    echo [ERROR] Port %NEW_PORT% is also in use.
    goto :prompt_for_port
)

set "APP_PORT=%NEW_PORT%"
exit /b 0

:is_port_in_use
set "CHECK_PORT=%~1"
set "PORT_IN_USE="

for /f "usebackq delims=" %%P in (`powershell -NoProfile -ExecutionPolicy Bypass -Command "$p = Get-NetTCPConnection -LocalPort %CHECK_PORT% -State Listen -ErrorAction SilentlyContinue ^| Select-Object -First 1 -ExpandProperty OwningProcess; if ($p) { Write-Output $p }"`) do (
    set "PORT_IN_USE=1"
)

if not defined PORT_IN_USE (
    for /f "tokens=5" %%P in ('netstat -ano ^| findstr /r /c:":%CHECK_PORT% .*LISTENING"') do (
        set "PORT_IN_USE=1"
    )
)

exit /b 0

:resolve_java_major
set "JAVA_MAJOR="
set "JAVA_VER_RAW="

for /f "tokens=3" %%V in ('%~1 -version 2^>^&1 ^| findstr /i "version"') do (
    set "JAVA_VER_RAW=%%~V"
    goto :resolve_java_major_parse
)

:resolve_java_major_parse
if not defined JAVA_VER_RAW exit /b 0

set "JAVA_VER_RAW=%JAVA_VER_RAW:"=%"
for /f "tokens=1 delims=._-" %%M in ("%JAVA_VER_RAW%") do set "JAVA_MAJOR=%%M"

if "%JAVA_MAJOR%"=="1" (
    for /f "tokens=2 delims=._-" %%M in ("%JAVA_VER_RAW%") do set "JAVA_MAJOR=%%M"
)

exit /b 0
