# JangbogoConfig 설정 가이드

## 📋 개요

`config/jbg_config.yml` 파일을 통해 장보고 프로젝트의 설정을 관리합니다.
`JangbogoConfig` 클래스가 애플리케이션 시작 시 자동으로 이 파일을 로드합니다.

---

## 📁 파일 구조

```
config/
├── jbg_config.yml          # 실제 설정 파일
└── jbg_config.yml.example  # 예제 파일 (템플릿)
```

---

## 📝 설정 파일 예시

### `config/jbg_config.yml`

```yaml
# 데이터베이스 설정
localdb-name: jangbogo-dev
localdb-path: ./db

# 재시도 및 타임아웃 설정
max-retry-count: 3
timeout-seconds: 30

# 디버그 설정
debug-mode: true

# 애플리케이션 정보
app-version: 1.0.0

# 쇼핑몰 로그인 지연 시간 (밀리초)
mall-signin-delay: 21600000  # 6시간

# 기본 웹 드라이버 설정
default-web-driver: chrome  # chrome, edge, firefox

# 브라우저 헤드리스 모드
browser-headless: false
```

---

## 🔄 동작 방식

### 1. 애플리케이션 시작 시

```
Spring Boot 시작
    ↓
JangbogoConfig @PostConstruct 실행
    ↓
config/jbg_config.yml 파일 확인
    ↓
┌─────────────────┐
│ 파일 존재?      │
└─────────────────┘
    ↓               ↓
  YES             NO
    ↓               ↓
YAML 로드      기본값 사용
    ↓               ↓
필드에 값 설정 ←─┘
    ↓
내부 맵(configMap) 초기화
    ↓
사용 가능
```

### 2. 설정 값 우선순위

1. **`config/jbg_config.yml`** (최우선)
2. **기본값** (jbg_config.yml이 없을 때)

---

## 💻 사용 방법

### 방법 1: 직접 Getter 사용

```java
@Autowired
private JangbogoConfig jangbogoConfig;

public void example() {
    String dbName = jangbogoConfig.getLocaldbName();
    String dbPath = jangbogoConfig.getLocaldbPath();
    int maxRetry = jangbogoConfig.getMaxRetryCount();
    boolean debugMode = jangbogoConfig.isDebugMode();
    long delay = jangbogoConfig.getMallSigninDelay();
    String driver = jangbogoConfig.getDefaultWebDriver();
}
```

### 방법 2: get() 메서드 사용 (PropertiesUtil 스타일)

```java
@Autowired
private JangbogoConfig jangbogoConfig;

public void example() {
    String dbName = jangbogoConfig.get("LOCALDB_NAME");
    String dbPath = jangbogoConfig.get("LOCALDB_PATH");
    String maxRetry = jangbogoConfig.get("MAX_RETRY_COUNT");
    String debugMode = jangbogoConfig.get("DEBUG_MODE");
    String delay = jangbogoConfig.get("MALL_SIGNIN_DELAY");
    String driver = jangbogoConfig.get("DEFAULT_WEB_DRIVER");
}
```

**지원하는 키 형식:**
- `LOCALDB_NAME` (언더스코어)
- `LOCALDB-NAME` (하이픈)
- `localdb_name` (소문자)
- `LocalDb-Name` (대소문자 혼합)

→ 모두 동일한 값 반환!

---

## 🎯 주요 설정 항목

| 설정 키 | 타입 | 기본값 | 설명 |
|---------|------|--------|------|
| `localdb-name` | String | `jangbogo-dev` | 로컬 DB 이름 |
| `localdb-path` | String | `./db` | 로컬 DB 경로 |
| `max-retry-count` | int | `3` | 최대 재시도 횟수 |
| `timeout-seconds` | int | `30` | 타임아웃 (초) |
| `debug-mode` | boolean | `true` | 디버그 모드 |
| `app-version` | String | `1.0.0` | 앱 버전 |
| `mall-signin-delay` | long | `21600000` | 쇼핑몰 로그인 지연 (ms) |
| `default-web-driver` | String | `chrome` | 기본 웹 드라이버 |
| `browser-headless` | boolean | `false` | 헤드리스 모드 |

