# 아이콘 파일 준비 가이드

Jangbogo 애플리케이션을 빌드하려면 다음 아이콘 파일들이 필요합니다.

## 필요한 아이콘 파일

### 1. Windows 설치 파일 아이콘
**파일명:** `jangbogo.ico`  
**위치:** `packaging/windows/jangbogo.ico`  
**형식:** ICO (Windows Icon)  
**권장 크기:** 256x256, 128x128, 64x64, 48x48, 32x32, 16x16 (다중 해상도 포함)

### 2. 트레이 아이콘
**파일명:** `tray-icon.png`  
**위치:** `src/main/resources/icons/tray-icon.png`  
**형식:** PNG  
**권장 크기:** 32x32 또는 16x16

## 아이콘 생성 방법

### 옵션 1: 온라인 도구 사용
1. 로고 이미지를 준비 (PNG, 정사각형, 투명 배경 권장)
2. https://favicon.io/ 또는 https://www.icoconverter.com/ 접속
3. 이미지를 업로드하여 ICO 파일 생성
4. 생성된 파일을 해당 위치에 저장

### 옵션 2: GIMP 사용 (무료)
1. GIMP 설치 (https://www.gimp.org/)
2. 로고 이미지 열기
3. 이미지 → 크기 조정 (256x256 권장)
4. 파일 → Export As → .ico 선택
5. 생성된 파일을 해당 위치에 저장

### 옵션 3: ImageMagick 사용 (명령줄)
```bash
# PNG를 ICO로 변환
magick convert logo.png -define icon:auto-resize=256,128,64,48,32,16 jangbogo.ico

# 트레이 아이콘 생성 (32x32)
magick convert logo.png -resize 32x32 tray-icon.png
```

## 임시 아이콘 사용

아이콘 파일이 없어도 빌드는 가능합니다:
- Windows 아이콘이 없으면 기본 Java 아이콘이 사용됩니다.
- 트레이 아이콘이 없으면 코드에서 생성한 기본 아이콘이 사용됩니다.

## 디자인 가이드라인

- **색상:** 파란색 계열 (브랜드 색상)
- **모티브:** 쇼핑백, 쇼핑카트, 구매 내역 등을 상징
- **스타일:** 심플하고 모던한 디자인
- **배경:** 투명 배경 권장
- **가독성:** 16x16 크기에서도 식별 가능해야 함

## 참고 자료

- Windows 아이콘 가이드: https://docs.microsoft.com/en-us/windows/win32/uxguide/vis-icons
- 아이콘 디자인 베스트 프랙티스: https://material.io/design/iconography/product-icons.html

