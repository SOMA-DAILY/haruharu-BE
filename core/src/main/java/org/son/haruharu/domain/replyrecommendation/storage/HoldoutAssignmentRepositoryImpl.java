package org.son.haruharu.domain.replyrecommendation.storage;

import static org.son.haruharu.domain.replyrecommendation.storage.AiReplySql.ts;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.son.haruharu.domain.replyrecommendation.implement.ExperimentArm;
import org.son.haruharu.domain.replyrecommendation.implement.HoldoutAssignment;
import org.son.haruharu.domain.replyrecommendation.implement.HoldoutAssignmentRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** 비교 그룹 배정(이슈 #560). 표는 scripts/sql/561-reply-effect-up.sql 이 정의한다(엔티티 없음). */
@Repository
public class HoldoutAssignmentRepositoryImpl implements HoldoutAssignmentRepository {

    private final JdbcClient jdbc;
    private volatile boolean ready;

    public HoldoutAssignmentRepositoryImpl(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    /** 배정 표와 reply_link.experiment_arm 칸이 모두 있어야 한다. 한 번 확인되면 다시 묻지 않는다(되돌리기는 비율 0 먼저). */
    @Override
    public boolean schemaReady() {
        if (ready) {
            return true;
        }
        Long found = jdbc.sql("""
                        SELECT (SELECT count(*) FROM information_schema.tables
                                 WHERE lower(table_schema) = 'ai_reply' AND lower(table_name) = 'holdout_assignment')
                             + (SELECT count(*) FROM information_schema.columns
                                 WHERE lower(table_schema) = 'ai_reply' AND lower(table_name) = 'reply_link'
                                   AND lower(column_name) = 'experiment_arm')""")
                .query(Long.class)
                .single();
        ready = found != null && found == 2;
        return ready;
    }

    @Override
    public Optional<ExperimentArm> findArm(Long roomId, Long userId) {
        return jdbc.sql("SELECT arm FROM ai_reply.holdout_assignment WHERE room_id = :roomId AND user_id = :userId"
                        + " ORDER BY assigned_at LIMIT 1")
                .param("roomId", roomId)
                .param("userId", userId)
                .query(String.class)
                .optional()
                .map(ExperimentArm::valueOf);
    }

    @Override
    public void lockRoom(Long roomId) {
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended('reply-holdout:' || :roomId, 0))")
                .param("roomId", roomId)
                .query((rs, row) -> 1)
                .list();
    }

    @Override
    public boolean roomAssigned(Long roomId) {
        Long found = jdbc.sql("SELECT count(*) FROM ai_reply.holdout_assignment WHERE room_id = :roomId")
                .param("roomId", roomId)
                .query(Long.class)
                .single();
        return found != null && found > 0;
    }

    @Override
    public void insertAll(List<HoldoutAssignment> assignments, Instant now) {
        for (HoldoutAssignment a : assignments) {
            jdbc.sql("""
                            INSERT INTO ai_reply.holdout_assignment
                                (group_key, room_id, user_id, arm, hash, ratio, assigned_at)
                            VALUES (:groupKey, :roomId, :userId, :arm, :hash, :ratio, :now)
                            ON CONFLICT (group_key, room_id, user_id) DO NOTHING""")
                    .param("groupKey", a.groupKey())
                    .param("roomId", a.roomId())
                    .param("userId", a.userId())
                    .param("arm", a.arm().name())
                    .param("hash", BigDecimal.valueOf(a.hash()).setScale(18, RoundingMode.DOWN))
                    .param("ratio", BigDecimal.valueOf(a.ratio()).setScale(3, RoundingMode.HALF_UP))
                    .param("now", ts(now))
                    .update();
        }
    }
}
