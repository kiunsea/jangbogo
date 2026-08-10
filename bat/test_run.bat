@echo off
REM -- Set UTF-8 codepage FIRST. Everything below may contain Korean text, and cmd
REM -- parses this file line by line using the *current* codepage. If a multi-byte
REM -- character is read as CP949 the byte pairing shifts and can swallow the line
REM -- break, gluing the next command onto a comment. Keep this line above any
REM -- non-ASCII character. (BuildScriptHygieneTest enforces this.)
chcp 65001 >nul 2>&1
setlocal enabledelayedexpansion

REM ============================================================
REM  Jangbogo 개발 테스트 실행 — 소스에서 바로 띄운다
REM
REM  이 스크립트는 "지금 소스에 있는 수정 사항을 그대로 실행해 보는 것" 이 역할이다.
REM  jar 을 만들지 않고 컴파일된 클래스로 띄우므로 devtools 핫리로드가 살아 있다.
REM  패키징된 jar 을 확인하려면 run_jar.bat 을 써라. 역할이 다르다.
REM
REM  이 스크립트는 아무것도 지우지 않는다. 그 이유는 bat\README.md 에 적어 두었다.
REM  요약하면, build\ 아래에는 빌드 산출물과 배포본 인스턴스의 실제 구매 내역이
REM  섞여 있어서 한 번의 삭제로 복구 불가능한 데이터 손실이 난다.
REM  클린 빌드가 필요하면 clean_build.bat 을 써라.
REM ============================================================

cd /d "%~dp0\.."

echo ========================================================
echo   Jangbogo 개발 테스트 실행 (소스 기반)
echo ========================================================
echo.
echo 작업 디렉토리: %CD%
echo.

if not exist "gradlew.bat" (
    echo [오류] gradlew.bat 파일을 찾을 수 없습니다.
    echo 프로젝트 루트 디렉토리: %CD%
    pause
    exit /b 1
)

REM ------------------------------------------------------------
REM  기동 자동수집은 기본으로 끈다.
REM
REM  application.yml 의 jangbogo.startup.collect.enabled 는 true 다. 끄지 않으면
REM  이 스크립트를 돌릴 때마다 부팅 직후 1회 수집과 스케줄 복원이 돌면서 실제
REM  쇼핑몰에 로그인한다. 개발 중 여러 번 돌리면 같은 계정으로 반복 로그인하게
REM  되고, 그것이 바로 쇼핑몰이 차단하는 패턴이다.
REM
REM  수집까지 함께 보려면:  test_run.bat --jangbogo.startup.collect.enabled=true
REM  그 밖의 인자도 그대로 애플리케이션에 전달된다.
REM ------------------------------------------------------------
set "APP_ARGS=--jangbogo.startup.collect.enabled=false"
if not "%~1"=="" (
    echo %*| findstr /i /c:"startup.collect" >nul
    if !ERRORLEVEL! EQU 0 (
        set "APP_ARGS=%*"
    ) else (
        set "APP_ARGS=--jangbogo.startup.collect.enabled=false %*"
    )
)

echo Spring Boot 애플리케이션을 소스에서 실행합니다.
echo   포트: 8282        접속: http://localhost:8282
echo   전달 인자: !APP_ARGS!
echo   DB: %CD%\db\jangbogo-dev.db
echo.
echo 종료하려면 Ctrl+C를 누르세요.
echo ========================================================
echo.

REM 개발 편의를 위한 캐시 비활성화 (템플릿/정적 리소스)
set SPRING_THYMELEAF_CACHE=false
set SPRING_WEB_RESOURCES_CACHE_PERIOD=0

call gradlew.bat bootRun --args="!APP_ARGS!"

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
