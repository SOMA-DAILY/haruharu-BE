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

    public ChatDeliveryService(ChatRoomReader chatRoomReader) {
        this.chatRoomReader = chatRoomReader;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deliverIfActive(Long chatRoomId, Runnable delivery) {
        if (chatRoomReader.lockIfPresent(chatRoomId) && chatRoomReader.findById(chatRoomId).isPresent()) {
            delivery.run();
        }
    }

    /**
     * 방이 아직 활성인지(물리·소프트 삭제 아님). 다른 인스턴스가 발행한 메시지를 로컬 전달하기 직전 재확인용(#453) —
     * 발행 인스턴스의 {@link #deliverIfActive} 이후 방이 닫혔으면 버린다. 잠금 없이 조회만 한다.
     */
    public boolean isActive(Long chatRoomId) {
        return chatRoomReader.findById(chatRoomId).isPresent();
    }
}
