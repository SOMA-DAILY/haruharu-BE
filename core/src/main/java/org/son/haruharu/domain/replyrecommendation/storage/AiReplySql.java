package org.son.haruharu.domain.replyrecommendation.storage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * ai_reply 스키마 SQL 공통 도우미. 이 스키마는 Hibernate 가 관리하지 않고(엔티티 없음)
 * scripts/sql/558-reply-recommendations-up.sql 이 정의한다. 시각은 timestamptz(UTC)로 주고받는다.
 */
final class AiReplySql {

    private AiReplySql() {
    }

    static OffsetDateTime ts(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}
