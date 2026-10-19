-- #531 적용 확인. 모든 줄이 기대값과 같아야 한다.
SELECT 'question_day column' AS check_name,
       (SELECT is_nullable || ':' || data_type || ':' || column_default
          FROM information_schema.columns
         WHERE table_name = 'anonymous_questions' AND column_name = 'question_day') AS actual,
       'NO:integer:2' AS expected
UNION ALL
SELECT 'grant question_day column',
       (SELECT is_nullable || ':' || data_type || ':' || column_default
          FROM information_schema.columns
         WHERE table_name = 'question_grants' AND column_name = 'question_day'),
       'NO:integer:2'
UNION ALL
SELECT 'question unique per day',
       (SELECT count(*)::text FROM pg_constraint WHERE conname = 'uq_question_sender_target_day'),
       '1'
UNION ALL
SELECT 'old question unique removed',
       (SELECT count(*)::text FROM pg_indexes
         WHERE tablename = 'anonymous_questions' AND indexname = 'uq_question_sender_target'),
       '0'
UNION ALL
SELECT 'grant unique per day',
       (SELECT count(*)::text FROM pg_constraint WHERE conname = 'uq_question_grant_day'),
       '1'
UNION ALL
SELECT 'old grant unique removed',
       (SELECT count(*)::text FROM pg_indexes
         WHERE tablename = 'question_grants' AND indexname = 'uq_question_grant'),
       '0'
UNION ALL
SELECT 'questions outside 2..3',
       (SELECT count(*)::text FROM anonymous_questions WHERE question_day NOT IN (2, 3)),
       '0';
