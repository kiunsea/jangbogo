@echo off
REM Jangbogo 제거 전 스크립트
REM 이 스크립트는 Jangbogo 제거 전에 자동으로 실행됩니다.

echo ========================================
echo Jangbogo 제거 준비 중...
echo ========================================

REM 설치 디렉토리 확인
set INSTALL_DIR=%~dp0..
cd /d "%INSTALL_DIR%"

echo 설치 디렉토리: %INSTALL_DIR%

REM 서비스 중지 및 제거
if exist "%INSTALL_DIR%\winsw\jangbogo-service.exe" (
    cd /d "%INSTALL_DIR%\winsw"
    
    REM 서비스가 존재하는지 확인
    sc query jangbogo >nul 2>&1
    if %ERRORLEVEL% EQU 0 (
        echo Jangbogo 서비스 중지 중...
        jangbogo-service.exe stop
        
        REM 서비스가 완전히 중지될 때까지 대기
        timeout /t 3 /nobreak >nul
        
        echo Jangbogo 서비스 제거 중...
        jangbogo-service.exe uninstall
        
        if %ERRORLEVEL% EQU 0 (
            echo 서비스 제거 완료
        ) else (
            echo [경고] 서비스 제거 실패. 수동으로 제거가 필요할 수 있습니다.
        )
    ) else (
        echo 등록된 서비스를 찾을 수 없습니다.
    )
) else (
    echo WinSW 서비스 파일을 찾을 수 없습니다.
)

REM 실행 중인 jangbogo 프로세스 종료
echo 실행 중인 프로세스 확인 중...
tasklist /FI "IMAGENAME eq jangbogo.exe" 2>NUL | find /I /N "jangbogo.exe">NUL
if "%ERRORLEVEL%"=="0" (
    echo jangbogo.exe 프로세스 종료 중...
    taskkill /F /IM jangbogo.exe >nul 2>&1
    timeout /t 1 /nobreak >nul
)

REM Java 프로세스 중 jangbogo 관련 프로세스 종료
tasklist /FI "IMAGENAME eq java.exe" 2>NUL | find /I /N "java.exe">NUL
if "%ERRORLEVEL%"=="0" (
    echo Java 프로세스 확인 중...
    REM 8282 포트를 사용하는 프로세스 종료
    for /f "tokens=5" %%a in ('netstat -ano ^| findstr :8282 ^| findstr LISTENING') do (
        echo 포트 8282를 사용하는 프로세스(%%a) 종료 중...
        taskkill /F /PID %%a >nul 2>&1
    )
)

echo ========================================
echo 제거 준비 완료
echo ========================================

timeout /t 2

exit /b 0

