# 세션 검사 전역 처리 개선 가이드

## 📋 개선 개요

Jangbogo 프로젝트에서 세션 검사를 전역적으로 처리하도록 개선하여 코드 중복을 제거하고 유지보수성을 향상시켰습니다.

## 🎯 개선 목표

1. **코드 중복 제거**: 각 Controller 메서드에서 반복되는 세션 체크 제거
2. **일관성 확보**: 모든 요청에 동일한 인증 로직 적용
3. **유지보수 향상**: 세션 관련 로직을 한 곳에 집중
4. **보안 강화**: 로깅 및 타임아웃 관리 강화

---

## 📁 변경된 파일 목록

### 1. 새로 생성된 파일

#### `src/main/java/com/jiniebox/jangbogo/config/SessionConstants.java`
- **목적**: 세션 관련 상수 중앙 관리
- **내용**:
  - 세션 키 상수 (ADMIN_LOGGED_IN, ADMIN_USERNAME 등)
  - 세션 타임아웃 설정 (30분)
  - 로그인 시간, 마지막 활동 시간 키

```java
public static final String SESSION_ADMIN_KEY = "ADMIN_LOGGED_IN";
public static final String SESSION_USERNAME_KEY = "ADMIN_USERNAME";
public static final String SESSION_LOGIN_TIME_KEY = "LOGIN_TIME";
public static final String SESSION_LAST_ACTIVITY_KEY = "LAST_ACTIVITY_TIME";
public static final int SESSION_TIMEOUT_MINUTES = 30;
```

### 2. 개선된 파일

#### `src/main/java/com/jiniebox/jangbogo/config/AuthInterceptor.java`
- **개선 사항**:
  - 로깅 강화 (Log4j2 사용)
  - 세션 타임아웃 자동 체크
  - 클라이언트 IP 추적
  - 세션 활동 시간 자동 갱신
  - 뷰에 사용자 정보 자동 추가 (postHandle)
  - 예외 처리 강화 (afterCompletion)
  - API/페이지 요청 구분 처리

```java
// 주요 기능
- preHandle(): 세션 검사 및 인증 처리
- postHandle(): 뷰에 사용자 정보 자동 추가
- afterCompletion(): 예외 로깅
- isAuthenticated(): 세션 타임아웃 체크 포함
- getClientIP(): 프록시 환경에서도 실제 IP 추적
```

#### `src/main/java/com/jiniebox/jangbogo/config/WebMvcConfig.java`
- **개선 사항**:
  - 제외 경로를 그룹화하여 가독성 향상
  - 우선순위 설정 (order=1)
  - 정적 리소스 경로 추가 (robots.txt 등)
  - 주석 개선

#### `src/main/java/com/jiniebox/jangbogo/controller/AdminController.java`
- **개선 사항**:
  - SessionConstants 사용으로 세션 키 중앙 관리
  - `getInfo()` 메서드에서 중복 세션 체크 제거
  - 로그인 시 세션 타임아웃 및 로그인 시간 설정
  - 모든 세션 관련 코드에서 SessionConstants 사용

**변경 전:**
```java
private static final String SESSION_ADMIN_KEY = "ADMIN_LOGGED_IN";
private static final String SESSION_USERNAME_KEY = "ADMIN_USERNAME";

// getInfo()에서 중복 세션 체크
Boolean isLoggedIn = (Boolean) session.getAttribute(SESSION_ADMIN_KEY);
if (isLoggedIn == null || !isLoggedIn) {
    node.put("success", false);
    node.put("code", 401);
    node.put("message", "로그인이 필요합니다.");
    return node;
}
```

**변경 후:**
```java
// SessionConstants 사용
import com.jiniebox.jangbogo.config.SessionConstants;

// getInfo()에서 중복 체크 제거 (Interceptor가 처리)
String username = (String) session.getAttribute(SessionConstants.SESSION_USERNAME_KEY);
// 세션이 여기까지 도달했다면 이미 인증된 상태
```

