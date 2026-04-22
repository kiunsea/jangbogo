-- schema.sql
-- SQLite 환경에 맞게 테이블 정의

--------------------------------------------------------
-- 테이블 jbg_item 구조
--------------------------------------------------------
CREATE TABLE IF NOT EXISTS jbg_item (
  seq INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL DEFAULT '0',
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
  updated_time INTEGER, -- 마지막 업데이트 시간 (millisecond)
  last_export_time INTEGER -- 마지막 파일 저장 시간 (millisecond)
);

--------------------------------------------------------
-- 테이블 jbg_collect_log 구조 (수집 실행 로그)
--------------------------------------------------------
CREATE TABLE IF NOT EXISTS jbg_collect_log (
  seq INTEGER PRIMARY KEY AUTOINCREMENT,
  seq_mall INTEGER NOT NULL,                    -- 쇼핑몰 seq
  mall_name TEXT,                               -- 쇼핑몰 이름
  status TEXT NOT NULL DEFAULT 'SUCCESS',       -- SUCCESS / FAIL
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