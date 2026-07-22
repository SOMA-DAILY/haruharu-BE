package org.son.haruharu.domain.chat.implement;

import java.util.List;
import java.util.Optional;

public interface ChatRoomMemberRepository {
    ChatRoomMember save(ChatRoomMember member);
    Optional<ChatRoomMember> findByChatRoomIdAndUserId(Long chatRoomId, Long userId);
    /** 내가 나가지 않은(활성) 방 멤버십 목록. */
    List<ChatRoomMember> findActiveByUserId(Long userId);
    /** 특정 방의 전체 멤버(상대 찾기용). */
    List<ChatRoomMember> findByChatRoomId(Long chatRoomId);

    /** 데모 정리 전용 삭제(#266). UNIQUE(chat_room_id,user_id) 재생성을 위해 제거한다. */
    void deleteByChatRoomId(Long chatRoomId);
}
