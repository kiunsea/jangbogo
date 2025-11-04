@echo off
REM Jangbogo 실행 스크립트
cd /d "%~dp0"

REM Java가 설치되어 있는지 확인
where java >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo Java가 설치되어 있지 않습니다.
    echo Java 21 이상을 설치해주세요: https://adoptium.net/
    pause
    exit /b 1
)

REM Java 버전 확인
for /f "tokens=3" %%g in ('java -version 2^>^&1 ^| findstr /i "version"') do (
    set JAVA_VERSION=%%g
)
echo Java 버전: %JAVA_VERSION%

REM JAR 파일 실행
echo Jangbogo 애플리케이션을 시작합니다...
java -Xms256m -Xmx1024m -jar jangbogo-0.0.1-SNAPSHOT.jar

pause

