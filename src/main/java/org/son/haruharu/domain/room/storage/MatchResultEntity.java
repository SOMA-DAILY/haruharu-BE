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
import java.util.UUID;

@Entity
@Table(name = "match_results", uniqueConstraints = @UniqueConstraint(columnNames = {"member_a_id", "member_b_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchResultEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID memberAId;

    @Column(nullable = false)
    private UUID memberBId;

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
