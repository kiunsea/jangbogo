# 장보고 배포 가이드

## 목차
1. [빌드 방법](#빌드-방법)
2. [배포 패키지 구성](#배포-패키지-구성)
3. [설치 방법](#설치-방법)
4. [실행 방법](#실행-방법)
5. [관리자 계정 설정](#관리자-계정-설정)
6. [Windows 서비스 등록](#windows-서비스-등록)
7. [제거 방법](#제거-방법)
8. [문제 해결](#문제-해결)

---

## 빌드 방법

### 사전 요구 사항
- Java 21 (JDK)
- Gradle 8.x (프로젝트에 포함됨)

### 빌드 명령

```powershell
# 프로젝트 디렉토리로 이동
cd D:\GIT\jangbogo

# 배포 패키지 빌드
.\gradlew clean bootJar createJre packageDist
```

빌드가 완료되면 다음 경로에 ZIP 파일이 생성됩니다:
```
build\distributions\Jangbogo-distribution.zip
```

**빌드 시간**: 약 1-2분  
**파일 크기**: 약 80-100MB

---

## 배포 패키지 구성

`Jangbogo-distribution.zip` 파일의 구조:

```
Jangbogo-distribution/
├─ Jangbogo.bat                      # 실행 스크립트
├─ jangbogo-0.5.0.jar                # Spring Boot 애플리케이션
├─ jre/                              # Custom Java 21 런타임 (번들)
│  ├─ bin/
│  │  ├─ java.exe
│  │  ├─ javaw.exe
│  │  └─ ...
│  ├─ conf/                          # Java 설정 파일
│  ├─ legal/                         # 라이선스 정보
│  └─ lib/                           # Java 라이브러리
├─ service/                          # Windows 서비스 관련 파일
│  ├─ jangbogo-service.exe           # WinSW 실행 파일
│  ├─ jangbogo-service.xml           # 서비스 설정
│  └─ README.md
├─ README.md                         # 프로젝트 소개
├─ 사용설명서.txt                    # 설치 및 설정 가이드
└─ 설치가이드.txt                    # 상세 설치 방법
```

**총 용량 (압축 해제 후)**: 약 150-200MB

---

## 설치 방법

### 1. ZIP 파일 압축 해제

`Jangbogo-distribution.zip` 파일을 원하는 위치에 압축 해제합니다.

**권장 설치 위치**:
- `C:\Program Files\Jangbogo`
- `C:\Users\<사용자명>\AppData\Local\Jangbogo`
- `C:\Jangbogo`
- 또는 사용자가 원하는 위치

**예시 (PowerShell):**
```powershell
# 압축 해제
Expand-Archive -Path Jangbogo-distribution.zip -DestinationPath C:\Jangbogo

# 디렉토리 이동
cd C:\Jangbogo
```

### 2. 설치 확인

압축 해제 후 다음 파일들이 있는지 확인:
- ✅ `Jangbogo.bat`
- ✅ `jangbogo-0.5.0.jar`
- ✅ `jre\bin\java.exe`

### 3. 자동 생성 폴더

첫 실행 시 다음 폴더가 자동으로 생성됩니다:

| 폴더 | 용도 | 파일 형식 |
|------|------|-----------|
| `db/` | 데이터베이스 저장 | `jangbogo-dev.db` (SQLite) |
| `logs/` | 로그 파일 저장 | `jangbogo.log`, `jangbogo-error.log` |
| `exports/` | 구매내역 파일 저장 | JSON, CSV, Excel (.xlsx) |

**참고:** 이 폴더들은 배포 패키지에 포함되지 않으며, 프로그램 실행 시 자동으로 생성됩니다.

---

## 실행 방법

### 방법 1: 기본 실행 (권장)

**`Jangbogo.bat` 파일을 더블클릭**

또는 CMD에서:
```cmd
Jangbogo.bat
```

**동작:**
1. 번들된 Java 런타임 확인
2. 필요한 디렉토리 자동 생성:
   - `db/` - 데이터베이스 저장소
   - `logs/` - 로그 파일 저장소
   - `exports/` - 구매내역 파일 저장소
3. Spring Boot WAS 시작 (포트 8282)
4. 웹 브라우저 자동 실행
5. 로그인 화면 표시

### 방법 2: CMD 창에서 실행 (로그 확인용)

```cmd
cd C:\Jangbogo
Jangbogo.bat
```

콘솔 창에서 실시간 로그를 확인할 수 있습니다.

### 방법 3: 백그라운드 실행

```cmd
start /B Jangbogo.bat
```

백그라운드에서 실행됩니다.

### 실행 확인

**브라우저 자동 실행:**
- 자동으로 `http://localhost:8282` 열림

**수동 접속:**
- 브라우저에서 직접 `http://localhost:8282` 입력
- 또는 `http://127.0.0.1:8282`

**프로세스 확인:**
```cmd
# Java 프로세스 확인
tasklist | findstr "java"

# 포트 사용 확인
netstat -ano | findstr "8282"
```

### 종료 방법

**콘솔 창에서:**
- `Ctrl + C` 누르기

**프로세스 강제 종료:**
```cmd
# Java 프로세스 찾기
tasklist | findstr "java"

# PID로 종료
taskkill /F /PID [PID번호]
```

---

## 관리자 계정 설정

### 기본 계정

첫 설치 시 기본 계정:
```
아이디: admin_main
비밀번호: admin1234_main
```

⚠️ **보안을 위해 반드시 변경하세요!**

### 계정 변경 방법

자세한 내용은 압축 해제 폴더의 **`사용설명서.txt`**를 참조하세요.

**방법 1: 환경 변수 사용 (권장)**
```powershell
# Windows 환경 변수 설정
$env:ADMIN_ID = "새로운_아이디"
$env:ADMIN_PASS = "새로운_비밀번호"
```

**방법 2: 설정 파일 사용**
```
1. config 폴더 생성
2. config/admin.properties 파일 생성
3. 내용 입력:
   admin.id=새로운_아이디
   admin.pass=새로운_비밀번호
```

**방법 3: 배치 스크립트 수정**
```cmd
# Jangbogo.bat 파일 수정
@echo off
set ADMIN_ID=새로운_아이디
set ADMIN_PASS=새로운_비밀번호
...
```

---

## Windows 서비스 등록

OS 시작 시 자동으로 실행되도록 Windows 서비스로 등록할 수 있습니다.

### 서비스 등록

**관리자 권한으로 CMD 실행** 후:

```cmd
cd "C:\Jangbogo\service"

# 서비스 설치
jangbogo-service.exe install

# 서비스 시작
jangbogo-service.exe start
```

### 서비스 상태 확인

```cmd
# 서비스 상태 확인
jangbogo-service.exe status

# 또는 Windows 서비스 관리자
services.msc
```

서비스 이름: **`JangbogoService`**

### 서비스 중지 및 제거

```cmd
# 서비스 중지
jangbogo-service.exe stop

# 서비스 제거
jangbogo-service.exe uninstall
```

### 서비스 설정 파일

서비스 설정을 변경하려면 `service/jangbogo-service.xml` 파일을 수정하세요.

```xml
<service>
  <id>JangbogoService</id>
  <name>Jangbogo Service</name>
  <description>장보고 구매내역 수집 서비스</description>
  <executable>%BASE%\..\jre\bin\java.exe</executable>
  <arguments>-Xms256m -Xmx1024m -jar "%BASE%\..\jangbogo-0.5.0.jar"</arguments>
  ...
</service>
```

---

## 제거 방법

### 1. 서비스 제거 (서비스 등록한 경우)

```cmd
cd "C:\Jangbogo\service"
jangbogo-service.exe stop
jangbogo-service.exe uninstall
```

### 2. 애플리케이션 종료

- 실행 중인 프로세스 종료
- 또는 `Ctrl + C`

### 3. 데이터 백업 (선택사항)

필요 시 다음 파일/폴더를 백업:
- `db/jangbogo-dev.db` - 데이터베이스 (모든 구매내역 데이터)
- `logs/` - 로그 파일 (문제 해결용)
- `exports/` - 내보낸 구매내역 파일 (JSON, CSV, Excel)

### 4. 폴더 삭제

설치 폴더(예: `C:\Jangbogo`)를 삭제합니다.

---

## 문제 해결

### 1. "Java를 찾을 수 없습니다" 오류

**증상**: `jre\bin\java.exe`를 찾을 수 없다는 메시지

**원인**: 
- ZIP 압축 해제가 불완전함
- `jre` 폴더가 누락됨

**해결 방법**:
1. ZIP 파일을 다시 압축 해제
2. `jre\bin\java.exe` 파일이 있는지 확인
3. 없다면 빌드를 다시 수행

### 2. 애플리케이션이 시작되지 않음

**증상**: `Jangbogo.bat` 실행 시 창이 열렸다 닫힘

**해결 방법**:
1. CMD에서 실행하여 오류 메시지 확인:
   ```cmd
   cd C:\Jangbogo
   Jangbogo.bat
   ```

2. 로그 파일 확인:
   ```cmd
   type logs\jangbogo.log
   ```

3. 포트 충돌 확인:
   ```cmd
   netstat -ano | findstr :8282
   ```

4. 다른 프로세스가 8282 포트를 사용 중이면 종료:
   ```cmd
   taskkill /F /PID [PID번호]
   ```

### 3. 브라우저가 열리지 않음

**증상**: 서버는 시작되지만 브라우저가 자동으로 열리지 않음

**해결 방법**:
- 수동으로 브라우저에서 접속: `http://127.0.0.1:8282`

### 4. 로그인이 안 됨

**증상**: 아이디/비밀번호 오류

**해결 방법**:
1. 기본 계정 확인:
   ```
   아이디: admin_main
   비밀번호: admin1234_main
   ```

2. 대소문자 정확히 입력
3. 계정 변경했다면 변경한 계정 사용

### 5. 데이터베이스 오류

**증상**: `SQLiteException` 발생

**해결 방법**:
1. `db\jangbogo-dev.db` 파일 권한 확인
2. 읽기/쓰기 권한 부여
3. 데이터베이스 백업 후 재생성:
   ```cmd
   # 백업
   copy db\jangbogo-dev.db db\jangbogo-dev.db.bak
   
   # 삭제 (재시작 시 자동 생성됨)
   del db\jangbogo-dev.db
   ```

### 6. 서비스 등록 실패

**증상**: `jangbogo-service.exe install` 실패

**해결 방법**:
1. **관리자 권한**으로 CMD 실행 확인
2. WinSW 파일 확인: `service\jangbogo-service.exe`
3. 이미 등록된 서비스 제거 후 재시도:
   ```cmd
   sc delete JangbogoService
   ```

### 7. 메모리 부족

**증상**: `OutOfMemoryError` 발생

**해결 방법**:

**방법 1: 배치 스크립트 수정**

`Jangbogo.bat` 파일에서 메모리 설정 변경:
```cmd
"%JAVA_CMD%" -Xms512m -Xmx2048m -jar jangbogo-0.5.0.jar
```

**방법 2: 서비스 설정 파일 수정**

`service\jangbogo-service.xml`:
```xml
<arguments>-Xms512m -Xmx2048m -jar "%BASE%\..\jangbogo-0.5.0.jar"</arguments>
```

### 8. 웹 드라이버 오류

**증상**: 쇼핑몰 수집 시 `WebDriverException` 발생

**해결 방법**:
- Chrome 또는 Edge 브라우저 최신 버전 설치
- Selenium WebDriver는 자동으로 다운로드됨

---

## 추가 정보

### 로그 파일 위치

- **애플리케이션 로그**: `logs\jangbogo.log`
- **오류 로그**: `logs\jangbogo-error.log`
- **서비스 로그**: `service\jangbogo-service.log` (서비스 등록 시)

### 설정 파일

- **관리자 계정**: `config/admin.properties` (생성 시)
- **Spring Boot 설정**: JAR 내장 (`application.yml`)
- **서비스 설정**: `service/jangbogo-service.xml`

### 저장 위치

프로그램 실행 시 **저장 위치**가 자동으로 설정됩니다:
- 기본값: `[설치 폴더 절대 경로]\exports`
- 예시: `C:\Jangbogo\exports`
- 사용자가 UI에서 다른 경로로 변경 가능

### 포트 변경

기본 포트 8282를 변경하려면:

**방법 1: JVM 옵션 추가**

`Jangbogo.bat` 수정:
```cmd
"%JAVA_CMD%" -Dserver.port=9999 -Xms256m -Xmx1024m -jar jangbogo-0.5.0.jar
```

**방법 2: application.yml 사용**

`config/application.yml` 파일 생성:
```yaml
server:
  port: 9999
  address: 127.0.0.1
```

### 데이터베이스 백업

정기적으로 데이터베이스를 백업하세요:

```cmd
# 수동 백업
copy db\jangbogo-dev.db backup\jangbogo-dev_%date:~0,4%%date:~5,2%%date:~8,2%.db

# 내보내기 폴더도 백업
xcopy /E /I exports backup\exports
```

---

## 업그레이드 방법

새 버전으로 업그레이드:

### 1. 데이터 백업
```cmd
copy db\jangbogo-dev.db db\jangbogo-dev.db.bak
```

### 2. 애플리케이션 종료
```cmd
# 서비스 등록 시
cd service
jangbogo-service.exe stop

# 일반 실행 시
Ctrl + C
```

### 3. 새 ZIP 파일 압축 해제
- 기존 폴더에 덮어쓰기
- 또는 새 폴더에 설치 후 데이터 이동

### 4. 데이터 복원
```cmd
copy db\jangbogo-dev.db.bak db\jangbogo-dev.db
```

### 5. 재시작
```cmd
Jangbogo.bat
```

---

## 보안 권장사항

1. **관리자 계정 변경**: 기본 계정을 반드시 변경
2. **방화벽 설정**: localhost(127.0.0.1)만 접근 가능하도록 설정됨
3. **정기 백업**: 데이터베이스 및 내보낸 파일 백업
4. **로그 모니터링**: 정기적으로 로그 파일 확인
5. **업데이트**: 보안 패치 및 업데이트 적용

---

## 지원

문제가 해결되지 않으면 다음 정보를 포함하여 문의하세요:

1. 로그 파일 내용 (`logs\jangbogo.log`)
2. 에러 메시지 스크린샷
3. Windows 버전 및 시스템 사양
4. 설치 경로 및 실행 명령
5. 수행한 트러블슈팅 단계

---

**버전**: 0.5.0  
**최종 수정일**: 2025-11-07  
**라이선스**: AGPL-3.0-or-later
