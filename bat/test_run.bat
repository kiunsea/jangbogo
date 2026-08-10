@echo off
REM ============================================================
REM  Jangbogo - development run (from source)
REM
REM  Runs whatever is in the source tree right now. No JAR is built; Gradle
REM  compiles and launches the classes directly, so devtools hot reload works.
REM  To verify a packaged JAR instead, use run_jar.bat - different purpose.
REM
REM  THIS SCRIPT DELETES NOTHING. It used to run "gradlew clean" and then
REM  "rmdir /s /q build bin .gradle". Both are gone. The distribution package
REM  is often unzipped under build\distributions and run in place, and such an
REM  instance creates its own db\ there - so build\ mixes throwaway build output
REM  with real purchase history. One delete wiped it for good (no recycle bin).
REM  rmdir also bypasses the clean guard in build.gradle because Gradle never
REM  sees it. Need a clean build? Use clean_build.bat - the guard covers it.
REM
REM  ALL COMMENTS IN THIS FILE ARE ASCII ON PURPOSE. cmd parses a batch file
REM  line by line using the current codepage. Korean text in a REM line is read
REM  as CP949, the byte pairing shifts, and a word inside the comment ends up
REM  being executed as a command ("'xxx' is not recognized..."). chcp 65001 is
REM  not enough. BuildScriptHygieneTest enforces ASCII-only comments.
REM  Korean is fine in echo output - those bytes are only displayed, not parsed.
REM ============================================================
chcp 65001 >nul 2>&1
setlocal enabledelayedexpansion

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
REM  Startup collection is OFF by default.
REM
REM  application.yml sets jangbogo.startup.collect.enabled to true. Without the
REM  override below, every run of this script would kick off a one-shot collect
REM  and restore the schedule right after boot - which means logging in to the
REM  real shopping malls. Repeated logins from the same account during
REM  development is exactly the pattern those sites block.
REM
REM  To include collection:  test_run.bat --jangbogo.startup.collect.enabled=true
REM  Any other argument is forwarded to the application as-is.
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

REM Disable template / static resource caching for development convenience.
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
