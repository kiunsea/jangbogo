@echo off
REM Jangbogo 클린 빌드 스크립트
setlocal enabledelayedexpansion

REM 한글 출력을 위한 코드페이지 설정 (UTF-8)
chcp 65001 >nul 2>&1

REM 프로젝트 루트 디렉토리로 이동 (bat 폴더의 상위 디렉토리)
cd /d "%~dp0\.."

echo ========================================================
echo   Jangbogo 클린 빌드
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

echo 이전 빌드 결과를 삭제하고 새로 빌드합니다...
echo.
echo ========================================================
echo.

REM 클린 빌드
call gradlew.bat clean build

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ========================================================
    echo   빌드 실패!
    echo   오류 메시지를 확인해주세요.
    echo ========================================================
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo ========================================================
echo   클린 빌드 완료!
echo ========================================================
echo.
REM 버전을 적지 않는다 — 버전의 단일 출처는 build.gradle 의 version 하나다.
REM 실제 산출물은 실행 시점에 와일드카드로 찾는다 (install.bat 과 같은 방식).
REM plain JAR 은 build.gradle 에서 꺼져 있어 bootJar 산출물 하나만 잡힌다.
echo 생성된 파일:
set JAR_FOUND=0
for %%A in ("build\libs\jangbogo-*.jar") do (
    set JAR_FOUND=1
    set size=%%~zA
    set /a sizeMB=!size! / 1048576
    echo   - %%A ^(!sizeMB! MB^)
)
if !JAR_FOUND! EQU 0 (
    echo   [경고] build\libs\ 에 jangbogo-*.jar 가 없습니다.
)

echo.
pause

