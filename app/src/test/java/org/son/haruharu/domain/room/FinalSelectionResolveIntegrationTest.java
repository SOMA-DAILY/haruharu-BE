package org.son.haruharu.domain.room;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.room.business.RoomStatusReconciler;
import org.son.haruharu.domain.room.implement.FinalSelection;
import org.son.haruharu.domain.room.implement.FinalSelectionWriter;
import org.son.haruharu.domain.room.implement.MatchResultReader;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberWriter;
import org.son.haruharu.domain.room.implement.RoomWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 최종선택 동시공개 resolve end-to-end(#343). result_at 이 지난 방을 reconciler sweep 이 훑어
 * 상호선택쌍을 match_results 로 일괄 성사시키는지 실제 DB로 검증한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class FinalSelectionResolveIntegrationTest {

    @Autowired RoomStatusReconciler reconciler;
    @Autowired RoomWriter roomWriter;
    @Autowired RoomMemberWriter roomMemberWriter;
    @Autowired FinalSelectionWriter finalSelectionWriter;
    @Autowired MatchResultReader matchResultReader;

    /** result_at 이 이미 지난(1시간 전) 방. resolve 대상. */
    private Room persistResolvableRoom() {
        LocalDateTime startsAt = LocalDateTime.now().minusDays(3);
        LocalDateTime resultAt = LocalDateTime.now().minusHours(1);
        return roomWriter.save(Room.createScheduled(
                startsAt, startsAt.plusDays(2).withHour(18), resultAt.minusMinutes(30),
                resultAt, LocalDateTime.now().plusHours(1), 3));
    }

    @Test
    @DisplayName("result_at이 지난 방에서 상호선택쌍이 reconcile sweep으로 매칭 성사된다")
    void givenMutualSelectionAndResultAtPassed_whenReconcile_thenMatched() {
        Room room = persistResolvableRoom();
        RoomMember m1 = roomMemberWriter.save(
                RoomMember.of(room.id(), 701L, "MALE", LocalDate.of(2000, 1, 1), LocalDateTime.now()));
        RoomMember m2 = roomMemberWriter.save(
                RoomMember.of(room.id(), 702L, "FEMALE", LocalDate.of(2000, 1, 1), LocalDateTime.now()));
        // 서로 선택
        finalSelectionWriter.save(FinalSelection.of(m1.id(), m2.id(), LocalDateTime.now().minusHours(2)));
        finalSelectionWriter.save(FinalSelection.of(m2.id(), m1.id(), LocalDateTime.now().minusHours(2)));

        reconciler.reconcile(LocalDateTime.now());

        assertThat(matchResultReader.findByMember(m1.id())).isPresent();
        assertThat(matchResultReader.isMatched(m1.id(), m2.id())).isTrue();
    }

    @Test
    @DisplayName("재sweep해도 match_results가 중복 생성되지 않는다(멱등)")
    void givenAlreadyResolved_whenReconcileAgain_thenIdempotent() {
        Room room = persistResolvableRoom();
        RoomMember m1 = roomMemberWriter.save(
                RoomMember.of(room.id(), 711L, "MALE", LocalDate.of(2000, 1, 1), LocalDateTime.now()));
        RoomMember m2 = roomMemberWriter.save(
                RoomMember.of(room.id(), 712L, "FEMALE", LocalDate.of(2000, 1, 1), LocalDateTime.now()));
        finalSelectionWriter.save(FinalSelection.of(m1.id(), m2.id(), LocalDateTime.now().minusHours(2)));
        finalSelectionWriter.save(FinalSelection.of(m2.id(), m1.id(), LocalDateTime.now().minusHours(2)));

        reconciler.reconcile(LocalDateTime.now());
        reconciler.reconcile(LocalDateTime.now()); // 재sweep

        // isMatched가 true를 유지하고(중복 저장 시 UNIQUE 위반으로 터졌을 것) 예외 없이 통과
        assertThat(matchResultReader.isMatched(m1.id(), m2.id())).isTrue();
    }

    @Test
    @DisplayName("한쪽만 선택한 방은 매칭이 성사되지 않는다")
    void givenOneSidedSelection_whenReconcile_thenNoMatch() {
        Room room = persistResolvableRoom();
        RoomMember m1 = roomMemberWriter.save(
                RoomMember.of(room.id(), 721L, "MALE", LocalDate.of(2000, 1, 1), LocalDateTime.now()));
        RoomMember m2 = roomMemberWriter.save(
                RoomMember.of(room.id(), 722L, "FEMALE", LocalDate.of(2000, 1, 1), LocalDateTime.now()));
        finalSelectionWriter.save(FinalSelection.of(m1.id(), m2.id(), LocalDateTime.now().minusHours(2)));
        // m2는 선택 안 함(짝사랑)

        reconciler.reconcile(LocalDateTime.now());

        assertThat(matchResultReader.findByMember(m1.id())).isEmpty();
    }
}
