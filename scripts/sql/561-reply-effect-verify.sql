-- #560(이슈) 확인: 효과 라벨 칸 5개, 그룹 칸 1개, 배정 표 1개, 배정 표 인덱스 1개가 있는지 본다(없으면 0).
SELECT
    (SELECT count(*) FROM information_schema.columns
      WHERE table_schema = 'ai_reply' AND table_name = 'reply_depth_observation'
        AND column_name IN ('owner_responded', 'owner_response_minutes', 'response_window_minutes',
                            'bystander_members', 'bystanders_responded'))                          AS response_columns,
    (SELECT count(*) FROM information_schema.columns
      WHERE table_schema = 'ai_reply' AND table_name = 'reply_link' AND column_name = 'experiment_arm') AS arm_column,
    (SELECT count(*) FROM information_schema.tables
      WHERE table_schema = 'ai_reply' AND table_name = 'holdout_assignment')                       AS holdout_table,
    (SELECT count(*) FROM pg_indexes
      WHERE schemaname = 'ai_reply' AND indexname = 'idx_holdout_assignment_room')                  AS holdout_index;
