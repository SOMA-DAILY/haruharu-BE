package org.son.haruharu.domain.room.storage;

import org.son.haruharu.common.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.son.haruharu.domain.room.implement.MatchResult;

import java.time.LocalDateTime;

@Entity
@Table(name = "match_results", uniqueConstraints = @UniqueConstraint(columnNames = {"member_a_id", "member_b_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchResultEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 네이밍 전략이 단일 대문자 접미(memberAId)를 memberaid로 접어 유니크 제약과 어긋나므로 컬럼명을 명시한다 (#96)
    @Column(name = "member_a_id", nullable = false)
    private Long memberAId;

    @Column(name = "member_b_id", nullable = false)
    private Long memberBId;

    @Column(nullable = false)
    private LocalDateTime matchedAt;

    public static MatchResultEntity from(MatchResult result) {
        MatchResultEntity entity = new MatchResultEntity();
        entity.id = result.id();
        entity.memberAId = result.memberAId();
        entity.memberBId = result.memberBId();
        entity.matchedAt = result.matchedAt();
        return entity;
    }

    public MatchResult toDomain() {
        return new MatchResult(id, memberAId, memberBId, matchedAt);
    }
}
