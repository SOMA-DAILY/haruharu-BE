package org.son.haruharu.domain.chat.storage;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatRoomPairJpaRepository extends JpaRepository<ChatRoomPairEntity, Long> {

    Optional<ChatRoomPairEntity> findByRoomIdAndLowUserIdAndHighUserId(Long roomId, Long lowUserId, Long highUserId);

    @Query(value = """
            select p.* from chat_room_pairs p
            join chat_rooms c on c.id = p.chat_room_id
            where p.room_id = :roomId and c.close_at is null and c.deleted_at is null
            order by p.chat_room_id
            """, nativeQuery = true)
    List<ChatRoomPairEntity> findOpenByRoomId(@Param("roomId") Long roomId);
}
