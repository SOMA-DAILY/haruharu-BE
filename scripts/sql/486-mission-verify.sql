-- Run immediately after up while writers remain stopped. Raises on changed/lost historic data.
DO $$
BEGIN
    IF EXISTS ((SELECT * FROM room_schedule_items EXCEPT SELECT * FROM room_schedule_mission_backup_486)
        UNION ALL (SELECT * FROM room_schedule_mission_backup_486 EXCEPT SELECT * FROM room_schedule_items)) THEN
        RAISE EXCEPTION 'Legacy schedules changed';
    END IF;
    IF (SELECT count(*) FROM room_mission_instances) <> (SELECT count(*) FROM room_schedule_items)
        OR (SELECT count(*) FROM mission_announcements) <> (SELECT count(*) FROM room_schedule_items) THEN
        RAISE EXCEPTION 'Mission mapping count mismatch';
    END IF;
    IF EXISTS (SELECT 1 FROM room_schedule_items s LEFT JOIN room_mission_instances m ON m.id=s.id
        LEFT JOIN mission_announcements a ON a.legacy_schedule_item_id=s.id
        WHERE m.id IS NULL OR a.id IS NULL OR a.room_mission_id<>m.id OR a.room_id<>s.room_id
            OR m.opens_at<>s.run_at OR m.closes_at IS NOT NULL
            OR m.canceled_at IS DISTINCT FROM CASE WHEN s.status='CANCELED' THEN s.updated_at END
            OR m.type <> CASE WHEN s.reveal_fields IS NOT NULL THEN 'PROFILE_REVEAL' ELSE 'ANNOUNCEMENT' END
            OR (s.reveal_fields IS NULL AND m.details IS DISTINCT FROM jsonb_build_object('content',s.message_content))
            OR (s.reveal_fields IS NOT NULL AND (SELECT string_agg(f.value,',' ORDER BY f.ordinality)
                FROM jsonb_array_elements_text(m.details->'reveal_fields') WITH ORDINALITY f) IS DISTINCT FROM s.reveal_fields)
            OR a.status<>s.status
            OR a.sent_message_id IS DISTINCT FROM s.sent_message_id
            OR a.content IS DISTINCT FROM s.message_content OR m.deleted_at IS DISTINCT FROM s.deleted_at
            OR m.created_at IS DISTINCT FROM s.created_at OR m.updated_at IS DISTINCT FROM s.updated_at
            OR a.created_at IS DISTINCT FROM s.created_at OR a.updated_at IS DISTINCT FROM s.updated_at
            OR a.deleted_at IS DISTINCT FROM s.deleted_at) THEN
        RAISE EXCEPTION 'Mission mapping values mismatch';
    END IF;
    IF (SELECT count(*) FROM chat_messages) <> (SELECT count(*) FROM chat_message_mission_backup_486)
        OR EXISTS (SELECT 1 FROM chat_message_mission_backup_486 b LEFT JOIN chat_messages c ON c.id=b.id
            WHERE c.id IS NULL OR (to_jsonb(c)-ARRAY['message_type','room_mission_id','mission_announcement_id'])
                IS DISTINCT FROM (to_jsonb(b)-'message_type')
            OR c.message_type <> CASE WHEN b.schedule_item_id IS NULL THEN b.message_type ELSE 'MISSION' END
            OR c.room_mission_id IS DISTINCT FROM b.schedule_item_id
            OR c.mission_announcement_id IS DISTINCT FROM b.schedule_item_id) THEN
        RAISE EXCEPTION 'Historic message identity/content/audit/reply/deletion changed';
    END IF;
END $$;
