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
}
