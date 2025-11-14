@echo off
REM Jangbogo 애플리케이션 실행 스크립트
setlocal enabledelayedexpansion

REM 한글 출력을 위한 코드페이지 설정 (UTF-8)
chcp 65001 >nul 2>&1

REM 현재 디렉토리를 스크립트 위치로 설정
cd /d "%~dp0"

REM 번들된 JRE 확인
if exist "jre\bin\java.exe" (
    set "JAVA_CMD=jre\bin\java.exe"
    echo 번들된 Java 런타임을 사용합니다.
) else (
    REM 시스템 Java 확인
    where java >nul 2>&1
    if %ERRORLEVEL% NEQ 0 (
        echo ========================================
        echo   Java를 찾을 수 없습니다!
        echo   jre 폴더가 누락되었거나
        echo   시스템에 Java가 설치되어 있지 않습니다.
        echo ========================================
        pause
        exit /b 1
    )
    set "JAVA_CMD=java"
    echo 시스템 Java를 사용합니다.
)

REM 필요한 디렉토리 생성
if not exist "db" mkdir db
if not exist "logs" mkdir logs
if not exist "exports" mkdir exports

REM 애플리케이션 시작
echo ========================================
echo   Jangbogo 구매내역 수집 서비스 시작
echo ========================================
echo.
echo 브라우저가 자동으로 열립니다...
echo 접속 주소: http://localhost:8282
echo.
echo 종료하려면 Ctrl+C를 누르세요.
echo ========================================
echo.

"%JAVA_CMD%" -Xms256m -Xmx1024m -jar jangbogo-0.5.1.jar

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ========================================
    echo   오류가 발생했습니다!
    echo ========================================
    pause
)
