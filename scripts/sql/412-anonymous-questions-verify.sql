-- Read-only verification. Each violation query must return zero rows.
SELECT room_id, sender_user_id, count(*) FROM anonymous_questions
GROUP BY room_id, sender_user_id HAVING count(*) > 2;
SELECT room_id, sender_user_id, recipient_user_id, count(*) FROM anonymous_questions
GROUP BY room_id, sender_user_id, recipient_user_id HAVING count(*) > 1;
SELECT id FROM anonymous_questions
WHERE char_length(content) NOT BETWEEN 1 AND 50
   OR (answer IS NOT NULL AND char_length(answer) NOT BETWEEN 1 AND 100)
   OR sender_user_id = recipient_user_id;
SELECT room_id, user_id, count(*) FROM question_grants GROUP BY room_id, user_id HAVING count(*) > 1;
SELECT id FROM reports WHERE report_scope IS DISTINCT FROM
    CASE WHEN question_id IS NULL THEN 'USER' ELSE 'QUESTION:' || question_id END;
SELECT conname, pg_get_constraintdef(oid) FROM pg_constraint
WHERE conrelid IN ('anonymous_questions'::regclass, 'question_grants'::regclass, 'reports'::regclass, 'notification_history'::regclass)
ORDER BY conrelid, conname;
