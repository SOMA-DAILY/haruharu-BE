package org.son.haruharu.domain.room.business;

import java.time.Clock;
import java.time.LocalDateTime;

import org.son.haruharu.domain.mission.implement.UploadSlot;
import org.son.haruharu.domain.room.business.dto.FinalSelectionResponse;
import org.son.haruharu.domain.room.business.event.MemberSelectedEvent;
import org.son.haruharu.domain.room.business.event.MutualMatchEvent;
import org.son.haruharu.domain.room.business.exception.AlreadySelectedException;
import org.son.haruharu.domain.room.business.exception.CannotSelectSelfException;
import org.son.haruharu.domain.room.business.exception.NotFinalSelectionDayException;
import org.son.haruharu.domain.room.business.exception.RoomNotFoundException;
import org.son.haruharu.domain.room.business.exception.SelectionClosedException;
import org.son.haruharu.domain.room.implement.FinalSelection;
import org.son.haruharu.domain.room.implement.FinalSelectionReader;
import org.son.haruharu.domain.room.implement.FinalSelectionWriter;
import org.son.haruharu.domain.room.implement.MatchResult;
import org.son.haruharu.domain.room.implement.MatchResultWriter;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.storage.exception.RoomMemberNotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Day3 최종선택. 멤버당 1회 확정(변경 불가)이며, 상대도 나를 선택했으면
 * 즉시 상호매칭(MatchResult, canonical ordering 1쌍 1행)을 성사시킨다.
 */
@Service
@Transactional(readOnly = true)
public class FinalSelectionService {

    /** 최종선택이 열리는 Day. 기본 프로필 공개(PROFILE_REVEAL_DAY)와 동일 기준. */
    private static final int FINAL_SELECTION_DAY = 3;

    private final RoomReader roomReader;
    private final RoomMemberReader roomMemberReader;
    private final FinalSelectionReader finalSelectionReader;
    private final FinalSelectionWriter finalSelectionWriter;
    private final MatchResultWriter matchResultWriter;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public FinalSelectionService(RoomReader roomReader, RoomMemberReader roomMemberReader,
                                 FinalSelectionReader finalSelectionReader, FinalSelectionWriter finalSelectionWriter,
                                 MatchResultWriter matchResultWriter, ApplicationEventPublisher eventPublisher,
                                 Clock clock) {
        this.roomReader = roomReader;
        this.roomMemberReader = roomMemberReader;
        this.finalSelectionReader = finalSelectionReader;
        this.finalSelectionWriter = finalSelectionWriter;
        this.matchResultWriter = matchResultWriter;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public FinalSelectionResponse select(Long roomId, Long selectedMemberId, Long userId) {
        RoomMember me = roomMemberReader.getByRoomIdAndUserId(roomId, userId); // 멤버십 인가 게이트
        Room room = roomReader.findById(roomId)
                .orElseThrow(RoomNotFoundException::new);
        LocalDateTime now = LocalDateTime.now(clock);
        if (room.currentDayAt(now, UploadSlot.dayStartHour()) < FINAL_SELECTION_DAY) {
            throw new NotFinalSelectionDayException();
        }
        if (now.isBefore(selectionOpensAt(room))) {
            throw new NotFinalSelectionDayException();
        }
        // 결과 발표(22:00) 이후 선택 불가 — 발표 시점의 미매칭 확정(위로 구름 지급, #143)이 뒤집히지 않게 한다.
        if (!now.isBefore(selectionClosesAt(room))) {
            throw new SelectionClosedException();
        }
        // 기권: 아무도 선택하지 않고 최종선택을 확정한다. 상호매칭 판정·이벤트 없이 selectedMemberId=null 행만 남긴다.
        if (selectedMemberId == null) {
            if (finalSelectionReader.hasSelected(me.id())) {
                throw new AlreadySelectedException(); // 1회 확정
            }
            finalSelectionWriter.save(FinalSelection.of(me.id(), null, now));
            return new FinalSelectionResponse(null, false);
        }
        if (selectedMemberId.equals(me.id())) {
            throw new CannotSelectSelfException();
        }
        RoomMember selected = roomMemberReader.getById(selectedMemberId);
        if (!selected.roomId().equals(roomId)) {
            throw new RoomMemberNotFoundException(); // 같은 방 멤버만 선택 가능
        }
        if (finalSelectionReader.hasSelected(me.id())) {
            throw new AlreadySelectedException(); // 1회 확정
        }

        finalSelectionWriter.save(FinalSelection.of(me.id(), selectedMemberId, now));

        boolean matched = isMutual(selectedMemberId, me.id());
        if (matched) {
            MatchResult savedMatch = matchResultWriter.save(MatchResult.of(me.id(), selectedMemberId, now));
            eventPublisher.publishEvent(
                    new MutualMatchEvent(roomId, savedMatch.id(), me.userId(), selected.userId()));
        } else {
            eventPublisher.publishEvent(new MemberSelectedEvent(roomId, selected.userId()));
        }

        return new FinalSelectionResponse(selectedMemberId, matched);
    }

    /**
     * 최종선택 오픈 시각 = 마지막 날 마지막 슬롯 종료 시각(Day3 18:00).
     * 하루 경계가 09:00이라 Day3 심야(익일 00~09시)에도 열려 있어야 하므로 시(hour) 비교가 아닌 시각 비교로 판정한다.
     * 선행 currentDayAt 게이트가 PENDING(startedAt null)을 걸러 dateOfDay 호출이 안전하다.
     */
    private LocalDateTime selectionOpensAt(Room room) {
        int opensHour = UploadSlot.ofDay(FINAL_SELECTION_DAY).getLast().endHour();
        return room.dateOfDay(FINAL_SELECTION_DAY, UploadSlot.dayStartHour())
                .atStartOfDay().plusHours(opensHour);
    }

    /** 최종선택 마감 = 결과 발표 시각(Day3 22:00). 오픈과 같은 앵커(dateOfDay)에서 파생한다. */
    private LocalDateTime selectionClosesAt(Room room) {
        return room.dateOfDay(FINAL_SELECTION_DAY, UploadSlot.dayStartHour())
                .atStartOfDay().plusHours(Room.RESULT_ANNOUNCE_HOUR);
    }

    /** 상대(selectedMemberId)도 나(meId)를 최종선택했는지. 상대가 기권(selectedMemberId=null)했으면 미성립. */
    private boolean isMutual(Long selectedMemberId, Long meId) {
        return finalSelectionReader.findByRoomMemberId(selectedMemberId)
                .map(theirSelection -> theirSelection.selectedMemberId() != null
                        && theirSelection.selectedMemberId().equals(meId))
                .orElse(false);
    }
}
