-- #560 확인: 칼럼이 BOOLEAN NOT NULL DEFAULT false 로 있는지 본다(있으면 1, 없으면 0).
SELECT count(*) AS reply_recommendation_column
  FROM information_schema.columns
 WHERE table_schema = current_schema() AND table_name = 'rooms'
   AND column_name = 'reply_recommendation_enabled'
   AND data_type = 'boolean' AND is_nullable = 'NO' AND column_default = 'false';
