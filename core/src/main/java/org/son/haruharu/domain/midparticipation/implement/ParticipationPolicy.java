package org.son.haruharu.domain.midparticipation.implement;
import java.time.LocalDateTime;
import java.util.List;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.son.haruharu.domain.midparticipation.implement.exception.ParticipationUnavailableException;
public final class ParticipationPolicy {
    private ParticipationPolicy() {}
    public static void requireRecruiting(Room room,LocalDateTime now) {
        if(room.statusAt(now)!=RoomStatus.ACTIVE || room.finalSelectionOpensAt()==null || !now.isBefore(room.finalSelectionOpensAt()))
            throw new ParticipationUnavailableException("진행 중이며 최종 선택이 시작되기 전인 방만 모집할 수 있어요.");
    }
    public static LocalDateTime expiry(Room room,LocalDateTime now,LocalDateTime requested) {
        var expires=requested==null?now.plusHours(1):requested;
        if(expires.isAfter(room.finalSelectionOpensAt())) expires=room.finalSelectionOpensAt();
        if(!expires.isAfter(now)) throw new ParticipationUnavailableException("마감 시각은 현재보다 뒤로 지정해 주세요.");
        return expires;
    }
    public static List<Long> ids(List<Long> userIds) {
        if(userIds==null || userIds.isEmpty() || userIds.size()>100 || userIds.stream().anyMatch(id->id==null || id<1))
            throw new ParticipationUnavailableException("대상자는 1명 이상 100명 이하로 선택해 주세요.");
        return userIds.stream().distinct().sorted().toList();
    }
}
