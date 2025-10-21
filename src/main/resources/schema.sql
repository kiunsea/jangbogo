-- schema.sql
-- SQLite 환경에 맞게 테이블 정의

--------------------------------------------------------
-- 테이블 jbg_mall 구조
--------------------------------------------------------
CREATE TABLE IF NOT EXISTS jbg_mall (
  seq INTEGER PRIMARY KEY AUTOINCREMENT,
  id TEXT NOT NULL,
  name TEXT NOT NULL DEFAULT '0',
  details TEXT
);

--------------------------------------------------------
-- 테이블 jbg_access 구조
--------------------------------------------------------
CREATE TABLE IF NOT EXISTS jbg_access (
  seq_jbgmall INTEGER NOT NULL,
  account_status INTEGER NOT NULL DEFAULT 0, -- 서비스 이용 가능 여부(0:이용 불가, 1:이용 가능)
  encrypt_key TEXT, -- Encrypt SecretKey
  encrypt_iv TEXT, -- Encrypt IvParameterSpec
  last_signin_time INTEGER, -- 마지막 접속 시간 (millisecond)
  UNIQUE (seq_jbgmall, seq_user)
);

--------------------------------------------------------
-- 테이블 jbg_order 구조
--------------------------------------------------------
CREATE TABLE IF NOT EXISTS jbg_order (
  seq INTEGER PRIMARY KEY AUTOINCREMENT,
  serial_num TEXT NOT NULL DEFAULT '0', -- 시리얼 번호 (영수증 바코드 또는 주문번호)
  date_time INTEGER NOT NULL DEFAULT 0, -- 구매일자(YYYYMMDD)
  mall_name TEXT, -- 매장명
  seq_jbgmall INTEGER NOT NULL
);