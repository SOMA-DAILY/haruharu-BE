package org.son.haruharu.domain.room.storage;

import java.util.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.son.haruharu.domain.room.implement.*;

/** 원본 필드와 일반 삭제 표시는 보존하고 방 범위의 비노출 표시만 추가한다. */
@Repository @RequiredArgsConstructor
public class RoomParticipationRepositoryImpl implements RoomParticipationRepository {
    private final MemberRemovalJpaRepository removals;
    private final EntityManager em;
    public Optional<MemberRemoval> removal(Long memberId) { return removals.findById(memberId).map(MemberRemovalEntity::toDomain); }
    public boolean removed(Long roomId,Long userId) { return removals.existsByRoomIdAndUserId(roomId,userId); }
    public Set<String> historicalPseudonyms(Long roomId) {
        var result=new HashSet<String>();
        for(Object name:em.createNativeQuery("select pseudonym from room_members where room_id=:roomId and pseudonym is not null")
                .setParameter("roomId",roomId).getResultList()) result.add((String)name);
        return result;
    }

    private static final String GROUPS="select id from chat_rooms where room_id=:roomId and type='GROUP'";
    private static final String VIDEOS="select id from video_posts where room_member_id=:memberId";
    private static final String MESSAGES="select id from chat_messages where sender_id=:userId and chat_room_id in ("+GROUPS+")";
    private static final String QUESTIONS="select id from anonymous_questions where room_id=:roomId and (sender_user_id=:userId or recipient_user_id=:userId)";

