@echo off
REM ============================================================
REM  Jangbogo 패키징 산출물 실행 — 만들어진 jar 을 그대로 띄운다
REM
REM  이 스크립트의 역할은 "clean_build.bat 또는 build_package.bat 이 만든 jar 이
REM  실제로 도는지 확인하는 것" 이다. 소스에서 띄우는 test_run.bat 과 역할이 다르다.
REM
REM  왜 따로 두는가: bootRun 과 jar 실행은 같지 않다. 클래스패스 구성 순서, 리소스
REM  로딩 방식(파일 vs JAR 엔트리), spring.config.import 의 상대 경로 해석이 달라서
REM  소스에서는 되는데 jar 에서 안 되는 경우가 실제로 생긴다. 배포되는 것은 jar 이므로
REM  릴리스 전에 이쪽으로 한 번 확인하는 자리가 필요하다.
REM
REM  주의: 여기서 띄우는 것은 개발 트리의 jar 이고 작업 디렉터리도 프로젝트 루트라
REM  개발용 DB(db\jangbogo-dev.db)를 쓴다. 배포본을 그대로 재현하려면 배포 ZIP 을
REM  저장소 밖(예: D:\Jangbogo)에 풀고 그 폴더의 Jangbogo.bat 을 써라 — 그쪽은
REM  번들 JRE 를 쓰고 자기 db\ 를 그 폴더에 만든다.
REM ============================================================
setlocal enabledelayedexpansion

REM 한글 출력을 위한 코드페이지 설정 (UTF-8)
chcp 65001 >nul 2>&1

REM 프로젝트 루트 디렉토리로 이동 (bat 폴더의 상위 디렉토리)
cd /d "%~dp0\.."

echo ========================================================
echo   Jangbogo 패키징 산출물 실행 (jar 기반)
echo ========================================================
echo.
echo 작업 디렉토리: %CD%
echo.

REM ------------------------------------------------------------
REM  jar 을 찾는다 — 최신 것 하나.
REM  버전을 적지 않는다. 버전의 단일 출처는 build.gradle 의 version 하나이고,
REM  여기에 숫자를 적으면 bump 할 때마다 반드시 어긋난다(clean_build.bat 과 같은 방식).
REM  plain JAR 은 build.gradle 에서 꺼져 있어 bootJar 산출물 하나만 잡힌다.
REM ------------------------------------------------------------
set "APP_JAR="
for /f "delims=" %%F in ('dir /b /o:-d "build\libs\jangbogo-*.jar" 2^>nul') do (
    if not defined APP_JAR set "APP_JAR=build\libs\%%F"
)

if not defined APP_JAR (
    echo [오류] build\libs\ 에 jangbogo-*.jar 가 없습니다.
    echo.
    echo 먼저 빌드해야 합니다:
    echo   bat\clean_build.bat        ^(전체 클린 빌드^)
    echo   bat\build_package.bat      ^(배포 패키지까지^)
    echo.
    echo 클린 없이 jar 만 다시 만들려면:
    echo   gradlew.bat bootJar
    pause
    exit /b 1
)

REM Java 확인 — 배포본은 번들 JRE 를 쓰지만 여기는 PATH 의 java 를 쓴다.
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
REM  세션 기능을 켜려면 -D 는 -jar 앞에 와야 한다. 그 경우 아래 명령을 직접 써라:
REM     java -Djangbogo.session-profile.enabled=true -jar <jar> --jangbogo.startup.collect.enabled=false
REM  (SessionProfilePolicy 가 System.getProperty 로만 읽으므로 -- 인자로는 안 켜진다.
REM   배포본에서는 설치 폴더의 config\application.yml 에 적으면 브리지가 옮겨 준다.)
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
for %%A in ("!APP_JAR!") do (
    set "JAR_SIZE=%%~zA"
    set /a JAR_MB=!JAR_SIZE! / 1048576
    echo   크기: !JAR_MB! MB    빌드시각: %%~tA
)
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
