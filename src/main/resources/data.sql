-- data.sql
-- SQLite 환경에 맞게 데이터 삽입


-- jbg_mall 초기 데이터 삽입 (테이블이 비어있을 때만)
-- 주의: DELETE 문을 제거하여 사용자 계정 정보(encrypt_key, encrypt_iv)가 보존되도록 함
INSERT OR IGNORE INTO jbg_mall (seq, id, name, details, encrypt_key, encrypt_iv, account_status, last_signin_time) VALUES
	(1, 'ssg', 'SSG(신세계,이마트,트레이더스)', '이마트,트레이더스,노브랜드,자주,신세계몰,신세계백화점', NULL, NULL, 0, 0),
	(2, 'oasis', '오아시스', NULL, NULL, NULL, 0, 0),
	(3, 'hanaro', '하나로마트', NULL, NULL, NULL, 0, 0);