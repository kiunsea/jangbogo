# Jangbogo v0.5.0 릴리스 노트

**릴리스 날짜**: 2025-11-07  
**버전**: 0.5.0  
**다운로드**: Jangbogo-distribution.zip (~100 MB)

---

## 🎉 새로운 기능

### 배포 패키지
- ✨ **Java 설치 불필요**: Custom JRE 번들링 (jlink)
- ✨ **간편한 설치**: ZIP 압축 해제만 하면 실행
- ✨ **브라우저 자동 실행**: 프로그램 시작 시 자동으로 브라우저 열림
- ✨ **Windows 서비스 지원**: WinSW로 서비스 등록 가능
- ✨ **한글 출력 지원**: DOS 콘솔에서 한글 정상 출력 (UTF-8)
- ✨ **커스텀 배너**: JangBoGo ASCII 아트 배너 및 버전 정보 표시

### 구매내역 수집
- ✨ **신규 데이터만 저장**: 구매내역 수집 시 신규 추가된 항목만 파일로 저장
- ✨ **자동 저장 옵션**: "구매내역 수집시 함께 저장" 옵션 추가
- ✨ **다양한 포맷**: JSON, CSV, Excel 형식 지원

### 사용자 인터페이스
- ✨ **모던 UI**: Bootstrap 5 기반 깔끔한 디자인
- ✨ **실시간 피드백**: AJAX 기반 비동기 처리
- ✨ **세션 관리**: 로그인 상태 유지 및 자동 로그아웃
- ✨ **저장 위치 자동 설정**: 설치 경로의 exports 폴더 절대 경로 자동 표시

---

## 🔧 개선 사항

### 성능
- ⚡ **메모리 최적화**: Custom JRE로 50-70MB 크기
- ⚡ **빠른 시작**: 애플리케이션 시작 시간 단축

### 보안
- 🔒 **localhost 전용**: 127.0.0.1만 접근 가능
- 🔒 **계정 암호화**: 쇼핑몰 계정 정보 암호화 저장
- 🔒 **세션 타임아웃**: 30분 자동 로그아웃

### 안정성
- 🛡️ **오류 처리**: 예외 상황 처리 강화
- 🛡️ **로그 관리**: 상세한 로그 기록
- 🛡️ **자동 재시도**: 수집 실패 시 자동 재시도

---

## 📝 지원 쇼핑몰

| 쇼핑몰 | 상태 | 수집 항목 |
|--------|------|-----------|
| SSG(신세계, 이마트, 트레이더스) | ✅ 지원 | 주문번호, 일시, 매장, 상품 등 |
| 오아시스 | ✅ 지원 | 주문번호, 일시, 매장, 상품 등 |
| 하나로마트 | ✅ 지원 | 주문번호, 일시, 매장, 상품 등 |

---

## 🚀 설치 및 실행

### 시스템 요구사항
- Windows 10/11 (64bit)
- Java 설치 불필요 (번들 포함)
- 디스크 공간: 최소 500MB
- 인터넷 연결 (쇼핑몰 접속용)

### 설치 방법

1. **ZIP 파일 압축 해제**
   ```
   Jangbogo-v0.5.0.zip을 원하는 위치에 압축 해제
   예: C:\Jangbogo
   ```

2. **실행**
   ```
   Jangbogo.bat 더블클릭
   ```

3. **로그인**
   - 브라우저 자동 실행
   - 기본 계정: `admin_main` / `admin1234_main`

### 상세 가이드

배포 패키지 내 문서 참조:
- `README.md` - 빠른 시작
- `사용설명서.txt` - 설치 및 설정
- `설치가이드.txt` - 상세 설치 방법

---

## 📦 배포 파일 구성

```
Jangbogo-distribution.zip (~100 MB)
├─ jangbogo-0.5.0.jar          # Spring Boot 애플리케이션
├─ Jangbogo.bat                # 실행 스크립트
├─ jre/                        # Custom Java 21 런타임
│  ├─ bin/
│  │  ├─ java.exe
│  │  ├─ javaw.exe
│  │  └─ ...
│  ├─ conf/                    # Java 설정 파일
│  ├─ legal/                   # 라이선스 정보
│  └─ lib/                     # Java 라이브러리
├─ service/                    # Windows 서비스
│  ├─ jangbogo-service.exe
│  ├─ jangbogo-service.xml
│  └─ README.md
├─ README.md                   # 빠른 시작 가이드
├─ 사용설명서.txt              # 설치 가이드
└─ 설치가이드.txt              # 상세 설치 방법
```

---

## 🐛 버그 수정

- 🔧 파일 저장 시 데이터 변화 없으면 건너뛰기
- 🔧 자동 수집 중복 실행 방지
- 🔧 데이터베이스 연결 안정성 향상
- 🔧 브라우저 자동 실행 호환성 개선

---

## 📚 문서

### 사용자 문서
- [BUILD_GUIDE.md](BUILD_GUIDE.md) - 빌드 가이드
- [DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md) - 배포 가이드
- [USER_GUIDE.md](USER_GUIDE.md) - 사용자 매뉴얼

### 개발자 문서
- [DISTRIBUTION_IMPLEMENTATION_SUMMARY.md](../developer/DISTRIBUTION_IMPLEMENTATION_SUMMARY.md) - 구현 요약
- [DAO_INTEGRATION_GUIDE.md](../developer/DAO_INTEGRATION_GUIDE.md) - 데이터베이스
- [JBG_CONFIG_GUIDE.md](../developer/JBG_CONFIG_GUIDE.md) - 설정 관리
- [LOGIN_GUIDE.md](../developer/LOGIN_GUIDE.md) - 로그인 시스템
- [SESSION_IMPROVEMENT_GUIDE.md](../developer/SESSION_IMPROVEMENT_GUIDE.md) - 세션 관리

### 릴리스 문서
- [RELEASE_NOTES_v0.5.0.md](RELEASE_NOTES_v0.5.0.md) - v0.5.0 릴리스 노트

모든 문서는 `doc/` 폴더에 정리되어 있습니다.

---

## ⚠️ 알려진 제한사항

1. **쇼핑몰 제공 범위**: 각 쇼핑몰에서 제공하는 기간 내의 구매내역만 수집 가능 (일반적으로 6개월~1년)
2. **Windows 전용**: 현재 버전은 Windows만 지원
3. **브라우저 필요**: Chrome 또는 Edge 브라우저 설치 필요 (Selenium 크롤링용)

---

## 🔜 향후 계획

- [ ] 자동 업데이트 기능
- [ ] 추가 쇼핑몰 지원
- [ ] 데이터 분석 및 통계 기능
- [ ] 모바일 앱 연동

---

## 📮 지원 및 문의

- **GitHub Issues**: 버그 리포트 및 기능 제안
- **로그 파일**: `logs\jangbogo.log` 확인
- **라이선스**: AGPL-3.0-or-later

---

## 🙏 감사의 말

Jangbogo v0.5.0을 사용해주셔서 감사합니다!

**즐거운 쇼핑 관리 되세요!** 🛍️

---

**Copyright © 2025 jiniebox**

