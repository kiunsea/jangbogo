-- schema.sql
-- SQLite 환경에 맞게 테이블 정의

--------------------------------------------------------
-- 테이블 jbg_item 구조
--------------------------------------------------------
CREATE TABLE IF NOT EXISTS jbg_item (
  seq INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL DEFAULT '0',
  qty TEXT DEFAULT '', -- 수량 (문자열로 저장 — 몰마다 표기가 다르다)
  seq_order INTEGER,
  insert_time INTEGER -- 등록시간(millisecond)
);

--------------------------------------------------------
-- 테이블 jbg_mall 구조
--------------------------------------------------------
CREATE TABLE IF NOT EXISTS jbg_mall (
  seq INTEGER PRIMARY KEY AUTOINCREMENT,
  id TEXT NOT NULL,
  name TEXT NOT NULL DEFAULT '0',
  details TEXT,
  encrypt_key TEXT, -- Encrypt SecretKey
  encrypt_iv TEXT, -- Encrypt IvParameterSpec
  account_status INTEGER NOT NULL DEFAULT 0, -- 서비스 이용 가능 여부(0:이용 불가, 1:이용 가능)
  last_signin_time INTEGER, -- 마지막 접속 시간 (millisecond)
  auto_collect INTEGER DEFAULT 0, -- 자동수집 사용 여부 (0:안 함, 1:함)
  collect_interval_minutes INTEGER DEFAULT 0 -- 자동수집 주기 (분 단위, 0이면 주기적 실행 안 함)
);

--------------------------------------------------------
-- 테이블 jbg_order 구조
--------------------------------------------------------
CREATE TABLE IF NOT EXISTS jbg_order (
  seq INTEGER PRIMARY KEY AUTOINCREMENT,
  serial_num TEXT NOT NULL DEFAULT '0', -- 시리얼 번호 (영수증 바코드 또는 주문번호)
  date_time INTEGER NOT NULL DEFAULT 0, -- 구매일자(YYYYMMDD)
  mall_name TEXT, -- 매장명
  seq_mall INTEGER NOT NULL,
  insert_time INTEGER -- 등록시간(millisecond)
);

--------------------------------------------------------
-- 테이블 jbg_export_config 구조
--------------------------------------------------------
CREATE TABLE IF NOT EXISTS jbg_export_config (
  id INTEGER PRIMARY KEY DEFAULT 1, -- 단일 설정 레코드 (항상 1)
  save_path TEXT NOT NULL DEFAULT '', -- 저장 경로
  save_format TEXT NOT NULL DEFAULT 'json', -- 저장 포맷 (json, yaml, csv)
  auto_save_enabled INTEGER NOT NULL DEFAULT 0, -- 자동수집시 함께 저장 (0:비활성, 1:활성)
  save_to_jiniebox INTEGER NOT NULL DEFAULT 0, -- jiniebox 로 FTP 전송 (0:비활성, 1:활성)
  ftp_address TEXT NOT NULL DEFAULT '', -- FTP 서버 주소
  ftp_id TEXT NOT NULL DEFAULT '', -- FTP 계정
  ftp_pass TEXT NOT NULL DEFAULT '', -- FTP 비밀번호 (암호화 저장)
  public_key TEXT NOT NULL DEFAULT '', -- 페이로드 암호화용 공개키
  ftp_encrypt_enabled INTEGER NOT NULL DEFAULT 1, -- 전송 페이로드 암호화 (0:비활성, 1:활성)
  updated_time INTEGER, -- 마지막 업데이트 시간 (millisecond)
  last_export_time INTEGER -- 마지막 파일 저장 시간 (millisecond)
);

--------------------------------------------------------
-- 테이블 jbg_collect_breaker 구조 (수집기 서킷 브레이커 상태)
--
-- 차단 단위가 수집기인 이유: seq=1 은 수집기가 둘(SSG·Emart)이라 몰 단위로 세면
-- 한쪽이 계속 죽어도 다른 쪽이 성공하는 한 몰 결과는 SUCCESS 로 남는다.
--
-- 상태를 테이블에 두는 이유: 트립은 재시작을 넘어 살아남아야 한다. 메모리에만 두면
-- 재기동이 차단된 수집기를 되살려 다시 두드린다.
--------------------------------------------------------
CREATE TABLE IF NOT EXISTS jbg_collect_breaker (
  seq_mall INTEGER NOT NULL, -- 쇼핑몰 seq
  collector TEXT NOT NULL, -- 수집기 이름 (SSG / Emart / Oasis / Hanaro)
  consecutive_failures INTEGER NOT NULL DEFAULT 0, -- 연속 실패 횟수 (성공 시 0)
  streak_started_time INTEGER DEFAULT 0, -- 현재 연속 실패가 시작된 시각 (millisecond)
  last_failure_time INTEGER DEFAULT 0, -- 마지막 실패 시각 (millisecond)
  last_success_time INTEGER DEFAULT 0, -- 마지막 성공 시각 (millisecond)
  tripped_time INTEGER DEFAULT 0, -- 브레이커가 열린 시각 (0이면 닫힘)
  last_reason TEXT, -- 마지막 판정 사유 (사람이 읽는 용도)
  PRIMARY KEY (seq_mall, collector)
);

--------------------------------------------------------
-- 테이블 jbg_collect_log 구조 (수집 실행 로그)
--------------------------------------------------------
CREATE TABLE IF NOT EXISTS jbg_collect_log (
  seq INTEGER PRIMARY KEY AUTOINCREMENT,
  seq_mall INTEGER NOT NULL,                    -- 쇼핑몰 seq
  mall_name TEXT,                               -- 쇼핑몰 이름
  collector TEXT,                               -- 수집기 이름 (SSG/Emart/Oasis/Hanaro). NULL 이면 실행 단위 집계 행
  status TEXT NOT NULL DEFAULT 'SUCCESS',       -- SUCCESS / FAIL / SKIPPED
  order_count INTEGER DEFAULT 0,                -- 수집된 주문 수
  item_count INTEGER DEFAULT 0,                 -- 수집된 아이템 수
  error_message TEXT,                           -- 오류 메시지 (실패 시)
  error_detail TEXT,                            -- 상세 오류 (스택트레이스)
  step_name TEXT,                               -- 실패한 단계명 (예: signin, navigatePurchased)
  current_url TEXT,                             -- 실패 시점 WebDriver의 현재 URL
  page_title TEXT,                              -- 실패 시점 페이지 타이틀
  target_selector TEXT,                         -- 실패한 타겟 셀렉터 (있으면)
  screenshot_path TEXT,                         -- 실패 시점 스크린샷 파일 경로
  started_at INTEGER,                           -- 실행 시작 시간 (millisecond)
  finished_at INTEGER,                          -- 실행 종료 시간 (millisecond)
  insert_time INTEGER                           -- 등록 시간 (millisecond)
);