package org.son.haruharu.domain.room.business;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import org.son.haruharu.domain.room.implement.*;
import org.son.haruharu.domain.room.business.event.RoomMemberRemovedEvent;
import org.son.haruharu.domain.room.business.exception.RoomNotFoundException;
import org.son.haruharu.domain.room.storage.exception.RoomMemberNotFoundException;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class RoomMemberRemovalService {
    private final RoomReader rooms;
    private final RoomMemberReader members;
    private final RoomParticipationGate gate;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    @Transactional
    public MemberRemoval remove(Long roomId,Long memberId,String reason,Long adminId) {
        var room=rooms.findByIdForUpdate(roomId).orElseThrow(RoomNotFoundException::new);
        var existing=gate.removal(memberId);
        if(existing.isPresent()) {
            if(!existing.orElseThrow(org.son.haruharu.domain.room.storage.exception.RoomMemberNotFoundException::new).roomId().equals(roomId)) throw new RoomMemberNotFoundException();
            return existing.orElseThrow(org.son.haruharu.domain.room.storage.exception.RoomMemberNotFoundException::new);
        }
        var member=members.getById(memberId);
        if(!member.roomId().equals(roomId)) throw new RoomMemberNotFoundException();
        members.lockById(memberId);
        gate.initialize(room);
        var removal=new MemberRemoval(memberId,roomId,member.userId(),LocalDateTime.now(clock),reason,adminId);
        var chats=gate.remove(removal);
        events.publishEvent(new RoomMemberRemovedEvent(roomId,member.userId(),chats));
        return removal;
    }
}
