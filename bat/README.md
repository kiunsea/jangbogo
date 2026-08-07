# 개발 스크립트

Jangbogo 개발을 위한 Windows 배치 스크립트 모음입니다.

---

## 🚀 빠른 시작

### 소스 수정 사항을 바로 실행 (개발 반복)
```cmd
bat\test_run.bat
```

### 빌드된 JAR 을 실행 (패키징 검증)
```cmd
bat\run_jar.bat
```

### 클린 빌드
```cmd
bat\clean_build.bat
```

### 배포 패키지 빌드
```cmd
bat\build_package.bat
```

---

## 📋 스크립트 목록

| 파일 | 실행 방식 | 역할 |
|------|--------|------|
| **test_run.bat** | `gradlew bootRun` (소스에서 직접) | 지금 소스에 있는 수정 사항을 바로 실행. 핫리로드 살아 있음 |
| **run_jar.bat** | `java -jar build\libs\jangbogo-*.jar` | 빌드된 JAR 이 실제로 도는지 확인 |
| **clean_build.bat** | `gradlew clean build` | 클린 빌드 + 테스트 |
| **build_package.bat** | `gradlew clean bootJar createJre packageDist` | 배포 ZIP 생성 |

> **`test_run.bat` 과 `run_jar.bat` 은 역할이 다릅니다.** 앞은 *소스*를, 뒤는 *산출물*을 실행합니다.
> `bootRun` 과 JAR 실행은 클래스패스 구성 순서·리소스 로딩 방식(파일 vs JAR 엔트리)·
> `spring.config.import` 의 상대 경로 해석이 달라, **소스에서는 되는데 JAR 에서 안 되는 경우가
> 실제로 생깁니다.** 배포되는 것은 JAR 이므로 릴리스 전에는 `run_jar.bat` 으로 한 번 확인하세요.

### ⚠️ 두 스크립트 모두 기동 자동수집을 기본으로 끕니다

`application.yml` 의 `jangbogo.startup.collect.enabled` 는 `true` 입니다. 끄지 않으면 **띄우는 즉시
1회 수집과 스케줄 복원이 돌면서 실제 쇼핑몰에 로그인합니다.** 개발 중 스크립트를 여러 번 돌리면
같은 계정으로 반복 로그인하게 되고, 그것이 바로 쇼핑몰이 차단하는 패턴입니다.

수집까지 함께 보려면 인자로 명시하세요. 그 밖의 인자도 그대로 애플리케이션에 전달됩니다.

```cmd
bat\test_run.bat --jangbogo.startup.collect.enabled=true
bat\run_jar.bat --server.port=8283
```

---

## 🔧 스크립트 상세 기능

### test_run.bat - 소스 기반 개발 실행

**지금 소스에 있는 수정 사항을 그대로 실행해 보는** 스크립트입니다. JAR 을 만들지 않고 컴파일된
클래스로 띄우므로 devtools 핫리로드가 살아 있습니다.

**주요 기능:**
- **소스에서 실행**: `gradlew bootRun`
- **자동수집 기본 차단**: `--jangbogo.startup.collect.enabled=false` 를 기본으로 붙임
- **인자 전달**: 스크립트에 준 인자를 애플리케이션으로 그대로 넘김
- **캐시 비활성화**: 템플릿 및 정적 리소스 캐시를 꺼 실시간 반영
- **DB**: 프로젝트 루트의 `db\jangbogo-dev.db`

**지우지 않습니다.** 예전에는 여기서 `clean` 과 `rmdir /s /q build bin .gradle` 을 돌렸는데 둘 다
들어냈습니다. 이 프로젝트는 배포 패키지를 `build\distributions` 아래에 풀어 그 자리에서 실행하는
관행이 있고, 그렇게 실행된 인스턴스는 자기 `db\` 를 그 안에 만듭니다. 즉 `build\` 아래에
"지워도 되는 빌드 산출물" 과 "지우면 안 되는 실제 구매 내역" 이 섞입니다. **실제로 그 한 줄에
배포본 인스턴스와 DB 가 통째로 사라진 적이 있고, 휴지통을 거치지 않아 복구하지 못했습니다.**
클린 빌드가 필요하면 `clean_build.bat` 을 쓰세요 — 그쪽은 `build.gradle` 의 clean 가드가 지킵니다.

---

### run_jar.bat - 패키징 산출물 실행

`clean_build.bat` 또는 `build_package.bat` 이 만든 **JAR 을 그대로 띄웁니다.**

**주요 기능:**
- **최신 JAR 자동 선택**: `build\libs\jangbogo-*.jar` 중 가장 최근 것 (버전을 적지 않음)
- **없으면 안내**: 어떤 스크립트를 먼저 돌려야 하는지 알려 줌
- **자동수집 기본 차단** + **인자 전달** (`test_run.bat` 과 동일)
- **DB**: 프로젝트 루트의 `db\jangbogo-dev.db`

**주의:** 이것은 개발 트리의 JAR 을 개발용 DB 로 띄우는 것입니다. **배포본을 그대로 재현하려면**
배포 ZIP 을 저장소 **밖**(예: `D:\Jangbogo`)에 풀고 그 폴더의 `Jangbogo.bat` 을 쓰세요 — 그쪽은
번들 JRE 를 쓰고 자기 `db\` 를 그 폴더에 만듭니다.

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
- `build\libs\jangbogo-x.y.z.jar` - 빌드된 JAR 파일 (버전은 build.gradle 의 version 값)

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

**버전:** 0.6.0
**최종 업데이트:** 2026-01-28

