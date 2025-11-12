-- jangbogo 테스트 데이터 생성 스크립트
-- 
-- 사용법:
-- sqlite3 localdb/jangbogo.db < test_data.sql

-- 1. 기존 테스트 데이터 삭제 (선택)
DELETE FROM jbg_item WHERE seq_order IN (SELECT seq FROM jbg_order WHERE serial_num LIKE 'TEST-%');
DELETE FROM jbg_order WHERE serial_num LIKE 'TEST-%';

-- 2. 테스트 주문 데이터 삽입

-- 주문 1: 이마트 (seq_mall=1)
INSERT INTO jbg_order (serial_num, date_time, mall_name, seq_mall)
VALUES ('TEST-20241109-001', '20241109', '이마트몰', 1);

-- 주문 2: SSG (seq_mall=1)
INSERT INTO jbg_order (serial_num, date_time, mall_name, seq_mall)
VALUES ('TEST-20241109-002', '20241109', 'SSG', 1);

-- 주문 3: 오아시스 (seq_mall=2)
INSERT INTO jbg_order (serial_num, date_time, mall_name, seq_mall)
VALUES ('TEST-20241108-001', '20241108', '오아시스', 2);

-- 주문 4: 이마트 (seq_mall=1) - 어제
INSERT INTO jbg_order (serial_num, date_time, mall_name, seq_mall)
VALUES ('TEST-20241108-002', '20241108', '이마트몰', 1);

-- 3. 테스트 상품 데이터 삽입

-- 주문 1의 상품들
INSERT INTO jbg_item (name, qty, seq_order, insert_time)
VALUES ('바나나', '5', (SELECT seq FROM jbg_order WHERE serial_num = 'TEST-20241109-001'), 1699523425000);

INSERT INTO jbg_item (name, qty, seq_order, insert_time)
VALUES ('두유 900ml', '3', (SELECT seq FROM jbg_order WHERE serial_num = 'TEST-20241109-001'), 1699523425000);

INSERT INTO jbg_item (name, qty, seq_order, insert_time)
VALUES ('식빵', '2', (SELECT seq FROM jbg_order WHERE serial_num = 'TEST-20241109-001'), 1699523425000);

-- 주문 2의 상품들
INSERT INTO jbg_item (name, qty, seq_order, insert_time)
VALUES ('닭가슴살 1kg', '1', (SELECT seq FROM jbg_order WHERE serial_num = 'TEST-20241109-002'), 1699523425000);

INSERT INTO jbg_item (name, qty, seq_order, insert_time)
VALUES ('계란 30구', '2', (SELECT seq FROM jbg_order WHERE serial_num = 'TEST-20241109-002'), 1699523425000);

-- 주문 3의 상품들
INSERT INTO jbg_item (name, qty, seq_order, insert_time)
VALUES ('생수 2L', '6', (SELECT seq FROM jbg_order WHERE serial_num = 'TEST-20241108-001'), 1699523425000);

INSERT INTO jbg_item (name, qty, seq_order, insert_time)
VALUES ('휴지 30롤', '1', (SELECT seq FROM jbg_order WHERE serial_num = 'TEST-20241108-001'), 1699523425000);

-- 주문 4의 상품들
INSERT INTO jbg_item (name, qty, seq_order, insert_time)
VALUES ('사과', '4', (SELECT seq FROM jbg_order WHERE serial_num = 'TEST-20241108-002'), 1699523425000);

INSERT INTO jbg_item (name, qty, seq_order, insert_time)
VALUES ('우유 1L', '2', (SELECT seq FROM jbg_order WHERE serial_num = 'TEST-20241108-002'), 1699523425000);

INSERT INTO jbg_item (name, qty, seq_order, insert_time)
VALUES ('요거트', '5', (SELECT seq FROM jbg_order WHERE serial_num = 'TEST-20241108-002'), 1699523425000);

-- 4. 데이터 확인
SELECT '=== 주문 데이터 ===';
SELECT * FROM jbg_order WHERE serial_num LIKE 'TEST-%' ORDER BY date_time DESC, seq DESC;

SELECT '=== 상품 데이터 ===';
SELECT i.seq, i.name, i.qty, o.serial_num, o.date_time, o.mall_name
FROM jbg_item i
INNER JOIN jbg_order o ON i.seq_order = o.seq
WHERE o.serial_num LIKE 'TEST-%'
ORDER BY o.date_time DESC, i.seq DESC;

-- 완료 메시지
SELECT '테스트 데이터 생성 완료!';
SELECT 'jangbogo 관리 화면에서 "파일 저장 및 FTP 업로드" 버튼을 클릭하여 테스트하세요.';

