package org.son.haruharu.admin.domain.user.implement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.son.haruharu.admin.domain.user.implement.UserRoomHistory.Outcome;
import org.son.haruharu.domain.room.implement.FinalSelection;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class UserRoomHistoryTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 9, 1, 19, 0);
    private static final RoomMember ME = new RoomMember(1L, 10L, 100L, "FEMALE", "하늘", null, AT);
    private static final RoomMember HIM = new RoomMember(2L, 10L, 200L, "MALE", "바다", null, AT);

    /**
     * 결과는 선택 기록으로 정하되, 선택이 열리기 전(시작 전·진행 중)이나 취소된 방에서
     * 아직 아무것도 내지 않았다면 "미제출"이 아니라 결과 없음(null)이다.
     */
    @ParameterizedTest(name = "{0} · 내 선택={1} · 상대 선택={2} · 매칭={3} → {4}")
    @DisplayName("방 상태와 선택 기록으로 결과를 정한다")
    @CsvSource(nullValues = "null", value = {
            "ENDED,     HIM,  ME,   true,  MATCHED",
            "ENDED,     HIM,  ME,   false, MUTUAL",
            "ENDED,     HIM,  null, false, ONE_WAY",
            "ENDED,     HIM,  NONE, false, ONE_WAY",
            "ENDED,     NONE, ME,   false, PASSED",
            "ENDED,     null, ME,   false, NOT_SUBMITTED",
            "SELECTING, null, null, false, NOT_SUBMITTED",
            "ACTIVE,    null, null, false, null",
            "PENDING,   null, null, false, null",
            "CANCELLED, null, null, false, null",
    })
    void givenRoomStatusAndSelections_whenOutcome_thenClassified(RoomStatus status, String mine, String his, boolean matched, Outcome expected) {
        List<FinalSelection> selections = new java.util.ArrayList<>();
        if (mine != null) selections.add(FinalSelection.of(ME.id(), mine.equals("HIM") ? HIM.id() : null, AT));
        if (his != null) selections.add(FinalSelection.of(HIM.id(), his.equals("ME") ? ME.id() : null, AT));

        UserRoomHistory history = UserRoomHistory.of(room(), ME, List.of(ME, HIM), selections,
                matched ? Set.of(UserRoomHistory.pairKey(HIM.id(), ME.id())) : Set.of(), 0, 0);

        assertThat(history.outcomeAt(status)).isEqualTo(expected);
    }

    @Test
    @DisplayName("나를 고른 같은 방 멤버 수를 센다(기권·다른 사람 선택 제외)")
    void givenSelectionsTowardMe_whenBuild_thenReceivedCountExcludesPassAndOthers() {
        RoomMember third = new RoomMember(3L, 10L, 300L, "MALE", "산", null, AT);
        RoomMember fourth = new RoomMember(4L, 10L, 400L, "MALE", "강", null, AT);
        List<FinalSelection> selections = List.of(
                FinalSelection.of(HIM.id(), ME.id(), AT),
                FinalSelection.of(third.id(), ME.id(), AT),
                FinalSelection.of(fourth.id(), null, AT));

        UserRoomHistory history = UserRoomHistory.of(room(), ME, List.of(ME, HIM, third, fourth), selections, Set.of(), 0, 0);

        assertThat(history.receivedCount()).isEqualTo(2);
        assertThat(history.selected()).isNull();
        assertThat(history.submitted()).isFalse();
    }

    private static Room room() {
        return new Room(10L, RoomStatus.ENDED, LocalDate.of(2026, 9, 1), 3, null, null);
    }
}
