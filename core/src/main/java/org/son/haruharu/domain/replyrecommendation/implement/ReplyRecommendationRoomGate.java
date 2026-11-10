package org.son.haruharu.domain.replyrecommendation.implement;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.springframework.stereotype.Component;

/**
 * 이 방에서 영상 답장 추천이 켜져 있는지(#560). 분석 요청·추천·사건·답장 연결이 모두 이 판단을 따른다.
 * 켜짐 = 전체 스위치(enabled) AND (방 칸 rooms.reply_recommendation_enabled OR 설정 room-ids 에 포함).
 * room-ids 는 기존 시험 방 호환용이고, 비어 있어도 모든 방을 뜻하지 않는다.
 * 전체 스위치가 꺼져 있거나 room-ids 로 판단이 끝나면 방을 조회하지 않는다. 없는 방은 꺼짐이다.
 */
@Component
@RequiredArgsConstructor
public class ReplyRecommendationRoomGate {

    private final ReplyRecommendationProperties properties;
    private final RoomReader roomReader;

    public boolean isOpen(Long roomId) {
        if (!properties.enabled() || roomId == null) {
            return false;
        }
        if (properties.roomIds().contains(roomId)) {
            return true;
        }
        return roomReader.findById(roomId).map(Room::replyRecommendationEnabled).orElse(false);
    }
}
