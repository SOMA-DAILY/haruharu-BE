package org.son.haruharu.domain.room.business;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.son.haruharu.domain.mission.implement.UploadSlot;
import org.son.haruharu.domain.room.business.command.CreateRoomCommand;
import org.son.haruharu.domain.room.business.dto.RoomResponse;
import org.son.haruharu.domain.room.business.exception.RoomInvalidScheduleException;
import org.son.haruharu.domain.room.business.exception.RoomNotFoundException;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RoomService {

    private final RoomReader roomReader;
    private final RoomWriter roomWriter;
    private final RoomMemberReader roomMemberReader;
    private final Clock clock;

    public RoomService(RoomReader roomReader, RoomWriter roomWriter,
                       RoomMemberReader roomMemberReader, Clock clock) {
        this.roomReader = roomReader;
        this.roomWriter = roomWriter;
        this.roomMemberReader = roomMemberReader;
        this.clock = clock;
    }

    /**
     * 초대코드 모델 방 생성(#311). 운영진/어드민이 스케줄 시각을 입력해 PENDING 방을 만든다.
     * 멤버 입장은 초대코드(#312), HTTP 진입점은 admin 세션이 담당한다 — 여기선 core 도메인 로직만.
     * 반환은 생성된 roomId(호출측이 코드 발급·응답 구성).
     */
    @Transactional
    public Long createRoom(CreateRoomCommand command) {
        validateSchedule(command);
        Room saved = roomWriter.save(Room.createScheduled(
                command.startsAt(), command.finalSelectionOpensAt(), command.finalSelectionClosesAt(),
                command.resultAt(), command.endsAt(), command.totalDays()));
        return saved.id();
    }

    private void validateSchedule(CreateRoomCommand c) {
        boolean allPresent = c.startsAt() != null && c.finalSelectionOpensAt() != null
                && c.finalSelectionClosesAt() != null && c.resultAt() != null && c.endsAt() != null;
        if (!allPresent || c.totalDays() <= 0) {
            throw new RoomInvalidScheduleException();
        }
        boolean ordered = c.startsAt().isBefore(c.finalSelectionOpensAt())
                && c.finalSelectionOpensAt().isBefore(c.finalSelectionClosesAt())
                && !c.finalSelectionClosesAt().isAfter(c.resultAt())
                && !c.resultAt().isAfter(c.endsAt());
        if (!ordered) {
            throw new RoomInvalidScheduleException();
        }
    }

    public RoomResponse getMyRoom(Long userId) {
        return findMyRoom(userId).orElseThrow(RoomNotFoundException::new);
    }

    /** 종료되지 않은 내 방(없으면 empty). 홈 화면 등 도메인 간 집계에서 404 대신 부재로 다루기 위한 변형. */
    public Optional<RoomResponse> findMyRoom(Long userId) {
        return roomReader.findCurrent(candidateRoomIds(userId))
            .map(room -> RoomResponse.from(room, roomMemberReader.getByRoomId(room.id()),
                    room.currentDayAt(LocalDateTime.now(clock), UploadSlot.dayStartHour())));
    }

    /** 종료되지 않은(PENDING/ACTIVE) 방 보유 여부. 매칭 큐 참가 차단 등 도메인 간 조율에 쓰인다. */
    public boolean hasCurrentRoom(Long userId) {
        List<Long> candidateRoomIds = candidateRoomIds(userId);
        if (candidateRoomIds.isEmpty()) {
            return false;
        }
        return roomReader.findCurrent(candidateRoomIds).isPresent();
    }

    /** 유저는 과거 방 이력(재매칭 방지용 보존)을 가질 수 있으므로, 후보 방 id를 모아 종료되지 않은 방을 한 번에 조회한다. */
    private List<Long> candidateRoomIds(Long userId) {
        return roomMemberReader.findByUserId(userId).stream()
            .map(RoomMember::roomId)
            .toList();
    }

    public RoomResponse getRoom(Long roomId, Long userId) {
        roomMemberReader.getByRoomIdAndUserId(roomId, userId); // 멤버십 인가 게이트
        Room room = roomReader.findById(roomId)
            .orElseThrow(RoomNotFoundException::new);
        List<RoomMember> members = roomMemberReader.getByRoomId(roomId);
        return RoomResponse.from(room, members,
                room.currentDayAt(LocalDateTime.now(clock), UploadSlot.dayStartHour()));
    }
}
