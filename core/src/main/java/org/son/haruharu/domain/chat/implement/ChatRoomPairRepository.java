package org.son.haruharu.domain.chat.implement;

import java.util.List;
import java.util.Optional;

public interface ChatRoomPairRepository {
    ChatRoomPair save(ChatRoomPair pair);
    Optional<ChatRoomPair> findByChatRoomId(Long chatRoomId);
    Optional<ChatRoomPair> findByRoomIdAndUsers(Long roomId, Long lowUserId, Long highUserId);
    /** 원본 방의 아직 닫히지 않은 PAIR 채팅방의 쌍(방 종료 처리용, 채팅방 id 순). */
    List<ChatRoomPair> findOpenByRoomId(Long roomId);
}
