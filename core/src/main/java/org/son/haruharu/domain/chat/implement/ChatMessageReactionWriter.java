package org.son.haruharu.domain.chat.implement;

import org.springframework.stereotype.Component;

@Component
public class ChatMessageReactionWriter {

    private final ChatMessageReactionRepository repository;

    public ChatMessageReactionWriter(ChatMessageReactionRepository repository) {
        this.repository = repository;
    }

    public boolean insertIfAbsent(ChatMessageReaction reaction) {
        return repository.insertIfAbsent(reaction);
    }

    public ChatMessageReaction save(ChatMessageReaction reaction) {
        return repository.save(reaction);
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }
}
