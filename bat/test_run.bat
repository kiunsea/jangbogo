@echo off
REM ============================================================
REM  Jangbogo 개발 테스트 실행 — 소스에서 바로 띄운다
REM
REM  이 스크립트의 역할은 "지금 소스에 있는 수정 사항을 그대로 실행해 보는 것" 이다.
REM  jar 을 만들지 않고 컴파일된 클래스로 띄우므로 devtools 핫리로드가 살아 있다.
REM
REM  패키징된 jar 을 확인하려면 run_jar.bat 을 써라. 역할이 다르다.
REM
REM  [지우지 않는다]
REM  예전에는 여기서 gradlew clean 과 rmdir /s /q build bin .gradle 을 돌렸다.
REM  둘 다 들어냈다. 이 프로젝트는 배포 패키지를 build\distributions 아래에 풀어
REM  그 자리에서 실행하는 관행이 있고, 그렇게 실행된 인스턴스는 자기 db\ 를 그 안에
REM  만든다. 즉 build\ 아래에 "지워도 되는 빌드 산출물" 과 "지우면 안 되는 실제
REM  구매 내역" 이 섞인다. 실제로 그 한 줄에 배포본 인스턴스와 DB 가 통째로 사라진
REM  적이 있고, Gradle 이든 rmdir 이든 휴지통을 거치지 않아 복구 수단이 없었다.
REM
REM  특히 rmdir 은 build.gradle 의 clean 가드를 우회한다 — Gradle 을 거치지 않기
REM  때문이다. 개발 반복에 매번 전체 삭제가 필요하지도 않다.
REM  정말로 클린 빌드가 필요하면 clean_build.bat 을 써라(그쪽은 가드가 지킨다).
REM ============================================================
setlocal enabledelayedexpansion

REM 한글 출력을 위한 코드페이지 설정 (UTF-8)
chcp 65001 >nul 2>&1

REM 프로젝트 루트 디렉토리로 이동 (bat 폴더의 상위 디렉토리)
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
REM  쇼핑몰에 로그인한다. 개발 중 스크립트를 여러 번 돌리면 같은 계정으로 반복
REM  로그인하게 되고, 그것이 바로 쇼핑몰이 차단하는 패턴이다.
REM
REM  수집까지 함께 보려면 인자로 명시해라:
REM     test_run.bat --jangbogo.startup.collect.enabled=true
REM  그 밖의 인자도 그대로 애플리케이션에 전달된다. 예:
REM     test_run.bat -Dnothing --server.port=8283
REM ------------------------------------------------------------
set "APP_ARGS=--jangbogo.startup.collect.enabled=false"
if not "%~1"=="" (
    echo %*| findstr /i /c:"startup.collect" >nul
    if !ERRORLEVEL! EQU 0 (
        REM 사용자가 직접 지정했으므로 기본값을 얹지 않는다 (중복 지정 시 해석이 갈린다).
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
