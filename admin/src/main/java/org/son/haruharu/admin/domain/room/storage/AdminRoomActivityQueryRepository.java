package org.son.haruharu.admin.domain.room.storage;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.son.haruharu.admin.domain.room.implement.RoomActivity;
import org.son.haruharu.admin.domain.room.implement.RoomActivityRepository;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/** UNION을 DB에서 정렬/페이지 처리한다. native SQL이므로 모든 soft-delete 조건을 명시한다. */
@Repository
public class AdminRoomActivityQueryRepository implements RoomActivityRepository {
    private final EntityManager em;

    public AdminRoomActivityQueryRepository(EntityManager em) { this.em = em; }

    private static final String EVENTS = """
            select 'VIDEO' as type, v.id, v.created_at as occurred_at, m.user_id as actor_user_id,
                   cast(null as bigint) as target_user_id, cast(null as text) as content,
                   v.id as video_id, v.video_url, v.thumbnail_url, v.caption,
                   cast(null as text) as reaction_type, cast(null as text) as answer,
                   cast(null as timestamp) as answered_at, v.is_hidden as hidden, v.mission_id, v.slot_start
            from video_posts v join room_members m on m.id = v.room_member_id
            where m.room_id = :roomId and m.deleted_at is null and v.deleted_at is null
            union all
            select 'CHAT', c.id, c.created_at, c.sender_id, null, c.content,
                   null, null, null, null, null, null, null, false, null, null
            from chat_messages c join chat_rooms r on r.id = c.chat_room_id
            where r.room_id = :roomId and r.type = 'GROUP' and r.deleted_at is null and c.deleted_at is null
              and c.sender_type = 'USER'
            union all
            select 'REACTION', x.id, x.created_at, x.user_id, m.user_id, null,
                   v.id, v.video_url, v.thumbnail_url, v.caption, x.reaction_type, null, null, v.is_hidden, v.mission_id, v.slot_start
            from video_reactions x join video_posts v on v.id = x.video_post_id
            join room_members m on m.id = v.room_member_id
            where m.room_id = :roomId and m.deleted_at is null and v.deleted_at is null
            union all
            select 'QUESTION', q.id, q.created_at, q.sender_user_id, q.recipient_user_id, q.content,
                   null, null, null, null, null, q.answer, q.answered_at, false, null, null
            from anonymous_questions q where q.room_id = :roomId and q.deleted_at is null
            """;

    private String where(RoomActivity.Filter f) {
        return " where 1 = 1"
                + (f.from() == null ? "" : " and (case when e.type = 'VIDEO' then coalesce(e.slot_start, e.occurred_at) else e.occurred_at end) >= :fromTime and (case when e.type = 'VIDEO' then coalesce(e.slot_start, e.occurred_at) else e.occurred_at end) < :toTime")
                + (f.userId() == null ? "" : " and (e.actor_user_id = :userId or e.target_user_id = :userId)")
                + (f.type() == null ? "" : " and e.type = :type");
    }

    private Query query(String projection, String suffix, RoomActivity.Filter f) {
        Query q = em.createNativeQuery(projection + " from (" + EVENTS + ") e" + where(f) + suffix);
        q.setParameter("roomId", f.roomId());
        if (f.from() != null) {
            q.setParameter("fromTime", f.from());
            q.setParameter("toTime", f.to());
        }
        if (f.userId() != null) q.setParameter("userId", f.userId());
        if (f.type() != null) q.setParameter("type", f.type());
        return q;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<RoomActivity> find(RoomActivity.Filter f, int offset, int limit, boolean oldestFirst) {
        String direction = oldestFirst ? " asc" : " desc";
        String sortTime = "VIDEO".equals(f.type()) ? "coalesce(e.slot_start, e.occurred_at)" : "e.occurred_at";
        List<Object[]> rows = query("select e.*", " order by " + sortTime + direction
                + ", e.type" + direction + ", e.id" + direction, f)
                .setFirstResult(offset).setMaxResults(limit).getResultList();
        return rows.stream().map(r -> new RoomActivity((String) r[0], number(r[1]), time(r[2]),
                number(r[3]), number(r[4]), (String) r[5], number(r[6]), (String) r[7], (String) r[8],
                (String) r[9], (String) r[10], (String) r[11], time(r[12]), Boolean.TRUE.equals(r[13]), number(r[14]), time(r[15])))
                .toList();
    }

    @Override
    public long count(RoomActivity.Filter f) {
        return ((Number) query("select count(*)", "", f).getSingleResult()).longValue();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<RoomActivity.Count> counts(RoomActivity.Filter f) {
        List<Object[]> rows = query("select e.type, e.actor_user_id, e.target_user_id, count(*)",
                " group by e.type, e.actor_user_id, e.target_user_id", f).getResultList();
        return rows.stream().map(r -> new RoomActivity.Count((String) r[0], number(r[1]), number(r[2]), number(r[3]))).toList();
    }

    private static Long number(Object v) { return v == null ? null : ((Number) v).longValue(); }
    private static LocalDateTime time(Object v) {
        return v == null ? null : v instanceof Timestamp t ? t.toLocalDateTime() : (LocalDateTime) v;
    }
}
