package org.son.haruharu.domain.room.implement;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.son.haruharu.domain.midparticipation.implement.exception.ParticipationUnavailableException;

/** 모든 입장 경로가 같은 방 잠금 안에서 호출한다. 성별 정원은 중간 참여의 자동 제외 조건이 아니다. */
@Component @RequiredArgsConstructor
public class RoomParticipationGate {
    private final RoomParticipationRepository repository;
    private final RoomMemberReader members;
    private final RoomInviteReader invites;
    private final RoomReader rooms;
    /** 정원이 없는 방의 빈자리. 한 번에 보낼 수 있는 제안 상한과 같다. */
    private static final int UNLIMITED_SEATS=100;
    /** 남녀 정원 합, 없으면 활성 코드 인원. 둘 다 없으면 정원 없음(null). */
    private Integer capacity(Room room) {
        var c=room.composition();
        if(c.maleCapacity()!=null && c.femaleCapacity()!=null && c.maleCapacity()+c.femaleCapacity()>0) return c.maleCapacity()+c.femaleCapacity();
        var active=invites.findByRoomId(room.id()).stream().filter(i->i.status()==RoomInviteStatus.ACTIVE).toList();
        if(active.isEmpty() || active.stream().anyMatch(i->i.maxUses()==null)) return null;
        return active.stream().mapToInt(RoomInvite::maxUses).max().orElseThrow()+(int)members.countByRoomIdAndEntryPath(room.id(),RoomEntryPath.APPLICATION);
    }
    public int available(Room room) {
        Integer capacity=capacity(room);
        return capacity==null ? UNLIMITED_SEATS : Math.max(0,capacity-(int)members.countByRoomId(room.id()));
    }
    public boolean isRemoved(Long roomId,Long userId) { return repository.removed(roomId,userId); }
    public void assertNotRemoved(Long roomId,Long userId) {
        if(isRemoved(roomId,userId)) throw new ParticipationUnavailableException("참여가 종료된 방에는 다시 입장할 수 없어요.");
    }
    public void checkJoin(Long roomId,Long userId) {
        assertNotRemoved(roomId,userId);
        var room=rooms.findById(roomId).orElseThrow(org.son.haruharu.domain.room.business.exception.RoomNotFoundException::new);
        Integer capacity=capacity(room);
        if(capacity!=null && members.countByRoomId(roomId)>=capacity) throw new ParticipationUnavailableException("이미 다른 분이 참여해 자리가 마감되었어요.");
    }
    public String uniquePseudonym(Long roomId,String gender,int initialIndex) {
        if(gender==null) return null;
        Set<String> used=repository.historicalPseudonyms(roomId);
        int index=initialIndex;
        String name=PseudonymPool.assign(gender,index);
        while(name!=null && used.contains(name)) name=PseudonymPool.assign(gender,++index);
        return name;
    }
    public RoomVisibility visibility(Long userId) { return repository.visibility(userId); }
    public Optional<MemberRemoval> removal(Long memberId) { return repository.removal(memberId); }
    public void remove(MemberRemoval removal) { repository.remove(removal); }
}
