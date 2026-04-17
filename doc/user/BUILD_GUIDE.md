# Jangbogo 배포 패키지 빌드 가이드

이 문서는 Jangbogo를 **Java 설치 없이 실행 가능한 배포 패키지**로 빌드하는 방법을 설명합니다.

## 빌드 방식 개요

Jangbogo는 **Custom JRE 번들 + ZIP 배포** 방식을 사용합니다:

### ✅ 장점
- **Java 설치 불필요**: Custom JRE를 jlink로 생성하여 번들링
- **간단한 배포**: ZIP 파일 하나로 배포
- **빠른 설치**: 압축 해제만 하면 즉시 사용 가능
- **크로스 플랫폼**: 원하는 위치에 복사하여 사용
- **빠른 빌드**: 1-2분 소요

### 📦 배포 구성
- Spring Boot executable JAR
- Custom JRE (jlink로 생성)
- Jangbogo.bat 실행 스크립트
- Windows 서비스 설정 파일 (WinSW)
- 사용설명서, 설치가이드, 고급가이드 및 사용자 매뉴얼

---

## 사전 요구사항

### 1. JDK 21 설치 (개발/빌드용)
- Oracle JDK 21 또는 OpenJDK 21 설치
- `JAVA_HOME` 환경 변수 설정
- 확인: `java -version`

```bash
java -version
# java version "21.0.2" 2024-01-16 LTS
```

### 2. Gradle 설치 (프로젝트에 포함됨)
- 프로젝트에 Gradle Wrapper(`gradlew.bat`) 포함
- 별도 설치 불필요

### 3. WinSW 다운로드 (Windows 서비스용, 선택사항)
WinSW는 Windows 서비스 래퍼로, Jangbogo를 서비스로 등록하는 데 사용됩니다.

**다운로드 링크:**
```
https://github.com/winsw/winsw/releases/download/v3.0.0-alpha.11/WinSW-x64.exe
```

**설치 방법:**
1. 위 링크에서 `WinSW-x64.exe` 다운로드
2. `jangbogo-service.exe`로 이름 변경
3. `packaging/winsw/` 디렉토리에 복사

---

## 빌드 방법

### 1. 프로젝트 클린

```bash
.\gradlew clean
```

### 2. 배포 패키지 빌드

```bash
.\gradlew clean bootJar createJre packageDist
```

이 명령은 다음 작업을 수행합니다:

1. **`clean`**: 이전 빌드 결과물 삭제
2. **`bootJar`**: Spring Boot executable JAR 생성
3. **`createJre`**: jlink로 Custom JRE 생성 (약 50-70MB)
4. **`packageDist`**: 모든 파일을 ZIP으로 패키징

**소요 시간**: 약 1-2분

### 3. 빌드 결과 확인

생성된 파일 위치:
```
build/distributions/Jangbogo-distribution.zip
```

**파일 크기**: 약 80-100MB (Custom JRE 포함)

---

## 빌드 출력물

ZIP 파일 내부 구조:

```
Jangbogo-distribution/
├─ Jangbogo.bat                      # 실행 스크립트
├─ jangbogo-0.6.0.jar                # Spring Boot 애플리케이션
├─ jre/                              # Custom Java 21 런타임 (약 50-70MB)
│  ├─ bin/
│  │  ├─ java.exe                    # Java 실행 파일
│  │  ├─ javaw.exe
│  │  └─ ...
│  ├─ conf/                          # Java 설정 파일
│  ├─ legal/                         # 라이선스 정보
│  └─ lib/                           # Java 라이브러리
├─ service/                          # Windows 서비스 파일
│  ├─ jangbogo-service.exe           # WinSW 실행 파일
│  ├─ jangbogo-service.xml           # 서비스 설정
│  └─ README.md                      # 서비스 등록 가이드
├─ README.md                         # 프로젝트 소개
├─ 사용설명서.txt                    # 설치 및 설정 가이드
├─ 설치가이드.txt                    # 상세 설치 방법
└─ 고급가이드.txt                    # config 폴더 시스템 설정 가이드 (고급 사용자용)
```

---

