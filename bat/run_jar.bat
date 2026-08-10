@echo off
REM -- Set UTF-8 codepage FIRST. Everything below may contain Korean text, and cmd
REM -- parses this file line by line using the *current* codepage. If a multi-byte
REM -- character is read as CP949 the byte pairing shifts and can swallow the line
REM -- break, gluing the next command onto a comment. Keep this line above any
REM -- non-ASCII character. (BuildScriptHygieneTest enforces this.)
chcp 65001 >nul 2>&1
setlocal enabledelayedexpansion

REM ============================================================
REM  Jangbogo 패키징 산출물 실행 — 만들어진 jar 을 그대로 띄운다
REM
REM  clean_build.bat 또는 build_package.bat 이 만든 jar 이 실제로 도는지 확인하는
REM  자리다. 소스에서 띄우는 test_run.bat 과 역할이 다르다.
REM
REM  왜 따로 두는가: bootRun 과 jar 실행은 같지 않다. 클래스패스 구성 순서, 리소스
REM  로딩 방식(파일 vs JAR 엔트리), spring.config.import 의 상대 경로 해석이 달라서
REM  소스에서는 되는데 jar 에서 안 되는 경우가 실제로 생긴다. 배포되는 것은 jar 이다.
REM
REM  주의: 여기서 띄우는 것은 개발 트리의 jar 이고 작업 디렉터리도 프로젝트 루트라
REM  개발용 DB(db\jangbogo-dev.db)를 쓴다. 배포본을 그대로 재현하려면 배포 ZIP 을
REM  저장소 밖(예: D:\Jangbogo)에 풀고 그 폴더의 Jangbogo.bat 을 써라.
REM ============================================================

cd /d "%~dp0\.."

echo ========================================================
echo   Jangbogo 패키징 산출물 실행 (jar 기반)
echo ========================================================
echo.
echo 작업 디렉토리: %CD%
echo.

REM jar 을 찾는다 — 최신 것 하나. 버전을 적지 않는다(단일 출처는 build.gradle).
set "APP_JAR="
for /f "delims=" %%F in ('dir /b /o:-d "build\libs\jangbogo-*.jar" 2^>nul') do (
    if not defined APP_JAR set "APP_JAR=build\libs\%%F"
)

if not defined APP_JAR (
    echo [오류] build\libs\ 에 jangbogo-*.jar 가 없습니다.
    echo.
    echo 먼저 빌드해야 합니다:
    echo   bat\clean_build.bat        전체 클린 빌드
    echo   bat\build_package.bat      배포 패키지까지
    echo.
    echo 클린 없이 jar 만 다시 만들려면:  gradlew.bat bootJar
    pause
    exit /b 1
)

where java >nul 2>&1
if errorlevel 1 (
    echo [오류] PATH 에서 java 를 찾을 수 없습니다. JDK/JRE 21 이상이 필요합니다.
    pause
    exit /b 1
)

REM ------------------------------------------------------------
REM  기동 자동수집은 기본으로 끈다 (test_run.bat 과 같은 이유).
REM  application.yml 이 true 라서 끄지 않으면 부팅 직후 실계정에 로그인한다.
REM
REM  수집까지 보려면:  run_jar.bat --jangbogo.startup.collect.enabled=true
REM  세션 기능을 켜려면 -D 가 -jar 앞에 와야 하므로 아래를 직접 실행해라:
REM     java -Djangbogo.session-profile.enabled=true -jar <jar> --jangbogo.startup.collect.enabled=false
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

echo 실행할 JAR: !APP_JAR!
echo   전달 인자: !APP_ARGS!
echo   DB: %CD%\db\jangbogo-dev.db
echo.
echo   포트: 8282        접속: http://localhost:8282
echo 종료하려면 Ctrl+C를 누르세요.
echo ========================================================
echo.

java -Xms256m -Xmx1024m -jar "!APP_JAR!" !APP_ARGS!

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ========================================================
    echo   애플리케이션이 오류로 종료되었습니다.
    echo   포트 8282 가 이미 사용 중이면 --server.port=8283 처럼 지정하세요.
    echo ========================================================
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo ========================================================
echo   애플리케이션이 종료되었습니다.
echo ========================================================
pause
