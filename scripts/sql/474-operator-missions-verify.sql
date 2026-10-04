SELECT status, count(*) FROM operator_missions GROUP BY status;
SELECT operator_mission_id, count(*) FROM chat_messages WHERE operator_mission_id IS NOT NULL
GROUP BY operator_mission_id HAVING count(*) > 1;
SELECT id FROM operator_missions WHERE status = 'SENT' AND message_id IS NULL;

SELECT count(*) AS mission_image_count FROM operator_mission_images;
