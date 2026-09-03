package org.son.haruharu.domain.room.implement;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.common.exception.BusinessException;
import org.son.haruharu.domain.room.implement.exception.InvalidRoomInviteMaxUsesException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

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
    @DisplayName("issue는 null 또는 양수 maxUses만 발급하고 0·음수는 안정된 도메인 오류로 거부한다")
    void givenNonPositiveMaxUses_whenIssue_thenRejectsAtFactoryBoundary() {
        Throwable zero = catchThrowable(() -> RoomInvite.issue(1L, "ABC123", 0, null, "admin"));

        assertThat(zero).isInstanceOf(InvalidRoomInviteMaxUsesException.class);
        assertThat(((BusinessException) zero).getCode()).isEqualTo("ROOM_INVITE_MAX_USES_INVALID");
        assertThatThrownBy(() -> RoomInvite.issue(1L, "ABC123", -1, null, "admin"))
                .isInstanceOf(InvalidRoomInviteMaxUsesException.class);
        assertThat(RoomInvite.issue(1L, "ABC123", null, null, "admin").maxUses()).isNull();
    }

    @Test
    @DisplayName("기존 DB의 non-positive maxUses 행은 record 복원은 허용하지만 새 멤버를 받을 수 없다")
    void givenLegacyNonPositiveMaxUses_whenRehydrated_thenHasNoCapacity() {
        RoomInvite legacy = new RoomInvite(1L, 1L, "ABC123", RoomInviteStatus.ACTIVE, 0, 0, null, "admin");

        assertThat(legacy.hasCapacity(0)).isFalse();
    }

    @Test
    @DisplayName("isUsable: ACTIVE·미만료면 true")
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
    @DisplayName("isUsable: usedCount는 usage 메타데이터이므로 정원과 무관하게 lifecycle만 판단한다")
    void givenUsageReached_whenIsUsable_thenLifecycleStillDeterminesUsability() {
        RoomInvite invite = new RoomInvite(1L, 1L, "C", RoomInviteStatus.ACTIVE, 2, 6, null, "admin");
        assertThat(invite.isUsable(NOW)).isTrue();
    }

    @Test
    @DisplayName("maxUses=2면 active member 두 명까지는 이미 찬 방으로 판정한다")
    void givenMaxUsesTwo_whenCheckingActiveCapacity_thenAllowsOneAndRejectsTwo() {
        RoomInvite invite = RoomInvite.issue(1L, "ABC123", 2, null, "admin");

        assertThat(invite.hasCapacity(1)).isTrue();
        assertThat(invite.hasCapacity(2)).isFalse();
    }

    @Test
    @DisplayName("maxUses=8이면 active member 일곱 명까지 새 멤버를 허용한다")
    void givenMaxUsesEight_whenCheckingActiveCapacity_thenAllowsSevenAndRejectsEight() {
        RoomInvite invite = RoomInvite.issue(1L, "ABC123", 8, null, "admin");

        assertThat(invite.hasCapacity(7)).isTrue();
        assertThat(invite.hasCapacity(8)).isFalse();
    }

    @Test
    @DisplayName("maxUses가 null이면 기존 active member가 여섯 명을 넘어도 무제한이다")
    void givenUnlimitedMaxUses_whenCheckingActiveCapacity_thenAllowsMoreThanSixMembers() {
        RoomInvite invite = RoomInvite.issue(1L, "ABC123", null, null, "admin");

        assertThat(invite.hasCapacity(6)).isTrue();
        assertThat(invite.hasCapacity(100)).isTrue();
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

    @Test
    @DisplayName("expired는 상태를 EXPIRED로 바꾸고 나머지 필드는 보존한다 (#318 재발급)")
    void givenActiveInvite_whenExpired_thenStatusExpiredAndFieldsKept() {
        RoomInvite invite = new RoomInvite(1L, 7L, "C", RoomInviteStatus.ACTIVE, 6, 2, NOW, "admin");

        RoomInvite expired = invite.expired();

        assertThat(expired.status()).isEqualTo(RoomInviteStatus.EXPIRED);
        assertThat(expired.id()).isEqualTo(1L);
        assertThat(expired.roomId()).isEqualTo(7L);
        assertThat(expired.code()).isEqualTo("C");
        assertThat(expired.usedCount()).isEqualTo(2);
        assertThat(expired.isUsable(NOW.minusDays(1))).isFalse();
    }
}
