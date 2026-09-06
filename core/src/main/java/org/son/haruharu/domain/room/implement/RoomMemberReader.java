package org.son.haruharu.domain.room.implement;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.room.implement.exception.NotRoomMemberException;
import org.son.haruharu.domain.room.storage.exception.RoomMemberNotFoundException;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RoomMemberReader {

    private final RoomMemberRepository roomMemberRepository;

    public RoomMember getById(Long id) {
        return roomMemberRepository.findById(id)
                .orElseThrow(RoomMemberNotFoundException::new);
    }

    public List<RoomMember> findByUserId(Long userId) {
        return roomMemberRepository.findByUserId(userId);
    }

    public List<RoomMember> getByRoomId(Long roomId) {
        return roomMemberRepository.findByRoomId(roomId);
    }

    public List<RoomMember> findByRoomIds(Collection<Long> roomIds) {
        if (roomIds.isEmpty()) {
            return List.of();
        }
        return roomMemberRepository.findByRoomIds(roomIds);
    }

    /** 방-유저 멤버십 확인. 참여자가 아니면 예외(거의 모든 방 API의 인가 게이트). */
    public RoomMember getByRoomIdAndUserId(Long roomId, Long userId) {
        return roomMemberRepository.findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(NotRoomMemberException::new);
    }

    public Optional<RoomMember> findByRoomIdAndUserId(Long roomId, Long userId) {
        return roomMemberRepository.findByRoomIdAndUserId(roomId, userId);
    }

    public long countByRoomId(Long roomId) {
        return roomMemberRepository.countByRoomId(roomId);
    }

    /** 같은 방·성별의 기존 멤버 수. 가명 배정의 입장 순번(0-based)으로 쓴다(#393). */
    public long countByRoomIdAndGender(Long roomId, String gender) {
        return roomMemberRepository.countByRoomIdAndGender(roomId, gender);
    }
}
