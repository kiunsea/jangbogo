-- jbg_order와 jbg_item 테이블 데이터 삭제
DELETE FROM jbg_item;
DELETE FROM jbg_order;

-- AUTO_INCREMENT 시퀀스 초기화
DELETE FROM sqlite_sequence WHERE name='jbg_item';
DELETE FROM sqlite_sequence WHERE name='jbg_order';

-- 결과 확인
SELECT '초기화 완료: jbg_order' as message;
SELECT COUNT(*) as order_count FROM jbg_order;
SELECT COUNT(*) as item_count FROM jbg_item;

