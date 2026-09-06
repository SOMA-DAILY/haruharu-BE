-- 컬럼 확인 (VARCHAR(30), nullable)
SELECT column_name, data_type, character_maximum_length, is_nullable
FROM information_schema.columns
WHERE table_schema = current_schema()
  AND table_name = 'room_members' AND column_name = 'pseudonym';

-- 성별 있는 멤버 중 가명 미배정 건수(신규 입장분은 0에 가까워야 함 — 마이그레이션 이전 기존 멤버는 null 잔존 가능)
SELECT count(*) AS gendered_without_pseudonym
FROM room_members
WHERE gender IN ('MALE', 'FEMALE') AND pseudonym IS NULL;
