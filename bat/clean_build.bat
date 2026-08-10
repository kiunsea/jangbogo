@echo off
REM -- Set UTF-8 codepage FIRST, above any non-ASCII byte. cmd parses this file line
REM -- by line in the *current* codepage; Korean text read as CP949 can shift the byte
REM -- pairing and swallow a line break, gluing the next command onto a comment.
chcp 65001 >nul 2>&1
setlocal enabledelayedexpansion

REM Jangbogo - clean build script

REM Move to the project root (parent of the bat folder).
cd /d "%~dp0\.."

echo ========================================================
echo   Jangbogo 클린 빌드
echo ========================================================
echo.

REM Show the working directory.
echo 작업 디렉토리: %CD%
echo.

REM Make sure the Gradle wrapper is here.
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

REM Clean build. The clean guard in build.gradle stops this if build\ holds an app DB.
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
REM Never hardcode a version here - build.gradle owns the single source of truth.
REM The artifact is located by wildcard at run time (same approach as install.bat).
REM The plain JAR is disabled in build.gradle, so only the bootJar artifact matches.
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