---

## 🔧 설정 추가 방법

### 1. `jbg_config.yml`에 새 설정 추가

```yaml
# 새로운 설정 추가
my-custom-setting: some-value
```

### 2. `JangbogoConfig.java`에 필드 추가

```java
private String myCustomSetting;

public String getMyCustomSetting() {
    return myCustomSetting;
}

public void setMyCustomSetting(String myCustomSetting) {
    this.myCustomSetting = myCustomSetting;
}
```

### 3. `loadConfig()` 메서드에서 로드

```java
this.myCustomSetting = getStringValue(yamlData, "my-custom-setting");
```

### 4. `populateConfigMapFromFields()`에 추가

```java
configMap.put("MY_CUSTOM_SETTING", myCustomSetting);
configMap.put("MY-CUSTOM-SETTING", myCustomSetting);
```

### 5. `getFromFields()` 스위치에 추가

```java
case "MY_CUSTOM_SETTING":
    return myCustomSetting;
```

---

## 🧪 테스트 방법

### 브라우저에서 테스트

```
http://localhost:8282/dev/test-config
```

콘솔에 다음과 같은 출력이 표시됩니다:

```
========================================
JangbogoConfig 사용 예제
========================================

=== 예제 1: 직접 필드 접근 ===
localdbName: jangbogo-dev
localdbPath: ./db
maxRetryCount: 3
...

=== 예제 2: get() 메서드 사용 ===
LOCALDB_NAME: jangbogo-dev
LOCALDB_PATH: ./db
...
```

---

## 📊 application.yml과의 차이점

### application.yml (기존)

```yaml
jangbogo:
  config:
    localdb-name: jangbogo-dev
    localdb-path: ./db
```

**특징:**
- Spring의 `@ConfigurationProperties`로 자동 바인딩
- 재시작 필요

### config/jbg_config.yml (신규)

```yaml
localdb-name: jangbogo-dev
localdb-path: ./db
```

**특징:**
- `@PostConstruct`에서 수동 로드
- `reloadConfig()` 메서드로 재로드 가능
- 더 유연한 관리

---

## 🔒 보안 고려사항

**민감 정보가 포함된 경우:**

`.gitignore`에서 주석 제거:
```gitignore
# config/jbg_config.yml  ← 이 줄 주석 제거
config/jbg_config.yml
```

**민감하지 않은 경우:**

그대로 주석 처리하여 Git에 커밋 가능

---

## 📚 API 메서드

### JangbogoConfig 클래스 주요 메서드

| 메서드 | 설명 |
|--------|------|
| `get(String propertyName)` | 속성값 조회 (문자열 반환) |
| `getAllConfig()` | 모든 설정을 Map으로 반환 |
| `reloadConfig()` | 설정 파일 재로드 |
| `getLocaldbName()` | DB 이름 조회 |
| `getMallSigninDelay()` | 로그인 지연 시간 조회 |
| `isDebugMode()` | 디버그 모드 여부 |
| `isBrowserHeadless()` | 헤드리스 모드 여부 |

---

## ⚠️ 주의사항

1. **타입 변환**: `get()` 메서드는 항상 String을 반환하므로 필요시 형변환 필요
   ```java
   int maxRetry = Integer.parseInt(jangbogoConfig.get("MAX_RETRY_COUNT"));
   ```

2. **null 체크**: 존재하지 않는 키는 `null` 반환
   ```java
   String value = jangbogoConfig.get("NON_EXISTENT_KEY");
   if (value != null) {
       // 처리
   }
   ```

3. **재로드**: 운영 중 설정 변경 시 `reloadConfig()` 호출

---

**작성일**: 2025-10-29  
**버전**: 1.0.0

