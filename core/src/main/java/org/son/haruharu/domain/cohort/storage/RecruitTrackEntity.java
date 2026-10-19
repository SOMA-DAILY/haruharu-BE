package org.son.haruharu.domain.cohort.storage;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.son.haruharu.common.entity.BaseEntity;
import org.son.haruharu.domain.cohort.implement.RecruitAudience;
import org.son.haruharu.domain.cohort.implement.RecruitTrack;
import org.son.haruharu.domain.cohort.implement.TrackQuestion;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "recruit_tracks",
        uniqueConstraints = @UniqueConstraint(name = "uq_recruit_tracks_key", columnNames = "track_key"),
        check = @CheckConstraint(name = "ck_recruit_tracks_age", constraint = "min_age >= 19 AND min_age <= max_age AND max_age <= 100"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecruitTrackEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // key 는 H2 예약어라 컬럼 이름을 track_key 로 둔다.
    @Column(name = "track_key", nullable = false, length = 40)
    private String key;

    @Column(nullable = false, length = 40)
    private String label;

    @Column(nullable = false, length = 40)
    private String region;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecruitAudience audience;

    @Column(name = "min_age", nullable = false)
    private int minAge;

    @Column(name = "max_age", nullable = false)
    private int maxAge;

    // 트랙별 추가 문항 배열(PostgreSQL jsonb). 문항 형식은 TrackQuestion 이 해석한다.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<Map<String, Object>> questions;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private boolean active;

    public static RecruitTrackEntity from(RecruitTrack track) {
        var entity = new RecruitTrackEntity();
        entity.id = track.id();
        entity.key = track.key();
        entity.label = track.label();
        entity.region = track.region();
        entity.audience = track.audience();
        entity.minAge = track.minAge();
        entity.maxAge = track.maxAge();
        entity.questions = new ArrayList<>(track.questions().stream().map(TrackQuestion::toStored).toList());
        entity.displayOrder = track.displayOrder();
        entity.active = track.active();
        entity.createdAt = track.createdAt();
        entity.updatedAt = track.updatedAt();
        return entity;
    }

    public RecruitTrack toDomain() {
        return new RecruitTrack(id, key, label, region, audience, minAge, maxAge,
                questions == null ? List.of() : questions.stream().map(TrackQuestion::fromStored).toList(),
                displayOrder, active, createdAt, updatedAt);
    }
}
