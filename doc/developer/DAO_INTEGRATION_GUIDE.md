# DAO 클래스 통합 가이드

## 📋 통합 개요

`jbg_access` 테이블과 `jbg_mall` 테이블을 `jbg_mall` 테이블로 통합함에 따라, 
DAO 클래스도 `JbgMallDataAccessObject`로 통합하였습니다.

---

## 🔄 통합 전후 비교

### Before (통합 전)

**테이블 구조:**
- `jbg_mall`: 쇼핑몰 기본 정보
- `jbg_access`: 사용자별 쇼핑몰 접속 정보 (encrypt_key, encrypt_iv, account_status 등)

**DAO 클래스:**
- `JbgMallDataAccessObject`: jbg_mall 테이블 접근
- `JbgAccessDataAccessObject`: jbg_access 테이블 접근

### After (통합 후)

**테이블 구조:**
- `jbg_mall`: 쇼핑몰 정보 + 접속 정보 통합

```sql
CREATE TABLE IF NOT EXISTS jbg_mall (
  seq INTEGER PRIMARY KEY AUTOINCREMENT,
  id TEXT NOT NULL,
  name TEXT NOT NULL DEFAULT '0',
  details TEXT,
  encrypt_key TEXT,           -- 암호화 키
  encrypt_iv TEXT,            -- 암호화 IV
  account_status INTEGER NOT NULL DEFAULT 0,  -- 계정 상태
  last_signin_time INTEGER    -- 마지막 로그인 시간
);
```

**DAO 클래스:**
- `JbgMallDataAccessObject`: 모든 기능 통합

---

## 📁 변경된 파일 목록

### 1. 통합된 DAO 클래스

#### `src/main/java/com/jiniebox/jangbogo/dao/JbgMallDataAccessObject.java`

**통합된 메서드:**

| 메서드 | 원본 클래스 | 설명 |
|--------|------------|------|
| `getMalls()` | JbgMallDataAccessObject | 쇼핑몰 기본 정보 목록 |
| `getName(String seq)` | JbgMallDataAccessObject | 쇼핑몰 이름 조회 |
| `getAccessInfos()` | JbgAccessDataAccessObject | 쇼핑몰 목록 + 접속 상태 |
| `getAccessInfo(String seqMall)` | JbgAccessDataAccessObject | 특정 쇼핑몰 접속 정보 |
| `checkAccountStatus(String seqJbgmall)` | JbgAccessDataAccessObject | 계정 상태 확인 |
| `update(String seqJbgmall, ...)` | JbgAccessDataAccessObject | 계정 정보 업데이트 |
| `updateLastSigninTime(String seqJbgmall)` | JbgAccessDataAccessObject | 마지막 로그인 시간 업데이트 |
| `setAccountStatus(String seqJbgmall, int accountStatus)` | JbgAccessDataAccessObject | 계정 상태 설정 |
| `add(String seqJbgmall, ...)` | JbgAccessDataAccessObject | 계정 정보 등록 (UPDATE로 변경) |

**주요 변경 사항:**

1. **쿼리 변경**: `jbg_access` 테이블 → `jbg_mall` 테이블
2. **WHERE 절 변경**: `seq_jbgmall` → `seq`
3. **JOIN 제거**: LEFT JOIN이 필요 없어짐
4. **add() 메서드**: INSERT → UPDATE로 변경 (테이블이 이미 존재하므로)

### 2. 삭제된 클래스

- **`JbgAccessDataAccessObject.java`** → 백업: `JbgAccessDataAccessObject.java.bak`

### 3. 수정된 클래스 (import 변경)

- `AdminController.java`: `JbgAccessDataAccessObject` → `JbgMallDataAccessObject`
- `JangBoGoManager.java`: `JbgAccessDataAccessObject` → `JbgMallDataAccessObject`
- `MallOrderUpdater.java`: `JbgAccessDataAccessObject` → `JbgMallDataAccessObject`

---

## 🔧 주요 쿼리 변경 사항

### 1. getAccessInfos()

**Before:**
```java
StringBuffer querySb = new StringBuffer("SELECT m.seq seq, m.id id, a.account_status status");
querySb.append(" FROM jbg_mall m LEFT JOIN jbg_access a");
querySb.append(" ON a.seq_jbgmall = m.seq");
querySb.append(" AND a.account_status > -1");
```

**After:**
```java
StringBuffer querySb = new StringBuffer("SELECT seq, id, name, details, ");
querySb.append("account_status status, encrypt_key, encrypt_iv, last_signin_time");
querySb.append(" FROM jbg_mall");
querySb.append(" WHERE account_status > -1");
```

### 2. checkAccountStatus()

**Before:**
```sql
SELECT account_status from jbg_access
WHERE seq_jbgmall = ?
```

**After:**
```sql
SELECT account_status from jbg_mall
WHERE seq = ?
```

### 3. update()

