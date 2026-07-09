package org.son.haruharu.domain.chat.implement;

import org.springframework.stereotype.Component;

@Component
public class ChatMessageWriter {

    private final ChatMessageRepository chatMessageRepository;

    public ChatMessageWriter(ChatMessageRepository chatMessageRepository) {
        this.chatMessageRepository = chatMessageRepository;
    }

    public ChatMessage save(ChatMessage message) {
        return chatMessageRepository.save(message);
    }
}
