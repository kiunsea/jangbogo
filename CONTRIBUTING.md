# Contributing to Jangbogo

Jangbogo 프로젝트에 기여해주셔서 감사합니다! 🎉

이 문서는 프로젝트에 기여하는 방법을 안내합니다.

---

## 📋 목차

1. [행동 강령](#행동-강령)
2. [기여 방법](#기여-방법)
3. [개발 환경 설정](#개발-환경-설정)
4. [코드 스타일](#코드-스타일)
5. [커밋 메시지 규칙](#커밋-메시지-규칙)
6. [Pull Request 절차](#pull-request-절차)
7. [이슈 제출](#이슈-제출)

---

## 행동 강령

Jangbogo 프로젝트는 [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)를 준수합니다.

참여하시는 모든 분들께서 서로 존중하고 협력하는 환경을 만들어주시기 바랍니다.

---

## 기여 방법

다음과 같은 방법으로 기여할 수 있습니다:

- 🐛 **버그 리포트**: 발견한 버그를 이슈로 제출
- ✨ **기능 제안**: 새로운 기능 아이디어 제안
- 📝 **문서 개선**: 오타 수정, 설명 추가 등
- 💻 **코드 기여**: 버그 수정, 기능 구현
- 🌐 **번역**: 문서 번역 (영어 등)
- 🧪 **테스트**: 버그 재현, 테스트 케이스 작성

---

## 개발 환경 설정

### 1. 필수 도구

- **JDK 21** (OpenJDK 또는 Oracle JDK)
- **Git**
- **Gradle** (프로젝트에 포함된 Wrapper 사용)
- **IDE** (IntelliJ IDEA, Eclipse, VS Code 등)
- **Chrome 또는 Edge** 브라우저 (Selenium 테스트용)

### 2. 프로젝트 클론

```bash
git clone https://github.com/kiunsea/jangbogo.git
cd jangbogo
```

### 3. 의존성 설치 및 빌드

```bash
# Windows
.\gradlew build

# Linux/Mac
./gradlew build
```

### 4. 개발 모드 실행

```bash
.\gradlew bootRun
```

브라우저에서 `http://localhost:8282` 접속

### 5. 관리자 계정 설정

개발용 계정 생성:

**config/admin.properties** 파일 생성:
```properties
admin.id=dev_admin
admin.pass=dev1234
```

---

## 코드 스타일

### Java 코드 스타일

- **들여쓰기**: Tab (4 spaces)
- **클래스명**: PascalCase (예: `MallOrderUpdater`)
- **메서드명**: camelCase (예: `updateMallOrders`)
- **상수명**: UPPER_SNAKE_CASE (예: `MAX_RETRY_COUNT`)
- **주석**: 한글 주석 허용, 복잡한 로직은 주석 필수

**예시:**
```java
/**
 * 쇼핑몰 구매내역을 수집합니다.
 * 
 * @param mallSeq 쇼핑몰 시퀀스 번호
 * @return 수집된 주문 개수
 */
public int collectOrders(int mallSeq) {
    // 구현...
}
```

### HTML/JavaScript 스타일

- **들여쓰기**: 4 spaces
- **변수명**: camelCase
- **함수명**: camelCase
- **주석**: 한글 주석 허용

### SQL 스타일

- **키워드**: 대문자 (SELECT, FROM, WHERE)
- **테이블명**: 소문자 snake_case
- **컬럼명**: 소문자 snake_case

---

## 커밋 메시지 규칙

[Conventional Commits](https://www.conventionalcommits.org/) 규칙을 따릅니다.

### 형식

```
<타입>(<범위>): <제목>

<본문>

<푸터>
```

### 타입

- **feat**: 새로운 기능 추가
- **fix**: 버그 수정
- **docs**: 문서 변경
- **style**: 코드 포맷팅 (기능 변경 없음)
- **refactor**: 리팩토링
- **test**: 테스트 추가/수정
- **chore**: 빌드 설정, 패키지 등

### 예시

```
feat(mall): SSG 쇼핑몰 구매내역 수집 기능 추가

- Selenium으로 로그인 자동화
- 주문 목록 파싱 및 저장
- 에러 처리 추가

Closes #123
```

```
fix(dao): 데이터베이스 연결 누수 문제 수정

Connection을 제대로 닫지 않아 발생하는 문제 해결

Fixes #456
```

```
docs: README에 설치 방법 추가
```

---

## Pull Request 절차

### 1. Fork 및 브랜치 생성

```bash
# Fork 후 클론
git clone https://github.com/YOUR_USERNAME/jangbogo.git
cd jangbogo

# upstream 설정
git remote add upstream https://github.com/kiunsea/jangbogo.git

# 기능 브랜치 생성
git checkout -b feat/your-feature-name
```

### 2. 코드 작성

- 기능 구현 또는 버그 수정
- 테스트 코드 작성 (가능한 경우)
- 문서 업데이트

### 3. 커밋

```bash
git add .
git commit -m "feat(mall): 새로운 쇼핑몰 지원 추가"
```

### 4. Push 및 PR 생성

```bash
git push origin feat/your-feature-name
```

GitHub에서 Pull Request 생성

### 5. PR 템플릿

PR 생성 시 다음 정보를 포함해주세요:

- **변경 사항**: 무엇을 변경했는지
- **이유**: 왜 변경했는지
- **테스트**: 어떻게 테스트했는지
- **스크린샷**: UI 변경 시 첨부
- **관련 이슈**: `Closes #123`, `Fixes #456`

---

## 이슈 제출

### 버그 리포트

**제목 형식:** `[BUG] 간단한 버그 설명`

**내용 포함 사항:**
1. **환경 정보**
   - OS 버전 (예: Windows 11)
   - Java 버전
   - Jangbogo 버전

2. **재현 방법**
   - 단계별 재현 절차

3. **기대 동작**
   - 어떻게 동작해야 하는지

4. **실제 동작**
   - 실제로 어떻게 동작하는지

5. **로그 및 스크린샷**
   - `logs/jangbogo.log` 내용
   - 에러 메시지 스크린샷

### 기능 제안

**제목 형식:** `[FEATURE] 기능 이름`

**내용 포함 사항:**
1. **제안 배경**: 왜 이 기능이 필요한지
2. **구체적인 기능**: 어떤 기능인지
3. **사용 시나리오**: 어떻게 사용될지
4. **참고 자료**: 유사 기능 예시 등

---

## 개발 가이드

### 프로젝트 구조

```
src/main/java/com/jiniebox/jangbogo/
├─ ctrl/       # Controller - REST API 엔드포인트
├─ dao/        # Data Access Object - DB 접근
├─ dto/        # Data Transfer Object
├─ svc/        # Service - 비즈니스 로직
│  └─ mall/    # 쇼핑몰별 구현
├─ sys/        # System - 인증, 세션, 설정
└─ util/       # Utility - 유틸리티 클래스
```

### 새로운 쇼핑몰 추가

1. `svc/mall/` 에 새 클래스 생성 (예: `NewMall.java`)
2. `MallSession` 인터페이스 구현
3. `data.sql`에 쇼핑몰 정보 추가
4. `index.html`에 UI 추가
5. 테스트 코드 작성

### 데이터베이스 스키마 변경

1. `src/main/resources/schema.sql` 수정
2. 마이그레이션 스크립트 작성 (필요 시)
3. DAO 클래스 업데이트
4. 테스트 확인

### 로컬 테스트

```bash
# 단위 테스트
.\gradlew test

# 애플리케이션 실행
.\gradlew bootRun

# 배포 패키지 빌드
.\gradlew clean bootJar createJre packageDist
```

---

## 코드 리뷰 가이드라인

PR 리뷰 시 확인 사항:

- ✅ 코드가 프로젝트 스타일 가이드를 따르는지
- ✅ 테스트가 포함되어 있는지
- ✅ 문서가 업데이트되었는지
- ✅ 커밋 메시지가 규칙을 따르는지
- ✅ 기존 기능을 깨뜨리지 않는지
- ✅ 보안 문제가 없는지

---

## 라이선스

기여하신 모든 코드는 **AGPL-3.0-or-later** 라이선스 하에 배포됩니다.

Pull Request를 제출함으로써 다음에 동의하는 것으로 간주됩니다:
- 작성한 코드를 AGPL-3.0 라이선스로 배포하는 것에 동의
- 저작권은 원작자(jiniebox) 또는 각 기여자에게 귀속

---

## 질문이 있으신가요?

- **GitHub Issues**에 질문 등록: https://github.com/kiunsea/jangbogo/issues
- **Email**: kiunsea@gmail.com
- **Website**: https://jiniebox.com
- **Discussions** 활용 (활성화된 경우)
- 관련 문서 참조:
  - [개발자 문서](doc/README.md)
  - [빌드 가이드](doc/BUILD_GUIDE.md)

---

## 감사합니다!

여러분의 기여가 Jangbogo를 더 나은 프로젝트로 만듭니다. 🙏

**Happy Coding!** 💻

---

**Copyright © 2025 [jiniebox.com](https://jiniebox.com)**