**Before:**
```sql
UPDATE jbg_access SET
  account_status = ?,
  encrypt_key = ?,
  encrypt_iv = ?
WHERE seq_jbgmall = ?
```

**After:**
```sql
UPDATE jbg_mall SET
  account_status = ?,
  encrypt_key = ?,
  encrypt_iv = ?
WHERE seq = ?
```

### 4. add() - 중요 변경!

**Before (INSERT):**
```sql
INSERT INTO jbg_access (seq_jbgmall, account_status, encrypt_key, encrypt_iv, last_signin_time)
VALUES (?, ?, ?, ?, ?)
```

**After (UPDATE):**
```sql
UPDATE jbg_mall SET
  account_status = ?,
  encrypt_key = ?,
  encrypt_iv = ?,
  last_signin_time = ?
WHERE seq = ?
```

**이유**: 테이블 통합으로 인해 `jbg_mall` 레코드는 이미 존재하므로 INSERT가 아닌 UPDATE 수행

---

## ⚠️ 주의사항

### 1. add() 메서드 동작 변경

- **기존**: 새로운 레코드 INSERT
- **통합 후**: 기존 레코드 UPDATE
- **영향**: `MallOrderUpdater.java`에서 호출 시 레코드가 미리 존재해야 함

### 2. 데이터 마이그레이션

테이블을 통합했다면 기존 `jbg_access` 데이터를 `jbg_mall`로 마이그레이션해야 합니다:

```sql
-- jbg_access의 데이터를 jbg_mall로 업데이트
UPDATE jbg_mall
SET 
  encrypt_key = (SELECT encrypt_key FROM jbg_access WHERE seq_jbgmall = jbg_mall.seq),
  encrypt_iv = (SELECT encrypt_iv FROM jbg_access WHERE seq_jbgmall = jbg_mall.seq),
  account_status = (SELECT account_status FROM jbg_access WHERE seq_jbgmall = jbg_mall.seq),
  last_signin_time = (SELECT last_signin_time FROM jbg_access WHERE seq_jbgmall = jbg_mall.seq)
WHERE EXISTS (SELECT 1 FROM jbg_access WHERE seq_jbgmall = jbg_mall.seq);

-- 마이그레이션 후 jbg_access 테이블 삭제
DROP TABLE IF EXISTS jbg_access;
```

### 3. data.sql 수정

테이블 통합에 맞춰 `data.sql`의 INSERT 문도 수정되었습니다:

```sql
INSERT INTO jbg_mall (seq, id, name, details, encrypt_key, encrypt_iv, account_status, last_signin_time)
VALUES (1, 'ssg', '...', '...', 'key', 'iv', 1, 1760748896236);
```

---

## 🧪 테스트 체크리스트

- [ ] 애플리케이션 시작 시 schema.sql 정상 실행
- [ ] data.sql의 INSERT 문 정상 실행
- [ ] `jaDao.getAccessInfos()` 호출 시 정상 동작
- [ ] `jaDao.getAccessInfo(seqMall)` 호출 시 정상 동작
- [ ] `jaDao.add()` 호출 시 UPDATE 정상 수행
- [ ] `jaDao.updateLastSigninTime()` 정상 동작
- [ ] `jaDao.setAccountStatus()` 정상 동작

---

## 📊 통합 효과

### ✅ 장점

1. **테이블 구조 단순화**: 2개 테이블 → 1개 테이블
2. **JOIN 제거**: 성능 향상
3. **코드 중복 제거**: 2개 DAO 클래스 → 1개 DAO 클래스
4. **유지보수 향상**: 하나의 클래스에서 모든 기능 관리

### ⚠️ 주의점

1. **add() 메서드**: INSERT → UPDATE로 변경
2. **레코드 사전 존재**: jbg_mall 레코드가 미리 존재해야 함
3. **마이그레이션 필요**: 기존 jbg_access 데이터 이전

---

## 🚀 마이그레이션 스크립트

기존 DB를 사용 중이라면 다음 스크립트를 실행하세요:

```sql
-- 1. jbg_access 데이터를 jbg_mall로 병합
UPDATE jbg_mall
SET 
  encrypt_key = COALESCE((SELECT encrypt_key FROM jbg_access WHERE seq_jbgmall = jbg_mall.seq), encrypt_key),
  encrypt_iv = COALESCE((SELECT encrypt_iv FROM jbg_access WHERE seq_jbgmall = jbg_mall.seq), encrypt_iv),
  account_status = COALESCE((SELECT account_status FROM jbg_access WHERE seq_jbgmall = jbg_mall.seq), account_status),
  last_signin_time = COALESCE((SELECT last_signin_time FROM jbg_access WHERE seq_jbgmall = jbg_mall.seq), last_signin_time);

-- 2. jbg_access 테이블 삭제
DROP TABLE IF EXISTS jbg_access;

-- 3. 확인
SELECT * FROM jbg_mall;
```

---

**작성일**: 2025-10-26  
**버전**: 1.0.0

