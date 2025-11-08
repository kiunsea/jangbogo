@echo off
REM Jangbogo 개발 테스트 실행 스크립트
setlocal enabledelayedexpansion

REM 한글 출력을 위한 코드페이지 설정 (UTF-8)
chcp 65001 >nul 2>&1

REM 프로젝트 루트 디렉토리로 이동 (bat 폴더의 상위 디렉토리)
cd /d "%~dp0\.."

echo ========================================================
echo   Jangbogo 개발 테스트 실행
echo ========================================================
echo.

REM 현재 디렉토리 확인
echo 작업 디렉토리: %CD%
echo.

REM Gradle 확인
if not exist "gradlew.bat" (
    echo [오류] gradlew.bat 파일을 찾을 수 없습니다.
    echo 프로젝트 루트 디렉토리: %CD%
    pause
    exit /b 1
)

echo Gradle bootRun 태스크를 실행합니다...
echo 포트: 8282
echo 접속: http://localhost:8282
echo.
echo 종료하려면 Ctrl+C를 누르세요.
echo ========================================================
echo.

REM Spring Boot 애플리케이션 실행 (개발 모드)
call gradlew.bat bootRun

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ========================================================
    echo   오류가 발생했습니다!
    echo   로그를 확인해주세요.
    echo ========================================================
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo ========================================================
echo   애플리케이션이 종료되었습니다.
echo ========================================================
pause

