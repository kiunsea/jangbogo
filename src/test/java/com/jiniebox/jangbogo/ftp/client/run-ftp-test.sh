#!/bin/bash
# =====================================================
#  Jangbogo FTP TLS Upload Test 실행 스크립트 (Linux/Mac)
# =====================================================

echo ""
echo "====================================================="
echo "  Jangbogo FTP TLS Upload Test"
echo "====================================================="
echo ""

# FTP 서버 정보 설정 (필요시 수정)
# export FTP_HOST=ftp.dlptest.com
# export FTP_PORT=21
# export FTP_USER=dlpuser
# export FTP_PASS=rNrKYTX9g7z3RgJRmxWuGHbeu

echo "[1/3] 환경 변수 확인 중..."
if [ -z "$FTP_HOST" ]; then
    echo "  ⚠️  FTP_HOST가 설정되지 않았습니다."
    echo "  기본값 사용: ftp.dlptest.com"
    export FTP_HOST=ftp.dlptest.com
else
    echo "  ✅ FTP_HOST: $FTP_HOST"
fi

if [ -z "$FTP_PORT" ]; then
    echo "  ⚠️  FTP_PORT가 설정되지 않았습니다."
    echo "  기본값 사용: 21"
    export FTP_PORT=21
else
    echo "  ✅ FTP_PORT: $FTP_PORT"
fi

if [ -z "$FTP_USER" ]; then
    echo "  ⚠️  FTP_USER가 설정되지 않았습니다."
    echo "  기본값 사용: dlpuser"
    export FTP_USER=dlpuser
else
    echo "  ✅ FTP_USER: $FTP_USER"
fi

if [ -z "$FTP_PASS" ]; then
    echo "  ⚠️  FTP_PASS가 설정되지 않았습니다."
    echo "  기본값 사용: rNrKYTX9g7z3RgJRmxWuGHbeu"
    export FTP_PASS=rNrKYTX9g7z3RgJRmxWuGHbeu
else
    echo "  ✅ FTP_PASS: ***"
fi

echo ""
echo "[2/3] 프로젝트 빌드 중..."
./gradlew compileTestJava

if [ $? -ne 0 ]; then
    echo ""
    echo "❌ 빌드 실패!"
    echo ""
    exit 1
fi

echo "  ✅ 빌드 완료"
echo ""

echo "[3/3] FTP 테스트 실행 중..."
echo ""
echo "====================================================="
echo ""

# Gradle을 통해 main 메서드 실행
./gradlew -PmainClass=com.jiniebox.jangbogo.SimpleFtpTlsUploadTest execute

if [ $? -ne 0 ]; then
    echo ""
    echo "❌ 테스트 실행 실패!"
    echo ""
    echo "💡 수동 실행 방법:"
    echo "   IDE에서 SimpleFtpTlsUploadTest.java의 main 메서드 실행"
    echo ""
else
    echo ""
    echo "====================================================="
    echo "  ✅ 테스트 완료!"
    echo "====================================================="
fi

echo ""

