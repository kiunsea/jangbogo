@echo off
REM ===========================================================================
REM  Restart-Tray.bat
REM
REM  Forces a clean restart of the Jangbogo system tray icon.
REM
REM  Use this when:
REM    - Tray icon is missing after Windows reboot (explorer NotifyIcon refresh
REM      glitch leaves the process alive but invisible).
REM    - You want to ensure exactly one tray instance is running.
REM
REM  No administrator privileges required.
REM ===========================================================================
setlocal

set "SCRIPT_DIR=%~dp0"
set "TRAY_PS1=%SCRIPT_DIR%Jangbogo-Tray.ps1"

if not exist "%TRAY_PS1%" (
    echo [ERROR] Jangbogo-Tray.ps1 not found in: %SCRIPT_DIR%
    pause
    exit /b 1
)

echo [INFO] Restarting Jangbogo tray icon...
start "" /B powershell.exe -NoProfile -ExecutionPolicy Bypass -STA -WindowStyle Hidden -File "%TRAY_PS1%" -Restart

echo [INFO] Tray restart requested. The icon should appear in the system tray shortly.
echo        If it still does not appear, log out and back in, or reboot once more.

REM Brief delay so user can read the message before window closes
timeout /T 3 /NOBREAK >nul

endlocal
exit /b 0
