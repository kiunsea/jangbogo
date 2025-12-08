# 개발 스크립트

Jangbogo 개발을 위한 Windows 배치 스크립트 모음입니다.

---

## 🚀 빠른 시작

### 개발 테스트
```cmd
bat\test_run.bat
```

### 배포 패키지 빌드
```cmd
bat\build_package.bat
```

### 클린 빌드
```cmd
bat\clean_build.bat
```

---

## 📋 스크립트 목록

| 파일 | 명령어 | 설명 |
|------|--------|------|
| **test_run.bat** | `gradlew bootRun` | 개발 모드 실행 |
| **build_package.bat** | `gradlew clean bootJar createJre packageDist` | 배포 ZIP 생성 |
| **clean_build.bat** | `gradlew clean build` | 클린 빌드 + 테스트 |

---

## 🔧 스크립트 상세 기능

### test_run.bat - 개발 테스트 실행

개발 환경에서 Spring Boot 애플리케이션을 실행하는 스크립트입니다.

**주요 기능:**
- **환경 초기화**: Gradle Daemon 중지 및 clean 실행
- **캐시 삭제**: `build`, `bin`, `.gradle` 디렉토리 완전 삭제로 깨끗한 환경 구성
- **개발 모드 실행**: Spring Boot 애플리케이션을 개발 모드로 실행 (`bootRun`)
- **캐시 비활성화**: 템플릿 및 정적 리소스 캐시를 비활성화하여 실시간 변경사항 반영
- **포트 정보**: 애플리케이션이 `http://localhost:8282`에서 실행됨을 안내

**실행 결과:**
- 애플리케이션이 백그라운드에서 실행되며, `Ctrl+C`로 종료 가능
- 오류 발생 시 상세한 오류 메시지 표시

---

### build_package.bat - 배포 패키지 빌드

배포용 ZIP 패키지를 생성하는 스크립트입니다.

**주요 기능:**
- **이전 빌드 정리**: `clean`으로 이전 빌드 결과물 삭제
- **JAR 파일 생성**: `bootJar`로 실행 가능한 Spring Boot JAR 파일 생성
- **Custom JRE 생성**: `createJre`로 애플리케이션에 필요한 최소 JRE 생성
- **배포 패키지 생성**: `packageDist`로 배포용 ZIP 파일 생성
- **결과 확인**: 생성된 ZIP 파일의 크기를 MB 단위로 표시
- **자동 열기**: 빌드 완료 후 Windows 탐색기에서 ZIP 파일 위치 자동 열기

**생성 파일:**
- `build\distributions\Jangbogo-distribution.zip` - 배포용 ZIP 패키지

**예상 소요 시간:** 1-2분

---

### clean_build.bat - 클린 빌드

이전 빌드 결과를 완전히 삭제하고 새로 빌드하는 스크립트입니다.

**주요 기능:**
- **완전 정리**: `clean`으로 이전 빌드 산출물 삭제
- **전체 빌드**: `build`로 프로젝트 전체 빌드 및 테스트 실행
- **결과 확인**: 생성된 JAR 파일의 크기를 MB 단위로 표시

**생성 파일:**
- `build\libs\jangbogo-0.5.0.jar` - 빌드된 JAR 파일

**사용 시나리오:**
- 빌드 캐시 문제 해결
- 깨끗한 빌드 환경에서 테스트
- 배포 전 최종 빌드 확인

---

## 💡 사용 팁

### Windows 탐색기에서 실행 (가장 간단)
1. `bat` 폴더 열기
2. 원하는 `.bat` 파일 더블클릭
3. **자동으로 프로젝트 루트로 이동하여 실행** ✅

### 명령 프롬프트에서 실행
```cmd
cd D:\GIT\jangbogo
bat\test_run.bat
```

### PowerShell에서 실행
```powershell
cd D:\GIT\jangbogo
.\bat\test_run.bat
```

**참고:** 모든 스크립트는 자동으로 프로젝트 루트(`%~dp0\..`)로 이동하여 실행됩니다.

---

## 📚 상세 문서

더 자세한 내용은 [DEVELOPMENT_SCRIPTS.md](../DEVELOPMENT_SCRIPTS.md)를 참조하세요.

---

**버전:** 0.5.0  
**최종 업데이트:** 2025-11-07

