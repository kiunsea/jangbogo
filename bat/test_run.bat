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

echo Gradle 캐시 및 빌드 산출물을 초기화합니다...
echo.

REM Gradle Daemon 중지 (선택)
call gradlew.bat --stop >nul 2>&1

REM Gradle Clean 실행
call gradlew.bat clean
if %ERRORLEVEL% NEQ 0 (
    echo [오류] gradlew clean 실행에 실패했습니다.
    pause
    exit /b %ERRORLEVEL%
)

REM 추가 캐시/산출물 디렉토리 삭제
set CACHE_DIRS=build bin .gradle
for %%D in (%CACHE_DIRS%) do (
    if exist "%%D" (
        echo  - %%D 디렉토리를 삭제합니다.
        rmdir /s /q "%%D"
    )
)

echo.
echo Spring Boot 애플리케이션을 새 환경으로 실행합니다...
echo 포트: 8282
echo 접속: http://localhost:8282
echo.
echo 종료하려면 Ctrl+C를 누르세요.
echo ========================================================
echo.

REM 개발 편의를 위한 캐시 비활성화 (템플릿/정적 리소스)
set SPRING_THYMELEAF_CACHE=false
set SPRING_WEB_RESOURCES_CACHE_PERIOD=0

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