## Custom JRE 생성 (jlink)

`createJre` 태스크는 jlink를 사용하여 필요한 Java 모듈만 포함한 경량 JRE를 생성합니다.

### 포함된 Java 모듈

```
java.base, java.compiler, java.desktop, java.instrument,
java.logging, java.management, java.management.rmi, java.naming,
java.net.http, java.prefs, java.rmi, java.scripting,
java.security.jgss, java.security.sasl, java.sql,
java.transaction.xa, java.xml, java.xml.crypto,
jdk.crypto.ec, jdk.httpserver, jdk.jdwp.agent, jdk.jfr,
jdk.management, jdk.management.agent, jdk.naming.dns,
jdk.net, jdk.security.auth, jdk.unsupported, jdk.zipfs
```

**특징:**
- Spring Boot 및 Tomcat 구동에 필요한 모든 모듈 포함
- 불필요한 모듈 제거로 용량 최적화
- `--strip-debug`, `--no-man-pages`, `--no-header-files`로 추가 최적화
- `--compress=2`로 압축

---

## 배포 방법

### 1. ZIP 파일 배포

빌드된 `Jangbogo-distribution.zip`을 사용자에게 전달합니다.

**배포 방법:**
- 이메일, USB, 클라우드 드라이브 등
- 파일 크기: 약 80-100MB

### 2. 사용자 설치

사용자가 다음 단계로 설치:

1. **ZIP 파일 압축 해제**
   ```
   원하는 위치에 압축 해제
   예: C:\Jangbogo
   ```

2. **Jangbogo.bat 실행**
   - `Jangbogo.bat` 파일을 더블클릭
   - 또는 CMD에서: `Jangbogo.bat`

3. **브라우저 접속**
   - 자동으로 브라우저 열림
   - 또는 수동 접속: `http://127.0.0.1:8282`

4. **로그인**
   ```
   아이디: admin_main
   비밀번호: admin1234_main
   ```

---

## Jangbogo.bat 스크립트

배치 스크립트는 다음 기능을 제공합니다:

### 주요 기능

1. **한글 출력 지원**
   - UTF-8 코드페이지 설정 (`chcp 65001`)
   - DOS 콘솔에서 한글이 깨지지 않고 정상 출력
   - 커스텀 JangBoGo ASCII 배너 표시 (Spring Boot 배너 대체)

2. **Java 런타임 감지**
   - 번들된 JRE 우선 사용
   - 없으면 시스템 Java 사용
   - 둘 다 없으면 오류 메시지 표시

3. **필수 디렉토리 생성**
   - `db/` - 데이터베이스
   - `logs/` - 로그 파일
   - `exports/` - 내보낸 파일

4. **Spring Boot 애플리케이션 실행**
   - JVM 옵션: `-Xms256m -Xmx1024m`
   - 자동으로 포트 8282에서 서버 시작

### 사용 예시

```cmd
# 일반 실행
Jangbogo.bat

# 출력 예시:
# 
#    ___                  ____        ____       
#   |_  | __ _ _ _  __ _| __ )  ___ / ___|  ___ 
#   | | |/ _` | ' \/ _` |  _ \ / _ \ |  _ / _ \
#  _| | | (_| | | | (_| | |_) | (_) | |_| | (_) |
# |___| \__,_|_||_\__, |____/ \___/ \____|\___/ 
#                 |___/                          
# ========================================================
# :: JangBoGo :: 장보고 구매내역 수집 서비스
# :: Version :: 0.6.0
# :: Spring Boot :: 3.5.6
# :: Powered by :: jiniebox.com
# ========================================================
```

---

## 트러블슈팅

### 빌드 실패: "Task 'createJre' not found"

**원인**: Gradle 캐시 문제

**해결:**
```bash
.\gradlew clean --refresh-dependencies
.\gradlew clean bootJar createJre packageDist
```

### 빌드 실패: "JDK 21이 필요합니다"

**원인**: JDK 버전 문제

**해결:**
```bash
# Java 버전 확인
java -version

# JAVA_HOME 확인
echo %JAVA_HOME%

