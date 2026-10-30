package org.son.haruharu.domain.question.storage;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface QuestionJpaRepository extends JpaRepository<QuestionEntity, Long> {
    List<QuestionEntity> findByRoomIdAndSenderUserIdOrderByIdDesc(
            Long roomId, Long userId, Pageable pageable);

    java.util.Optional<QuestionEntity> findByRoomIdAndSenderUserIdAndRequestId(
            Long roomId, Long userId, java.util.UUID requestId);

    long countByRoomIdAndSenderUserId(Long roomId, Long userId);

    boolean existsByRoomIdAndSenderUserIdAndRecipientUserIdAndDay(
            Long roomId, Long userId, Long recipientUserId, int day);

    @Query(
            "select q from QuestionEntity q where q.roomId=:roomId and q.recipientUserId=:userId"
                + " and q.recipientReported=false and q.senderUserId not in :blocked and q.id <"
                + " :cursor order by q.id desc")
    List<QuestionEntity> findReceived(
            @Param("roomId") Long roomId,
            @Param("userId") Long userId,
            @Param("blocked") java.util.Set<Long> blocked,
            @Param("cursor") Long cursor,
            Pageable pageable);

    @Query(
            "select count(q) from QuestionEntity q where q.roomId=:roomId and"
                + " q.recipientUserId=:userId and q.recipientReported=false and q.senderUserId not"
                + " in :blocked")
    long countReceived(
            @Param("roomId") Long roomId,
            @Param("userId") Long userId,
            @Param("blocked") java.util.Set<Long> blocked);

    @Query(
            """
select q from QuestionEntity q where q.roomId=:roomId and
((q.recipientUserId=:userId and q.recipientRead=false and q.recipientReported=false and q.senderUserId not in :blocked)
or (q.senderUserId=:userId and q.answer is not null and q.senderRead=false and q.senderReported=false and q.recipientUserId not in :blocked))
order by case when q.senderUserId=:userId then q.answeredAt else q.createdAt end desc, q.id desc
""")
    List<QuestionEntity> latestUnread(
            @Param("roomId") Long roomId,
            @Param("userId") Long userId,
            @Param("blocked") java.util.Set<Long> blocked,
            Pageable pageable);

    @Query(
            value =
                    """
select r.id from rooms r join room_members m on m.room_id=r.id and m.user_id=:userId and m.deleted_at is null and m.member_removed_at is null
join users u on u.id=m.user_id and u.status='ACTIVE' and u.deleted_at is null
where r.id < :cursor and r.deleted_at is null
and (r.status='CANCELLED' or r.ends_at <= :now or (r.ends_at is null and r.status='ENDED'))
and exists (select 1 from anonymous_questions q where q.room_id=r.id and q.deleted_at is null and q.member_removed_at is null
  and ((q.sender_user_id=:userId and not q.sender_reported and not exists
    (select 1 from user_blocks b where b.blocker_user_id=:userId and b.blocked_user_id=q.recipient_user_id and b.deleted_at is null))
  or (q.recipient_user_id=:userId and not q.recipient_reported and not exists
    (select 1 from user_blocks b where b.blocker_user_id=:userId and b.blocked_user_id=q.sender_user_id and b.deleted_at is null))))
order by r.id desc
""",
            nativeQuery = true)
    List<Long> historyRoomIds(
            @Param("userId") Long userId,
            @Param("cursor") Long cursor,
            @Param("now") LocalDateTime now,
            Pageable pageable);

    @Query(
            value =
                    """
                    select id from rooms where id > :afterId and deleted_at is null
                    and status <> 'CANCELLED' and starts_at <= :now
                    and final_selection_opens_at > :now and ends_at > :now order by id
                    """,
            nativeQuery = true)
    List<Long> findGrantCandidateRoomIds(
            @Param("afterId") Long afterId,
            @Param("now") LocalDateTime now,
            Pageable pageable);
}
