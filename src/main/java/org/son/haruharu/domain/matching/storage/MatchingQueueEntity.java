package org.son.haruharu.domain.matching.storage;

import org.son.haruharu.common.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.son.haruharu.domain.matching.implement.MatchingQueue;
import org.son.haruharu.domain.matching.implement.MatchingQueueStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "matching_queue")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchingQueueEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 10)
    private String gender;

    @Column
    private LocalDate birthday;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MatchingQueueStatus status;

    @Column(nullable = false)
    private LocalDateTime queuedAt;

    @Column
    private LocalDateTime matchedAt;

    public static MatchingQueueEntity from(MatchingQueue queue) {
        MatchingQueueEntity entity = new MatchingQueueEntity();
        entity.id = queue.id();
        entity.userId = queue.userId();
        entity.gender = queue.gender();
        entity.birthday = queue.birthday();
        entity.status = queue.status();
        entity.queuedAt = queue.queuedAt();
        entity.matchedAt = queue.matchedAt();
        return entity;
    }

    public MatchingQueue toDomain() {
        return new MatchingQueue(id, userId, gender, birthday, status, queuedAt, matchedAt);
    }
}
