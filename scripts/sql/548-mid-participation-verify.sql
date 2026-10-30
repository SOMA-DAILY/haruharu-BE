-- 읽기 전용 배포/검증 확인. 각 행은 0이어야 한다.
SELECT 'campaign_count' AS invariant, count(*) AS violations
 FROM mid_participation_campaigns WHERE seats<1 OR accepted_count<0 OR accepted_count>seats
UNION ALL
SELECT 'invitation_versions',count(*) FROM mid_participation_invitations
 WHERE delivery_version<1 OR seen_version<0 OR seen_version>delivery_version OR notified_version>delivery_version
UNION ALL
SELECT 'accepted_and_declined',count(*) FROM mid_participation_invitations
 WHERE accepted_at IS NOT NULL AND declined_at IS NOT NULL
UNION ALL
SELECT 'removed_member_hidden',count(*) FROM room_member_removals x
 JOIN room_members m ON m.id=x.member_id WHERE m.member_removed_at IS NULL
UNION ALL
SELECT 'removed_messages_hidden',count(*) FROM room_member_removals x
 JOIN chat_rooms c ON c.room_id=x.room_id AND c.type='GROUP'
 JOIN chat_messages m ON m.chat_room_id=c.id AND m.sender_id=x.user_id
 WHERE m.member_removed_at IS NULL;
