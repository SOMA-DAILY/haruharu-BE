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
    private int resolvedCapacity(Room room) {
        var existing=repository.capacity(room.id());
        int configured=configuredCapacity(room);
        if (existing.isPresent()) return configured>0 ? configured : existing.orElseThrow(org.son.haruharu.domain.midparticipation.implement.exception.ParticipationUnavailableException::new);
        long active=members.countByRoomId(room.id());
        int capacity=configured;
        if(capacity<=0) {
            int inviteCapacity=invites.findByRoomId(room.id()).stream()
                    .map(RoomInvite::maxUses).filter(Objects::nonNull).findFirst().orElse(0);
            capacity=(int)Math.max(active,inviteCapacity+members.countByRoomIdAndEntryPath(room.id(),RoomEntryPath.APPLICATION));
        }
        return capacity;
    }
    public int initialize(Room room) {
        int capacity=resolvedCapacity(room);
        if(repository.capacity(room.id()).isEmpty()) repository.saveCapacity(room.id(),capacity);
        return capacity;
    }
    private int configuredCapacity(Room room) {
        var c=room.composition();
        return c.maleCapacity()!=null && c.femaleCapacity()!=null ? c.maleCapacity()+c.femaleCapacity() : 0;
    }
    public int available(Room room) {
        return Math.max(0,resolvedCapacity(room)-(int)members.countByRoomId(room.id()));
    }
    public void assertNotRemoved(Long roomId,Long userId) {
        if(repository.removed(roomId,userId)) throw new ParticipationUnavailableException("참여가 종료된 방에는 다시 입장할 수 없어요.");
    }
    public void checkJoin(Long roomId,Long userId) {
        assertNotRemoved(roomId,userId);
        var limit=repository.capacity(roomId);
        if(limit.isEmpty()) return;
        var room=rooms.findById(roomId).orElseThrow(org.son.haruharu.domain.room.business.exception.RoomNotFoundException::new);
        int configured=configuredCapacity(room);
        int capacity=configured>0 ? configured : limit.orElseThrow(org.son.haruharu.domain.midparticipation.implement.exception.ParticipationUnavailableException::new);
        if(members.countByRoomId(roomId)>=capacity) throw new ParticipationUnavailableException("이미 다른 분이 참여해 자리가 마감되었어요.");
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
    public List<Long> remove(MemberRemoval removal) { return repository.remove(removal); }
}
