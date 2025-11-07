# =====================================================
#  Jangbogo FTP TLS Upload Test 실행 스크립트 (PowerShell)
# =====================================================

Write-Host ""
Write-Host "=====================================================" -ForegroundColor Cyan
Write-Host "  Jangbogo FTP TLS Upload Test" -ForegroundColor Cyan
Write-Host "=====================================================" -ForegroundColor Cyan
Write-Host ""

# FTP 서버 정보 설정 (필요시 수정)
# $env:FTP_HOST = "ftp.dlptest.com"
# $env:FTP_PORT = "21"
# $env:FTP_USER = "dlpuser"
# $env:FTP_PASS = "rNrKYTX9g7z3RgJRmxWuGHbeu"

Write-Host "[1/3] 환경 변수 확인 중..." -ForegroundColor Yellow

if (-not $env:FTP_HOST) {
    Write-Host "  ⚠️  FTP_HOST가 설정되지 않았습니다." -ForegroundColor Yellow
    Write-Host "  기본값 사용: ftp.dlptest.com"
    $env:FTP_HOST = "ftp.dlptest.com"
} else {
    Write-Host "  ✅ FTP_HOST: $env:FTP_HOST" -ForegroundColor Green
}

if (-not $env:FTP_PORT) {
    Write-Host "  ⚠️  FTP_PORT가 설정되지 않았습니다." -ForegroundColor Yellow
    Write-Host "  기본값 사용: 21"
    $env:FTP_PORT = "21"
} else {
    Write-Host "  ✅ FTP_PORT: $env:FTP_PORT" -ForegroundColor Green
}

if (-not $env:FTP_USER) {
    Write-Host "  ⚠️  FTP_USER가 설정되지 않았습니다." -ForegroundColor Yellow
    Write-Host "  기본값 사용: dlpuser"
    $env:FTP_USER = "dlpuser"
} else {
    Write-Host "  ✅ FTP_USER: $env:FTP_USER" -ForegroundColor Green
}

if (-not $env:FTP_PASS) {
    Write-Host "  ⚠️  FTP_PASS가 설정되지 않았습니다." -ForegroundColor Yellow
    Write-Host "  기본값 사용: rNrKYTX9g7z3RgJRmxWuGHbeu"
    $env:FTP_PASS = "rNrKYTX9g7z3RgJRmxWuGHbeu"
} else {
    Write-Host "  ✅ FTP_PASS: ***" -ForegroundColor Green
}

Write-Host ""
Write-Host "[2/3] 프로젝트 빌드 중..." -ForegroundColor Yellow

& .\gradlew.bat compileTestJava

if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "❌ 빌드 실패!" -ForegroundColor Red
    Write-Host ""
    Read-Host "Press Enter to exit"
    exit 1
}

Write-Host "  ✅ 빌드 완료" -ForegroundColor Green
Write-Host ""

Write-Host "[3/3] FTP 테스트 실행 중..." -ForegroundColor Yellow
Write-Host ""
Write-Host "=====================================================" -ForegroundColor Cyan
Write-Host ""

# IDE에서 실행하는 방법 안내
Write-Host "💡 IDE에서 실행하세요:" -ForegroundColor Yellow
Write-Host ""
Write-Host "  1. IntelliJ IDEA / Eclipse 열기"
Write-Host "  2. SimpleFtpTlsUploadTest.java 파일 찾기"
Write-Host "  3. main() 메서드에서 우클릭"
Write-Host "  4. 'Run SimpleFtpTlsUploadTest.main()' 선택"
Write-Host ""
Write-Host "또는 다음 명령으로 직접 실행:"
Write-Host ""
Write-Host "  java -cp ""build\classes\java\test;lib\*"" com.jiniebox.jangbogo.SimpleFtpTlsUploadTest"
Write-Host ""

Write-Host "=====================================================" -ForegroundColor Cyan
Write-Host ""

Read-Host "Press Enter to exit"

