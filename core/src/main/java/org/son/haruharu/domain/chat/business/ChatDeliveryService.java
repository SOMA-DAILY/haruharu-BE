package org.son.haruharu.domain.chat.business;

import org.son.haruharu.domain.chat.implement.ChatRoomReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 커밋 후 전달의 트랜잭션 경계. 외부 전달이 끝날 때까지 방 잠금을 유지한다. */
@Service
@Transactional(readOnly = true)
public class ChatDeliveryService {
    private final ChatRoomReader chatRoomReader;
    private final ChatRoomLifecyclePolicy lifecycle;
    private final org.son.haruharu.domain.chat.implement.ChatRoomMemberReader members;

    public ChatDeliveryService(ChatRoomReader chatRoomReader, ChatRoomLifecyclePolicy lifecycle,org.son.haruharu.domain.chat.implement.ChatRoomMemberReader members) {
        this.chatRoomReader = chatRoomReader;
        this.lifecycle = lifecycle;
        this.members = members;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deliverIfActive(Long chatRoomId, Runnable delivery) {
        if (chatRoomReader.lockIfPresent(chatRoomId) && isActive(chatRoomId)) {
            delivery.run();
        }
    }

    /**
     * 방이 아직 활성인지(삭제되지 않았고 GROUP·PAIR의 close_at이 비어 있는지). 다른 인스턴스의 메시지를 로컬 전달하기 직전 재확인용(#453) —
     * 발행 인스턴스의 {@link #deliverIfActive} 이후 방이 닫혔으면 버린다. 잠금 없이 조회만 한다.
     */
    public boolean isActiveFor(Long chatRoomId,Long userId) {
        return isActive(chatRoomId) && members.findActiveMembership(chatRoomId,userId).isPresent();
    }
    public java.util.Set<Long> activeUserIds(Long chatRoomId) {
        return members.findByChatRoomId(chatRoomId).stream().filter(m->!m.hasLeft()).map(m->m.userId()).collect(java.util.stream.Collectors.toSet());
    }
    public boolean isActive(Long chatRoomId) {
        return chatRoomReader.findById(chatRoomId).filter(lifecycle::isActive).isPresent();
    }
}
