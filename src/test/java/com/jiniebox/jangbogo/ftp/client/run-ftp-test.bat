@echo off
REM =====================================================
REM  Jangbogo FTP TLS Upload Test 실행 스크립트
REM =====================================================

echo.
echo =====================================================
echo   Jangbogo FTP TLS Upload Test
echo =====================================================
echo.

REM FTP 서버 정보 설정 (필요시 수정)
REM set FTP_HOST=ftp.dlptest.com
REM set FTP_PORT=21
REM set FTP_USER=dlpuser
REM set FTP_PASS=rNrKYTX9g7z3RgJRmxWuGHbeu

echo [1/3] 환경 변수 확인 중...
if not defined FTP_HOST (
    echo   ⚠️  FTP_HOST가 설정되지 않았습니다.
    echo   기본값 사용: ftp.dlptest.com
    set FTP_HOST=ftp.dlptest.com
) else (
    echo   ✅ FTP_HOST: %FTP_HOST%
)

if not defined FTP_PORT (
    echo   ⚠️  FTP_PORT가 설정되지 않았습니다.
    echo   기본값 사용: 21
    set FTP_PORT=21
) else (
    echo   ✅ FTP_PORT: %FTP_PORT%
)

if not defined FTP_USER (
    echo   ⚠️  FTP_USER가 설정되지 않았습니다.
    echo   기본값 사용: dlpuser
    set FTP_USER=dlpuser
) else (
    echo   ✅ FTP_USER: %FTP_USER%
)

if not defined FTP_PASS (
    echo   ⚠️  FTP_PASS가 설정되지 않았습니다.
    echo   기본값 사용: rNrKYTX9g7z3RgJRmxWuGHbeu
    set FTP_PASS=rNrKYTX9g7z3RgJRmxWuGHbeu
) else (
    echo   ✅ FTP_PASS: ***
)

echo.
echo [2/3] 프로젝트 빌드 중...
call gradlew.bat compileTestJava

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ❌ 빌드 실패!
    echo.
    pause
    exit /b 1
)

echo   ✅ 빌드 완료
echo.

echo [3/3] FTP 테스트 실행 중...
echo.
echo =====================================================
echo.

REM Gradle을 통해 main 메서드 실행
call gradlew.bat -PmainClass=com.jiniebox.jangbogo.SimpleFtpTlsUploadTest execute

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ❌ 테스트 실행 실패!
    echo.
    echo 💡 수동 실행 방법:
    echo    IDE에서 SimpleFtpTlsUploadTest.java의 main 메서드 실행
    echo.
) else (
    echo.
    echo =====================================================
    echo   ✅ 테스트 완료!
    echo =====================================================
)

echo.
pause

