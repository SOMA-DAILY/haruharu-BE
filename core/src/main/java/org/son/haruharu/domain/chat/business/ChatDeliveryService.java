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
    private final org.son.haruharu.domain.chat.implement.ChatMessageReader messageReader;

    private final org.son.haruharu.domain.chat.implement.ChatMissionPresenter missionPresenter;

    public ChatDeliveryService(ChatRoomReader chatRoomReader, org.son.haruharu.domain.chat.implement.ChatMessageReader messageReader, org.son.haruharu.domain.chat.implement.ChatMissionPresenter missionPresenter) {
        this.chatRoomReader = chatRoomReader;
        this.messageReader = messageReader;
        this.missionPresenter = missionPresenter;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deliverMessage(org.son.haruharu.domain.chat.business.dto.ChatMessageBroadcast original,
            java.util.function.Consumer<org.son.haruharu.domain.chat.business.dto.ChatMessageBroadcast> delivery) {
        if (chatRoomReader.lockIfPresent(original.chatRoomId()) && chatRoomReader.findById(original.chatRoomId()).isPresent()) {
            messageReader.findById(original.messageId()).ifPresent(message ->
                    delivery.accept(org.son.haruharu.domain.chat.business.dto.ChatMessageBroadcast.from(message, missionPresenter.present(message.visibleMission()))));
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deliverIfActive(Long chatRoomId, Runnable delivery) {
        if (chatRoomReader.lockIfPresent(chatRoomId) && chatRoomReader.findById(chatRoomId).isPresent()) {
            delivery.run();
        }
    }
}
