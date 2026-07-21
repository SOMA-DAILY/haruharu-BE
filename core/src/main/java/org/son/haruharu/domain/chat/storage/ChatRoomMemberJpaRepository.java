package org.son.haruharu.domain.chat.storage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatRoomMemberJpaRepository extends JpaRepository<ChatRoomMemberEntity, Long> {

    Optional<ChatRoomMemberEntity> findByChatRoomIdAndUserId(Long chatRoomId, Long userId);

    List<ChatRoomMemberEntity> findByUserIdAndLeftAtIsNull(Long userId);

    List<ChatRoomMemberEntity> findByChatRoomId(Long chatRoomId);

    // chat_room_members는 BaseEntity 미상속(soft-delete 아님)이라 JPQL 벌크 delete가 물리 삭제로 동작한다.
    @Modifying
    @Query("delete from ChatRoomMemberEntity m where m.chatRoomId = :chatRoomId")
    void deleteByChatRoomId(@Param("chatRoomId") Long chatRoomId);
}
