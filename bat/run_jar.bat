@echo off
REM ============================================================
REM  Jangbogo - run the packaged JAR
REM
REM  Runs the JAR produced by clean_build.bat or build_package.bat, to confirm
REM  the actual artifact works. Different purpose from test_run.bat, which runs
REM  from source.
REM
REM  Why a separate script: bootRun and "java -jar" are not the same. Classpath
REM  ordering, resource loading (loose files vs JAR entries) and relative path
REM  resolution in spring.config.import all differ, so "works from source but
REM  not from the JAR" really happens. What ships is the JAR.
REM
REM  Note: this runs the development tree's JAR from the project root, so it
REM  uses the development DB (db\jangbogo-dev.db). To reproduce a real install,
REM  unzip the distribution OUTSIDE the repository (e.g. D:\Jangbogo) and use
REM  its Jangbogo.bat - that one uses the bundled JRE and its own db\ folder.
REM
REM  ALL COMMENTS IN THIS FILE ARE ASCII ON PURPOSE - see test_run.bat header.
REM ============================================================
chcp 65001 >nul 2>&1
setlocal enabledelayedexpansion

cd /d "%~dp0\.."

echo ========================================================
echo   Jangbogo 패키징 산출물 실행 (jar 기반)
echo ========================================================
echo.
echo 작업 디렉토리: %CD%
echo.

REM Pick the newest JAR. Never hardcode a version - build.gradle owns it.
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
REM  Startup collection is OFF by default - same reason as test_run.bat.
REM  application.yml has it enabled, so without this the app logs in to the
REM  real shopping malls right after boot.
REM
REM  To include collection:  run_jar.bat --jangbogo.startup.collect.enabled=true
REM  The session-profile killswitch needs -D BEFORE -jar, so run that directly:
REM    java -Djangbogo.session-profile.enabled=true -jar <jar> --jangbogo.startup.collect.enabled=false
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
