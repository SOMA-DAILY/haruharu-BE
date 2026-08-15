package org.son.haruharu.domain.room.implement;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoomInviteTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 24, 12, 0);

    @Test
    @DisplayName("issue는 ACTIVE·usedCount 0으로 발급한다")
    void givenNewInvite_whenIssue_thenActiveZeroUsed() {
        RoomInvite invite = RoomInvite.issue(1L, "ABC123", 6, null, "admin");
        assertThat(invite.status()).isEqualTo(RoomInviteStatus.ACTIVE);
        assertThat(invite.usedCount()).isZero();
        assertThat(invite.maxUses()).isEqualTo(6);
    }

    @Test
    @DisplayName("isUsable: ACTIVE·미만료·정원 미달이면 true")
    void givenActiveNotExpiredNotFull_whenIsUsable_thenTrue() {
        RoomInvite invite = new RoomInvite(1L, 1L, "C", RoomInviteStatus.ACTIVE, 6, 3, NOW.plusHours(1), "admin");
        assertThat(invite.isUsable(NOW)).isTrue();
    }

    @Test
    @DisplayName("isUsable: 만료 시각을 지나면 false")
    void givenExpired_whenIsUsable_thenFalse() {
        RoomInvite invite = new RoomInvite(1L, 1L, "C", RoomInviteStatus.ACTIVE, 6, 0, NOW.minusMinutes(1), "admin");
        assertThat(invite.isUsable(NOW)).isFalse();
    }

    @Test
    @DisplayName("isUsable: 정원(maxUses) 도달이면 false")
    void givenFull_whenIsUsable_thenFalse() {
        RoomInvite invite = new RoomInvite(1L, 1L, "C", RoomInviteStatus.ACTIVE, 6, 6, null, "admin");
        assertThat(invite.isUsable(NOW)).isFalse();
    }

    @Test
    @DisplayName("isUsable: ACTIVE가 아니면 false")
    void givenDisabled_whenIsUsable_thenFalse() {
        RoomInvite invite = new RoomInvite(1L, 1L, "C", RoomInviteStatus.DISABLED, 6, 0, null, "admin");
        assertThat(invite.isUsable(NOW)).isFalse();
    }

    @Test
    @DisplayName("incrementUsed는 usedCount를 1 증가시킨다")
    void givenInvite_whenIncrementUsed_thenUsedCountPlusOne() {
        RoomInvite invite = new RoomInvite(1L, 1L, "C", RoomInviteStatus.ACTIVE, 6, 2, null, "admin");
        assertThat(invite.incrementUsed().usedCount()).isEqualTo(3);
    }
}
