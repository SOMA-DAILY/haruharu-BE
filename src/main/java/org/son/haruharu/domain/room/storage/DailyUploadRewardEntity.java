package org.son.haruharu.domain.room.storage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.son.haruharu.domain.room.implement.DailyUploadReward;

import java.time.LocalDateTime;

@Entity
@Table(name = "daily_upload_reward",
        uniqueConstraints = @UniqueConstraint(name = "ux_daily_upload_reward_member_day",
                columnNames = {"roomMemberId", "dayNumber"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyUploadRewardEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long roomMemberId;

    @Column(nullable = false)
    private int dayNumber;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false, updatable = false)
    private LocalDateTime grantedAt;

    public static DailyUploadRewardEntity from(DailyUploadReward reward) {
        var entity = new DailyUploadRewardEntity();
        entity.id = reward.id();
        entity.roomMemberId = reward.roomMemberId();
        entity.dayNumber = reward.dayNumber();
        entity.amount = reward.amount();
        entity.grantedAt = reward.grantedAt();
        return entity;
    }

    public DailyUploadReward toDomain() {
        return new DailyUploadReward(id, roomMemberId, dayNumber, amount, grantedAt);
    }
}