# JDK 21 설치 후 환경 변수 설정
```

### 빌드 실패: "jlink 명령을 찾을 수 없습니다"

**원인**: JRE만 설치되어 있고 JDK가 없음

**해결:**
- JDK 21 (not JRE) 설치
- JDK에는 jlink 도구 포함

### Custom JRE 크기가 너무 큼

**현재 크기**: 약 50-70MB

**추가 최적화 방법:**
1. `build.gradle`의 `createJre` 태스크에서 불필요한 모듈 제거
2. `--compress=2` 대신 `--compress=zip`사용
3. 7-Zip 등으로 ZIP 파일 재압축

### ZIP 파일이 생성되지 않음

**확인 사항:**
1. `build/distributions/` 폴더 확인
2. Gradle 빌드 로그 확인
3. 디스크 공간 확인 (최소 500MB 필요)

---

## 개발 모드 실행

배포 패키지 빌드 없이 개발 모드로 실행:

```bash
# Spring Boot 실행 (포트 8282)
.\gradlew bootRun

# 또는 IDE에서 JangbogoLauncher.main() 실행
```

---

## Gradle 태스크

### 주요 태스크

```bash
# 컴파일만
.\gradlew compileJava

# 테스트
.\gradlew test

# Spring Boot JAR 빌드
.\gradlew bootJar

# Custom JRE 생성
.\gradlew createJre

# 배포 패키지 생성 (ZIP)
.\gradlew packageDist

# 전체 빌드 (권장)
.\gradlew clean bootJar createJre packageDist
```

### 빌드 옵션 커스터마이징

`build.gradle` 파일에서 설정 변경 가능:

```groovy
// Custom JRE 모듈 설정
'--add-modules', '모듈_목록',

// JVM 메모리 설정
[JavaOptions]
java-options=-Xms256m
java-options=-Xmx1024m

// 애플리케이션 정보
'--name', 'Jangbogo',
'--app-version', '1.0.0',
'--description', '장보고 구매내역 수집 서비스',
'--vendor', 'Jiniebox'
```

---

## 로그 확인

### 빌드 로그
- 터미널 출력 확인
- 또는 `--info` 옵션: `.\gradlew packageDist --info`

### 애플리케이션 로그
- **개발 모드**: `logs\jangbogo.log`
- **배포 모드**: `[압축해제경로]\logs\jangbogo.log`
- **서비스 모드**: `[압축해제경로]\service\logs\`

---

## 배포 패키지 크기 최적화

### 현재 크기
- **ZIP 파일**: 약 80-100MB
- **압축 해제 후**: 약 150-200MB

### 최적화 방법

1. **Custom JRE 모듈 최소화**
   - 필요한 모듈만 포함
   - 현재 약 30개 모듈 포함

2. **추가 압축**
   - 7-Zip으로 재압축: 약 60-70MB까지 감소 가능
   - 단, 사용자가 7-Zip 필요

3. **온라인 배포**
   - GitHub Releases
   - Google Drive, OneDrive 등

---

## 참고 문서

- **[배포 가이드](DEPLOYMENT_GUIDE.md)** - 설치, 실행, 서비스 등록
- **[사용자 매뉴얼](USER_GUIDE.md)** - 기능 사용법
- **[문서 인덱스](../README.md)** - 전체 문서 목록

---

## CI/CD 통합

### GitHub Actions 예시

```yaml
name: Build Distribution

on:
  push:
    tags:
      - 'v*'

jobs:
  build:
    runs-on: windows-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up JDK 21
        uses: actions/setup-java@v3
        with:
          java-version: '21'
          distribution: 'temurin'
      
      - name: Build with Gradle
        run: .\gradlew clean bootJar createJre packageDist
      
      - name: Upload Distribution
        uses: actions/upload-artifact@v3
        with:
          name: Jangbogo-distribution
          path: build/distributions/Jangbogo-distribution.zip
```

---

## 문의 및 지원

- **이슈 리포트**: [GitHub Issues]
- **라이선스**: AGPL-3.0-or-later

---

**버전**: 0.6.0
**최종 업데이트**: 2026-01-28
