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

    /**
     * 멤버 행을 비관적 락(SELECT ... FOR UPDATE)으로 잠근다. 영상 게시처럼 "멤버·슬롯당 1개" 불변식을 지켜야 하는
     * 쓰기에서 검사와 저장 사이의 경쟁을 멤버 단위로 직렬화한다. 반드시 트랜잭션 안에서 <b>그 검사 직전</b>에 호출한다.
     */
    public void lockById(Long id) {
        roomMemberRepository.lockById(id);
    }

    public RoomMember getById(Long id) {
        return roomMemberRepository.findById(id)
                .orElseThrow(RoomMemberNotFoundException::new);
    }

    public Optional<RoomMember> findById(Long id) {
        return roomMemberRepository.findById(id);
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

    /** 같은 방에 이 경로로 들어온 멤버 수. 초대코드 정원은 INVITE 인원만 센다(#513). */
    public long countByRoomIdAndEntryPath(Long roomId, RoomEntryPath entryPath) {
        return roomMemberRepository.countByRoomIdAndEntryPath(roomId, entryPath);
    }

    /** 같은 방·성별의 기존 멤버 수. 가명 배정의 입장 순번(0-based)으로 쓴다(#393). */
    public long countByRoomIdAndGender(Long roomId, String gender) {
        return roomMemberRepository.countByRoomIdAndGender(roomId, gender);
    }
}
