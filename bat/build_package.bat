@echo off
REM Jangbogo 배포 패키지 빌드 스크립트
setlocal enabledelayedexpansion

REM 한글 출력을 위한 코드페이지 설정 (UTF-8)
chcp 65001 >nul 2>&1

REM 프로젝트 루트 디렉토리로 이동 (bat 폴더의 상위 디렉토리)
cd /d "%~dp0\.."

echo ========================================================
echo   Jangbogo 배포 패키지 빌드
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

echo 배포 패키지를 빌드합니다...
echo - clean: 이전 빌드 결과 삭제
echo - bootJar: Spring Boot JAR 생성
echo - createJre: Custom JRE 생성
echo - packageDist: ZIP 패키지 생성
echo.
echo 예상 소요 시간: 1-2분
echo ========================================================
echo.

REM 배포 패키지 빌드
call gradlew.bat clean bootJar createJre packageDist

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
echo   빌드 완료!
echo ========================================================
echo.
echo 생성된 파일:
echo   - build\distributions\Jangbogo-distribution.zip
echo.
echo 파일 크기 확인 중...
if exist "build\distributions\Jangbogo-distribution.zip" (
    for %%A in ("build\distributions\Jangbogo-distribution.zip") do (
        set size=%%~zA
        set /a sizeMB=!size! / 1048576
        echo   - 크기: !sizeMB! MB
    )
    echo.
    echo ZIP 파일을 탐색기에서 열려면 아무 키나 누르세요...
    pause >nul
    explorer /select,"build\distributions\Jangbogo-distribution.zip"
) else (
    echo [경고] ZIP 파일을 찾을 수 없습니다.
    pause
)