#### `src/main/java/com/jiniebox/jangbogo/dev/HomeController.java`
- **개선 사항**:
  - SessionConstants 사용
  - `index()` 메서드에서 중복 세션 체크 제거
  - `signin()` 메서드는 Interceptor 제외 경로이므로 내부 체크 유지

---

## 🔄 동작 흐름

### 1. 인증이 필요한 페이지/API 요청 시

```
사용자 요청
    ↓
[AuthInterceptor.preHandle()]
    ↓
제외 경로 체크 (로그인 페이지, 정적 리소스 등)
    ↓ (제외 경로가 아닌 경우)
세션 검사
    ↓
┌─────────────┐
│ 인증 실패   │ → API 요청: 401 JSON 응답
│             │ → 페이지 요청: /signin 리다이렉트
└─────────────┘
    ↓
┌─────────────┐
│ 인증 성공   │ → 세션 활동 시간 갱신
│             │ → 로깅
└─────────────┘
    ↓
Controller 메서드 실행
    ↓
[AuthInterceptor.postHandle()]
    ↓
뷰에 사용자 정보 자동 추가
    ↓
응답 반환
```

### 2. 로그인 처리

```
POST /api/login
    ↓
[AdminController.login()]
    ↓
ID/PW 검증
    ↓
세션 생성:
  - SESSION_ADMIN_KEY = true
  - SESSION_USERNAME_KEY = username
  - SESSION_LOGIN_TIME_KEY = 현재 시간
  - 세션 타임아웃 설정 (30분)
    ↓
로그인 성공 응답
```

---

## 📊 개선 효과

### Before (개선 전)
```java
@GetMapping("/malls/getinfo")
@ResponseBody
public JsonNode getInfo(HttpSession session) {
    // 🔴 모든 메서드마다 세션 체크 중복
    Boolean isLoggedIn = (Boolean) session.getAttribute(SESSION_ADMIN_KEY);
    if (isLoggedIn == null || !isLoggedIn) {
        node.put("success", false);
        node.put("code", 401);
        node.put("message", "로그인이 필요합니다.");
        return node;
    }
    
    // 실제 비즈니스 로직...
}
```

### After (개선 후)
```java
@GetMapping("/malls/getinfo")
@ResponseBody
public JsonNode getInfo(HttpSession session) {
    // ✅ Interceptor가 세션 체크 수행
    // 여기까지 도달했다면 이미 인증된 상태
    
    // 실제 비즈니스 로직...
}
```

### 개선 효과 요약

| 항목 | 개선 전 | 개선 후 |
|------|---------|---------|
| 세션 체크 코드 | 각 메서드마다 중복 | Interceptor에서 전역 처리 |
| 세션 키 관리 | 각 클래스마다 상수 선언 | SessionConstants에서 중앙 관리 |
| 로깅 | 거의 없음 | 전체 요청 추적 가능 |
| 타임아웃 관리 | 서버 기본값 의존 | 명시적 30분 설정 + 자동 체크 |
| IP 추적 | 없음 | 프록시 환경 대응 IP 추적 |
| 유지보수성 | 낮음 (분산된 로직) | 높음 (중앙화된 로직) |

---

## 🔒 보안 개선

1. **세션 타임아웃 관리**
   - 로그인 시 명시적으로 30분 설정
   - 각 요청마다 경과 시간 체크
   - 타임아웃 시 자동 로그아웃

2. **로깅 강화**
   - 모든 인증 실패 로깅 (URI, IP 포함)
   - 로그인/로그아웃 이벤트 추적
   - 디버그 모드에서 상세 세션 정보 로깅

3. **IP 추적**
   - 프록시/로드밸런서 환경 대응
   - X-Forwarded-For 헤더 체크
   - 실제 클라이언트 IP 기록

---

## 🛠️ 사용 방법

### 새로운 보호된 API 추가하기

