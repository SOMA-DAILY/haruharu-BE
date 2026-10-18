package org.son.haruharu.domain.room.business;

import org.son.haruharu.domain.room.business.event.RoomMemberJoinedEvent;
import org.son.haruharu.domain.room.implement.PseudonymPool;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomMemberWriter;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 방 입장 공통 경로(#513): 가명 배정 → 멤버 저장 → {@link RoomMemberJoinedEvent}.
 * 초대코드 입장(RoomInviteService)과 앱 신청자 배정(#516)이 같은 동작으로 멤버를 만들게 한 곳에 둔다.
 * <p>
 * 호출측 책임: 같은 트랜잭션에서 방 행을 먼저 잠그고(가명 순번·정원 계산이 한 줄로 서게),
 * 이미 멤버인지·정원이 남았는지 확인한 뒤 부른다. 트랜잭션 경계는 호출 Service 가 갖는다.
 */
@Component
public class RoomMemberJoiner {

    private final RoomMemberReader roomMemberReader;
    private final RoomMemberWriter roomMemberWriter;
    private final ApplicationEventPublisher eventPublisher;

    public RoomMemberJoiner(RoomMemberReader roomMemberReader, RoomMemberWriter roomMemberWriter,
                            ApplicationEventPublisher eventPublisher) {
        this.roomMemberReader = roomMemberReader;
        this.roomMemberWriter = roomMemberWriter;
        this.eventPublisher = eventPublisher;
    }

    /** 새 멤버를 저장하고 저장된 멤버를 돌려준다. 가명은 같은 성별 기존 인원(0-based)을 순번으로 배정한다(#393). */
    public RoomMember join(RoomMember newcomer) {
        String gender = newcomer.gender();
        String pseudonym = gender == null ? null
                : PseudonymPool.assign(gender, (int) roomMemberReader.countByRoomIdAndGender(newcomer.roomId(), gender));
        RoomMember saved = roomMemberWriter.save(newcomer.withPseudonym(pseudonym));
        eventPublisher.publishEvent(new RoomMemberJoinedEvent(newcomer.roomId(), newcomer.userId(), newcomer.joinedAt()));
        return saved;
    }
}
