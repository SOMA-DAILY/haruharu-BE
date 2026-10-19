package org.son.haruharu.domain.room.business;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.son.haruharu.domain.room.business.command.CreateRoomCommand;
import org.son.haruharu.domain.room.business.dto.RoomResponse;
import org.son.haruharu.domain.room.business.exception.RoomAlreadyEndedException;
import org.son.haruharu.domain.room.business.exception.RoomInvalidScheduleException;
import org.son.haruharu.domain.room.business.exception.RoomNotFoundException;
import org.son.haruharu.domain.room.business.exception.RoomStartsAtInPastException;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.son.haruharu.domain.room.implement.RoomWriter;
import org.springframework.context.ApplicationEventPublisher;
import org.son.haruharu.domain.room.business.event.RoomEndedEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RoomService {

    private final RoomReader roomReader;
    private final RoomWriter roomWriter;
    private final RoomMemberReader roomMemberReader;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public RoomService(RoomReader roomReader, RoomWriter roomWriter,
                       RoomMemberReader roomMemberReader, Clock clock,
                       ApplicationEventPublisher events) {
        this.roomReader = roomReader;
        this.roomWriter = roomWriter;
        this.roomMemberReader = roomMemberReader;
        this.clock = clock;
        this.events = events;
    }

    /**
     * 초대코드 모델 방 생성(#311). 운영진/어드민이 스케줄 시각을 입력해 PENDING 방을 만든다.
     * 멤버 입장은 초대코드(#312), HTTP 진입점은 admin 세션이 담당한다 — 여기선 core 도메인 로직만.
     * 반환은 생성된 roomId(호출측이 코드 발급·응답 구성).
     */
    @Transactional
    public Long createRoom(CreateRoomCommand command) {
        validateSchedule(command);
        validateNotInPast(command, LocalDateTime.now(clock));
        Room saved = roomWriter.save(Room.createScheduled(
                command.startsAt(), command.finalSelectionOpensAt(), command.finalSelectionClosesAt(),
                command.resultAt(), command.endsAt(), command.totalDays()).withTestType(command.isTest()));
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

    /**
     * 시작 시각이 과거면 거부(#320). startsAt 하나만 본다 — 나머지 4개는 순서 규약상 startsAt 보다 뒤라
     * 자동으로 미래가 된다. 유예(grace)는 두지 않는다: 어드민은 방을 미리 만드는 게 정상 워크플로다.
     */
    private void validateNotInPast(CreateRoomCommand command, LocalDateTime now) {
        if (command.startsAt().isBefore(now)) {
            throw new RoomStartsAtInPastException();
        }
    }

    /**
     * 운영진 방 취소(#320). status 를 CANCELLED 로 못박아 statusAt 파생과 무관하게 sticky 하게 만든다
     * — 취소된 방은 reconciler 전이·초대코드 입장에서 모두 제외된다.
     * 이미 종료(ENDED)된 방은 되돌리지 않는다.
     */
    @Transactional
    public void cancel(Long roomId) {
        Room room = roomReader.findByIdForUpdate(roomId)
                .orElseThrow(RoomNotFoundException::new);
        if (room.statusAt(LocalDateTime.now(clock)) == RoomStatus.ENDED) {
            throw new RoomAlreadyEndedException();
        }
        roomWriter.save(room.cancelled());
        events.publishEvent(new RoomEndedEvent(roomId));
    }

    @Transactional
    public Room updateTestType(Long roomId, boolean isTest) {
        Room room = roomReader.findByIdForUpdate(roomId).orElseThrow(RoomNotFoundException::new);
        return roomWriter.save(room.withTestType(isTest));
    }

    public RoomResponse getMyRoom(Long userId) {
        return findMyRoom(userId).orElseThrow(RoomNotFoundException::new);
    }

    /** 종료되지 않은 내 방(없으면 empty). 홈 화면 등 도메인 간 집계에서 404 대신 부재로 다루기 위한 변형. */
    public Optional<RoomResponse> findMyRoom(Long userId) {
        LocalDateTime now = LocalDateTime.now(clock);
        return roomReader.findCurrent(candidateRoomIds(userId), now)
            .map(room -> RoomResponse.from(room, roomMemberReader.getByRoomId(room.id()),
                    room.currentDayAt(now), now));
    }

    /** 종료되지 않은 방 보유 여부. 테스트 방 생성 등 도메인 간 조율에 쓰인다. */
    public boolean hasCurrentRoom(Long userId) {
        List<Long> candidateRoomIds = candidateRoomIds(userId);
        if (candidateRoomIds.isEmpty()) {
            return false;
        }
        return roomReader.findCurrent(candidateRoomIds, LocalDateTime.now(clock)).isPresent();
    }

    /** 유저는 과거 방 이력을 가질 수 있으므로, 후보 방 id를 모아 종료되지 않은 방을 한 번에 조회한다. */
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
        LocalDateTime now = LocalDateTime.now(clock);
        return RoomResponse.from(room, members, room.currentDayAt(now), now);
    }
}