```java
@GetMapping("/api/protected-resource")
@ResponseBody
public JsonNode getProtectedResource(HttpSession session) {
    // ✅ 세션 체크 불필요 (Interceptor가 자동 처리)
    
    String username = (String) session.getAttribute(SessionConstants.SESSION_USERNAME_KEY);
    
    // 비즈니스 로직 구현
    return response;
}
```

### 제외 경로 추가하기

인증이 필요 없는 새로운 경로를 추가하려면:

1. **AuthInterceptor.java** 수정:
```java
private static final List<String> EXCLUDE_PATHS = Arrays.asList(
    "/signin",
    "/api/login",
    "/api/session-check",
    "/error",
    "/actuator",
    "/new-public-path"  // ← 새 경로 추가
);
```

2. **WebMvcConfig.java** 수정:
```java
.excludePathPatterns(
    "/signin",
    "/api/login",
    "/api/session-check",
    "/error",
    "/actuator/**",
    "/new-public-path",  // ← 새 경로 추가
    // ...
)
```

---

## 🧪 테스트 시나리오

### 1. 로그인 전 접근 테스트
```
1. 브라우저에서 http://localhost:8282/ 접속
2. 로그인하지 않은 상태
3. 예상: /signin 페이지로 리다이렉트
4. 로그 확인: "인증 실패 - URI: /, IP: ..."
```

### 2. 로그인 후 접근 테스트
```
1. /signin에서 로그인
2. 메인 페이지 접속
3. 예상: index.html 표시
4. 로그 확인: "인증 성공 - User: admin_local, URI: /, Session: ..."
```

### 3. API 인증 실패 테스트
```
1. 로그인하지 않은 상태에서 /malls/getinfo 호출
2. 예상: 401 JSON 응답
   {
     "success": false,
     "code": 401,
     "message": "로그인이 필요합니다.",
     "redirectUrl": "/signin"
   }
```

### 4. 세션 타임아웃 테스트
```
1. 로그인 후 30분 대기
2. 페이지 새로고침
3. 예상: /signin 페이지로 리다이렉트
4. 로그 확인: "세션 타임아웃 - 경과 시간: 30분"
```

---

## 📝 주의사항

1. **세션 타임아웃 설정**
   - 현재 30분으로 설정
   - 변경하려면 `SessionConstants.SESSION_TIMEOUT_MINUTES` 수정

2. **제외 경로 이중 관리**
   - AuthInterceptor와 WebMvcConfig 양쪽에서 관리
   - 성능 최적화를 위해 양쪽 모두 설정 권장

3. **로그 레벨**
   - DEBUG 레벨: 모든 세션 검사 로깅
   - INFO 레벨: 로그인/로그아웃/타임아웃만 로깅
   - `log4j2-spring.xml`에서 조정 가능

4. **HomeController 위치**
   - 현재 `dev` 패키지에 위치
   - 실제 운영 시 적절한 패키지로 이동 필요

---

## 🚀 향후 개선 가능 사항

1. **권한 기반 접근 제어**
   - Spring Security 도입 검토
   - Role 기반 권한 체크

2. **다중 로그인 방지**
   - 동일 계정 중복 로그인 차단
   - SessionRegistry 활용

3. **Remember Me 기능**
   - 자동 로그인 옵션
   - 토큰 기반 인증

4. **API 응답 표준화**
   - 공통 응답 포맷 정의
   - ResponseEntity 사용

5. **세션 저장소 변경**
   - Redis 기반 세션 스토어
   - 클러스터 환경 대응

---

## 📚 참고 자료

- [Spring HandlerInterceptor 공식 문서](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/servlet/HandlerInterceptor.html)
- [Session Management in Spring](https://docs.spring.io/spring-session/reference/)
- [Log4j2 Configuration](https://logging.apache.org/log4j/2.x/manual/configuration.html)

---

## 📞 문의

개선 사항에 대한 문의나 버그 리포트는 프로젝트 관리자에게 연락하세요.

**작성일**: 2025-10-25
**버전**: 1.0.0

