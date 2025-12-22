# Eclipse IDE 개발 환경 설정 가이드

jangbogo 프로젝트를 Eclipse IDE에서 import하고 개발/테스트를 실행하는 방법을 안내합니다.

---

## 📋 목차

1. [필수 요구사항](#필수-요구사항)
2. [Eclipse 플러그인 설치](#eclipse-플러그인-설치)
3. [프로젝트 Import](#프로젝트-import)
4. [프로젝트 설정 확인](#프로젝트-설정-확인)
5. [테스트 실행](#테스트-실행)
6. [애플리케이션 실행](#애플리케이션-실행)
7. [문제 해결](#문제-해결)

---

<a id="필수-요구사항"></a>
## 필수 요구사항

### 1. Java 개발 환경

- **JDK 21** 이상 설치 필요
- Eclipse가 JDK 21을 인식하도록 설정

**확인 방법:**
```
Window → Preferences → Java → Installed JREs
```

JDK 21이 없으면 "Add..." 버튼으로 추가

### 2. Eclipse 버전

- **Eclipse IDE for Enterprise Java and Web Developers** (권장)
- 또는 **Eclipse IDE for Java Developers** (최소 버전: 2023-09 이상)

---

<a id="eclipse-플러그인-설치"></a>
## Eclipse 플러그인 설치

### 1. Buildship (Gradle Integration)

jangbogo는 Gradle 프로젝트이므로 Buildship 플러그인이 필요합니다.

**설치 방법:**

1. **Help → Eclipse Marketplace**
2. 검색창에 "Buildship" 입력
3. **"Buildship Gradle Integration"** 설치
4. Eclipse 재시작

**또는 수동 설치:**

1. **Help → Install New Software**
2. Work with: `https://download.eclipse.org/buildship/updates/e4.29/`
3. "Buildship Gradle Integration" 선택 후 설치

### 2. Spring Tools (선택사항, 권장)

Spring Boot 개발을 위한 플러그인입니다.

**설치 방법:**

1. **Help → Eclipse Marketplace**
2. 검색창에 "Spring Tools" 입력
3. **"Spring Tools 4"** 설치

---

<a id="프로젝트-import"></a>
## 프로젝트 Import

### 방법 1: Gradle 프로젝트로 Import (권장)

1. **File → Import...**
2. **Gradle → Existing Gradle Project** 선택
3. **Next** 클릭
4. **Project root directory**에서 `D:\GIT\jangbogo` 선택
5. **Finish** 클릭

**참고:** Buildship이 자동으로 Gradle 빌드를 수행하고 의존성을 다운로드합니다.

### 방법 2: 기존 프로젝트로 Import

프로젝트에 이미 `.project` 파일이 있으므로:

1. **File → Import...**
2. **General → Existing Projects into Workspace** 선택
3. **Select root directory**에서 `D:\GIT\jangbogo` 선택
4. 프로젝트가 자동으로 감지됨
5. **Finish** 클릭

**주의:** 이 방법을 사용하면 Gradle 동기화가 필요할 수 있습니다.

---

<a id="프로젝트-설정-확인"></a>
## 프로젝트 설정 확인

### 1. Java Build Path 확인

1. 프로젝트 우클릭 → **Properties**
2. **Java Build Path → Libraries** 탭 확인
3. Gradle 의존성이 자동으로 추가되어 있어야 함

### 2. Gradle 동기화

프로젝트를 import한 후:

1. 프로젝트 우클릭 → **Gradle → Refresh Gradle Project**
2. 또는 **Gradle Tasks** 뷰에서 새로고침

**Gradle Tasks 뷰 열기:**
- **Window → Show View → Other...**
- **Gradle → Gradle Tasks** 선택

### 3. 프로젝트 구조 확인

정상적으로 import되면 다음과 같은 구조가 보입니다:

```
jangbogo/
├── src/
│   ├── main/
│   │   ├── java/          (소스 코드)
│   │   └── resources/     (설정 파일)
│   └── test/
│       ├── java/          (테스트 코드)
│       └── resources/      (테스트 리소스)
├── build.gradle           (빌드 설정)
└── settings.gradle        (프로젝트 설정)
```

---

<a id="테스트-실행"></a>
## 테스트 실행

### 방법 1: JUnit 테스트 실행 (권장)

#### 개별 테스트 클래스 실행

1. **Package Explorer**에서 테스트 파일 열기
   - 예: `src/test/java/com/jiniebox/jangbogo/JdbcConnectionTest.java`
2. 테스트 클래스 또는 메서드 우클릭
3. **Run As → JUnit Test** 선택

#### 모든 테스트 실행

1. 프로젝트 우클릭
2. **Run As → JUnit Test** 선택

#### 테스트 뷰에서 실행

1. **Window → Show View → Other...**
2. **JUnit** 선택
3. 테스트 클래스를 드래그 앤 드롭하거나
4. 테스트 클래스 우클릭 → **Run As → JUnit Test**

### 방법 2: Gradle을 통한 테스트 실행

1. **Gradle Tasks** 뷰 열기
2. **jangbogo → verification → test** 더블 클릭
3. 또는 터미널에서:
   ```bash
   .\gradlew.bat test
   ```

### 테스트 실행 예시

#### 1. JdbcConnectionTest 실행

```
위치: src/test/java/com/jiniebox/jangbogo/JdbcConnectionTest.java
실행: 우클릭 → Run As → JUnit Test
```

#### 2. FTP 테스트 실행

```
위치: src/test/java/com/jiniebox/jangbogo/ftp/client/FtpTlsConnectionTest.java
주의: @Disabled 어노테이션이 있으므로 수동으로 활성화 필요
```

**FTP 테스트 활성화 방법:**

1. 테스트 파일 열기
2. `@Disabled("실제 FTP 서버 정보 필요 - 수동 테스트용")` 주석 처리 또는 제거
3. 환경 변수 설정:
   ```
   FTP_HOST=your-ftp-server.com
   FTP_PORT=21
   FTP_USER=username
   FTP_PASS=password
   ```
4. 테스트 실행

---

<a id="애플리케이션-실행"></a>
## 애플리케이션 실행

### 방법 1: Java Application으로 실행 (권장)

#### 1. Run Configuration 생성

1. **Run → Run Configurations...**
2. **Java Application** 우클릭 → **New Configuration**
3. 설정:
   - **Name**: `Jangbogo Application`
   - **Project**: `jangbogo`
   - **Main class**: `com.jiniebox.jangbogo.JangbogoLauncher`
   - **Arguments** 탭:
     - **Program arguments**: (비워두거나 `--tray` 등 모드 지정)
   - **JRE** 탭:
     - **Use a project specific JRE**: JDK 21 선택
4. **Apply** → **Run**

#### 2. 빠른 실행

1. `JangbogoLauncher.java` 파일 열기
2. `main` 메서드에서 우클릭
3. **Run As → Java Application**

### 방법 2: Spring Boot App으로 실행 (Spring Tools 설치 시)

1. `JangbogoApplication.java` 또는 `JangbogoLauncher.java` 파일 열기
2. 우클릭 → **Run As → Spring Boot App**

### 방법 3: Gradle을 통한 실행

1. **Gradle Tasks** 뷰에서
2. **jangbogo → application → run** 더블 클릭

또는 터미널에서:
```bash
.\gradlew.bat run
```

### 실행 모드 옵션

`JangbogoLauncher`는 실행 인자에 따라 다른 모드로 동작합니다:

| 인자 | 설명 | 브라우저 자동 실행 | 트레이 아이콘 |
|------|------|------------------|--------------|
| (없음) | 일반 실행 (개발 모드) | ✅ | ❌ |
| `--service` | 서비스 모드 | ❌ | ❌ |
| `--tray` | 트레이 모드 | ✅ | ✅ |
| `--install-complete` | 설치 완료 모드 | ✅ | ✅ |

**Run Configuration에서 인자 설정:**
- **Arguments** 탭 → **Program arguments**에 원하는 모드 입력

---

## 디버깅

### 테스트 디버깅

1. 테스트 파일에서 **Breakpoint** 설정
2. 테스트 클래스/메서드 우클릭
3. **Debug As → JUnit Test** 선택

### 애플리케이션 디버깅

1. 소스 코드에 **Breakpoint** 설정
2. `JangbogoLauncher.java`의 `main` 메서드에서 우클릭
3. **Debug As → Java Application** 선택

---

<a id="문제-해결"></a>
## 문제 해결

### 1. "AfterEach cannot be resolved to a type" 오류

**원인:** JUnit 5 의존성이 IDE에서 인식되지 않음

**해결 방법:**

1. 프로젝트 우클릭 → **Gradle → Refresh Gradle Project**
2. **Project → Clean...** → 프로젝트 선택 → **Clean**
3. Eclipse 재시작

### 2. Gradle 동기화 실패

**해결 방법:**

1. 프로젝트 우클릭 → **Gradle → Refresh Gradle Project**
2. **Window → Preferences → Gradle**
   - **Gradle distribution**: "Gradle wrapper" 선택 확인
3. `.gradle` 폴더 삭제 후 재시도:
   ```
   프로젝트 루트/.gradle 폴더 삭제
   ```

### 3. Java 버전 불일치

**오류 메시지:**
```
The project cannot be built until build path errors are resolved
```

**해결 방법:**

1. 프로젝트 우클릭 → **Properties**
2. **Java Build Path → Libraries** 탭
3. **Modulepath** 또는 **Classpath**에서 JRE 확인
4. JDK 21로 변경:
   - **Remove** → **Add Library...** → **JRE System Library** → JDK 21 선택

### 4. Lombok 어노테이션 미작동

**해결 방법:**

1. **Help → Eclipse Marketplace**
2. "Lombok" 검색 → **"Lombok"** 설치
3. Eclipse 재시작
4. `lombok.jar` 위치 확인 후 수동 설치:
   ```
   java -jar lombok.jar
   ```

### 5. Spring Boot 애플리케이션이 시작되지 않음

**확인 사항:**

1. **application.yml** 파일이 `src/main/resources/`에 있는지 확인
2. 데이터베이스 파일 경로 확인:
   ```yaml
   spring:
     datasource:
       url: jdbc:sqlite:db/jangbogo-dev.db
   ```
3. 로그 확인:
   - **Console** 뷰에서 오류 메시지 확인
   - `logs/` 폴더의 로그 파일 확인

### 6. 테스트가 실행되지 않음

**해결 방법:**

1. **Window → Preferences → Java → Compiler**
   - **Compiler compliance level**: 21 확인
2. 프로젝트 우클릭 → **Properties → Java Compiler**
   - **Compiler compliance level**: 21 확인
3. **Project → Clean...** → **Clean**

---

## 유용한 Eclipse 기능

### 1. Gradle Tasks 뷰

**열기:** Window → Show View → Other... → Gradle → Gradle Tasks

**주요 태스크:**
- `build`: 전체 빌드
- `test`: 테스트 실행
- `bootJar`: 실행 가능한 JAR 생성
- `clean`: 빌드 결과물 삭제

### 2. Problems 뷰

컴파일 오류 및 경고 확인:
- **Window → Show View → Problems**

### 3. Console 뷰

애플리케이션 출력 및 로그 확인:
- **Window → Show View → Console**

### 4. Package Explorer 필터

불필요한 파일 숨기기:
- **Window → Preferences → Java → Build Path → Classpath Variables**
- 또는 Package Explorer의 필터 아이콘 사용

---

## 빠른 참조

### 자주 사용하는 단축키

| 단축키 | 기능 |
|--------|------|
| `Ctrl + Shift + O` | Import 정리 |
| `Ctrl + Shift + F` | 코드 포맷팅 |
| `Ctrl + Space` | 자동 완성 |
| `F11` | 디버그 실행 |
| `Ctrl + F11` | 실행 |
| `Alt + Shift + X, T` | JUnit 테스트 실행 |

### 주요 실행 클래스

| 클래스 | 용도 |
|--------|------|
| `JangbogoLauncher` | 메인 애플리케이션 런처 |
| `JangbogoApplication` | Spring Boot 애플리케이션 |
| `JdbcConnectionTest` | 데이터베이스 연결 테스트 |
| `FtpTlsConnectionTest` | FTP/FTPS 연결 테스트 |

---

## 추가 리소스

- [Eclipse 공식 문서](https://www.eclipse.org/documentation/)
- [Buildship 문서](https://github.com/eclipse/buildship)
- [Spring Tools 문서](https://spring.io/tools)
- [JUnit 5 사용 가이드](https://junit.org/junit5/docs/current/user-guide/)

---

**최종 업데이트:** 2025-11-07  
**작성자:** jiniebox.com

