package org.son.haruharu.domain.chat.storage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatRoomMemberJpaRepository extends JpaRepository<ChatRoomMemberEntity, Long> {

    Optional<ChatRoomMemberEntity> findByChatRoomIdAndUserId(Long chatRoomId, Long userId);

    List<ChatRoomMemberEntity> findByUserIdAndLeftAtIsNull(Long userId);

    List<ChatRoomMemberEntity> findByChatRoomId(Long chatRoomId);
}
