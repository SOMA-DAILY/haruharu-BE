-- #558 확인: ai_reply 표 27개와 캡션 수정 이력 표가 있는지, 유효 요청 부분 유니크 인덱스가 있는지 본다.
SELECT
    (SELECT count(*) FROM information_schema.tables WHERE table_schema = 'ai_reply')                AS ai_reply_tables,
    (SELECT count(*) FROM information_schema.tables
      WHERE table_schema = current_schema() AND table_name = 'video_caption_revisions')              AS caption_revision_table,
    (SELECT count(*) FROM pg_indexes WHERE schemaname = 'ai_reply'
      AND indexname IN ('uq_analysis_request_active', 'uq_recommendation_request_active'))           AS partial_unique_indexes;
