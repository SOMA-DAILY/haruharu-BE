package org.son.haruharu.domain.room.implement;

import java.util.Optional;

import org.son.haruharu.domain.room.business.exception.DemoRoomNotFoundException;
import org.springframework.stereotype.Component;

/**
 * 데모 방 조회 단일 진입점(#266). "호출자가 속한 비종료 데모 방"이라는 규칙을 한 곳에 두어
 * 데모 유스케이스들(방 라이프사이클·최종선택·리셋)이 같은 규칙을 중복 구현하지 않게 한다.
 * <p>
 * 서비스끼리 서로를 주입해 순환 의존이 생기는 것을 피하려고 협력자(implement)로 분리했다.
 * 유저 본인 방 조회라 배치 격리(demo=false)와는 무관하다.
 */
@Component
public class DemoRoomFinder {

    private final RoomReader roomReader;
    private final RoomMemberReader roomMemberReader;

    public DemoRoomFinder(RoomReader roomReader, RoomMemberReader roomMemberReader) {
        this.roomReader = roomReader;
        this.roomMemberReader = roomMemberReader;
    }

    public Optional<Room> find(Long userId) {
        return roomMemberReader.findByUserId(userId).stream()
                .map(member -> roomReader.findById(member.roomId()))
                .flatMap(Optional::stream)
                .filter(room -> room.demo() && room.status() != RoomStatus.ENDED)
                .findFirst();
    }

    public Room require(Long userId) {
        return find(userId).orElseThrow(DemoRoomNotFoundException::new);
    }
}
