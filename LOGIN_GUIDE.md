# 🔐 장보고 로그인 시스템 가이드

## 📋 개요

장보고 프로젝트에 세션 기반 로그인 시스템이 구현되었습니다.

## ✨ 주요 기능

### 1. 로그인 화면 (`/signin`)
- 💎 **모던한 UI**: Bootstrap 5 기반 그라데이션 디자인
- 🔄 **AJAX 로그인**: 페이지 리로드 없이 로그인 처리
- ⚡ **실시간 피드백**: 로딩 스피너, 에러 메시지, 성공 알림
- 🎯 **자동 리다이렉트**: 로그인 성공 시 메인 페이지로 이동
- 🔒 **세션 체크**: 이미 로그인된 경우 자동으로 메인 페이지로 이동

### 2. 세션 관리
- ✅ **세션 기반 인증**: HttpSession을 사용한 상태 유지
- 🛡️ **인터셉터 보호**: 보호된 페이지 자동 체크
- 🚪 **로그아웃**: 세션 무효화 및 로그인 페이지 리다이렉트

### 3. API 엔드포인트

#### POST `/api/login`
로그인 요청

**Request:**
```json
{
  "username": "admin_prop",
  "password": "admin1234_prop"
}
```

**Response (성공):**
```json
{
  "success": true,
  "message": "로그인 성공",
  "username": "admin_prop"
}
```

**Response (실패):**
```json
{
  "success": false,
  "message": "아이디 또는 비밀번호가 일치하지 않습니다."
}
```

#### POST `/api/logout`
로그아웃 요청

**Response:**
```json
{
  "success": true,
  "message": "로그아웃되었습니다."
}
```

#### GET `/api/session-check`
세션 상태 확인

**Response (로그인됨):**
```json
{
  "authenticated": true,
  "username": "admin_prop"
}
```

**Response (로그인 안됨):**
```json
{
  "authenticated": false
}
```

#### GET `/api/getinfo` (보호된 API 예제)
세션 체크가 필요한 보호된 API

**Response (인증됨):**
```json
{
  "success": true,
  "message": "장보고 프로젝트",
  "code": 200,
  "currentUser": "admin_prop",
  "user": {
    "name": "홍길동",
    "level": 5
  }
}
```

**Response (인증 안됨):**
```json
{
  "success": false,
  "code": 401,
  "message": "로그인이 필요합니다."
}
```

## 🚀 사용 방법

### 1. 애플리케이션 시작

```bash
# Gradle로 실행
./gradlew bootRun

# 또는 JAR 파일로 실행
./gradlew build
java -jar build/libs/jangbogo-1.0.0.jar
```

### 2. 브라우저에서 접속

```
http://localhost:8282
```

- 로그인되지 않은 경우 자동으로 `/signin` 페이지로 리다이렉트됩니다.

### 3. 로그인

현재 설정된 계정으로 로그인:

**Option 1: config/admin.properties (우선순위 높음)**
```properties
admin.id=admin_prop
admin.pass=admin1234_prop
```

**Option 2: application-local.yml (Profile 사용 시)**
```yaml
admin:
  id: admin_local
  pass: admin1234_local
```

**Option 3: application.yml (기본값)**
```yaml
admin:
  id: ${ADMIN_ID:admin_main}
  pass: ${ADMIN_PASS:admin1234_main}
```

**Option 4: 환경변수 (최우선)**
```bash
export ADMIN_ID=my_admin
export ADMIN_PASS=my_password
```

### 4. 로그아웃

메인 페이지 상단 헤더의 로그아웃 버튼 클릭

## 🏗️ 아키텍처

### 컴포넌트 구조

```
┌─────────────────────────────────────────────────┐
│            signin.html (로그인 화면)              │
│         Bootstrap 5 + AJAX + Animation          │
└─────────────────┬───────────────────────────────┘
                  │ POST /api/login
                  ↓
┌─────────────────────────────────────────────────┐
│         AdminController (컨트롤러)                │
│  - login()  : 로그인 처리 + 세션 생성            │
│  - logout() : 로그아웃 + 세션 무효화             │
│  - checkSession() : 세션 상태 확인               │
│  - getInfo() : 보호된 API (세션 체크)            │
└─────────────────┬───────────────────────────────┘
                  │
                  ↓
┌─────────────────────────────────────────────────┐
│            HttpSession (세션 저장소)              │
│  - ADMIN_LOGGED_IN : true/false                 │
│  - ADMIN_USERNAME  : 사용자 ID                   │
└─────────────────┬───────────────────────────────┘
                  │
                  ↓
┌─────────────────────────────────────────────────┐
│      AuthInterceptor (인터셉터)                   │
│  모든 요청을 가로채서 세션 체크                    │
│  - 로그인 안됨 → /signin 리다이렉트               │
│  - API 인증 실패 → 401 에러                       │
└─────────────────────────────────────────────────┘
```

