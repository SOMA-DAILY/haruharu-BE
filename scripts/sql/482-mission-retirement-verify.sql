-- #482 배포 전후 현황 확인 전용. 데이터·스키마를 변경하지 않으며 콘텐츠/사용자 ID를 출력하지 않는다.
-- 테이블이 없는 신규 환경에서도 실행 가능. 결과를 배포 기록에 보관한다.
BEGIN TRANSACTION READ ONLY;

SELECT table_name, column_name, data_type, character_maximum_length
FROM information_schema.columns
WHERE table_schema = 'public'
  AND ((table_name IN ('video_posts', 'video_upload_sessions') AND column_name = 'caption')
       OR (table_name = 'chat_messages' AND column_name IN
           ('operator_mission_id', 'mission_room_id', 'mission_template_id', 'mission_title', 'mission_image_id')))
ORDER BY table_name, column_name;

DO $$
DECLARE
    relation_name TEXT;
    row_count BIGINT;
    summary RECORD;
BEGIN
    FOREACH relation_name IN ARRAY ARRAY[
        'missions', 'mission_revisions', 'mission_revision_eligibilities',
        'room_mission_settings', 'room_mission_slots', 'room_missions',
        'mission_assignment_incidents', 'mission_member_states', 'mission_outcomes',
        'operator_missions', 'operator_mission_images'
    ] LOOP
        IF to_regclass(format('public.%I', relation_name)) IS NULL THEN
            RAISE NOTICE '%: absent', relation_name;
        ELSE
            EXECUTE format('SELECT count(*) FROM public.%I', relation_name) INTO row_count;
            RAISE NOTICE '%: rows=%', relation_name, row_count;
        END IF;
    END LOOP;

    IF to_regclass('public.operator_missions') IS NOT NULL THEN
        FOR summary IN EXECUTE $query$
            SELECT coalesce(to_jsonb(m)->>'status', 'UNKNOWN') AS status,
                   count(*) AS total,
                   count(*) FILTER (WHERE to_jsonb(m)->>'delete_requested_at' IS NOT NULL
                                      AND to_jsonb(m)->>'delete_processed_at' IS NULL) AS pending_delete
            FROM public.operator_missions m
            GROUP BY 1 ORDER BY 1
        $query$ LOOP
            RAISE NOTICE 'operator_missions status=% rows=% pending_delete=%',
                summary.status, summary.total, summary.pending_delete;
        END LOOP;
    END IF;

    FOREACH relation_name IN ARRAY ARRAY['video_posts', 'video_upload_sessions'] LOOP
        IF EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = 'public' AND table_name = relation_name AND column_name = 'caption') THEN
            EXECUTE format('SELECT count(*) FROM public.%I WHERE length(caption) > 20', relation_name)
                INTO row_count;
            RAISE NOTICE '%: captions_over_20=%', relation_name, row_count;
        END IF;
    END LOOP;
    IF to_regclass('public.video_upload_sessions') IS NOT NULL THEN
        EXECUTE $query$
            SELECT count(*) FROM public.video_upload_sessions s
            WHERE length(to_jsonb(s)->>'caption') > 20
              AND to_jsonb(s)->>'post_id' IS NULL
              AND coalesce(to_jsonb(s)->>'cancelled', 'false') = 'false'
        $query$ INTO row_count;
        RAISE NOTICE 'video_upload_sessions: unfinished_long_caption_sessions=%', row_count;
    END IF;
END $$;

COMMIT;
