package org.son.haruharu.domain.chat.storage;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.chat.implement.ChatRoomMember;
import org.son.haruharu.domain.chat.implement.ChatRoomMemberRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ChatRoomMemberRepositoryImpl implements ChatRoomMemberRepository {

    private final ChatRoomMemberJpaRepository chatRoomMemberJpaRepository;

    @Override
    public ChatRoomMember save(ChatRoomMember member) {
        return chatRoomMemberJpaRepository.save(ChatRoomMemberEntity.from(member)).toDomain();
    }

    @Override
    public Optional<ChatRoomMember> findByChatRoomIdAndUserId(Long chatRoomId, Long userId) {
        return chatRoomMemberJpaRepository.findByChatRoomIdAndUserId(chatRoomId, userId)
                .map(ChatRoomMemberEntity::toDomain);
    }

    @Override
    public List<ChatRoomMember> findActiveByUserId(Long userId) {
        return chatRoomMemberJpaRepository.findByUserIdAndLeftAtIsNull(userId).stream()
                .map(ChatRoomMemberEntity::toDomain)
                .toList();
    }

    @Override
    public List<ChatRoomMember> findByChatRoomId(Long chatRoomId) {
        return chatRoomMemberJpaRepository.findByChatRoomId(chatRoomId).stream()
                .map(ChatRoomMemberEntity::toDomain)
                .toList();
    }

    @Override
    public List<ChatRoomMember> findByChatRoomIdForUpdate(Long chatRoomId) {
        return chatRoomMemberJpaRepository.findByChatRoomIdForUpdate(chatRoomId).stream()
                .map(ChatRoomMemberEntity::toDomain)
                .toList();
    }

    @Override
    public void deleteByChatRoomId(Long chatRoomId) {
        chatRoomMemberJpaRepository.deleteByChatRoomId(chatRoomId);
    }
}
