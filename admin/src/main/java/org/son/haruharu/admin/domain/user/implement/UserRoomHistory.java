package org.son.haruharu.admin.domain.user.implement;

import org.son.haruharu.domain.room.implement.FinalSelection;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomStatus;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 유저 한 명의 방 한 곳 참여 이력(#508): 방 + 그 방의 내 멤버 행 + 최종 선택·매칭 결과 + 활동 수.
 *
 * @param selected      내가 고른 같은 방 멤버(미제출·기권이면 null)
 * @param submitted     최종 선택 제출 여부(기권 포함)
 * @param passed        "아무도 선택 안 하기"로 제출
 * @param mutual        고른 상대도 나를 골랐는지
 * @param receivedCount 나를 고른 같은 방 멤버 수
 * @param matched       상호 선택이 매칭 결과로 성사됐는지
 */
public record UserRoomHistory(
        Room room,
        RoomMember member,
        RoomMember selected,
        boolean submitted,
        boolean passed,
        boolean mutual,
        int receivedCount,
        boolean matched,
        long videoCount,
        long chatCount
) {
    public enum Outcome { MATCHED, MUTUAL, ONE_WAY, PASSED, NOT_SUBMITTED }

    /**
     * 방 멤버 전원과 그들의 최종 선택에서 내 기준 이력을 만든다.
     * matchedPairs 는 {@link #pairKey} 로 만든 매칭 성사 쌍 — 상호 선택일 때만 성사로 인정한다.
     */
    public static UserRoomHistory of(Room room, RoomMember me, List<RoomMember> roomMembers,
                                     List<FinalSelection> roomSelections, Set<String> matchedPairs,
                                     long videoCount, long chatCount) {
        Map<Long, RoomMember> membersById = roomMembers.stream()
                .collect(Collectors.toMap(RoomMember::id, Function.identity(), (a, b) -> a));
        Map<Long, FinalSelection> selectionsByChooser = roomSelections.stream()
                .filter(s -> membersById.containsKey(s.roomMemberId()))
                .collect(Collectors.toMap(FinalSelection::roomMemberId, Function.identity(), (a, b) -> a));

        FinalSelection mine = selectionsByChooser.get(me.id());
        RoomMember selected = mine == null || mine.selectedMemberId() == null
                ? null : membersById.get(mine.selectedMemberId());
        FinalSelection reverse = selected == null ? null : selectionsByChooser.get(selected.id());
        boolean mutual = reverse != null && me.id().equals(reverse.selectedMemberId());
        int received = (int) selectionsByChooser.values().stream()
                .filter(s -> !me.id().equals(s.roomMemberId()) && me.id().equals(s.selectedMemberId()))
                .count();

        return new UserRoomHistory(room, me, selected, mine != null, mine != null && mine.selectedMemberId() == null,
                mutual, received, mutual && matchedPairs.contains(pairKey(me.id(), selected.id())),
                videoCount, chatCount);
    }

    /**
     * 결과 표기. 최종 선택이 열리기 전(시작 전·진행 중)이나 취소된 방에서 아직 낸 게 없으면 결과가 없다(null) —
     * "미제출"은 선택이 열렸거나 끝난 방에서만 의미가 있다.
     */
    public Outcome outcomeAt(RoomStatus status) {
        if (!submitted) {
            return status == RoomStatus.SELECTING || status == RoomStatus.ENDED ? Outcome.NOT_SUBMITTED : null;
        }
        if (passed) {
            return Outcome.PASSED;
        }
        if (matched) {
            return Outcome.MATCHED;
        }
        return mutual ? Outcome.MUTUAL : Outcome.ONE_WAY;
    }

    /** 매칭 결과를 멤버 쌍으로 찾을 때 쓰는 순서 무관 키(작은 id:큰 id). */
    public static String pairKey(Long a, Long b) {
        Objects.requireNonNull(a);
        Objects.requireNonNull(b);
        return Math.min(a, b) + ":" + Math.max(a, b);
    }
}