### 페이지 흐름

```
1. 사용자가 / 접속
   ↓
2. AuthInterceptor가 세션 체크
   ↓
3-a. 로그인 안됨 → /signin으로 리다이렉트
   ↓
4. 로그인 폼 제출 (AJAX)
   ↓
5. AdminController.login()
   ↓
6. 인증 성공 → 세션 생성
   ↓
7. 클라이언트가 / 로 리다이렉트
   ↓
8. AuthInterceptor가 세션 체크 → 통과
   ↓
9. index.html 표시 (로그인 완료)

3-b. 로그인됨 → index.html 바로 표시
```

## 🔒 보안 특징

### 1. 세션 보안
- ✅ HttpOnly 쿠키 (JavaScript에서 접근 불가)
- ✅ 세션 타임아웃 (기본 30분, 설정 가능)
- ✅ 로그아웃 시 세션 완전 무효화

### 2. 인증 체크
- ✅ 인터셉터를 통한 자동 인증 체크
- ✅ API와 페이지 요청 분리 처리
- ✅ 정적 리소스는 인증 제외

### 3. 설정 보안
- ✅ 민감정보 외부 파일 분리
- ✅ 환경변수 지원
- ✅ `.gitignore`로 민감정보 보호

## 📝 설정 변경

### 세션 타임아웃 변경

`application.yml`에 추가:
```yaml
server:
  servlet:
    session:
      timeout: 30m  # 30분 (기본값)
```

### 쿠키 보안 강화

`application.yml`에 추가:
```yaml
server:
  servlet:
    session:
      cookie:
        http-only: true
        secure: true  # HTTPS 사용 시
        same-site: strict
```

## 🧪 테스트

### 1. 로그인 테스트 (curl)

```bash
# 로그인
curl -X POST http://localhost:8282/api/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin_prop","password":"admin1234_prop"}' \
  -c cookies.txt

# 세션 체크
curl http://localhost:8282/api/session-check \
  -b cookies.txt

# 보호된 API 호출
curl http://localhost:8282/api/getinfo \
  -b cookies.txt

# 로그아웃
curl -X POST http://localhost:8282/api/logout \
  -b cookies.txt
```

### 2. 브라우저 개발자 도구 테스트

```javascript
// 콘솔에서 실행
// 로그인
await fetch('/api/login', {
  method: 'POST',
  headers: {'Content-Type': 'application/json'},
  body: JSON.stringify({username: 'admin_prop', password: 'admin1234_prop'})
}).then(r => r.json()).then(console.log);

// 세션 체크
await fetch('/api/session-check').then(r => r.json()).then(console.log);

// 로그아웃
await fetch('/api/logout', {method: 'POST'}).then(r => r.json()).then(console.log);
```

## 📂 파일 구조

```
src/main/
├── java/com/jiniebox/jangbogo/
│   ├── controller/
│   │   └── AdminController.java       # 로그인/로그아웃 API
│   ├── config/
│   │   ├── AuthInterceptor.java       # 인증 인터셉터
│   │   └── WebMvcConfig.java          # 인터셉터 등록
│   └── HomeController.java            # 페이지 라우팅
└── resources/
    └── templates/
        ├── signin.html                 # 로그인 페이지
        ├── index.html                  # 메인 페이지 (보호됨)
        └── fragments/
            └── header.html             # 헤더 (로그아웃 버튼 포함)
```

## 🎯 다음 단계 (추천)

1. **HTTPS 적용**: 운영 환경에서 SSL/TLS 인증서 설정
2. **비밀번호 암호화**: BCrypt 등으로 비밀번호 해싱
3. **다중 사용자**: DB 연동하여 여러 사용자 계정 관리
4. **권한 관리**: Role 기반 접근 제어 (ADMIN, USER 등)
5. **로그인 시도 제한**: Brute Force 공격 방지
6. **2FA (Two-Factor Authentication)**: 이중 인증 추가

## 🐛 트러블슈팅

### 로그인이 안 됨
1. `config/admin.properties` 파일 확인
2. 콘솔에서 "✓ 로그인 성공" 또는 "✗ 로그인 실패" 로그 확인
3. 아이디/비밀번호 대소문자 확인

### 세션이 유지되지 않음
1. 브라우저 쿠키 설정 확인
2. 세션 타임아웃 시간 확인
3. 서버 재시작 시 세션은 초기화됨

### 로그아웃 후에도 접근됨
1. 브라우저 캐시 삭제
2. 시크릿/프라이빗 모드에서 테스트

## 📞 문의

- GitHub Issues: 버그 리포트 및 기능 제안
- LICENSE: AGPL-3.0-or-later

---

**Made with ❤️ by jiniebox**