    public void remove(MemberRemoval r) {
        em.flush();
        @SuppressWarnings("unchecked")
        List<Number> privateRows=parameterize("""
            select distinct c.id from chat_rooms c
            where c.deleted_at is null and c.member_removed_at is null and (
                (c.type='PAIR' and c.room_id=:roomId and exists (
                    select 1 from chat_room_members m where m.chat_room_id=c.id and m.user_id=:userId))
                or (c.type='DIRECT' and c.match_result_id in (
                    select id from match_results where member_a_id=:memberId or member_b_id=:memberId)))
            """,r).getResultList();
        List<Long> privateIds=privateRows.stream().map(Number::longValue).sorted().toList();
        var lockIds=new java.util.TreeSet<Long>(privateIds);
        for(Object id:parameterize(GROUPS,r).getResultList()) lockIds.add(((Number)id).longValue());
        // 전송·사진·반응·읽음도 같은 chat_room 행을 잠근다. 반드시 정리보다 먼저 id 오름차순으로 획득한다.
        if(!lockIds.isEmpty()) em.createNativeQuery("select id from chat_rooms where id in (:ids) order by id for update")
                .setParameter("ids",lockIds).getResultList();
        // 인용한 사람의 본문과 원본 스냅샷은 보관하고 인용 표시만 응답에서 숨긴다.
        update("update chat_messages set reference_suppressed=true where chat_room_id in ("+GROUPS+
                ") and (reply_to_message_id in ("+MESSAGES+") or video_post_id in ("+VIDEOS+"))",r);
        update("update video_reactions set member_removed_at=:now where video_post_id in ("+VIDEOS+
                ") or (user_id=:userId and video_post_id in (select v.id from video_posts v join room_members m on m.id=v.room_member_id where m.room_id=:roomId))",r);
        update("update chat_message_reactions set member_removed_at=:now where message_id in ("+MESSAGES+
                ") or (user_id=:userId and message_id in (select id from chat_messages where chat_room_id in ("+GROUPS+")))",r);
        update("update anonymous_questions set member_removed_at=:now where room_id=:roomId and (sender_user_id=:userId or recipient_user_id=:userId)",r);
        update("update video_posts set member_removed_at=:now where room_member_id=:memberId",r);
        update("update video_upload_sessions set member_removed_at=:now where member_id=:memberId",r);
        update("update chat_messages set member_removed_at=:now where sender_id=:userId and chat_room_id in ("+GROUPS+")",r);
        update("update chat_room_members set member_removed_at=:now where user_id=:userId and chat_room_id in ("+GROUPS+")",r);
        update("update final_selections set member_removed_at=:now where room_member_id=:memberId",r);
        update("update match_results set member_removed_at=:now where member_a_id=:memberId or member_b_id=:memberId",r);
        if(!privateIds.isEmpty()) {
            for(String table:List.of("chat_room_members","chat_messages")) {
                em.createNativeQuery("update "+table+" set member_removed_at=:now where chat_room_id in (:ids)")
                        .setParameter("now",r.removedAt()).setParameter("ids",privateIds).executeUpdate();
            }
            em.createNativeQuery("update chat_message_reactions set member_removed_at=:now where message_id in (select id from chat_messages where chat_room_id in (:ids))")
                    .setParameter("now",r.removedAt()).setParameter("ids",privateIds).executeUpdate();
            em.createNativeQuery("update chat_rooms set member_removed_at=:now where id in (:ids)")
                    .setParameter("now",r.removedAt()).setParameter("ids",privateIds).executeUpdate();
        }
        // 기존 알림에도 가명·참조가 남지 않게 연결된 기록만 숨긴다. 다른 방 알림은 유지한다.
        // 방이 생기기 전 알림은 이 방과 무관하다. 전체 이력을 파싱하지 않도록 먼저 좁힌다.
        String afterRoomCreated=" and created_at>=(select created_at from rooms where id=:roomId)";
        String json="cast(coalesce(payload_json,'{}') as jsonb)";
        String sourceRoom="coalesce("+json+"->>'room_id',"+json+"->>'roomId')";
        String sourceVideo="coalesce("+json+"->>'video_id',"+json+"->>'videoId',"+json+"->>'video_post_id',"+json+"->>'videoPostId')";
        String sourceMessage="coalesce("+json+"->>'message_id',"+json+"->>'messageId')";
        String sourceQuestion="coalesce("+json+"->>'question_id',"+json+"->>'questionId')";
        update("update notification_history set member_removed_at=:now where (("+sourceRoom+"=cast(:roomId as text) and user_id=:userId)"
                +" or "+sourceVideo+" in (select cast(id as text) from video_posts where room_member_id=:memberId)"
                +" or "+sourceMessage+" in (select cast(id as text) from chat_messages where sender_id=:userId and chat_room_id in ("+GROUPS+"))"
                +" or "+sourceQuestion+" in (select cast(id as text) from anonymous_questions where id in ("+QUESTIONS+")))"+afterRoomCreated,r);
        // 반응자가 퇴장한 경우: 새 payload는 식별자로, 기존 이력은 같은 방 영상과 가명으로 한정한다.
        String reactionActor=json+"->>'reactor_user_id'";
        update("update notification_history set member_removed_at=:now where type='REACTION_RECEIVED'"+afterRoomCreated+" and "+sourceVideo+
                " in (select cast(v.id as text) from video_posts v join room_members m on m.id=v.room_member_id where m.room_id=:roomId)"
                +" and ("+reactionActor+"=cast(:userId as text) or ("+reactionActor+" is null and position((select concat(coalesce(pseudonym,'익명'),'님이 반응을 남겼어요') from room_members where id=:memberId) in title)>0))",r);
        String priorChat="coalesce("+json+"->>'chat_room_id',"+json+"->>'chatRoomId')";
        update("update notification_history set member_removed_at=:now where type in ('CHAT','CHAT_REPLY','VIDEO_REPLY')"+afterRoomCreated+" and "+priorChat+
                " in (select cast(id as text) from chat_rooms where room_id=:roomId and type='GROUP')"
                +" and (user_id=:userId or body like (select concat(coalesce(pseudonym,'익명'),'님%') from room_members where id=:memberId))",r);
        if(!privateIds.isEmpty()) {
            em.createNativeQuery("update notification_history set member_removed_at=:now where created_at>=(select created_at from rooms where id=:roomId) and "+priorChat+
                    " in (select cast(id as text) from chat_rooms where id in (:ids))")
                    .setParameter("now",r.removedAt()).setParameter("roomId",r.roomId()).setParameter("ids",privateIds).executeUpdate();
        }
        update("update mid_participation_invitations set member_removed_at=:now where user_id=:userId and campaign_id in (select id from mid_participation_campaigns where room_id=:roomId)",r);
        update("update room_members set member_removed_at=:now where id=:memberId",r);
        em.clear();
        removals.saveAndFlush(MemberRemovalEntity.from(r));
    }
    public RoomVisibility visibility(Long userId) {
        Object[] version=(Object[])em.createNativeQuery("select count(*),max(x.removed_at) from room_member_removals x where x.user_id=:userId or exists (select 1 from room_members m where m.room_id=x.room_id and m.user_id=:userId and m.deleted_at is null and m.member_removed_at is null)").setParameter("userId",userId).getSingleResult();
        @SuppressWarnings("unchecked")
        List<Number> rooms=em.createNativeQuery("select room_id from room_member_removals where user_id=:userId order by room_id").setParameter("userId",userId).getResultList();
        @SuppressWarnings("unchecked")
        List<Number> chats=em.createNativeQuery("select distinct c.id from chat_rooms c join chat_room_members m on m.chat_room_id=c.id where m.user_id=:userId and c.member_removed_at is not null order by c.id").setParameter("userId",userId).getResultList();
        return new RoomVisibility(version[0]+":"+String.valueOf(version[1]),rooms.stream().map(Number::longValue).toList(),chats.stream().map(Number::longValue).toList());
    }
    private void update(String sql,MemberRemoval r) { parameterize(sql,r).executeUpdate(); }
    private Query parameterize(String sql,MemberRemoval r) {
        Query q=em.createNativeQuery(sql);
        if(sql.contains(":roomId")) q.setParameter("roomId",r.roomId());
        if(sql.contains(":memberId")) q.setParameter("memberId",r.memberId());
        if(sql.contains(":userId")) q.setParameter("userId",r.userId());
        if(sql.contains(":now")) q.setParameter("now",r.removedAt());
        return q;
    }
}
