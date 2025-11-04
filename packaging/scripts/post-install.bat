@echo off
REM Jangbogo 설치 후 스크립트
REM 이 스크립트는 Jangbogo 설치 완료 후 자동으로 실행됩니다.

echo ========================================
echo Jangbogo 설치 후 설정 중...
echo ========================================

REM 설치 디렉토리 확인
set INSTALL_DIR=%~dp0..
cd /d "%INSTALL_DIR%"

echo 설치 디렉토리: %INSTALL_DIR%

REM WinSW 서비스 파일 확인
if not exist "%INSTALL_DIR%\winsw\jangbogo-service.exe" (
    echo [오류] WinSW 서비스 파일을 찾을 수 없습니다.
    echo 위치: %INSTALL_DIR%\winsw\jangbogo-service.exe
    echo 서비스 등록을 건너뜁니다.
    goto SKIP_SERVICE
)

if not exist "%INSTALL_DIR%\winsw\jangbogo-service.xml" (
    echo [오류] WinSW 설정 파일을 찾을 수 없습니다.
    echo 위치: %INSTALL_DIR%\winsw\jangbogo-service.xml
    echo 서비스 등록을 건너뜁니다.
    goto SKIP_SERVICE
)

REM 서비스 디렉토리로 이동
cd /d "%INSTALL_DIR%\winsw"

REM 기존 서비스가 있는지 확인
sc query jangbogo >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo 기존 서비스 발견 - 중지 및 제거 중...
    jangbogo-service.exe stop
    timeout /t 2 /nobreak >nul
    jangbogo-service.exe uninstall
    timeout /t 1 /nobreak >nul
)

REM 서비스 설치
echo Jangbogo 서비스 등록 중...
jangbogo-service.exe install

if %ERRORLEVEL% EQU 0 (
    echo 서비스 등록 완료
    
    REM 서비스 시작
    echo 서비스 시작 중...
    jangbogo-service.exe start
    
    if %ERRORLEVEL% EQU 0 (
        echo 서비스 시작 완료
    ) else (
        echo [경고] 서비스 시작 실패. 수동으로 시작해주세요.
    )
) else (
    echo [오류] 서비스 등록 실패
    goto SKIP_SERVICE
)

:SKIP_SERVICE

REM 원래 디렉토리로 돌아가기
cd /d "%INSTALL_DIR%"

REM 트레이 애플리케이션 실행 (브라우저 자동 열기)
echo ========================================
echo 트레이 애플리케이션 시작 중...
echo ========================================

REM 서버가 시작될 때까지 잠시 대기
timeout /t 5 /nobreak >nul

REM 트레이 애플리케이션 실행 (백그라운드)
if exist "%INSTALL_DIR%\jangbogo.exe" (
    start "" "%INSTALL_DIR%\jangbogo.exe" --install-complete
    echo 트레이 애플리케이션 시작 완료
) else (
    echo [경고] jangbogo.exe를 찾을 수 없습니다.
)

echo ========================================
echo 설치 완료!
echo 브라우저가 자동으로 열리지 않으면
echo http://127.0.0.1:8282 로 접속하세요.
echo ========================================

REM 5초 후 자동으로 창 닫기
timeout /t 5

exit /b 0

