package org.son.haruharu.domain.chat.implement;

import org.springframework.stereotype.Component;

@Component
public class ChatRoomPairWriter {

    private final ChatRoomPairRepository repository;

    public ChatRoomPairWriter(ChatRoomPairRepository repository) {
        this.repository = repository;
    }

    public ChatRoomPair save(ChatRoomPair pair) {
        return repository.save(pair);
    }
}
