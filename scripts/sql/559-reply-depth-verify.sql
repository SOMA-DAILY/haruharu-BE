-- #559 확인: Depth 라벨 표·영상 인덱스·상태 제약이 있는지(각 1), 보조 지표 칸 2개·기수 칸 1개가 있는지 본다(없으면 0).
SELECT
    (SELECT count(*) FROM information_schema.tables
      WHERE table_schema = 'ai_reply' AND table_name = 'reply_depth_observation')                 AS depth_table,
    (SELECT count(*) FROM pg_indexes
      WHERE schemaname = 'ai_reply' AND indexname = 'idx_reply_depth_observation_video')            AS depth_video_index,
    (SELECT count(*) FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
      WHERE n.nspname = 'ai_reply' AND c.conname = 'ck_reply_depth_status')                         AS depth_status_check,
    (SELECT count(*) FROM information_schema.columns
      WHERE table_schema = 'ai_reply' AND table_name = 'reply_depth_observation'
        AND column_name IN ('owner_replied', 'followup_message_count'))                             AS auxiliary_columns,
    (SELECT count(*) FROM information_schema.columns
      WHERE table_schema = 'ai_reply' AND table_name = 'reply_depth_observation'
        AND column_name = 'cohort_id')                                                              AS cohort_column;
